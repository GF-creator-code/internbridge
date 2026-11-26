'use client'

import { useState, useEffect } from 'react'
import { createSSEWithBackoff } from '@/lib/sse'
import { BiLogOut, BiSave, BiBriefcase, BiPlay, BiStop } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import PageHeader from '@/app/components/PageHeader'
import PlatformTip from '@/app/components/PlatformTip'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import AnalysisContent from '@/app/51job/analysis/AnalysisContent'
import { API_BASE } from '@/lib/api'
import { parseKeywordsForDisplay, serializeKeywords, parseSingleKeyword, parseKeywordArray } from '@/lib/keywords'
import FeedbackBar from '@/app/components/FeedbackBar'
import ConfigLoadError from '@/app/components/ConfigLoadError'
import { useTaskControl } from '@/lib/useTaskControl'
import { useTaskProgress } from '@/lib/useTaskProgress'
import TaskProgressPanel from '@/app/components/TaskProgressPanel'

interface Job51Config {
  id?: number
  keywords?: string
  jobArea?: string
  salary?: string // 存储JSON数组字符串，如 ["03","04","05"]
}

interface Job51Option { name: string; code: string }
interface Job51Options { jobArea: Job51Option[]; salary: Job51Option[] }

// 薪资选择状态（用于多选）
const MAX_SALARY_SELECTIONS = 5

export default function Job51Page() {
  const API = process.env.API_BASE_URL || `${API_BASE}`
  const [isLoggedIn, setIsLoggedIn] = useState(false)
  // 投递任务的启停与失败反馈统一由 useTaskControl 提供
  const task = useTaskControl(API_BASE)
  const isDelivering = task.isRunning
  const progress = useTaskProgress('51job')
  const [checkingLogin, setCheckingLogin] = useState(true)
  const [showLogoutDialog, setShowLogoutDialog] = useState(false)
  const [showSaveDialog, setShowSaveDialog] = useState(false)
  const [saveResult, setSaveResult] = useState<{ success: boolean; message: string } | null>(null)
  const [showLogoutResultDialog, setShowLogoutResultDialog] = useState(false)
  const [logoutResult, setLogoutResult] = useState<{ success: boolean; message: string } | null>(null)

  const [config, setConfig] = useState<Job51Config>({ keywords: '', jobArea: '', salary: '' })
  const [options, setOptions] = useState<Job51Options>({ jobArea: [], salary: [] })
  const [loadingConfig, setLoadingConfig] = useState(true)
  // 配置加载失败要显式告诉用户，否则空表单会被误当成真实配置
  const [configLoadError, setConfigLoadError] = useState(false)
  // 保存前的配置快照。用来判断「改了但没保存」——
  // 原先六个平台都没有这个判断，用户改了关键词直接点开始投递，
  // 跑的还是旧配置，而且界面完全看不出来。
  const [savedConfig, setSavedConfig] = useState<Job51Config | null>(null)
  const dirty = savedConfig !== null && (Object.keys(config) as (keyof Job51Config)[])
    .some(key => savedConfig[key] !== config[key])
  const [backendAvailable, setBackendAvailable] = useState(false)
  const [cookieSavedAfterLogin, setCookieSavedAfterLogin] = useState(false)
  // 薪资多选状态：存储选中的code数组
  const [selectedSalaries, setSelectedSalaries] = useState<string[]>([])
  // 薪资下拉面板开关状态
  const [salaryDropdownOpen, setSalaryDropdownOpen] = useState(false)

  useEffect(() => {
    if (!backendAvailable) {
      setCheckingLogin(false)
      return
    }
    console.log('[51job] useEffect 开始执行')
    console.log('[51job] window:', typeof window)
    console.log('[51job] EventSource:', typeof EventSource)

    if (typeof window === 'undefined' || typeof EventSource === 'undefined') {
      console.warn('[51job] EventSource 不可用，无法连接SSE')
      setCheckingLogin(false)
      return
    }

    const client = createSSEWithBackoff(`${API}/api/jobs/login-status/stream`, {
      onOpen: () => {
        console.log('[51job SSE] ✅ 连接已打开')
      },
      onError: (e, attempt, delay) => {
        console.warn(`[51job SSE] 连接错误，准备第${attempt}次重连，延迟 ${delay}ms`, e)
        setCheckingLogin(false)
      },
      // 断线重连后补一次登录态拉取。SSE 断开期间服务端推的状态变化已经丢了，
      // 只依赖推送的话，页面会永远停在「请先登录」——而用户其实早就登录了。
      onReconnect: () => {
        void (async () => {
          try {
            const res = await fetch(`${API_BASE}/api/51job/login-status`)
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
            console.log('[51job SSE] ✅ 收到 connected 事件:', event.data)
            try {
              const data = JSON.parse(event.data)
              setIsLoggedIn(data.job51LoggedIn || false)
              if (data.job51LoggedIn && !cookieSavedAfterLogin) {
                fetch(`${API}/api/cookie/save?platform=51job`, { method: 'POST' }).catch(() => {})
                setCookieSavedAfterLogin(true)
              }
              setCheckingLogin(false)
            } catch (error) {
              console.error('[51job SSE] ❌ 解析连接消息失败:', error)
            }
          },
        },
        {
          name: 'login-status',
          handler: (event) => {
            try {
              const data = JSON.parse(event.data)
              if (data.platform === '51job') {
                setIsLoggedIn(data.isLoggedIn)
                if (data.isLoggedIn && !cookieSavedAfterLogin) {
                  fetch(`${API}/api/cookie/save?platform=51job`, { method: 'POST' }).catch(() => {})
                  setCookieSavedAfterLogin(true)
                }
                setCheckingLogin(false)
              }
            } catch (error) {
              console.error('[51job SSE] ❌ 解析登录状态消息失败:', error)
            }
          },
        },
        {
          name: 'ping',
          handler: () => {
            // 心跳事件，无需处理，可以用于调试：console.debug('[51job SSE] ♥ ping')
          },
        },
      ],
    })

    return () => {
      console.log('[51job SSE] 🔌 关闭SSE连接')
      client.close()
    }
  }, [backendAvailable])

  // 点击外部关闭薪资下拉面板
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as HTMLElement
      // 如果点击的不是薪资下拉框或其子元素，则关闭
      if (!target.closest('.salary-dropdown-container')) {
        setSalaryDropdownOpen(false)
      }
    }

    if (salaryDropdownOpen) {
      document.addEventListener('mousedown', handleClickOutside)
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [salaryDropdownOpen])

  const fetchAllData = async () => {
    try {
      const res = await fetch(`${API}/api/51job/config`)
      if (!res.ok) {
        console.warn(`[51job] 获取配置失败: ${res.status}`)
        setConfigLoadError(true)
        return
      }
      const data = await res.json()
      if (data.config || data.options) {
        const opts: Job51Options = data.options || { jobArea: [], salary: [] }
        const conf: Job51Config = data.config || { keywords: '', jobArea: '', salary: '' }

        // 关键词标准化（展示用）
        const normalizedKeywords = parseKeywordsForDisplay(conf.keywords)

        // 从服务端字段提取第一个token，然后映射到code
        const rawArea = parseSingleKeyword(conf.jobArea)
        const rawSalaries = parseKeywordArray(conf.salary) // 薪资支持多个

        const areaList = opts.jobArea || []
        const salaryList = opts.salary || []

        const matchArea = areaList.find((o) => o.code === rawArea || o.name === rawArea)
        
        // 薪资多选：将每个值映射为code
        const salaryCodes = rawSalaries
          .map(raw => {
            const match = salaryList.find(o => o.code === raw || o.name === raw)
            return match?.code || raw
          })
          .filter(Boolean)
          .slice(0, MAX_SALARY_SELECTIONS) // 最多5个

        const areaCode = matchArea?.code || (areaList.find((o) => o.name === '不限')?.code || areaList.find((o) => o.code === '0')?.code || '')

        setOptions(opts)
        const normalizedConf = { ...conf, keywords: normalizedKeywords, jobArea: areaCode, salary: JSON.stringify(salaryCodes) }
        setConfig(normalizedConf)
        setSavedConfig(normalizedConf)
        setSelectedSalaries(salaryCodes)
      }
    } catch (e) {
      console.warn('[51job] 获取配置异常（可能后端未启动）:', e)
    } finally {
      setLoadingConfig(false)
    }
  }

  // 后端可用性探测：成功则加载配置，否则停止加载并禁用SSE
  useEffect(() => {
    (async () => {
      try {
        const res = await fetch(`${API}/api/51job/config`, { method: 'GET' })
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
  }, [])

  // 启动/停止的统一调用与失败反馈见 lib/useTaskControl。
  const handleStartDelivery = async () => {
    // 有未保存的修改时拦一道：不然跑的是旧配置，而用户以为改了就是改了
    if (dirty) {
      task.report({ kind: 'error', title: '请先保存配置', message: '当前配置有尚未保存的修改，直接开始投递会使用上一次保存的关键词和城市。' })
      return
    }
    await task.start('51job')
  }

  const handleStopDelivery = async () => {
    await task.stop('51job')
  }

  const triggerLogout = async () => {
    try {
      const response = await fetch(`${API}/api/51job/logout`, { method: 'POST' })
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
      const response = await fetch(`${API}/api/cookie/save?platform=51job`, { method: 'POST' })
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
      // 将 jobArea / salary 统一为“中文名”的括号列表字符串，满足后端保存中文的要求
      const toBracketListString = (v?: string, type?: 'jobArea' | 'salary') => {
        const t = (v || '').trim()
        if (!t) return '[]'
        if (type === 'jobArea') {
          // 城市允许手动输入，若下拉匹配不到则直接保存输入值
          const match = (options.jobArea || []).find((o) => o.code === t || o.name === t)
          const name = match?.name || t
          return `["${name.replace(/"/g, '\\"')}"]`
        }
        if (type === 'salary') {
          // 薪资多选：将selectedSalaries数组映射为中文名数组
          const names = selectedSalaries
            .map(code => {
              const match = (options.salary || []).find(o => o.code === code)
              return match?.name || ''
            })
            .filter(Boolean)
          return names.length > 0 ? JSON.stringify(names) : '[]'
        }
        return `["${t.replace(/"/g, '\\"')}"]"`
      }
      const payload = {
        ...config,
        keywords: serializeKeywords(config.keywords),
        jobArea: toBracketListString(config.jobArea, 'jobArea'),
        salary: toBracketListString(config.salary, 'salary'),
      }
      const response = await fetch(`${API}/api/51job/config`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
      if (response.ok) {
        // 保存配置成功后，同步保存 Cookie（按你的要求加到保存按钮）
        try {
          await fetch(`${API}/api/cookie/save?platform=51job`, { method: 'POST' })
        } catch (e) {
          console.warn('[51job] 保存 Cookie 失败:', e)
        }
        await fetchAllData()
        setSaveResult({ success: true, message: '保存成功，配置与Cookie已更新。新配置在下次启动投递任务时生效。' })
      } else {
        setSaveResult({ success: false, message: '保存失败：后端返回异常状态。' })
      }
      setShowSaveDialog(true)
    } catch (error) {
      console.error('[51job] 保存配置失败:', error)
      setSaveResult({ success: false, message: '保存失败：网络或服务异常。' })
      setShowSaveDialog(true)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        icon={<BiBriefcase className="text-2xl" />}
        title="51job"
        subtitle="按关键词与城市筛选岗位，登录后启动自动投递。"
        iconClass="text-white"
        accentBgClass="bg-blue-500"
        actions={
          <div className="flex items-center gap-2">
            {checkingLogin ? (
              <Button size="sm" disabled className="rounded-full bg-gray-300 text-gray-600 cursor-not-allowed px-4 shadow">
                <BiPlay className="mr-1" /> 检查登录中...
              </Button>
            ) : !isLoggedIn ? (
              <Button size="sm" disabled className="rounded-full bg-gray-300 text-gray-600 cursor-not-allowed px-4 shadow">
                <BiPlay className="mr-1" /> 请先登录51job
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
            <p>先在浏览器标签页登录 51job，登录态会自动检测；改完点「保存配置」，再点「开始投递」。</p>
            <p>该站验证码较频繁，投递间隔已在配置中留有余量，运行过程请耐心等待。</p>
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
                    <Label htmlFor="jobArea">城市区域</Label>
                    <Select
                      id="jobArea"
                      value={config.jobArea || ''}
                      onChange={(e) => setConfig((c) => ({ ...c, jobArea: e.target.value }))}
                    >
                      <option value="">请选择城市</option>
                      {options.jobArea.map((o) => (
                        <option key={o.code} value={o.code}>{o.name}</option>
                      ))}
                      {config.jobArea && !options.jobArea.some((o) => o.code === config.jobArea) && (
                        <option value={config.jobArea}>{config.jobArea}（当前配置）</option>
                      )}
                    </Select>
                    <p className="text-xs text-muted-foreground">从列表选择城市区域；已有配置不在列表中时会显示为「当前配置」。</p>
                  </div>
                  <div className="space-y-2">
                    <Label>薪资范围（最多选择5个）</Label>
                    <div className="relative salary-dropdown-container">
                      {/* 下拉多选框 */}
                      <div
                        className="flex h-10 w-full rounded-full px-4 py-2 text-sm border border-white/40 bg-white/5 shadow-[inset_0_1px_0_rgba(255,255,255,.25)] cursor-pointer hover:bg-white/10 transition-all duration-200"
                        onClick={() => setSalaryDropdownOpen(!salaryDropdownOpen)}
                      >
                        <span className="truncate text-sm">
                          {selectedSalaries.length > 0
                            ? selectedSalaries
                                .map((code) => options.salary.find((o) => o.code === code)?.name)
                                .filter(Boolean)
                                .join(', ')
                            : '请选择薪资范围'}
                        </span>
                      </div>
                      {/* 下拉选项面板 */}
                      {salaryDropdownOpen && (
                        <div className="dropdown-panel" style={{ position: 'absolute', marginTop: '8px', width: '100%' }}>
                          <ul className="py-1">
                            {options.salary.map((o) => {
                              const isSelected = selectedSalaries.includes(o.code)
                              const canSelect = selectedSalaries.length < MAX_SALARY_SELECTIONS || isSelected
                              return (
                                <li
                                  key={o.code}
                                  className={`group flex items-center gap-3 px-3 py-2 cursor-pointer transition-all border-b border-white/12 last:border-b-0 ${
                                    !canSelect ? 'opacity-50 cursor-not-allowed' : 'hover:bg-white/12'
                                  } ${
                                    isSelected ? 'bg-gradient-to-r from-emerald-500/12 to-cyan-500/12' : ''
                                  }`}
                                  onClick={() => {
                                    if (!canSelect) return
                                    let newSalaries: string[]
                                    if (isSelected) {
                                      newSalaries = selectedSalaries.filter((code) => code !== o.code)
                                    } else {
                                      if (selectedSalaries.length < MAX_SALARY_SELECTIONS) {
                                        newSalaries = [...selectedSalaries, o.code]
                                      } else {
                                        return
                                      }
                                    }
                                    setSelectedSalaries(newSalaries)
                                    setConfig((c) => ({ ...c, salary: JSON.stringify(newSalaries) }))
                                  }}
                                >
                                  <span
                                    className={`inline-flex h-4 w-4 items-center justify-center rounded-md border border-white/30 bg-white/10 shadow-inner transition-all ${
                                      isSelected ? 'bg-emerald-400/60 border-emerald-300/80' : ''
                                    }`}
                                  >
                                    {isSelected && (
                                      <svg className="w-3 h-3 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                                      </svg>
                                    )}
                                  </span>
                                  <span className="text-sm truncate">{o.name}</span>
                                </li>
                              )
                            })}
                          </ul>
                        </div>
                      )}
                    </div>
                    <p className="text-xs text-muted-foreground">
                      已选择 {selectedSalaries.length}/{MAX_SALARY_SELECTIONS} 个薪资范围
                    </p>
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
