'use client'

import { useState, useEffect } from 'react'
import { createSSEWithBackoff } from '@/lib/sse'
import { BiLogOut, BiSave, BiBriefcase, BiPlay, BiStop } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import AnalysisContent from '@/app/shixiseng/analysis/AnalysisContent'
import PageHeader from '@/app/components/PageHeader'
import PlatformTip from '@/app/components/PlatformTip'
import KeywordListsField, { parseKeywordList, serializeKeywordList } from '@/app/components/KeywordListsField'
import { API_BASE } from '@/lib/api'
import { parseKeywordsForDisplay, serializeKeywords } from '@/lib/keywords'
import { useProfileOptions } from '@/lib/useProfileOptions'
import FeedbackBar from '@/app/components/FeedbackBar'
import ConfigLoadError from '@/app/components/ConfigLoadError'
import { useTaskControl } from '@/lib/useTaskControl'
import { useTaskProgress } from '@/lib/useTaskProgress'
import TaskProgressPanel from '@/app/components/TaskProgressPanel'

interface ShixisengConfig {
  id?: number
  keywords?: string
  city?: string
  includeKeywords?: string
  excludeKeywords?: string
  waitTime?: number
}




export default function ZhilianPage() {
  const [isLoggedIn, setIsLoggedIn] = useState(false)
  // 投递任务的启停与失败反馈统一由 useTaskControl 提供
  const task = useTaskControl(API_BASE)
  const isDelivering = task.isRunning
  const progress = useTaskProgress('shixiseng')
  const [checkingLogin, setCheckingLogin] = useState(true)
  const [showLogoutDialog, setShowLogoutDialog] = useState(false)
  const [showSaveDialog, setShowSaveDialog] = useState(false)
  const [saveResult, setSaveResult] = useState<{ success: boolean; message: string } | null>(null)
  const [showLogoutResultDialog, setShowLogoutResultDialog] = useState(false)
  const [logoutResult, setLogoutResult] = useState<{ success: boolean; message: string } | null>(null)
  const [backendAvailable, setBackendAvailable] = useState(true)

  const [config, setConfig] = useState<ShixisengConfig>({ keywords: '', city: '', includeKeywords: '', excludeKeywords: '', waitTime: 2 })
  const [options] = useState<{ city: {code:string;name:string}[] }>({ city: [] })
  const [loadingConfig, setLoadingConfig] = useState(true)
  // 配置加载失败要显式告诉用户，否则空表单会被误当成真实配置
  const [configLoadError, setConfigLoadError] = useState(false)
  const cityOptions = useProfileOptions().city
  // 保存前的配置快照。用来判断「改了但没保存」——
  // 原先六个平台都没有这个判断，用户改了关键词直接点开始投递，
  // 跑的还是旧配置，而且界面完全看不出来。
  const [savedConfig, setSavedConfig] = useState<ShixisengConfig | null>(null)
  const dirty = savedConfig !== null && (Object.keys(config) as (keyof ShixisengConfig)[])
    .some(key => savedConfig[key] !== config[key])

  useEffect(() => {
    if (typeof window === 'undefined' || typeof EventSource === 'undefined') {
      console.warn('[实习僧] EventSource 不可用，无法连接SSE')
      setCheckingLogin(false)
      return
    }

    const client = createSSEWithBackoff(`${API_BASE}/api/jobs/login-status/stream`, {
      onOpen: () => console.log('[实习僧 SSE] 连接已打开'),
      onError: (e, attempt, delay) => {
        console.warn(`[实习僧 SSE] 连接错误，第${attempt}次重连，延迟 ${delay}ms`, e)
        setCheckingLogin(false)
      },
      // 断线重连后补一次登录态拉取。SSE 断开期间服务端推的状态变化已经丢了，
      // 只依赖推送的话，页面会永远停在「请先登录」——而用户其实早就登录了。
      onReconnect: () => {
        void (async () => {
          try {
            const res = await fetch(`${API_BASE}/api/shixiseng/login-status`)
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
              console.log('[实习僧 SSE] connected事件数据:', data)
              console.log('[实习僧 SSE] shixisengLoggedIn状态:', data.shixisengLoggedIn)
              setIsLoggedIn(data.shixisengLoggedIn || false)
              setCheckingLogin(false)
            } catch (error) {
              console.error('[实习僧 SSE] 解析连接消息失败:', error)
            }
          },
        },
        {
          name: 'login-status',
          handler: (event) => {
            try {
              const data = JSON.parse(event.data)
              console.log('[实习僧 SSE] login-status事件数据:', data)
              if (data.platform === 'shixiseng') {
                console.log('[实习僧 SSE] 实习僧登录状态变更:', data.isLoggedIn)
                setIsLoggedIn(data.isLoggedIn)
                setCheckingLogin(false)
              }
            } catch (error) {
              console.error('[实习僧 SSE] 解析登录状态消息失败:', error)
            }
          },
        },
        { name: 'ping', handler: () => {} },
      ],
    })

    return () => client.close()
  }, [])

  // 与猎聘一致的关键词解析/序列化
  const fetchAllData = async () => {
    try {
      const res = await fetch(`${API_BASE}/api/shixiseng/config`)
      const data = await res.json()
      if (data.config) {
        const normalized = { ...data.config }
        normalized.keywords = parseKeywordsForDisplay(data.config.keywords)
        normalized.includeKeywords = parseKeywordList(data.config.includeKeywords)
        normalized.excludeKeywords = parseKeywordList(data.config.excludeKeywords)
        normalized.waitTime = Number(data.config.waitTime) > 0 ? Number(data.config.waitTime) : 2
        setConfig(normalized)
        setSavedConfig(normalized)
      }
      // 实习僧城市为自由文本输入，无下拉选项
    } catch (e) {
      console.error('[实习僧] 获取配置失败:', e)
      setConfigLoadError(true)
    } finally {
      setLoadingConfig(false)
    }
  }

  useEffect(() => { fetchAllData() }, [])

  // 探测后端可用性（与 51job 保持一致风格）
  useEffect(() => {
    (async () => {
      try {
        const res = await fetch(`${API_BASE}/api/shixiseng/config`, { method: 'GET' })
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

  // 启动/停止的统一调用与失败反馈见 lib/useTaskControl。
  // 原先这里把后端返回的中文原因丢进了 console，界面上什么都不显示。
  const handleStartDelivery = async () => {
    // 有未保存的修改时拦一道：不然跑的是旧配置，而用户以为改了就是改了
    if (dirty) {
      task.report({ kind: 'error', title: '请先保存配置', message: '当前配置有尚未保存的修改，直接开始投递会使用上一次保存的关键词和城市。' })
      return
    }
    await task.start('shixiseng')
  }

  const handleStopDelivery = async () => {
    await task.stop('shixiseng')
  }

  const triggerLogout = async () => {
    try {
      const response = await fetch(`${API_BASE}/api/shixiseng/logout`, { method: 'POST' })
      const data = await response.json()
      setIsLoggedIn(false)
      setLogoutResult({ success: data.success, message: data.success ? '已退出登录，Cookie已清空。' : data.message })
      setShowLogoutResultDialog(true)
    } catch (error) {
      setLogoutResult({ success: false, message: '退出登录失败：网络或服务异常。' })
      setShowLogoutResultDialog(true)
    }
  }

  const handleSaveCookie = async () => {
    try {
      const response = await fetch(`${API_BASE}/api/cookie/save?platform=shixiseng`, { method: 'POST' })
      const data = await response.json()
      setSaveResult({ success: data.success, message: data.success ? '配置保存成功。' : data.message })
      setShowSaveDialog(true)
    } catch (error) {
      setSaveResult({ success: false, message: '配置保存失败：网络或服务异常。' })
      setShowSaveDialog(true)
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
      const response = await fetch(`${API_BASE}/api/shixiseng/config`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
      if (response.ok) {
        try { await fetch(`${API_BASE}/api/cookie/save?platform=shixiseng`, { method: 'POST' }) } catch {}
        await fetchAllData()
        setSaveResult({ success: true, message: '保存成功，配置已更新。新配置在下次启动投递任务时生效。' })
      } else {
        setSaveResult({ success: false, message: '保存失败：后端返回异常状态。' })
      }
      setShowSaveDialog(true)
    } catch (error) {
      console.error('[实习僧] 保存配置失败:', error)
      setSaveResult({ success: false, message: '保存失败：网络或服务异常。' })
      setShowSaveDialog(true)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        icon={<BiBriefcase className="text-2xl" />}
        title="实习僧"
        subtitle="整站均为实习岗，按关键词筛选后自动投递。"
        iconClass="text-white"
        accentBgClass="bg-purple-500"
        actions={
          <div className="flex items-center gap-2">
            {checkingLogin ? (
              <Button size="sm" disabled className="rounded-full bg-gray-300 text-gray-600 cursor-not-allowed px-4 shadow">
                <BiPlay className="mr-1" /> 检查登录中...
              </Button>
            ) : !isLoggedIn ? (
              <Button size="sm" disabled className="rounded-full bg-gray-300 text-gray-600 cursor-not-allowed px-4 shadow">
                <BiPlay className="mr-1" /> 请先登录实习僧
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
          <PlatformTip>
            <p>先在浏览器标签页登录实习僧，登录态会自动检测；改完点「保存配置」，再点「开始投递」。</p>
            <p>整站都是实习岗，通常不必再填「标题必含关键词」；投递前请在原站选好附件简历。</p>
          </PlatformTip>

          {/* 配置表单 */}
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
                <div className="space-y-4">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label>搜索关键词（逗号分隔）</Label>
                      <Input
                        placeholder="如：实习生, 数据分析实习"
                        value={config.keywords || ''}
                        onChange={(e) => setConfig((c) => ({ ...c, keywords: e.target.value }))}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="shixiseng-city">城市</Label>
                      <Select id="shixiseng-city" value={config.city || ''}
                        onChange={(e) => setConfig((c) => ({ ...c, city: e.target.value }))}>
                        <option value="">不限</option>
                        {cityOptions.map(city => <option key={city} value={city}>{city}</option>)}
                      </Select>
                    </div>
                  </div>
                  <div className="space-y-3 rounded-lg border bg-muted/20 p-4">
                    <p className="text-sm font-medium">投递行为</p>
                    <p className="text-xs text-muted-foreground">全局黑名单（侧边栏「全局配置」）同样生效：命中的公司/职位自动跳过。</p>
                    <KeywordListsField
                      includeValue={config.includeKeywords || ''}
                      excludeValue={config.excludeKeywords || ''}
                      onIncludeChange={(v) => setConfig((c) => ({ ...c, includeKeywords: v }))}
                      onExcludeChange={(v) => setConfig((c) => ({ ...c, excludeKeywords: v }))}
                      includePlaceholder="实习僧整站均为实习岗，通常留空"
                      excludePlaceholder="留空即可保留所有方向"
                    />
                    <div className="flex flex-col gap-3 border-t pt-3 sm:items-center sm:justify-between sm:flex-row">
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
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="analytics" className="space-y-6 mt-6">
          <AnalysisContent />
        </TabsContent>
      </Tabs>

      {/* 退出确认弹框 */}
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

      {/* 退出登录结果弹框 */}
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

      {/* 保存Cookie结果弹框 */}
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
