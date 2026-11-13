'use client'

import { useState, useEffect } from 'react'
import { createSSEWithBackoff } from '@/lib/sse'
import { BiLogIn, BiLogOut, BiSave, BiBriefcase, BiPlay, BiStop } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import AnalysisContent from '@/app/offertoday/analysis/AnalysisContent'
import PageHeader from '@/app/components/PageHeader'
import ToggleSwitch, { toBool } from '@/app/components/ToggleSwitch'
import { API_BASE } from '@/lib/api'

interface OfferTodayConfig {
  id?: number
  keywords?: string
  city?: string
  account?: string
  password?: string
  skipLegal?: boolean
  enableAi?: boolean
  waitTime?: number
}

export default function OfferTodayPage() {
  const [isLoggedIn, setIsLoggedIn] = useState(false)
  const [isDelivering, setIsDelivering] = useState(false)
  const [checkingLogin, setCheckingLogin] = useState(true)
  const [showLogoutDialog, setShowLogoutDialog] = useState(false)
  const [showSaveDialog, setShowSaveDialog] = useState(false)
  const [saveResult, setSaveResult] = useState<{ success: boolean; message: string } | null>(null)
  const [showLogoutResultDialog, setShowLogoutResultDialog] = useState(false)
  const [logoutResult, setLogoutResult] = useState<{ success: boolean; message: string } | null>(null)
  const [backendAvailable, setBackendAvailable] = useState(true)

  const [config, setConfig] = useState<OfferTodayConfig>({ keywords: '', city: '香港', account: '', password: '', skipLegal: true, enableAi: true, waitTime: 2 })
  const [loadingConfig, setLoadingConfig] = useState(true)

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

  const parseKeywordsFromDb = (raw?: string): string => {
    if (!raw) return ''
    const t = raw.trim()
    if (t.startsWith('[') && t.endsWith(']')) {
      try {
        const arr = JSON.parse(t)
        if (Array.isArray(arr)) return arr.filter(Boolean).join(', ')
      } catch (e) {
        console.warn('[OfferToday] 解析关键词JSON失败，使用原值:', e)
      }
    }
    return t.replace(/，/g, ',')
  }

  const serializeKeywordsForDb = (display?: string): string => {
    const raw = (display || '').trim()
    if (!raw) return '[]'
    const norm = raw.replace(/，/g, ',')
    const tokens = norm
      .split(',')
      .map((s) => s.trim())
      .filter((s) => s.length > 0)
    return JSON.stringify(tokens)
  }

  const fetchAllData = async () => {
    try {
      const res = await fetch(`${API_BASE}/api/offertoday/config`)
      const data = await res.json()
      if (data.config) {
        const normalized = { ...data.config }
        normalized.keywords = parseKeywordsFromDb(data.config.keywords)
        normalized.skipLegal = toBool(data.config.skipLegal, true)
        normalized.enableAi = toBool(data.config.enableAi, true)
        normalized.waitTime = Number(data.config.waitTime) > 0 ? Number(data.config.waitTime) : 2
        setConfig(normalized)
      }
    } catch (e) {
      console.error('[OfferToday] 获取配置失败:', e)
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

  const handleStartDelivery = async () => {
    try {
      setIsDelivering(true)
      const response = await fetch(`${API_BASE}/api/offertoday/start`, { method: 'POST' })
      const data = await response.json()
      if (!data.success) setIsDelivering(false)
    } catch (error) {
      setIsDelivering(false)
    }
  }

  const handleStopDelivery = async () => {
    try {
      const response = await fetch(`${API_BASE}/api/offertoday/stop`, { method: 'POST' })
      const data = await response.json()
      if (data.success) setIsDelivering(false)
    } catch (error) {}
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
      const payload = { ...config, keywords: serializeKeywordsForDb(config.keywords) }
      const response = await fetch(`${API_BASE}/api/offertoday/config`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
      if (response.ok) {
        try { await fetch(`${API_BASE}/api/cookie/save?platform=offertoday`, { method: 'POST' }) } catch {}
        await fetchAllData()
        setSaveResult({ success: true, message: '保存成功，配置已更新。' })
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
        title="OfferToday 配置"
        subtitle="香港实习岗自动投递（只投「實習」类型）"
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

      <Tabs defaultValue="config" className="w-full">
        <TabsList className="grid w-full grid-cols-2">
          <TabsTrigger value="config">平台配置</TabsTrigger>
          <TabsTrigger value="analytics">投递分析</TabsTrigger>
        </TabsList>

        <TabsContent value="config" className="space-y-6 mt-6">
          <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <BiBriefcase className="text-primary" />
                OfferToday 平台说明
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                <p className="text-sm text-muted-foreground">OfferToday 是香港招聘平台，岗位全部在香港，薪资以 HKD 计。</p>
                <p className="text-sm text-muted-foreground">填好账号密码后点「登录 OfferToday」，系统会自动登录并保存 Cookie；失败时请在浏览器窗口手动完成。</p>
                <p className="text-sm text-muted-foreground">投递只针对卡片类型为「實習」的岗位，招呼语按 JD 用 AI 生成。</p>
              </div>
            </CardContent>
          </Card>

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
                    <Label>城市</Label>
                    <Input
                      placeholder="香港"
                      value={config.city || ''}
                      onChange={(e) => setConfig((c) => ({ ...c, city: e.target.value }))}
                    />
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
                      placeholder="OfferToday 密码"
                      value={config.password || ''}
                      onChange={(e) => setConfig((c) => ({ ...c, password: e.target.value }))}
                    />
                  </div>
                </div>
                <div className="mt-4 space-y-3 rounded-lg border bg-muted/20 p-4">
                  <p className="text-sm font-medium">投递行为</p>
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div className="space-y-1">
                      <Label htmlFor="skipLegal" className="text-base font-medium">跳过法务/法律岗</Label>
                      <p className="text-sm text-muted-foreground">开启后自动排除法务、律师、诉讼等法律类岗位。</p>
                    </div>
                    <ToggleSwitch
                      id="skipLegal"
                      label="跳过法务/法律岗"
                      checked={!!config.skipLegal}
                      onToggle={() => setConfig((c) => ({ ...c, skipLegal: !c.skipLegal }))}
                    />
                  </div>
                  <div className="flex flex-col gap-3 border-t pt-3 sm:flex-row sm:items-center sm:justify-between">
                    <div className="space-y-1">
                      <Label htmlFor="enableAi" className="text-base font-medium">AI 生成招呼语</Label>
                      <p className="text-sm text-muted-foreground">按岗位 JD 自动生成打招呼语；关闭后使用默认模板。</p>
                    </div>
                    <ToggleSwitch
                      id="enableAi"
                      label="AI 生成招呼语"
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
