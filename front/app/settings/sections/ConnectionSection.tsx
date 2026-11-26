'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { BiCodeAlt, BiSave } from 'react-icons/bi'
import ToggleSwitch, { toBool } from '@/app/components/ToggleSwitch'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { API_BASE } from '@/lib/api'

type Config = { BASE_URL: string; API_KEY: string; MODEL: string; HOOK_URL: string; BOT_IS_SEND: string }
const empty: Config = { BASE_URL: '', API_KEY: '', MODEL: '', HOOK_URL: '', BOT_IS_SEND: '0' }

/** 凭据类字段：后端只回传掩码，输入框留空表示不修改已保存的值。 */
const SECRET_KEYS = ['API_KEY', 'HOOK_URL'] as const
const MASK_PREFIX = '••••'

function isMasked(value: string): boolean {
  return value.startsWith(MASK_PREFIX)
}

export default function ConnectionSection({ onSwitch }: { onSwitch?: (tab: string) => void } = {}) {
  const [draft, setDraft] = useState(empty)
  const [saved, setSaved] = useState<Config | null>(null)
  const [loadError, setLoadError] = useState(false)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  const [keyError, setKeyError] = useState('')

  /** 已保存但被脱敏的凭据：键 -> 是否已配置 */
  const [secretStored, setSecretStored] = useState<Record<string, boolean>>({})

  const dirty = saved !== null && (Object.keys(empty) as (keyof Config)[]).some(key => {
    if (SECRET_KEYS.includes(key as (typeof SECRET_KEYS)[number])) {
      // 凭据只在用户真的输入了新值时才算改动
      return draft[key] !== '' && draft[key] !== saved[key]
    }
    return saved[key] !== draft[key]
  })

  async function load() {
    setLoadError(false)
    try {
      const res = await fetch(API_BASE + '/api/config')
      if (!res.ok) throw new Error()
      const body = await res.json()
      if (!body.success || !body.data) throw new Error()
      const config = { BASE_URL: body.data.BASE_URL || '', API_KEY: body.data.API_KEY || '', MODEL: body.data.MODEL || '',
        HOOK_URL: body.data.HOOK_URL || '', BOT_IS_SEND: toBool(body.data.BOT_IS_SEND) ? '1' : '0' }
      // 后端对凭据只回传掩码。这里把它换成一个「已配置」的标记，
      // 输入框本身保持空白 —— 这样用户不会误以为掩码就是真实密钥，
      // 保存时也不会把掩码原样提交回去（后端也会拒绝写回掩码）。
      const stored: Record<string, boolean> = {}
      for (const key of SECRET_KEYS) {
        // 后端对未配置的凭据返回空串，对已配置的返回掩码。两种都算「已配置」的前提是它非空。
        stored[key] = config[key] !== ''
        config[key] = ''
      }
      setSecretStored(stored)
      setDraft(config)
      setSaved(config)
    } catch { setLoadError(true) }
  }

  useEffect(() => { void load() }, [])

  function update(key: keyof Config, value: string) {
    setMessage('')
    setKeyError('')
    setDraft(previous => ({ ...previous, [key]: value }))
  }

  /** 三个 AI 配置项要么一起填，要么都不填——半填的状态到投递时才会炸。 */
  function validateAi(): string {
    const baseUrl = draft.BASE_URL.trim()
    const model = draft.MODEL.trim()
    const apiKey = draft.API_KEY.trim()
    const keyReady = apiKey !== '' || secretStored.API_KEY
    if (baseUrl === '' && model === '' && apiKey === '') return ''
    if (baseUrl === '') return '请填写模型地址（Base URL）。'
    if (!/^https?:\/\/.+/i.test(baseUrl)) return '模型地址需要以 http:// 或 https:// 开头。'
    if (model === '') return '请填写模型名称。'
    if (!keyReady) return '请填写 API 密钥。'
    return ''
  }

  async function save() {
    if (!saved || !dirty || saving) return
    const invalid = validateAi()
    if (invalid) { setKeyError(invalid); return }
    setSaving(true)
    setMessage('')
    setKeyError('')
    try {
      const changes: Record<string, string> = {}
      for (const key of Object.keys(empty) as (keyof Config)[]) {
        const isSecret = SECRET_KEYS.includes(key as (typeof SECRET_KEYS)[number])
        if (isSecret) {
          // 只提交用户真的输入过的凭据；留空 = 保持已保存的值
          if (draft[key] !== '') changes[key] = draft[key]
        } else if (saved[key] !== draft[key]) {
          changes[key] = draft[key]
        }
      }
      const res = await fetch(API_BASE + '/api/config', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(changes),
      })
      if (!res.ok || !(await res.json()).success) throw new Error()
      // 保存后把凭据输入框清空，并同步「已保存」状态
      const next = { ...draft, API_KEY: '', HOOK_URL: '' }
      const stored = { ...secretStored }
      if (draft.API_KEY !== '') stored.API_KEY = true
      if (draft.HOOK_URL !== '') stored.HOOK_URL = true
      setSecretStored(stored)
      setDraft(next)
      setSaved(next)
      setMessage('配置已保存。')
    } catch { setMessage('保存失败，输入仍在本页。请检查本地服务后重试。') }
    finally { setSaving(false) }
  }

  const aiReady = Boolean(saved?.BASE_URL && saved?.MODEL && secretStored.API_KEY)
  const secretPlaceholder = (key: string) => secretStored[key] ? '已保存（留空则不修改）' : '尚未配置'

  return <div className="space-y-5">
    {saved === null ? <Card><CardContent className="space-y-3 pt-6">
      {loadError ? <><p role="alert">无法读取已有配置，请检查本地服务后重试。</p>
        <Button variant="outline" onClick={load}>重新读取</Button></> : <p role="status">正在读取配置…</p>}
    </CardContent></Card> :
      <form className="space-y-5" onSubmit={event => { event.preventDefault(); void save() }}>
        <fieldset disabled={saving} className="min-w-0 space-y-5">
          <Card id="ai-model" className="scroll-mt-16">
            <CardHeader><CardTitle><h2>连接 AI 模型</h2></CardTitle>
              <CardDescription>填入服务商提供的兼容接口资料。密钥保存在本机数据库中，读取时只显示末四位。</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2"><Label htmlFor="baseUrl">模型地址（Base URL）</Label>
                <Input id="baseUrl" value={draft.BASE_URL} autoComplete="off" placeholder="https://api.example.com/v1" onChange={e => update('BASE_URL', e.target.value)} />
              </div>
              <div className="space-y-2"><Label htmlFor="model">模型名称</Label>
                <Input id="model" value={draft.MODEL} autoComplete="off" placeholder="服务商提供的模型 ID" onChange={e => update('MODEL', e.target.value)} />
              </div>
              <div className="space-y-2 sm:col-span-2"><Label htmlFor="apiKey">API 密钥</Label>
                <Input id="apiKey" type="password" autoComplete="off" value={draft.API_KEY} placeholder={secretPlaceholder('API_KEY')} onChange={e => update('API_KEY', e.target.value)} />
                <p className="text-xs text-muted-foreground">
                  {secretStored.API_KEY ? '已保存密钥，输入新值即可覆盖；留空表示保持不变。' : '尚未保存密钥。'}
                </p>
              </div>
            </CardContent>
          </Card>
          <Card><CardContent className="pt-5"><details>
            <summary className="cursor-pointer font-medium">企业微信通知（可选）</summary>
            <div className="mt-4 space-y-4">
              <div className="flex items-center justify-between gap-3"><Label htmlFor="botIsSend">发送企业微信通知</Label>
                <ToggleSwitch id="botIsSend" label="发送企业微信通知" checked={draft.BOT_IS_SEND === '1'} onToggle={() => update('BOT_IS_SEND', draft.BOT_IS_SEND === '1' ? '0' : '1')} /></div>
              <Label htmlFor="hookUrl">机器人 Webhook 地址</Label>
              <Input id="hookUrl" type="password" autoComplete="off" value={draft.HOOK_URL} placeholder={secretPlaceholder('HOOK_URL')} onChange={e => update('HOOK_URL', e.target.value)} />
            </div>
          </details></CardContent></Card>
          {keyError && <p role="alert" className="text-sm text-destructive">{keyError}</p>}
          <div className="flex flex-wrap items-center gap-3">
            <Button type="submit" disabled={!dirty || saving}><BiSave /> 保存配置</Button>
            <span role="status" className="text-sm">{saving ? '正在保存…' : message || (dirty ? '有尚未保存的修改' : '')}</span>
          </div>
          {!aiReady && !dirty && <p className="text-sm text-muted-foreground">
            提示：填写模型地址、模型名称和 API 密钥后，才能使用简历解析与岗位匹配。未配置时投递任务会直接失败并提示缺少必要配置。
          </p>}
        </fieldset>
        {aiReady && (onSwitch
          ? <button type="button" onClick={() => onSwitch('match')} className="inline-block py-2 font-medium text-primary underline underline-offset-4">前往「简历与匹配」</button>
          : <Link href="/settings?tab=match" className="inline-block py-2 font-medium text-primary underline underline-offset-4">前往「简历与匹配」</Link>)}
      </form>}
  </div>
}
