'use client'

import { useState, useEffect, useCallback } from 'react'
import { BiShield, BiTrash, BiPlus, BiRefresh, BiGlobe } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { API_BASE } from '@/lib/api'
import { useProfileOptions } from '@/lib/useProfileOptions'

interface BlacklistItem {
  id: number
  type: string
  value: string
  createdAt?: string
  updatedAt?: string
}

const TYPE_LABELS: Record<string, string> = {
  company: '公司',
  job: '职位',
  recruiter: '招聘者',
}

const TYPE_COLORS: Record<string, string> = {
  company: 'text-red-400',
  job: 'text-amber-400',
  recruiter: 'text-cyan-400',
}

export default function RulesSection() {
  const [items, setItems] = useState<BlacklistItem[]>([])
  const [type, setType] = useState('company')
  const [value, setValue] = useState('')
  const [filter, setFilter] = useState('')
  const [message, setMessage] = useState<{ success: boolean; text: string } | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  // 全局城市：统一在这里选，再一键同步到各平台
  const [globalCity, setGlobalCity] = useState('')
  const [savedCity, setSavedCity] = useState('')
  const [platformCities, setPlatformCities] = useState<Record<string, string>>({})
  const [citySaving, setCitySaving] = useState(false)
  const [cityApplying, setCityApplying] = useState(false)
  const cityOptions = useProfileOptions().city

  const fetchAll = useCallback(async () => {
    setLoadError(false)
    try {
      // 并行拉取黑名单、全局档案与各平台城市现状
      void (async () => {
        try {
          const [profileRes, citiesRes] = await Promise.all([
            fetch(`${API_BASE}/api/profile`),
            fetch(`${API_BASE}/api/global/cities`),
          ])
          if (profileRes.ok) {
            const p = await profileRes.json()
            if (p.success && p.data) {
              setGlobalCity(p.data.city || '')
              setSavedCity(p.data.city || '')
            }
          }
          if (citiesRes.ok) {
            const c = await citiesRes.json()
            if (c.success && c.data) setPlatformCities(c.data)
          }
        } catch {
          // 城市信息加载失败不影响黑名单功能
        }
      })()
      const res = await fetch(`${API_BASE}/api/global/blacklist`)
      if (!res.ok) throw new Error()
      const data = await res.json()
      // 这里必须严格校验：原先 Array.isArray(data) ? data : [] 会在后端返回
      // 异常结构时静默变成空列表，页面显示「黑名单为空，所有公司均可触达」——
      // 用户会以为黑名单已生效，实际上是读取失败。这是危险的假阴性。
      if (!Array.isArray(data)) throw new Error()
      setItems(data)
    } catch {
      setLoadError(true)
      setMessage({ success: false, text: '黑名单加载失败：网络或服务异常' })
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchAll() }, [fetchAll])

  const cityDirty = globalCity !== savedCity

  /** 保存全局城市到档案（不立即同步平台） */
  async function saveCity() {
    if (!cityDirty || citySaving) return
    setCitySaving(true)
    setMessage(null)
    try {
      const res = await fetch(`${API_BASE}/api/profile`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ city: globalCity }),
      })
      const body = await res.json().catch(() => null)
      if (!res.ok || !body?.success) throw new Error(body?.message || '保存失败')
      setSavedCity(globalCity)
      setMessage({ success: true, text: '全局城市已保存。点「应用到各平台」即可同步。' })
    } catch (error) {
      setMessage({
        success: false,
        text: error instanceof Error ? error.message : '保存失败，请检查本地服务。',
      })
    } finally {
      setCitySaving(false)
    }
  }

  /** 把全局城市同步到各平台（会覆盖平台已选城市） */
  async function applyCity() {
    if (!savedCity) {
      setMessage({ success: false, text: '请先选择并保存全局城市。' })
      return
    }
    if (!window.confirm(`确定把城市「${savedCity}」应用到所有平台吗？各平台当前选择的城市会被覆盖。`)) {
      return
    }
    setCityApplying(true)
    setMessage(null)
    try {
      const res = await fetch(`${API_BASE}/api/global/apply-city`, { method: 'POST' })
      const body = await res.json().catch(() => null)
      if (!body?.success) throw new Error(body?.message || '同步失败')
      setMessage({ success: true, text: body.message || '已同步到各平台。' })
      await fetchAll()
    } catch (error) {
      setMessage({
        success: false,
        text: error instanceof Error ? error.message : '同步失败，请检查本地服务。',
      })
    } finally {
      setCityApplying(false)
    }
  }

  const handleAdd = async () => {
    if (!value.trim()) {
      setMessage({ success: false, text: '请输入要拉黑的内容（公司名 / 职位关键词 / 招聘者名）' })
      return
    }
    try {
      const res = await fetch(`${API_BASE}/api/global/blacklist`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ type, value: value.trim() }),
      })
      const data = await res.json()
      if (data.success) {
        setMessage({ success: true, text: `已加入全局黑名单：${TYPE_LABELS[type]}「${value.trim()}」` })
        setValue('')
        await fetchAll()
      } else {
        setMessage({ success: false, text: data.message || '添加失败' })
      }
    } catch {
      setMessage({ success: false, text: '添加失败：网络或服务异常' })
    }
  }

  const handleDelete = async (id: number, label: string) => {
    try {
      const res = await fetch(`${API_BASE}/api/global/blacklist/${id}`, { method: 'DELETE' })
      const data = await res.json()
      if (data.success) {
        setMessage({ success: true, text: `已移出黑名单：${label}` })
        await fetchAll()
      } else {
        setMessage({ success: false, text: '删除失败' })
      }
    } catch {
      setMessage({ success: false, text: '删除失败：网络或服务异常' })
    }
  }

  const filtered = filter ? items.filter((i) => i.type === filter) : items
  const grouped = (t: string) => filtered.filter((i) => i.type === t)

  return (
    <div className="space-y-6">

      <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <BiGlobe className="text-primary" />
            全局城市
          </CardTitle>
          <CardDescription>选一次城市，同步到各个平台；香港平台固定，不受影响。</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto_auto] sm:items-end">
            <div className="space-y-2">
              <Label htmlFor="global-city">意向城市</Label>
              <Select id="global-city" value={globalCity} onChange={e => setGlobalCity(e.target.value)}>
                <option value="">不限</option>
                {cityOptions.map(city => <option key={city} value={city}>{city}</option>)}
              </Select>
            </div>
            <Button onClick={() => void saveCity()} disabled={!cityDirty || citySaving} size="sm" variant="outline">
              {citySaving ? '保存中…' : '保存城市'}
            </Button>
            <Button onClick={() => void applyCity()} disabled={!savedCity || cityDirty || cityApplying} size="sm">
              {cityApplying ? '同步中…' : '应用到各平台'}
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">
            {cityDirty
              ? '城市已修改但尚未保存，请先点「保存城市」。'
              : '保存后点「应用到各平台」会覆盖各平台当前选择的城市，操作前会二次确认。'}
          </p>
          {Object.keys(platformCities).length > 0 && (
            <div className="rounded-lg border bg-muted/30 p-3">
              <p className="mb-2 text-xs font-medium">各平台当前城市</p>
              <div className="grid gap-1.5 text-xs text-muted-foreground sm:grid-cols-2">
                {Object.entries(platformCities).map(([platform, city]) => (
                  <div key={platform} className="flex justify-between gap-2">
                    <span>{platform}</span>
                    <span className="text-foreground">{city || '未设置'}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <BiShield className="text-primary" />
            黑名单规则
          </CardTitle>
          <CardDescription>所有平台共用这一份黑名单，只在此处维护。</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-3 text-sm text-muted-foreground">
            <p>命中即跳过，不占用投递额度。各平台按自身字段应用：</p>
            <ul className="list-disc pl-5 space-y-1">
              <li><span className="text-foreground font-medium">公司黑名单</span>：岗位公司名包含条目即整家公司跳过（Boss / 实习僧 / OfferToday / JobsDB）</li>
              <li><span className="text-foreground font-medium">职位黑名单</span>：岗位标题包含条目即跳过（同上四平台）</li>
              <li><span className="text-foreground font-medium">招聘者黑名单</span>：仅 Boss 使用（其余平台卡片无招聘者字段）</li>
            </ul>
            <p>Boss 投递中自动拉黑的公司也会出现在这里，可随时移出。</p>
          </div>
        </CardContent>
      </Card>

      <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <BiPlus className="text-primary" />
            添加黑名单
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="space-y-2">
              <Label>类型</Label>
              <Select value={type} onChange={(e) => setType(e.target.value)}>
                <option value="company">公司</option>
                <option value="job">职位</option>
                <option value="recruiter">招聘者</option>
              </Select>
            </div>
            <div className="space-y-2 md:col-span-2">
              <Label>内容（支持部分匹配）</Label>
              <div className="flex gap-2">
                <Input
                  placeholder="如：腾讯、法务、某 HR 姓名"
                  value={value}
                  onChange={(e) => setValue(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') handleAdd() }}
                />
                <Button onClick={handleAdd} className="rounded-full bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white px-5 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105 shrink-0">
                  <BiPlus className="mr-1" /> 拉黑
                </Button>
              </div>
            </div>
          </div>
          {message && (
            <p className={`mt-3 text-sm ${message.success ? 'text-green-600' : 'text-red-600'}`}>{message.text}</p>
          )}
        </CardContent>
      </Card>

      <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
        <CardHeader>
          <CardTitle>黑名单列表（{items.length} 条）</CardTitle>
          <CardDescription>
            <select
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              className="mt-1 rounded-md border border-input bg-background px-2 py-1 text-sm"
            >
              <option value="">全部类型</option>
              <option value="company">公司（{items.filter((i) => i.type === 'company').length}）</option>
              <option value="job">职位（{items.filter((i) => i.type === 'job').length}）</option>
              <option value="recruiter">招聘者（{items.filter((i) => i.type === 'recruiter').length}）</option>
            </select>
          </CardDescription>
        </CardHeader>
        <CardContent>
          {loading ? (
            <p className="text-sm text-muted-foreground">正在加载…</p>
          ) : loadError ? (
            <div role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-4">
              <p className="text-sm text-destructive">无法读取黑名单，当前显示的是空列表，不代表黑名单已生效。</p>
              <Button variant="outline" size="sm" className="mt-3" onClick={fetchAll}>
                重新读取
              </Button>
            </div>
          ) : filtered.length === 0 ? (
            <p className="text-sm text-muted-foreground">黑名单为空，所有公司均可触达。</p>
          ) : (
            <div className="space-y-6">
              {(['company', 'job', 'recruiter'] as const).map((t) => {
                const rows = grouped(t)
                if (!rows.length) return null
                return (
                  <div key={t} className="space-y-2">
                    <p className={`text-sm font-semibold ${TYPE_COLORS[t]}`}>
                      {TYPE_LABELS[t]}黑名单（{rows.length}）
                    </p>
                    <div className="rounded-lg border divide-y">
                      {rows.map((item) => (
                        <div key={item.id} className="flex items-center justify-between px-4 py-2.5">
                          <span className="text-sm">{item.value}</span>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleDelete(item.id, `${TYPE_LABELS[item.type] || item.type}「${item.value}」`)}
                            className="text-red-500 hover:text-red-600 hover:bg-red-50 rounded-full px-3"
                          >
                            <BiTrash className="mr-1" /> 移出
                          </Button>
                        </div>
                      ))}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
