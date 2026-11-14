'use client'

import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { BiBarChart, BiDownload } from 'react-icons/bi'
import PageHeader from './PageHeader'
import JobChart, { type ChartValue } from './JobChart'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { API_BASE } from '@/lib/api'

const platforms = { jobsdb: 'JobsDB', offertoday: 'OfferToday', shixiseng: '实习僧' }
const statusOptions = ['未投递', '已投递', '已过滤', '投递失败']
const emptyFilters = { statuses: [] as string[], location: '', keyword: '' }
type Filters = typeof emptyFilters
type Job = { id: number; jobId: string; companyName?: string; jobTitle?: string; salary?: string; location?: string; deliveryStatus?: string; jobLink?: string; createTime?: string }
type List = { items: Job[]; total: number; page: number; size: number }
type Stats = { kpi: { total: number; delivered: number; pending: number; failed: number; avgMonthlyK?: number };
  charts: { byStatus: ChartValue[]; byCity: ChartValue[]; byCompany: ChartValue[]; salaryBuckets: { bucket: string; value: number }[] } }

function query(filters: Filters) {
  const params = new URLSearchParams()
  if (filters.statuses.length) params.set('statuses', filters.statuses.join(','))
  if (filters.location.trim()) params.set('location', filters.location.trim())
  if (filters.keyword.trim()) params.set('keyword', filters.keyword.trim())
  return params
}

async function fetchJson<T>(url: string, signal?: AbortSignal): Promise<T> {
  const res = await fetch(url, { signal })
  if (!res.ok) throw new Error('读取失败')
  return res.json()
}

function safeLink(value?: string) {
  if (!value) return undefined
  try {
    const url = new URL(value)
    return ['http:', 'https:'].includes(url.protocol) ? url.href : undefined
  } catch { return undefined }
}

function csvCell(value = '') {
  const text = /^[\s]*[=+@-]/.test(value) ? "'" + value : value
  return '"' + text.replaceAll('"', '""') + '"'
}

function topTen(values: ChartValue[]) {
  return [...values].sort((a, b) => b.value - a.value || a.name.localeCompare(b.name)).slice(0, 10)
}

export default function InternshipAnalysis({ platform, showHeader = false }: {
  platform: keyof typeof platforms; showHeader?: boolean
}) {
  const name = platforms[platform]
  const api = API_BASE + '/api/' + platform
  const [draft, setDraft] = useState(emptyFilters)
  const [filters, setFilters] = useState(emptyFilters)
  const [revision, setRevision] = useState(0)
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(20)
  const [pageInput, setPageInput] = useState('1')
  const [list, setList] = useState<List | null>(null)
  const [stats, setStats] = useState<Stats | null>(null)
  const [statsError, setStatsError] = useState(false)
  const [listError, setListError] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [exportError, setExportError] = useState(false)
  const params = query(filters).toString()
  const dirty = query(draft).toString() !== params

  useEffect(() => {
    const controller = new AbortController()
    setStats(null)
    setStatsError(false)
    fetchJson<Stats>(api + '/stats?' + params, controller.signal)
      .then(data => { if (!controller.signal.aborted) setStats(data) })
      .catch(() => { if (!controller.signal.aborted) setStatsError(true) })
    return () => controller.abort()
  }, [api, params, revision])

  useEffect(() => {
    const controller = new AbortController()
    setList(null)
    setListError(false)
    const search = new URLSearchParams(params)
    search.set('page', String(page))
    search.set('size', String(size))
    fetchJson<List>(api + '/list?' + search, controller.signal).then(data => {
      if (controller.signal.aborted) return
      const last = Math.max(1, Math.ceil(data.total / size))
      if (last < page) { setPage(last); return }
      setList(data)
    }).catch(() => { if (!controller.signal.aborted) setListError(true) })
    return () => controller.abort()
  }, [api, page, params, revision, size])

  useEffect(() => { setPageInput(String(page)) }, [page])

  function apply(next: Filters) {
    setFilters(next)
    setPage(1)
    setPageInput('1')
    setRevision(previous => previous + 1)
    setExportError(false)
  }

  async function exportCsv() {
    setExporting(true)
    setExportError(false)
    try {
      const search = new URLSearchParams(params)
      search.set('size', '500')
      let remaining = Infinity
      const jobs: Job[] = []
      for (let current = 1; jobs.length < remaining; current++) {
        search.set('page', String(current))
        const result = await fetchJson<List>(api + '/list?' + search)
        remaining = Math.min(remaining, result.total)
        jobs.push(...result.items)
        if (!result.items.length) break
      }
      const rows = [['公司', '岗位', '薪资', '地点', '投递状态', '原站链接', '记录日期'], ...jobs.map(job =>
        [job.companyName, job.jobTitle, job.salary, job.location, job.deliveryStatus, safeLink(job.jobLink), job.createTime?.slice(0, 10)])]
      const text = rows.map(row => row.map(value => csvCell(value)).join(',')).join('\r\n')
      const url = URL.createObjectURL(new Blob(['\uFEFF', text], { type: 'text/csv;charset=utf-8' }))
      const link = document.createElement('a')
      link.href = url
      link.download = platform + '_jobs_' + new Date().toISOString().slice(0, 10) + '.csv'
      document.body.appendChild(link)
      link.click()
      link.remove()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch { setExportError(true) }
    finally { setExporting(false) }
  }

  const charts = useMemo(() => stats ? [
    { title: '投递状态分布', values: stats.charts.byStatus, donut: true },
    { title: '地区岗位数前十', values: topTen(stats.charts.byCity) },
    { title: '公司岗位数前十', values: topTen(stats.charts.byCompany) },
    { title: '月薪区间分布', values: stats.charts.salaryBuckets.map(item => ({ name: item.bucket, value: item.value })) },
  ] : [], [stats])
  const pages = list ? Math.max(1, Math.ceil(list.total / size)) : 1
  function jump(event: FormEvent) {
    event.preventDefault()
    const value = Number(pageInput)
    if (Number.isInteger(value) && value >= 1 && value <= pages) setPage(value)
  }

  return <div className="min-w-0 space-y-5">
    {showHeader && <PageHeader title={name + ' 投递分析'} subtitle="筛选已采集的岗位，查看记录或前往原站。" icon={<BiBarChart />} />}
    <Card><CardHeader><CardTitle><h2>筛选岗位</h2></CardTitle></CardHeader>
      <CardContent><form className="space-y-4" onSubmit={event => { event.preventDefault(); apply(draft) }}>
        <fieldset><legend className="mb-2 text-sm font-medium">投递状态</legend>
          <div className="flex flex-wrap gap-2">{statusOptions.map(status =>
            <button type="button" key={status} aria-pressed={draft.statuses.includes(status)}
              onClick={() => setDraft(previous => ({ ...previous, statuses: previous.statuses.includes(status) ? previous.statuses.filter(s => s !== status) : [...previous.statuses, status] }))}
              className={'min-h-9 rounded-md border px-3 text-sm ' + (draft.statuses.includes(status) ? 'bg-primary text-primary-foreground' : 'bg-background')}>
              {status}
            </button>)}</div>
        </fieldset>
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1"><Label htmlFor="analysis-keyword">公司或岗位关键词</Label><Input id="analysis-keyword" value={draft.keyword}
            onChange={e => setDraft({ ...draft, keyword: e.target.value })} placeholder={platform === 'shixiseng' ? '例如 数据分析' : '例如 intern'} /></div>
          <div className="space-y-1"><Label htmlFor="analysis-location">地区（完整名称）</Label><Input id="analysis-location" value={draft.location}
            onChange={e => setDraft({ ...draft, location: e.target.value })} placeholder="与岗位记录的地点一致" /></div>
        </div>
        <div className="flex flex-wrap items-center gap-2"><Button type="submit">{dirty ? '应用筛选' : '刷新结果'}</Button>
          <Button variant="outline" type="button" onClick={() => { setDraft(emptyFilters); apply(emptyFilters) }}>重置筛选</Button>
          <Button type="button" variant="outline" disabled={dirty || exporting || !list?.total} onClick={exportCsv}><BiDownload />{exporting ? '正在导出…' : '导出筛选结果 CSV'}</Button>
        </div>
        {dirty && <p role="status" className="text-sm">条件已修改，应用后会同时更新统计和列表。</p>}
        {exportError && <p role="alert" className="text-sm text-destructive">导出失败，请重新导出。</p>}
      </form></CardContent>
    </Card>
    {statsError ? <p role="alert">统计加载失败。<button type="button" className="text-primary underline" onClick={() => setRevision(r => r + 1)}>重试</button></p> :
      stats ? <>
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          {[['总岗位数', stats.kpi.total], ['已投递', stats.kpi.delivered], ['未投递', stats.kpi.pending], ['平均月薪', stats.kpi.avgMonthlyK == null ? '暂无数据' : String(stats.kpi.avgMonthlyK) + 'K']].map(([label, value]) =>
            <Card key={label}><CardContent className="pt-4"><p className="text-sm text-muted-foreground">{label}</p><p className="mt-1 text-xl font-semibold">{value}</p></CardContent></Card>)}
        </div>
        {stats.kpi.total > 0 && <div className="grid min-w-0 gap-4 md:grid-cols-2">
          {charts.map(chart => <Card key={chart.title} className="min-w-0">
            <CardHeader><CardTitle><h3>{chart.title}</h3></CardTitle></CardHeader>
            <CardContent><JobChart {...chart} />
              {chart.title === '月薪区间分布' && <p className="mt-2 text-sm text-muted-foreground">仅统计可解析的月薪。原站的日薪、时薪和面议可在列表查看。</p>}
            </CardContent>
          </Card>)}
        </div>}
      </> : <p role="status">正在读取统计…</p>}
    <Card className="min-w-0"><CardHeader><CardTitle><h2>岗位列表</h2></CardTitle></CardHeader>
      <CardContent>
        {listError ? <p role="alert">岗位列表加载失败。<button type="button" className="text-primary underline" onClick={() => setRevision(r => r + 1)}>重试</button></p> :
          !list ? <p role="status">正在读取岗位…</p> :
          !list.total ? <p role="status">{params ? '没有符合条件的岗位，可修改或重置筛选。' : '尚无岗位记录。完成平台采集后，职位会出现在这里。'}</p> :
            <>
              <div className="overflow-x-auto" tabIndex={0} role="region" aria-label="岗位列表，可横向滚动">
                <table className="w-full text-left text-sm"><caption className="sr-only">{name} 已采集岗位</caption>
                  <thead><tr className="bg-muted">{['岗位', '公司', '地点', '薪资', '投递状态', '记录日期'].map(title => <th key={title} scope="col" className="whitespace-nowrap p-2">{title}</th>)}</tr></thead>
                  <tbody>{list.items.map(job => {
                    const href = safeLink(job.jobLink)
                    return <tr key={job.id ?? job.jobId} className="border-t">
                      <td className="min-w-36 p-2">{href ? <a href={href} target="_blank" rel="noopener noreferrer" className="text-primary underline">{job.jobTitle || '查看原站职位'}</a> : job.jobTitle || '—'}</td>
                      {[job.companyName, job.location, job.salary, job.deliveryStatus, job.createTime?.slice(0, 10)].map((value, i) => <td key={i} className="whitespace-nowrap p-2">{value || '—'}</td>)}
                    </tr>
                  })}</tbody>
                </table>
              </div>
              <div className="mt-4 flex flex-wrap items-center gap-2 text-sm">
                <span>共 {list.total} 条 · 第 {page} / {pages} 页</span>
                <Button type="button" variant="outline" disabled={page === 1} onClick={() => setPage(page - 1)}>上一页</Button>
                <Button type="button" variant="outline" disabled={page >= pages} onClick={() => setPage(page + 1)}>下一页</Button>
                <Label htmlFor="analysis-size">每页</Label><select id="analysis-size" value={size} className="min-h-9 rounded-md border bg-background px-2"
                  onChange={e => { setSize(Number(e.target.value)); setPage(1) }}>{[10, 20, 50, 100].map(s => <option key={s}>{s}</option>)}</select>
                {pages > 1 && <form onSubmit={jump} className="flex items-center gap-2"><Label htmlFor="analysis-page">页码</Label>
                  <Input id="analysis-page" className="w-20" type="number" min={1} max={pages} step={1} value={pageInput} onChange={e => setPageInput(e.target.value)} /><Button variant="outline" type="submit">跳转</Button></form>}
              </div>
            </>}
      </CardContent>
    </Card>
  </div>
}
