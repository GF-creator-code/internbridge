'use client'

import { useState, useEffect } from 'react'
import { createSSEWithBackoff } from '@/lib/sse'
import { BiLogIn, BiLogOut, BiSave, BiBriefcase, BiPlay, BiStop } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Label } from '@/components/ui/label'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import AnalysisContent from '@/app/offertoday/analysis/AnalysisContent'
import PageHeader from '@/app/components/PageHeader'
import PlatformTip from '@/app/components/PlatformTip'
import ToggleSwitch, { toBool } from '@/app/components/ToggleSwitch'
import KeywordListsField, { parseKeywordList, serializeKeywordList } from '@/app/components/KeywordListsField'
import { API_BASE } from '@/lib/api'
import { parseKeywordsForDisplay, serializeKeywords } from '@/lib/keywords'
import FeedbackBar from '@/app/components/FeedbackBar'
import ConfigLoadError from '@/app/components/ConfigLoadError'
import { useTaskControl } from '@/lib/useTaskControl'
import { useTaskProgress } from '@/lib/useTaskProgress'
import TaskProgressPanel from '@/app/components/TaskProgressPanel'

interface OfferTodayConfig {
  id?: number
  keywords?: string
  city?: string
  account?: string
  password?: string
  includeKeywords?: string
  excludeKeywords?: string
  enableAi?: boolean
  waitTime?: number
}

export default function OfferTodayPage() {
  const [isLoggedIn, setIsLoggedIn] = useState(false)
  // 投递任务的启停与失败反馈统一由 useTaskControl 提供
  const task = useTaskControl(API_BASE)
  const isDelivering = task.isRunning
  const progress = useTaskProgress('offertoday')
  const [checkingLogin, setCheckingLogin] = useState(true)
  const [showLogoutDialog, setShowLogoutDialog] = useState(false)
  const [showSaveDialog, setShowSaveDialog] = useState(false)
  const [saveResult, setSaveResult] = useState<{ success: boolean; message: string } | null>(null)
  const [showLogoutResultDialog, setShowLogoutResultDialog] = useState(false)
  const [logoutResult, setLogoutResult] = useState<{ success: boolean; message: string } | null>(null)
  const [backendAvailable, setBackendAvailable] = useState(true)

  const [config, setConfig] = useState<OfferTodayConfig>({ keywords: '', city: '香港', account: '', password: '', includeKeywords: '', excludeKeywords: '', enableAi: true, waitTime: 2 })
  const [loadingConfig, setLoadingConfig] = useState(true)
  // 配置加载失败要显式告诉用户，否则空表单会被误当成真实配置
  const [configLoadError, setConfigLoadError] = useState(false)
  // 保存前的配置快照。用来判断「改了但没保存」——
  // 原先六个平台都没有这个判断，用户改了关键词直接点开始投递，
  // 跑的还是旧配置，而且界面完全看不出来。
  const [savedConfig, setSavedConfig] = useState<OfferTodayConfig | null>(null)
  const dirty = savedConfig !== null && (Object.keys(config) as (keyof OfferTodayConfig)[])
    .some(key => savedConfig[key] !== config[key])

  useEffect(() => {
    if (typeof window === 'undefined' || typeof EventSource === 'undefined') {
      console.warn('[OfferToday] EventSource 不可用，无法连接SSE')
      setCheckingLogin(false)
      return
    }

    const client = createSSEWithBackoff(`${API_BASE}/api/jobs/login-status/stream`, {
      onOpen: () => console.log('[OfferToday SSE] 连接已打开'),
      onError: (e, attempt, delay) => {
        console.warn(`[OfferToday SSE] 连接错误，第${attempt}次重连，延迟 ${delay}ms`, e)
        setCheckingLogin(false)
      },
      // 断线重连后补一次登录态拉取。SSE 断开期间服务端推的状态变化已经丢了，
      // 只依赖推送的话，页面会永远停在「请先登录」——而用户其实早就登录了。
      onReconnect: () => {
        void (async () => {
          try {
            const res = await fetch(`${API_BASE}/api/offertoday/login-status`)
            if (!res.ok) return
            const data = await res.json()
            if (typeof data.isLoggedIn === 'boolean') {
              setIsLoggedIn(data.isLoggedIn)
              setCheckingLogin(false)
            }
          } catch {
            // 补偿拉取失败不影响后续重连
          }
        })()
      },
      listeners: [
        {
          name: 'connected',
          handler: (event) => {
            try {
              const data = JSON.parse(event.data)
              setIsLoggedIn(data.offertodayLoggedIn || false)
              setCheckingLogin(false)
            } catch (error) {
              console.error('[OfferToday SSE] 解析连接消息失败:', error)
            }
          },
        },
        {
          name: 'login-status',
          handler: (event) => {
            try {
              const data = JSON.parse(event.data)
              if (data.platform === 'offertoday') {
                setIsLoggedIn(data.isLoggedIn)
                setCheckingLogin(false)
              }
            } catch (error) {
              console.error('[OfferToday SSE] 解析登录状态消息失败:', error)
            }
          },
        },
        { name: 'ping', handler: () => {} },
      ],
    })

    return () => client.close()
  }, [])

  const fetchAllData = async () => {
    try {
      const res = await fetch(`${API_BASE}/api/offertoday/config`)
      const data = await res.json()
      if (data.config) {
        const normalized = { ...data.config }
        normalized.keywords = parseKeywordsForDisplay(data.config.keywords)
        normalized.includeKeywords = parseKeywordList(data.config.includeKeywords)
        normalized.excludeKeywords = parseKeywordList(data.config.excludeKeywords)
        normalized.enableAi = toBool(data.config.enableAi, true)
        normalized.waitTime = Number(data.config.waitTime) > 0 ? Number(data.config.waitTime) : 2
        setConfig(normalized)
        setSavedConfig(normalized)
      }
    } catch (e) {
      console.error('[OfferToday] 获取配置失败:', e)
      setConfigLoadError(true)
    } finally {
      setLoadingConfig(false)
    }
  }

  useEffect(() => { fetchAllData() }, [])

  useEffect(() => {
    (async () => {
      try {
        const res = await fetch(`${API_BASE}/api/offertoday/config`, { method: 'GET' })
        const ok = !!res && res.ok
        setBackendAvailable(ok)
        if (ok) {
          await fetchAllData()
        } else {
          setLoadingConfig(false)
        }
      } catch (e) {
        setBackendAvailable(false)
        setLoadingConfig(false)
      }
    })()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleLogin = async () => {
    try {
      setCheckingLogin(true)
      await fetch(`${API_BASE}/api/offertoday/login`, { method: 'POST' })
    } catch (error) {
      console.error('[OfferToday] 触发登录失败:', error)
    } finally {
      setTimeout(() => setCheckingLogin(false), 3000)
    }
  }

  // 启动/停止的统一调用与失败反馈见 lib/useTaskControl。
  // 原先这里把后端返回的中文原因丢进了 console，界面上什么都不显示。
  const handleStartDelivery = async () => {
    // 有未保存的修改时拦一道：不然跑的是旧配置，而用户以为改了就是改了
    if (dirty) {
      task.report({ kind: 'error', title: '请先保存配置', message: '当前配置有尚未保存的修改，直接开始投递会使用上一次保存的关键词和城市。' })
      return
    }
    await task.start('offertoday')
  }

  const handleStopDelivery = async () => {
    await task.stop('offertoday')
  }

  const triggerLogout = async () => {
    try {
      const response = await fetch(`${API_BASE}/api/offertoday/logout`, { method: 'POST' })
      const data = await response.json()
      setIsLoggedIn(false)
      setLogoutResult({ success: data.success, message: data.success ? '已退出登录，Cookie已清空。' : data.message })
      setShowLogoutResultDialog(true)
    } catch (error) {
      setLogoutResult({ success: false, message: '退出登录失败：网络或服务异常。' })
      setShowLogoutResultDialog(true)
    }
  }

  const handleSaveConfig = async () => {
    try {
      const payload = {
        ...config,
        keywords: serializeKeywords(config.keywords),
        includeKeywords: serializeKeywordList(config.includeKeywords),
        excludeKeywords: serializeKeywordList(config.excludeKeywords),
      }
      const response = await fetch(`${API_BASE}/api/offertoday/config`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
      if (response.ok) {
        try { await fetch(`${API_BASE}/api/cookie/save?platform=offertoday`, { method: 'POST' }) } catch {}
        await fetchAllData()
        setSaveResult({ success: true, message: '保存成功，配置已更新。新配置在下次启动投递任务时生效。' })
      } else {
        setSaveResult({ success: false, message: '保存失败：后端返回异常状态。' })
      }
      setShowSaveDialog(true)
    } catch (error) {
      console.error('[OfferToday] 保存配置失败:', error)
      setSaveResult({ success: false, message: '保存失败：网络或服务异常。' })
      setShowSaveDialog(true)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        icon={<BiBriefcase className="text-2xl" />}
        title="OfferToday"
        subtitle="香港岗位自动投递，只处理「實習」类型卡片。"
        iconClass="text-white"
        accentBgClass="bg-sky-600"
        actions={
          <div className="flex items-center gap-2">
            {checkingLogin ? (
              <Button size="sm" disabled className="rounded-full bg-gray-300 text-gray-600 cursor-not-allowed px-4 shadow">
                <BiPlay className="mr-1" /> 检查登录中...
              </Button>
            ) : !isLoggedIn ? (
              <Button onClick={handleLogin} size="sm" className="rounded-full bg-gradient-to-r from-sky-500 to-blue-600 hover:from-sky-600 hover:to-blue-700 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105">
                <BiLogIn className="mr-1" /> 登录 OfferToday
              </Button>
            ) : isDelivering ? (
              <Button onClick={handleStopDelivery} size="sm" className="rounded-full bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105">
                <BiStop className="mr-1" /> 停止投递
              </Button>
            ) : (
              <Button onClick={handleStartDelivery} size="sm" className="rounded-full bg-gradient-to-r from-teal-500 to-green-500 hover:from-teal-600 hover:to-green-600 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105">
                <BiPlay className="mr-1" /> 开始投递
              </Button>
            )}
            <Button onClick={() => setShowLogoutDialog(true)} size="sm" className="rounded-full bg-gradient-to-r from-red-500 to-pink-500 hover:from-red-600 hover:to-pink-600 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105">
              <BiLogOut className="mr-1" /> 退出登录
            </Button>
            <Button onClick={handleSaveConfig} size="sm" className="rounded-full bg-gradient-to-r from-blue-500 to-indigo-500 hover:from-blue-600 hover:to-indigo-600 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105">
              <BiSave className="mr-1" /> 保存配置
            </Button>
          </div>
        }
      />
      <FeedbackBar feedback={task.feedback} onDismiss={task.clearFeedback} />
      {dirty && (
        <p role="status" className="text-sm text-amber-600 dark:text-amber-400">
          有尚未保存的配置修改。请先点「保存配置」，再开始投递任务，否则会使用上一次保存的设置。
        </p>
      )}
      {configLoadError && (
        <ConfigLoadError onRetry={() => { setConfigLoadError(false); void fetchAllData() }} />
      )}
      <TaskProgressPanel
        latest={progress.latest}
        history={progress.history}
        connected={progress.connected}
        isRunning={isDelivering}
        onClear={progress.clear}
      />

      <Tabs defaultValue="config" className="w-full">
        <TabsList className="grid w-full grid-cols-2">
          <TabsTrigger value="config">平台配置</TabsTrigger>
          <TabsTrigger value="analytics">投递分析</TabsTrigger>
        </TabsList>

        <TabsContent value="config" className="space-y-6 mt-6">
          <PlatformTip title="平台须知">
            <p>香港站，岗位全部在香港，薪资以 HKD 计。填好账号密码后点「登录 OfferToday」，自动登录失败时请在浏览器窗口手动完成。</p>
            <p>只对卡片类型为「實習」的岗位投递，打招呼语按岗位 JD 用 AI 生成。</p>
          </PlatformTip>

          <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <BiBriefcase className="text-primary" />
                配置参数
              </CardTitle>
            </CardHeader>
            <CardContent>
              {loadingConfig ? (
                <p className="text-sm text-muted-foreground">配置加载中...</p>
              ) : (
                <>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="space-y-2 md:col-span-2">
                    <Label>搜索关键词（逗号分隔）</Label>
                    <Input
                      placeholder="如：實習, 數據分析, 市場營銷, 金融, 會計"
                      value={config.keywords || ''}
                      onChange={(e) => setConfig((c) => ({ ...c, keywords: e.target.value }))}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="offertoday-city">城市</Label>
                    <Select id="offertoday-city" value={config.city || '香港'}
                      onChange={(e) => setConfig((c) => ({ ...c, city: e.target.value }))}>
                      <option value="香港">香港</option>
                    </Select>
                    <p className="text-xs text-muted-foreground">OfferToday 目前只提供香港岗位。</p>
                  </div>
                  <div className="space-y-2">
                    <Label>登录账号（邮箱或手机号）</Label>
                    <Input
                      placeholder="OfferToday 账号"
                      value={config.account || ''}
                      onChange={(e) => setConfig((c) => ({ ...c, account: e.target.value }))}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>登录密码</Label>
                    <Input
                      type="password"
                      placeholder="已保存，留空则不修改"
                      value={config.password || ''}
                      onChange={(e) => setConfig((c) => ({ ...c, password: e.target.value }))}
                    />
                  </div>
                </div>
                <div className="mt-4 space-y-3 rounded-lg border bg-muted/20 p-4">
                  <p className="text-sm font-medium">投递行为</p>
                    <p className="text-xs text-muted-foreground">全局黑名单（侧边栏「全局配置」）同样生效：命中的公司/职位自动跳过。</p>
                  <KeywordListsField
                    includeValue={config.includeKeywords || ''}
                    excludeValue={config.excludeKeywords || ''}
                    onIncludeChange={(v) => setConfig((c) => ({ ...c, includeKeywords: v }))}
                    onExcludeChange={(v) => setConfig((c) => ({ ...c, excludeKeywords: v }))}
                    includePlaceholder="實習類型卡片已在采集层过滤，通常留空"
                    excludePlaceholder="留空即可保留所有方向"
                  />
                  <div className="flex flex-col gap-3 border-t pt-3 sm:flex-row sm:items-center sm:justify-between">
                    <div className="space-y-1">
                      <Label htmlFor="enableAi" className="text-base font-medium">AI 生成打招呼语</Label>
                      <p className="text-sm text-muted-foreground">按岗位 JD 自动生成打招呼语；关闭后使用默认模板。</p>
                    </div>
                    <ToggleSwitch
                      id="enableAi"
                      label="AI 生成打招呼语"
                      checked={!!config.enableAi}
                      onToggle={() => setConfig((c) => ({ ...c, enableAi: !c.enableAi }))}
                    />
                  </div>
                  <div className="flex flex-col gap-3 border-t pt-3 sm:flex-row sm:items-center sm:justify-between">
                    <div className="space-y-1">
                      <Label htmlFor="waitTime" className="text-base font-medium">投递间隔（秒）</Label>
                      <p className="text-sm text-muted-foreground">每两次投递之间的等待时间，建议 2–5 秒。</p>
                    </div>
                    <Input
                      id="waitTime"
                      type="number"
                      min={1}
                      max={30}
                      className="w-24"
                      value={config.waitTime ?? 2}
                      onChange={(e) => setConfig((c) => ({ ...c, waitTime: Number(e.target.value) || 2 }))}
                    />
                  </div>
                </div>
                </>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="analytics" className="space-y-6 mt-6">
          <AnalysisContent />
        </TabsContent>
      </Tabs>

      {showLogoutDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <Card className="bg-white dark:bg-neutral-900 rounded-2xl shadow-2xl w-[92%] max-w-sm border-0">
            <CardHeader className="pb-2">
              <CardTitle className="text-lg flex items-center gap-2">
                <BiLogOut className="text-red-500" /> 确认退出登录
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground mb-4">退出后将清除Cookie并切换为未登录状态。</p>
              <div className="flex justify-end gap-2">
                <Button variant="ghost" onClick={() => setShowLogoutDialog(false)} className="rounded-full px-4">取消</Button>
                <Button onClick={async () => { await triggerLogout(); setShowLogoutDialog(false) }} className="rounded-full bg-gradient-to-r from-red-500 to-rose-600 text-white px-4">确认退出</Button>
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      {showLogoutResultDialog && logoutResult && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30">
          <Card className="bg-white dark:bg-neutral-900 rounded-2xl shadow-2xl w-[92%] max-w-sm border-0">
            <CardHeader className="pb-2">
              <CardTitle className="text-lg flex items-center gap-2">
                <BiLogOut className={logoutResult.success ? 'text-green-500' : 'text-red-500'} />
                {logoutResult.success ? '退出登录成功' : '退出登录失败'}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground mb-4">{logoutResult.message}</p>
              <Button onClick={() => setShowLogoutResultDialog(false)} className={`rounded-full px-4 ${logoutResult.success ? 'bg-green-500' : 'bg-red-500'} text-white`}>知道了</Button>
            </CardContent>
          </Card>
        </div>
      )}

      {showSaveDialog && saveResult && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30">
          <Card className="bg-white dark:bg-neutral-900 rounded-2xl shadow-2xl w-[92%] max-w-sm border-0">
            <CardHeader className="pb-2">
              <CardTitle className="text-lg flex items-center gap-2">
                <BiSave className={saveResult.success ? 'text-green-500' : 'text-red-500'} />
                {saveResult.success ? '保存成功' : '保存失败'}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground mb-4">{saveResult.message}</p>
              <Button onClick={() => setShowSaveDialog(false)} className={`rounded-full px-4 ${saveResult.success ? 'bg-green-500' : 'bg-red-500'} text-white`}>知道了</Button>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  )
}
