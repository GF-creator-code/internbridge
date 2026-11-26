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
import AnalysisContent from '@/app/zhilian/analysis/AnalysisContent'
import PageHeader from '@/app/components/PageHeader'
import PlatformTip from '@/app/components/PlatformTip'
import { API_BASE } from '@/lib/api'
import { parseKeywordsForDisplay, serializeKeywords } from '@/lib/keywords'
import FeedbackBar from '@/app/components/FeedbackBar'
import ConfigLoadError from '@/app/components/ConfigLoadError'
import { useTaskControl } from '@/lib/useTaskControl'
import { useTaskProgress } from '@/lib/useTaskProgress'
import TaskProgressPanel from '@/app/components/TaskProgressPanel'

interface ZhilianConfig {
  id?: number
  keywords?: string
  cityCode?: string
  salary?: string
}

interface Option { name: string; code: string }
interface ZhilianOptions { city: Option[] }

export default function ZhilianPage() {
  const [isLoggedIn, setIsLoggedIn] = useState(false)
  // 投递任务的启停与失败反馈统一由 useTaskControl 提供
  const task = useTaskControl(API_BASE)
  const isDelivering = task.isRunning
  const progress = useTaskProgress('zhilian')
  const [checkingLogin, setCheckingLogin] = useState(true)
  const [showLogoutDialog, setShowLogoutDialog] = useState(false)
  const [showSaveDialog, setShowSaveDialog] = useState(false)
  const [saveResult, setSaveResult] = useState<{ success: boolean; message: string } | null>(null)
  const [showLogoutResultDialog, setShowLogoutResultDialog] = useState(false)
  const [logoutResult, setLogoutResult] = useState<{ success: boolean; message: string } | null>(null)
  const [backendAvailable, setBackendAvailable] = useState(true)

  const [config, setConfig] = useState<ZhilianConfig>({ keywords: '', cityCode: '', salary: '' })
  const [options, setOptions] = useState<ZhilianOptions>({ city: [] })
  const [loadingConfig, setLoadingConfig] = useState(true)
  // 配置加载失败要显式告诉用户，否则空表单会被误当成真实配置
  const [configLoadError, setConfigLoadError] = useState(false)
  // 保存前的配置快照。用来判断「改了但没保存」——
  // 原先六个平台都没有这个判断，用户改了关键词直接点开始投递，
  // 跑的还是旧配置，而且界面完全看不出来。
  const [savedConfig, setSavedConfig] = useState<ZhilianConfig | null>(null)
  const dirty = savedConfig !== null && (Object.keys(config) as (keyof ZhilianConfig)[])
    .some(key => savedConfig[key] !== config[key])

  useEffect(() => {
    if (typeof window === 'undefined' || typeof EventSource === 'undefined') {
      console.warn('[智联招聘] EventSource 不可用，无法连接SSE')
      setCheckingLogin(false)
      return
    }

    const client = createSSEWithBackoff(`${API_BASE}/api/jobs/login-status/stream`, {
      onOpen: () => console.log('[智联招聘 SSE] 连接已打开'),
      onError: (e, attempt, delay) => {
        console.warn(`[智联招聘 SSE] 连接错误，第${attempt}次重连，延迟 ${delay}ms`, e)
        setCheckingLogin(false)
      },
      // 断线重连后补一次登录态拉取。SSE 断开期间服务端推的状态变化已经丢了，
      // 只依赖推送的话，页面会永远停在「请先登录」——而用户其实早就登录了。
      onReconnect: () => {
        void (async () => {
          try {
            const res = await fetch(`${API_BASE}/api/zhilian/login-status`)
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
              console.log('[智联招聘 SSE] connected事件数据:', data)
              console.log('[智联招聘 SSE] zhilianLoggedIn状态:', data.zhilianLoggedIn)
              setIsLoggedIn(data.zhilianLoggedIn || false)
              setCheckingLogin(false)
            } catch (error) {
              console.error('[智联招聘 SSE] 解析连接消息失败:', error)
            }
          },
        },
        {
          name: 'login-status',
          handler: (event) => {
            try {
              const data = JSON.parse(event.data)
              console.log('[智联招聘 SSE] login-status事件数据:', data)
              if (data.platform === 'zhilian') {
                console.log('[智联招聘 SSE] 智联登录状态变更:', data.isLoggedIn)
                setIsLoggedIn(data.isLoggedIn)
                setCheckingLogin(false)
              }
            } catch (error) {
              console.error('[智联招聘 SSE] 解析登录状态消息失败:', error)
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
      const res = await fetch(`${API_BASE}/api/zhilian/config`)
      const data = await res.json()
      if (data.config) {
        const normalized = { ...data.config }
        normalized.keywords = parseKeywordsForDisplay(data.config.keywords)
        setConfig(normalized)
        setSavedConfig(normalized)
      }
      if (data.options) setOptions(data.options)
    } catch (e) {
      console.error('[智联] 获取配置失败:', e)
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
        const res = await fetch(`${API_BASE}/api/zhilian/config`, { method: 'GET' })
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
    await task.start('zhilian')
  }

  const handleStopDelivery = async () => {
    await task.stop('zhilian')
  }

  const triggerLogout = async () => {
    try {
      const response = await fetch(`${API_BASE}/api/zhilian/logout`, { method: 'POST' })
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
      const response = await fetch(`${API_BASE}/api/cookie/save?platform=zhilian`, { method: 'POST' })
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
      const payload = { ...config, keywords: serializeKeywords(config.keywords) }
      const response = await fetch(`${API_BASE}/api/zhilian/config`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
      if (response.ok) {
        try { await fetch(`${API_BASE}/api/cookie/save?platform=zhilian`, { method: 'POST' }) } catch {}
        await fetchAllData()
        setSaveResult({ success: true, message: '保存成功，配置已更新。新配置在下次启动投递任务时生效。' })
      } else {
        setSaveResult({ success: false, message: '保存失败：后端返回异常状态。' })
      }
      setShowSaveDialog(true)
    } catch (error) {
      console.error('[智联] 保存配置失败:', error)
      setSaveResult({ success: false, message: '保存失败：网络或服务异常。' })
      setShowSaveDialog(true)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        icon={<BiBriefcase className="text-2xl" />}
        title="智联招聘"
        subtitle="按关键词与城市筛选岗位，登录后启动自动投递。"
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
                <BiPlay className="mr-1" /> 请先登录智联招聘
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
            <p>先在浏览器标签页登录智联招聘，登录态会自动检测；改完点「保存配置」，再点「开始投递」。</p>
            <p>需先在智联网页端设好「默认投递简历」，否则任务无法完成投递。</p>
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
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label>搜索关键词（逗号分隔）</Label>
                    <Input
                      placeholder="如：Java, 后端, Spring"
                      value={config.keywords || ''}
                      onChange={(e) => setConfig((c) => ({ ...c, keywords: e.target.value }))}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>城市</Label>
                    <Select
                      value={config.cityCode || ''}
                      onChange={(e) => setConfig((c) => ({ ...c, cityCode: e.target.value }))}
                      placeholder="请选择城市"
                    >
                      {options.city.map((o) => (
                        <option key={o.code} value={o.code}>{o.name}</option>
                      ))}
                    </Select>
                  </div>
                  <div className="space-y-2">
                    <Label>薪资范围（最低和最高工资，用逗号分割）</Label>
                    <Input
                      placeholder="如：12000, 20000 或 不限"
                      value={config.salary || ''}
                      onChange={(e) => setConfig((c) => ({ ...c, salary: e.target.value }))}
                    />
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
