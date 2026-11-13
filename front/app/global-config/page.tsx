'use client'

import { useState, useEffect, useCallback } from 'react'
import { BiShield, BiTrash, BiPlus, BiSave } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import PageHeader from '@/app/components/PageHeader'
import { API_BASE } from '@/lib/api'

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

export default function GlobalConfigPage() {
  const [items, setItems] = useState<BlacklistItem[]>([])
  const [type, setType] = useState('company')
  const [value, setValue] = useState('')
  const [filter, setFilter] = useState('')
  const [message, setMessage] = useState<{ success: boolean; text: string } | null>(null)
  const [loading, setLoading] = useState(true)

  const fetchAll = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE}/api/global/blacklist`)
      const data = await res.json()
      setItems(Array.isArray(data) ? data : [])
    } catch {
      setMessage({ success: false, text: '黑名单加载失败：网络或服务异常' })
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchAll() }, [fetchAll])

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
      <PageHeader
        icon={<BiShield className="text-2xl" />}
        title="全局配置"
        subtitle="跨平台共享的黑名单等通用配置，所有平台投递时统一生效"
        iconClass="text-white"
        accentBgClass="bg-gradient-to-r from-red-500 to-rose-600"
        actions={
          <Button onClick={fetchAll} size="sm" className="rounded-full bg-gradient-to-r from-blue-500 to-indigo-600 hover:from-blue-600 hover:to-indigo-700 text-white px-4 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-105">
            <BiSave className="mr-1" /> 刷新
          </Button>
        }
      />

      <Card className="animate-in fade-in slide-in-from-bottom-5 duration-700">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <BiShield className="text-primary" />
            全局黑名单说明
          </CardTitle>
          <CardDescription>数据与 Boss 页内嵌的黑名单管理同表，双向等效</CardDescription>
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
              <Label>内容（公司名 / 职位关键词 / 招聘者名，支持部分匹配）</Label>
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
            <p className="text-sm text-muted-foreground">加载中...</p>
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
