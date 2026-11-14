'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { BiCodeAlt, BiSave } from 'react-icons/bi'
import PageHeader from '@/app/components/PageHeader'
import ToggleSwitch, { toBool } from '@/app/components/ToggleSwitch'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { API_BASE } from '@/lib/api'

type Config = { BASE_URL: string; API_KEY: string; MODEL: string; HOOK_URL: string; BOT_IS_SEND: string }
const empty: Config = { BASE_URL: '', API_KEY: '', MODEL: '', HOOK_URL: '', BOT_IS_SEND: '0' }

export default function EnvConfig() {
  const [draft, setDraft] = useState(empty)
  const [saved, setSaved] = useState<Config | null>(null)
  const [loadError, setLoadError] = useState(false)
  const [showKey, setShowKey] = useState(false)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  const dirty = saved !== null && (Object.keys(empty) as (keyof Config)[]).some(key => saved[key] !== draft[key])

  async function load() {
    setLoadError(false)
    try {
      const res = await fetch(API_BASE + '/api/config')
      if (!res.ok) throw new Error()
      const body = await res.json()
      if (!body.success || !body.data) throw new Error()
      const config = { BASE_URL: body.data.BASE_URL || '', API_KEY: body.data.API_KEY || '', MODEL: body.data.MODEL || '',
        HOOK_URL: body.data.HOOK_URL || '', BOT_IS_SEND: toBool(body.data.BOT_IS_SEND) ? '1' : '0' }
      setDraft(config)
      setSaved(config)
    } catch { setLoadError(true) }
  }

  useEffect(() => { void load() }, [])

  function update(key: keyof Config, value: string) {
    setMessage('')
    setDraft(previous => ({ ...previous, [key]: value }))
  }

  async function save() {
    if (!saved || !dirty || saving) return
    setSaving(true)
    setMessage('')
    try {
      const changes = Object.fromEntries(Object.entries(draft).filter(([key, value]) => saved[key as keyof Config] !== value))
      const res = await fetch(API_BASE + '/api/config', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(changes),
      })
      if (!res.ok || !(await res.json()).success) throw new Error()
      setSaved(draft)
      setMessage('配置已保存。')
    } catch { setMessage('保存失败，输入仍在本页。请检查本地服务后重试。') }
    finally { setSaving(false) }
  }

  return <div className="space-y-5">
    <PageHeader icon={<BiCodeAlt />} title="环境配置" subtitle="连接用于简历解析、岗位匹配和投递的 AI 模型。" />
    {saved === null ? <Card><CardContent className="space-y-3 pt-6">
      {loadError ? <><p role="alert">无法读取已有配置，请检查本地服务后重试。</p>
        <Button variant="outline" onClick={load}>重新读取</Button></> : <p role="status">正在读取配置…</p>}
    </CardContent></Card> :
      <form className="space-y-5" onSubmit={event => { event.preventDefault(); void save() }}>
        <fieldset disabled={saving} className="min-w-0 space-y-5">
          <Card id="ai-model" className="scroll-mt-16">
            <CardHeader><CardTitle><h2>连接 AI 模型</h2></CardTitle>
              <CardDescription>填入服务商提供的兼容接口资料。密钥保存在本机数据库中。</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2"><Label htmlFor="baseUrl">模型地址（Base URL）</Label>
                <Input id="baseUrl" value={draft.BASE_URL} autoComplete="off" placeholder="https://api.example.com/v1" onChange={e => update('BASE_URL', e.target.value)} />
              </div>
              <div className="space-y-2"><Label htmlFor="model">模型名称</Label>
                <Input id="model" value={draft.MODEL} autoComplete="off" placeholder="服务商提供的模型 ID" onChange={e => update('MODEL', e.target.value)} />
              </div>
              <div className="space-y-2 sm:col-span-2"><Label htmlFor="apiKey">API 密钥</Label>
                <div className="flex gap-2">
                  <Input id="apiKey" type={showKey ? 'text' : 'password'} autoComplete="off" value={draft.API_KEY} onChange={e => update('API_KEY', e.target.value)} />
                  <Button type="button" variant="outline" aria-controls="apiKey" aria-pressed={showKey} onClick={() => setShowKey(!showKey)}>{showKey ? '隐藏' : '显示'}</Button>
                </div>
              </div>
            </CardContent>
          </Card>
          <Card><CardContent className="pt-5"><details>
            <summary className="cursor-pointer font-medium">企业微信通知（可选）</summary>
            <div className="mt-4 space-y-4">
              <div className="flex items-center justify-between gap-3"><Label htmlFor="botIsSend">发送企业微信通知</Label>
                <ToggleSwitch id="botIsSend" label="发送企业微信通知" checked={draft.BOT_IS_SEND === '1'} onToggle={() => update('BOT_IS_SEND', draft.BOT_IS_SEND === '1' ? '0' : '1')} /></div>
              <Label htmlFor="hookUrl">机器人 Webhook 地址</Label>
              <Input id="hookUrl" type="password" autoComplete="off" value={draft.HOOK_URL} onChange={e => update('HOOK_URL', e.target.value)} />
            </div>
          </details></CardContent></Card>
          <div className="flex flex-wrap items-center gap-3">
            <Button type="submit" disabled={!dirty || saving}><BiSave /> 保存配置</Button>
            <span role="status" className="text-sm">{saving ? '正在保存…' : message || (dirty ? '有尚未保存的修改' : '')}</span>
          </div>
        </fieldset>
        {!dirty && saved.BASE_URL && saved.API_KEY && saved.MODEL && <Link href="/ai-config" className="inline-block py-2 font-medium text-primary underline underline-offset-4">前往简历与 AI 匹配</Link>}
      </form>}
  </div>
}
