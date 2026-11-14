'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { BiBrain, BiSave } from 'react-icons/bi'
import PageHeader from '@/app/components/PageHeader'
import ToggleSwitch, { toBool } from '@/app/components/ToggleSwitch'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { API_BASE } from '@/lib/api'
import ResumeSkills from './ResumeSkills'

type Profile = { introduce: string; prompt: string }
const empty: Profile = { introduce: '', prompt: '' }

export default function AiConfigPage() {
  const [draft, setDraft] = useState<Profile>(empty)
  const [saved, setSaved] = useState<Profile | null>(null)
  const [loadError, setLoadError] = useState(false)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState(false)
  const [justSaved, setJustSaved] = useState(false)
  const [bossEnabled, setBossEnabled] = useState<boolean | null>(null)
  const [switchBusy, setSwitchBusy] = useState(false)
  const [switchError, setSwitchError] = useState('')
  const dirty = saved !== null && (draft.introduce !== saved.introduce || draft.prompt !== saved.prompt)

  async function loadProfile() {
    setLoadError(false)
    try {
      const res = await fetch(API_BASE + '/api/ai/config')
      if (!res.ok) throw new Error()
      const body = await res.json()
      if (!body.success || !body.data) throw new Error()
      const profile = {
        introduce: body.data.introduce === '请在此填写您的技能介绍' ? '' : body.data.introduce || '',
        prompt: body.data.prompt === '请在此填写AI提示词模板' ? '' : body.data.prompt || '',
      }
      setDraft(profile)
      setSaved(profile)
    } catch { setLoadError(true) }
  }

  async function loadSwitch() {
    setSwitchError('')
    try {
      const res = await fetch(API_BASE + '/api/boss/config')
      if (!res.ok) throw new Error()
      const data = await res.json()
      if (!data.config) throw new Error()
      setBossEnabled(toBool(data.config.enableAi))
    } catch { setSwitchError('未能读取 BOSS 直聘的开关状态。') }
  }

  useEffect(() => { void loadProfile(); void loadSwitch() }, [])
  useEffect(() => {
    if (!dirty) return
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = true }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  function edit(profile: Profile) {
    setDraft(profile)
    setSaveError(false)
    setJustSaved(false)
  }

  async function save(profile: Profile) {
    if (!saved || saving) return
    edit(profile)
    setSaving(true)
    try {
      const res = await fetch(API_BASE + '/api/ai/config', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(profile),
      })
      if (!res.ok || !(await res.json()).success) throw new Error()
      setSaved(profile)
      setJustSaved(true)
    } catch { setSaveError(true) }
    finally { setSaving(false) }
  }

  async function toggleBoss() {
    if (bossEnabled === null || switchBusy) return
    setSwitchBusy(true)
    setSwitchError('')
    try {
      const next = !bossEnabled
      const res = await fetch(API_BASE + '/api/boss/config', {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enableAi: next ? 1 : 0 }),
      })
      if (!res.ok) throw new Error()
      setBossEnabled(next)
    } catch { setSwitchError('开关保存失败，请重试。') }
    finally { setSwitchBusy(false) }
  }

  return <div className="space-y-5">
    <PageHeader icon={<BiBrain />} title="简历与 AI 匹配" subtitle="整理可以用于投递的技能，核对香港及内地实习的职位要求。"
      iconClass="text-white" accentBgClass="bg-purple-600" />
    <p className="text-sm">首次使用时，先填写模型地址、密钥和名称。{' '}
      <Link href="/env-config#ai-model" target="_blank" rel="noopener" className="font-medium text-primary underline underline-offset-4">配置 AI 模型（新标签页）</Link>
    </p>
    {saved === null ? <Card><CardContent className="space-y-3 pt-6">
      {loadError ? <><p role="alert">无法读取已保存的技能介绍。请检查本地服务后重试。</p>
        <Button variant="outline" onClick={loadProfile}>重新读取</Button></> :
        <p role="status">正在读取已保存的技能介绍…</p>}
    </CardContent></Card> : <ResumeSkills profile={draft.introduce} saving={saving}
      onApply={introduce => edit({ ...draft, introduce })}
      onSave={introduce => save({ ...draft, introduce })}>
      <Card>
        <CardHeader><CardTitle><h2>投递用技能介绍</h2></CardTitle>
          <CardDescription>启用 AI 的 BOSS 直聘、JobsDB 和 OfferToday 会在下次任务中读取已保存的介绍。</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2"><Label htmlFor="introduce">技能介绍</Label>
            <Textarea id="introduce" value={draft.introduce} disabled={saving} className="min-h-32"
              onChange={event => edit({ ...draft, introduce: event.target.value })}
              placeholder="写下你做过的事，例如用 Excel 整理调查数据，或用 SQL 分析订单。" />
          </div>
          <details className="border-y py-3">
            <summary className="cursor-pointer text-sm font-medium">编辑投递提示词</summary>
            <div className="mt-3 space-y-2"><Label htmlFor="prompt">AI 提示词</Label>
              <Textarea id="prompt" value={draft.prompt} disabled={saving} className="min-h-24"
                onChange={event => edit({ ...draft, prompt: event.target.value })} />
              <p className="text-sm text-muted-foreground">留空使用平台默认提示词。模板中的五个 %s 依次引用技能介绍、目标方向、岗位名称、职位描述和参考问候。</p>
            </div>
          </details>
          <div className="flex flex-wrap items-center gap-3">
            <Button type="button" disabled={!dirty || saving} onClick={() => save(draft)}><BiSave /> 保存修改</Button>
            <span role="status" className="text-sm">{saving ? '正在保存…' : saveError ? '保存失败，修改仍在本页，可再次保存。' :
              dirty ? '有尚未保存的修改' : justSaved ? '已保存，下次投递任务将使用这份介绍。' : saved.introduce.trim() ? '已读取保存的介绍' : '尚未填写技能介绍'}</span>
          </div>
        </CardContent>
      </Card>
    </ResumeSkills>}
    <Card><CardContent className="space-y-2 pt-6">
      <div className="flex items-center justify-between gap-4">
        <Label htmlFor="boss-ai">BOSS 直聘投递时使用 AI</Label>
        <ToggleSwitch id="boss-ai" label="BOSS 直聘投递时使用 AI" checked={bossEnabled === true}
          disabled={bossEnabled === null || switchBusy} onToggle={toggleBoss} />
      </div>
      <p className="text-sm text-muted-foreground">简历解析与手动匹配可独立使用。两个香港平台的投递开关位于各自的配置页。</p>
      {switchError && <div role="alert" className="text-sm">{switchError} {bossEnabled === null && <button type="button" className="underline" onClick={loadSwitch}>重试</button>}</div>}
    </CardContent></Card>
  </div>
}
