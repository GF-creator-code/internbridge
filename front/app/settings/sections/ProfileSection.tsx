'use client'

import { useEffect, useState } from 'react'
import { BiUser, BiSave, BiBriefcase, BiCopy } from 'react-icons/bi'
import FeedbackBar from '@/app/components/FeedbackBar'
import ConfigLoadError from '@/app/components/ConfigLoadError'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Select } from '@/components/ui/select'
import { API_BASE } from '@/lib/api'
import { useProfileOptions } from '@/lib/useProfileOptions'

type Profile = {
  introduce: string
  resumePath: string
  workType: string
  city: string
  degree: string
  availableFrom: string
  email: string
  phone: string
  summary: string
}

const empty: Profile = {
  introduce: '', resumePath: '', workType: '不限', city: '', degree: '',
  availableFrom: '', email: '', phone: '', summary: '',
}

/** 工作形式选项。value 同时用于后端 WorkType 枚举解析。 */
const WORK_TYPES = [
  { value: '不限', hint: '不限定工作形式，岗位类型不限' },
  { value: '实习', hint: '搜索会带上「实习」，并跳过全职/社招岗位' },
  { value: '兼职', hint: '搜索会带上「兼职」，并跳过全职/社招岗位' },
  { value: '全职', hint: '搜索会带上「全职」，并跳过实习/兼职岗位' },
  { value: '校园招聘', hint: '面向应届生的校园招聘岗位' },
]

export default function ProfileSection({ onSwitch }: { onSwitch?: (tab: string) => void } = {}) {
  const [draft, setDraft] = useState<Profile>(empty)
  const [saved, setSaved] = useState<Profile | null>(null)
  const [loadError, setLoadError] = useState(false)
  const [saving, setSaving] = useState(false)
  const [feedback, setFeedback] = useState<{ kind: 'error' | 'success' | 'info'; message: string; title?: string } | null>(null)
  // 城市/学历/到岗时间都从后端选项表来，不让用户手打
  const options = useProfileOptions()

  // 城市在「全局规则」编辑、技能介绍在「简历与匹配」编辑，本页不参与比较，
  // 否则保存档案时会用旧值把那两处的数据覆盖掉。
  const EDITABLE: (keyof Profile)[] = ['workType', 'degree', 'availableFrom', 'resumePath', 'email', 'phone']
  const dirty = saved !== null && EDITABLE.some(k => saved[k] !== draft[k])

  async function load() {
    setLoadError(false)
    try {
      const res = await fetch(API_BASE + '/api/profile')
      if (!res.ok) throw new Error()
      const body = await res.json()
      if (!body.success || !body.data) throw new Error()
      const config: Profile = { ...empty, ...body.data }
      setDraft(config)
      setSaved(config)
    } catch {
      setLoadError(true)
    }
  }

  useEffect(() => { void load() }, [])

  function update(key: keyof Profile, value: string) {
    setFeedback(null)
    setDraft(prev => ({ ...prev, [key]: value }))
  }

  async function save() {
    if (!saved || !dirty || saving) return
    setSaving(true)
    setFeedback(null)
    try {
      const res = await fetch(API_BASE + '/api/profile', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(Object.fromEntries(EDITABLE.map(k => [k, draft[k]]))),
      })
      const body = await res.json().catch(() => null)
      if (!res.ok || !body?.success) throw new Error(body?.message || '保存失败')
      setSaved(draft)
      setFeedback({ kind: 'success', title: '已保存', message: '全局档案已更新，各平台配置页可一键引用。' })
    } catch (error) {
      setFeedback({
        kind: 'error',
        title: '保存失败',
        message: error instanceof Error ? error.message : '无法连接后端服务，请确认服务已启动。',
      })
    } finally {
      setSaving(false)
    }
  }

  async function copyIntroduce() {
    try {
      await navigator.clipboard.writeText(draft.introduce)
      setFeedback({ kind: 'info', title: '已复制', message: '技能介绍已复制到剪贴板。' })
    } catch {
      setFeedback({ kind: 'error', title: '复制失败', message: '浏览器拒绝了剪贴板访问，请手动选中复制。' })
    }
  }

  return (
    <div className="space-y-5">
      {saved === null ? (
        <Card><CardContent className="space-y-3 pt-6">
          {loadError ? (
            <>
              <ConfigLoadError onRetry={() => { setLoadError(false); void load() }} />
            </>
          ) : <p role="status">正在读取全局档案…</p>}
        </CardContent></Card>
      ) : (
        <form className="space-y-5" onSubmit={event => { event.preventDefault(); void save() }}>
          <fieldset disabled={saving} className="min-w-0 space-y-5">
            <Card>
              <CardHeader>
                <CardTitle><h2>工作形式</h2></CardTitle>
                <CardDescription>决定搜索时带什么限定词，以及哪些岗位会被自动跳过。</CardDescription>
              </CardHeader>
              <CardContent>
                <fieldset className="space-y-2">
                  <legend className="sr-only">选择工作形式</legend>
                  {WORK_TYPES.map(option => (
                    <label
                      key={option.value}
                      className={`flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors ${
                        draft.workType === option.value
                          ? 'border-primary bg-primary/5'
                          : 'border-border hover:bg-muted/40'
                      }`}
                    >
                      <input
                        type="radio"
                        name="workType"
                        value={option.value}
                        checked={draft.workType === option.value}
                        onChange={() => update('workType', option.value)}
                        className="mt-1"
                      />
                      <span className="min-w-0">
                        <span className="block text-sm font-medium">{option.value}</span>
                        <span className="block text-xs text-muted-foreground">{option.hint}</span>
                      </span>
                    </label>
                  ))}
                </fieldset>
                <p className="mt-3 text-xs text-muted-foreground">
                  修改后对所有平台生效。平台自己的关键词、城市等筛选项仍各平台独立配置。
                </p>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle><h2>基本信息</h2></CardTitle>
                <CardDescription>这些内容会被各平台的打招呼语和 AI 匹配复用。</CardDescription>
              </CardHeader>
              <CardContent className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label>意向城市（只读）</Label>
                  <p className="rounded-md border border-input bg-muted/30 px-3 py-2 text-sm">{draft.city || '未设置'}</p>
                  {onSwitch && <button type="button" onClick={() => onSwitch('rules')}
                    className="text-xs text-primary underline underline-offset-4">在「全局规则」中修改并同步到各平台</button>}
                </div>
                <div className="space-y-2">
                  <Label htmlFor="degree">最高学历</Label>
                  <Select id="degree" value={draft.degree} onChange={e => update('degree', e.target.value)}>
                    <option value="">不限</option>
                    {options.degree.map(degree => <option key={degree} value={degree}>{degree}</option>)}
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="availableFrom">可到岗时间</Label>
                  <Select id="availableFrom" value={draft.availableFrom} onChange={e => update('availableFrom', e.target.value)}>
                    <option value="">不限</option>
                    {options.availableFrom.map(value => <option key={value} value={value}>{value}</option>)}
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="resumePath">简历文件路径</Label>
                  <Input id="resumePath" value={draft.resumePath} placeholder="如：C:/resume/我的简历.pdf"
                    onChange={e => update('resumePath', e.target.value)} />
                  <p className="text-xs text-muted-foreground">留空则各平台使用自己填的路径。</p>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="email">邮箱</Label>
                  <Input id="email" type="email" value={draft.email} placeholder="用于投递时填写"
                    onChange={e => update('email', e.target.value)} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="phone">手机号</Label>
                  <Input id="phone" value={draft.phone} placeholder="用于投递时填写"
                    onChange={e => update('phone', e.target.value)} />
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle><h2>技能介绍</h2></CardTitle>
                <CardDescription>投递时生成打招呼语所用的技能介绍，统一在「简历与匹配」维护。</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                <p className="text-sm text-muted-foreground whitespace-pre-wrap">
                  {draft.introduce || '尚未填写技能介绍。'}
                </p>
                {onSwitch && <button type="button" onClick={() => onSwitch('match')}
                  className="text-sm font-medium text-primary underline underline-offset-4">去「简历与匹配」编辑 →</button>}
              </CardContent>
            </Card>

            <FeedbackBar feedback={feedback} onDismiss={() => setFeedback(null)} />

            <div className="flex flex-wrap items-center gap-3">
              <Button type="submit" disabled={!dirty || saving}><BiSave /> 保存档案</Button>
              <span role="status" className="text-sm">
                {saving ? '正在保存…' : dirty ? '有尚未保存的修改' : ''}
              </span>
            </div>
          </fieldset>
        </form>
      )}
    </div>
  )
}
