'use client'

import { useState, useEffect } from 'react'
import { createSSEWithBackoff } from '@/lib/sse'
import { BiSearch, BiSave, BiTargetLock, BiMap, BiMoney, BiTime, BiBookmark, BiBarChart, BiPlay, BiStop, BiLogOut, BiBriefcase } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import AnalysisContent from '@/app/liepin/analysis/AnalysisContent'
import PageHeader from '@/app/components/PageHeader'
import PlatformTip from '@/app/components/PlatformTip'
import { API_BASE } from '@/lib/api'
import { parseKeywordsForDisplay, serializeKeywords } from '@/lib/keywords'
import FeedbackBar from '@/app/components/FeedbackBar'
import ConfigLoadError from '@/app/components/ConfigLoadError'
import { useTaskControl } from '@/lib/useTaskControl'
import { useTaskProgress } from '@/lib/useTaskProgress'
import TaskProgressPanel from '@/app/components/TaskProgressPanel'

interface LiepinConfig {
  id?: number
  keywords?: string
  city?: string
  salaryCode?: string
}

interface LiepinOption {
  id: number
  type: string
  name: string
  code: string
}

interface LiepinOptions {
  city: LiepinOption[]
}

export default function LiepinPage() {
  const [config, setConfig] = useState<LiepinConfig>({
    keywords: '',
    city: '',
    salaryCode: '',
  })
  const [options, setOptions] = useState<LiepinOptions>({
    city: [],
  })
  const [loading, setLoading] = useState(true)
  // 配置加载失败要显式告诉用户，否则空表单会被误当成真实配置
  const [configLoadError, setConfigLoadError] = useState(false)
  // 保存前的配置快照。用来判断「改了但没保存」——
  // 原先六个平台都没有这个判断，用户改了关键词直接点开始投递，
  // 跑的还是旧配置，而且界面完全看不出来。
  const [savedConfig, setSavedConfig] = useState<LiepinConfig | null>(null)
  const dirty = savedConfig !== null && (Object.keys(config) as (keyof LiepinConfig)[])
    .some(key => savedConfig[key] !== config[key])
  const [showSaveDialog, setShowSaveDialog] = useState(false)
  const [saveResult, setSaveResult] = useState<{ success: boolean; message: string } | null>(null)
  const [isLoggedIn, setIsLoggedIn] = useState(false)
  // 投递任务的启停与失败反馈统一由 useTaskControl 提供
  const task = useTaskControl(API_BASE)
  const isDelivering = task.isRunning
  const progress = useTaskProgress('liepin')
  const [checkingLogin, setCheckingLogin] = useState(true)
  const [showLogoutDialog, setShowLogoutDialog] = useState(false)
  const [showLogoutResultDialog, setShowLogoutResultDialog] = useState(false)
  const [logoutResult, setLogoutResult] = useState<{ success: boolean; message: string } | null>(null)

  useEffect(() => {
    fetchAllData()

    // 确保在客户端环境且 EventSource 可用
    if (typeof window === 'undefined' || typeof EventSource === 'undefined') {
      console.warn('EventSource 不可用，无法连接SSE')
      setCheckingLogin(false)
      return
    }

    const client = createSSEWithBackoff(`${API_BASE}/api/jobs/login-status/stream`, {
      onOpen: () => {
        console.log('[SSE] 连接已打开')
      },
      onError: (e, attempt, delay) => {
        console.warn(`[SSE] 连接错误，准备第${attempt}次重连，延迟 ${delay}ms`, e)
        setCheckingLogin(false)
      },
      // 断线重连后补一次登录态拉取。SSE 断开期间服务端推的状态变化已经丢了，
      // 只依赖推送的话，页面会永远停在「请先登录」——而用户其实早就登录了。
      onReconnect: () => {
        void (async () => {
          try {
            const res = await fetch(`${API_BASE}/api/liepin/login-status`)
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
              setIsLoggedIn(data.liepinLoggedIn || false)
              setCheckingLogin(false)
            } catch (error) {
              console.error('[SSE] 解析连接消息失败:', error)
            }
          },
        },
        {
          name: 'login-status',
          handler: (event) => {
            try {
              const data = JSON.parse(event.data)
              if (data.platform === 'liepin') {
                setIsLoggedIn(data.isLoggedIn)
                setCheckingLogin(false)
              }
            } catch (error) {
              console.error('[SSE] 解析登录状态消息失败:', error)
            }
          },
        },
        { name: 'ping', handler: () => {} },
      ],
    })

    return () => {
      client.close()
    }
  }, [])

  // 将数据库中的 JSON 数组字符串转换为逗号分隔的可读字符串
  // 将输入的逗号分隔字符串转换为 JSON 数组字符串保存到数据库
  const fetchAllData = async () => {
    try {
      const response = await fetch(`${API_BASE}/api/liepin/config`)
      const data = await response.json()

      console.log('Fetched liepin data:', data)

      if (data.config) {
        const normalized = { ...data.config }
        normalized.keywords = parseKeywordsForDisplay(data.config.keywords)
        setConfig(normalized)
        setSavedConfig(normalized)
      }
      if (data.options) {
        setOptions(data.options)
      }
    } catch (error) {
      console.error('Failed to fetch liepin data:', error)
      setConfigLoadError(true)
    } finally {
      setLoading(false)
    }
  }

  const handleSave = async () => {
    try {
      const payload = { ...config, keywords: serializeKeywords(config.keywords) }
      const response = await fetch(`${API_BASE}/api/liepin/config`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(payload),
      })

      if (response.ok) {
        // 统一保存 Cookie（Liepin）
        try {
          await fetch(`${API_BASE}/api/cookie/save?platform=liepin`, { method: 'POST' })
        } catch (e) {
          console.warn('保存 Cookie 失败（Liepin）:', e)
        }

        fetchAllData()
        setSaveResult({ success: true, message: '保存成功，配置与Cookie已更新。新配置在下次启动投递任务时生效。' })
        setShowSaveDialog(true)
      } else {
        console.warn('保存失败：后端返回非 2xx 状态')
        setSaveResult({ success: false, message: '保存失败：后端返回异常状态。' })
        setShowSaveDialog(true)
      }
    } catch (error) {
      console.error('Failed to save config:', error)
      setSaveResult({ success: false, message: '保存失败：网络或服务异常。' })
      setShowSaveDialog(true)
    }
  }

  // 启动/停止的统一调用与失败反馈见 lib/useTaskControl。
  // 原先失败原因只写进 console，界面完全没有提示。
  const handleStartDelivery = async () => {
    // 有未保存的修改时拦一道：不然跑的是旧配置，而用户以为改了就是改了
    if (dirty) {
      task.report({ kind: 'error', title: '请先保存配置', message: '当前配置有尚未保存的修改，直接开始投递会使用上一次保存的关键词和城市。' })
      return
    }
    await task.start('liepin')
  }

  const handleStopDelivery = async () => {
    await task.stop('liepin')
  }

  const triggerLogout = async () => {
    try {
      const response = await fetch(`${API_BASE}/api/liepin/logout`, { method: 'POST' })
      const data = await response.json()
      if (data.success) {
        setIsLoggedIn(false)
        console.info('已退出登录，数据库Cookie已置空')
        setLogoutResult({ success: true, message: '已退出登录，Cookie已清空。' })
        setShowLogoutResultDialog(true)
      } else {
        console.warn('退出登录失败：', data.message)
        setLogoutResult({ success: false, message: `退出登录失败：${data.message || '服务返回异常。'}` })
        setShowLogoutResultDialog(true)
      }
    } catch (error) {
      console.error('Failed to logout:', error)
      setLogoutResult({ success: false, message: '退出登录失败：网络或服务异常。' })
      setShowLogoutResultDialog(true)
    }
  }

  if (loading) {
    return <div className="flex items-center justify-center h-screen">加载中...</div>
  }

  return (
    <div className="space-y-6">
      <PageHeader
        icon={<BiSearch className="text-2xl" />}
        title="猎聘"
        subtitle="按关键词与城市筛选岗位，扫码登录后启动自动投递。"
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
                <BiPlay className="mr-1" /> 请先登录猎聘
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
            <Button onClick={handleSave} size="sm" className="rounded-full bg-gradient-to-r from-blue-500 to-indigo-500 hover:from-blue-600 hover:to-indigo-600 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105">
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
          <p>先在浏览器标签页用微信扫码登录猎聘，登录态会自动检测；改完点「保存配置」，再点「开始投递」。</p>
          <p>建议在猎聘 App 里预先设好自定义打招呼语；平台对主动私信有频控，实际限额以平台提示为准。</p>
        </PlatformTip>

        {/* 搜索配置 */}
        <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BiSearch className="text-primary" />
              搜索配置
            </CardTitle>
            <CardDescription>设置职位搜索关键词和筛选条件</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2">
                <Label htmlFor="keywords">搜索关键词</Label>
                <Input
                  id="keywords"
                  value={config.keywords || ''}
                  onChange={(e) => setConfig({ ...config, keywords: e.target.value })}
                  placeholder="例如：大模型, Python, Golang"
                />
                <p className="text-xs text-muted-foreground">关键词可多选，使用英文逗号分隔，例如：大模型, Python, Golang</p>
              </div>

              <div className="space-y-2">
                <Label htmlFor="city">工作城市</Label>
                <Select
                  id="city"
                  value={config.city || ''}
                  onChange={(e) => setConfig({ ...config, city: e.target.value })}
                >
                  <option value="">请选择城市</option>
                  {options.city.map((city) => (
                    <option key={city.id} value={city.name}>{city.name}</option>
                  ))}
                  {config.city && !options.city.some((city) => city.name === config.city) && (
                    <option value={config.city}>{config.city}（当前配置）</option>
                  )}
                </Select>
                <p className="text-xs text-muted-foreground">从列表选择工作城市；已有配置不在列表中时会显示为「当前配置」。</p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 薪资配置 */}
        <Card className="animate-in fade-in slide-in-from-bottom-6 duration-700">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BiMoney className="text-primary" />
              薪资筛选
            </CardTitle>
            <CardDescription>设置期望薪资范围</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 gap-6">
              <div className="space-y-2">
                <Label htmlFor="salaryCode">薪资范围</Label>
                <Input
                  id="salaryCode"
                  value={config.salaryCode || ''}
                  onChange={(e) => setConfig({ ...config, salaryCode: e.target.value })}
                  placeholder="例如：15$30"
                />
                <p className="text-xs text-muted-foreground">薪资范围码（例如：15$30表示15k-30k）</p>
              </div>
            </div>
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
          <div className="bg-white dark:bg-neutral-900 rounded-2xl shadow-2xl w-[92%] max-w-sm border border-gray-200 dark:border-neutral-800 animate-in fade-in zoom-in-95">
            <Card className="border-0">
              <CardHeader className="pb-2">
                <CardTitle className="text-lg flex items-center gap-2">
                  <BiLogOut className="text-red-500" />
                  确认退出登录
                </CardTitle>
              </CardHeader>
              <CardContent className="pt-0">
                <p className="text-sm text-muted-foreground mb-4">退出后将清除Cookie并切换为未登录状态。</p>
                <div className="flex justify-end gap-2">
                  <Button
                    variant="ghost"
                    onClick={() => setShowLogoutDialog(false)}
                    className="rounded-full px-4"
                  >
                    取消
                  </Button>
                  <Button
                    onClick={async () => {
                      await triggerLogout()
                      setShowLogoutDialog(false)
                    }}
                    className="rounded-full bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white px-4"
                  >
                    确认退出
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      )}

      {/* 退出登录结果弹框 */}
      {showLogoutResultDialog && logoutResult && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" role="dialog" aria-modal="true">
          <div className="bg-white dark:bg-neutral-900 rounded-2xl shadow-2xl w-[92%] max-w-sm border border-gray-200 dark:border-neutral-800 animate-in fade-in zoom-in-95">
            <Card className="border-0">
              <CardHeader className="pb-2">
                <CardTitle className="text-lg flex items-center gap-2">
                  <BiLogOut className={logoutResult.success ? 'text-green-500' : 'text-red-500'} />
                  {logoutResult.success ? '退出登录成功' : '退出登录失败'}
                </CardTitle>
              </CardHeader>
              <CardContent className="pt-0">
                <p className="text-sm text-muted-foreground mb-4">{logoutResult.message}</p>
                <div className="flex justify-end gap-2">
                  <Button
                    onClick={() => setShowLogoutResultDialog(false)}
                    className={`rounded-full px-4 ${logoutResult.success ? 'bg-gradient-to-r from-green-500 to-emerald-600 hover:from-green-600 hover:to-emerald-700 text-white' : 'bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white'}`}
                  >
                    知道了
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      )}

      {/* 保存结果弹框 */}
      {showSaveDialog && saveResult && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" role="dialog" aria-modal="true">
          <div className="bg-white dark:bg-neutral-900 rounded-2xl shadow-2xl w-[92%] max-w-sm border border-gray-200 dark:border-neutral-800 animate-in fade-in zoom-in-95">
            <Card className="border-0">
              <CardHeader className="pb-2">
                <CardTitle className="text-lg flex items-center gap-2">
                  <BiSave className={saveResult.success ? 'text-green-500' : 'text-red-500'} />
                  {saveResult.success ? '保存成功' : '保存失败'}
                </CardTitle>
              </CardHeader>
              <CardContent className="pt-0">
                <p className="text-sm text-muted-foreground mb-4">{saveResult.message}</p>
                <div className="flex justify-end gap-2">
                  <Button
                    onClick={() => setShowSaveDialog(false)}
                    className={`rounded-full px-4 ${saveResult.success ? 'bg-gradient-to-r from-green-500 to-emerald-600 hover:from-green-600 hover:to-emerald-700 text-white' : 'bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white'}`}
                  >
                    知道了
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      )}
    </div>
  )
}
