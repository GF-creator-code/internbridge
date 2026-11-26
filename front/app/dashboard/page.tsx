'use client'

import { useCallback, useEffect, useState } from 'react'
import Link from 'next/link'
import { BiBarChart, BiRefresh, BiEnvelope, BiTime, BiCheckCircle, BiReply } from 'react-icons/bi'
import PageHeader from '@/app/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { API_BASE } from '@/lib/api'

type PlatformStat = {
  platform: string
  label?: string
  available: boolean
  total: number
  delivered: number
  replied: number
  replyAvailable?: boolean
}

type Summary = {
  from: string
  to: string
  total: number
  delivered: number
  replied: number
  platforms: PlatformStat[]
}

type Reply = {
  company?: string
  job?: string
  status?: string
  url?: string
  time?: string
}

type HistoryEntry = {
  date: string
  generatedAt: string
  total: number
  delivered: number
  replied: number
  report: string
}

export default function DashboardPage() {
  const [summary, setSummary] = useState<Summary | null>(null)
  const [replies, setReplies] = useState<Reply[]>([])
  const [history, setHistory] = useState<HistoryEntry[]>([])
  const [settings, setSettings] = useState<{ enabled: boolean; time: string } | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [generating, setGenerating] = useState(false)
  const [notice, setNotice] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [summaryRes, repliesRes, historyRes] = await Promise.all([
        fetch(API_BASE + '/api/stats/today'),
        fetch(API_BASE + '/api/stats/replies'),
        fetch(API_BASE + '/api/stats/history?limit=14'),
      ])
      if (!summaryRes.ok || !repliesRes.ok || !historyRes.ok) throw new Error()
      const [s, r, h] = await Promise.all([
        summaryRes.json(), repliesRes.json(), historyRes.json(),
      ])
      if (!s.success || !r.success || !h.success) throw new Error()
      setSummary(s.data)
      setReplies(r.data || [])
      setHistory(h.data || [])
      setSettings(h.settings || null)
    } catch {
      setError('无法读取统计数据，请确认本地服务已启动。')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void load() }, [load])

  async function generate() {
    setGenerating(true)
    setNotice('')
    try {
      const res = await fetch(API_BASE + '/api/stats/summarize', { method: 'POST' })
      const data = await res.json()
      if (!res.ok || !data.success) throw new Error(data?.message || '生成失败')
      setNotice('日报已生成并推送。')
      await load()
    } catch (error) {
      setNotice(error instanceof Error ? error.message : '生成失败，请查看后端日志。')
    } finally {
      setGenerating(false)
    }
  }

  return (
    <div className="space-y-5">
      <PageHeader
        icon={<BiBarChart className="text-2xl" />}
        title="投递数据看板"
        subtitle="汇总各平台的采集、投递、打招呼与回复情况。"
        iconClass="text-white"
        accentBgClass="bg-gradient-to-r from-emerald-500 to-teal-600"
        actions={
          <Button onClick={() => void load()} size="sm" variant="outline">
            <BiRefresh className="mr-1" /> 刷新
          </Button>
        }
      />

      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      {notice && <p role="status" className="text-sm text-emerald-600 dark:text-emerald-400">{notice}</p>}

      {loading ? (
        <p role="status" className="text-sm text-muted-foreground">正在加载统计数据…</p>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-3">
            <Card>
              <CardContent className="flex items-center gap-4 p-5">
                <div className="rounded-full bg-blue-500/10 p-3 text-blue-600 dark:text-blue-400"><BiEnvelope className="text-2xl" /></div>
                <div>
                  <p className="text-xs text-muted-foreground">今日采集岗位</p>
                  <p className="text-2xl font-medium">{summary?.total ?? 0}</p>
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="flex items-center gap-4 p-5">
                <div className="rounded-full bg-emerald-500/10 p-3 text-emerald-600 dark:text-emerald-400"><BiCheckCircle className="text-2xl" /></div>
                <div>
                  <p className="text-xs text-muted-foreground">今日投递 / 打招呼</p>
                  <p className="text-2xl font-medium">{summary?.delivered ?? 0}</p>
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="flex items-center gap-4 p-5">
                <div className="rounded-full bg-amber-500/10 p-3 text-amber-600 dark:text-amber-400"><BiReply className="text-2xl" /></div>
                <div>
                  <p className="text-xs text-muted-foreground">收到回复</p>
                  <p className="text-2xl font-medium">{summary?.replied ?? 0}</p>
                  <p className="text-xs text-muted-foreground">目前仅 Boss 可追踪</p>
                </div>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>分平台明细</CardTitle>
              <CardDescription>今日各平台的采集与投递数量</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-2">
                {(summary?.platforms || []).filter(p => p.available).map(p => (
                  <div key={p.platform} className="flex items-center justify-between rounded-lg border border-border px-4 py-2.5 text-sm">
                    <span className="font-medium">{p.label || p.platform}</span>
                    <div className="flex items-center gap-4 text-xs text-muted-foreground">
                      <span>采集 {p.total}</span>
                      <span>投递 {p.delivered}</span>
                      {p.replyAvailable !== false && <span>回复 {p.replied}</span>}
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>有回复的岗位</CardTitle>
              <CardDescription>Boss直聘中对方已读或回复的岗位</CardDescription>
            </CardHeader>
            <CardContent>
              {replies.length === 0 ? (
                <p className="text-sm text-muted-foreground">暂无回复记录。</p>
              ) : (
                <div className="space-y-2">
                  {replies.map((job, index) => (
                    <div key={`${job.company}-${index}`} className="rounded-lg border border-border p-3 text-sm">
                      <p className="font-medium">{job.company}</p>
                      <p className="text-muted-foreground">{job.job}</p>
                      <p className="mt-1 text-xs text-amber-600 dark:text-amber-400">{job.status}</p>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <BiTime /> 每日定时总结
              </CardTitle>
              <CardDescription>
                {settings
                  ? `每天 ${settings.time} 自动汇总前一天的投递与回复情况${settings.enabled ? '' : '（当前已关闭）'}`
                  : '读取配置中…'}
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <Button onClick={() => void generate()} disabled={generating} size="sm" variant="outline">
                {generating ? '正在生成…' : '立即生成昨日日报'}
              </Button>
              <p className="text-xs text-muted-foreground">
                日报会推送到企业微信机器人（需先在「设置中心 → 连接与通知」开启），最多保留最近 30 条总结。
              </p>
              {history.length > 0 && (
                <div className="space-y-2 pt-2">
                  {history.slice(0, 7).map(item => (
                    <details key={item.date} className="rounded-lg border border-border p-3">
                      <summary className="cursor-pointer text-sm">
                        {item.date} · 投递 {item.delivered} · 回复 {item.replied}
                      </summary>
                      <pre className="mt-2 whitespace-pre-wrap text-xs text-muted-foreground">{item.report}</pre>
                    </details>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          <p className="text-sm text-muted-foreground">
            <Link href="/settings?tab=profile" className="text-primary underline underline-offset-4">前往设置中心</Link>，修改工作形式或全局城市后对所有平台生效。
          </p>
        </>
      )}
    </div>
  )
}
