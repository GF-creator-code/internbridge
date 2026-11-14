'use client'

import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { API_BASE } from '@/lib/api'

type Skill = { name: string; evidence: string }
type MatchResult = { matches: { requirement: string; evidence: string }[]; gaps: string[] }

async function analyze<T>(action: 'skills' | 'match', init: RequestInit): Promise<T> {
  const response = await fetch(API_BASE + '/api/ai/resume/' + action, { method: 'POST', ...init })
  const body = await response.json().catch(() => null)
  if (!response.ok || body == null) {
    throw new Error(response.status === 413 ? '简历最大支持 10 MB。' : body?.message || '分析请求失败，请检查本地服务后重试。')
  }
  return body as T
}

export default function ResumeSkills({ profile, onApply }: { profile: string; onApply: (text: string) => void }) {
  const [file, setFile] = useState<File | null>(null)
  const [skills, setSkills] = useState<Skill[]>([])
  const [selected, setSelected] = useState<string[]>([])
  const [extracting, setExtracting] = useState(false)
  const [extractMessage, setExtractMessage] = useState('')
  const [job, setJob] = useState('')
  const [matching, setMatching] = useState(false)
  const [matchMessage, setMatchMessage] = useState('')
  const [result, setResult] = useState<{ profile: string; job: string; comparison: MatchResult } | null>(null)

  async function extract(next: File) {
    setFile(next)
    setSkills([])
    setSelected([])
    setExtractMessage('')
    if (next.size > 10 * 1024 * 1024 || !/\.(pdf|docx|txt)$/i.test(next.name)) {
      setFile(null)
      setExtractMessage('请选择 10 MB 以内的 PDF、DOCX 或 UTF-8 TXT。')
      return
    }
    setExtracting(true)
    try {
      const data = new FormData()
      data.set('file', next)
      const items = await analyze<Skill[]>('skills', { body: data })
      setSkills(items)
      setSelected(items.map(item => item.name))
      if (!items.length) setExtractMessage('这份简历中没有提取到有原文依据的技能，可以手动填写技能介绍。')
    } catch (error) {
      setExtractMessage(error instanceof Error ? error.message : '简历解析失败。')
    } finally { setExtracting(false) }
  }

  function apply() {
    const lines = skills.filter(item => selected.includes(item.name)).map(item => item.name + '：' + item.evidence)
    const previous = new Set(profile.split('\n').map(line => line.trim()))
    const addition = lines.filter(line => !previous.has(line)).join('\n')
    if (addition) onApply([profile.trim(), addition].filter(Boolean).join('\n'))
    setExtractMessage('已加入技能介绍，可编辑后点击「保存配置」供投递使用。')
  }

  async function compare() {
    setMatching(true)
    setMatchMessage('')
    setResult(null)
    try {
      const comparison = await analyze<MatchResult>('match', {
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ profile, job }),
      })
      setResult({ profile, job, comparison })
    } catch (error) {
      setMatchMessage(error instanceof Error ? error.message : '匹配失败，请重试。')
    } finally { setMatching(false) }
  }

  const comparison = result?.profile === profile && result.job === job ? result.comparison : null

  return (
    <Card>
      <CardHeader>
        <CardTitle>简历技能与岗位匹配</CardTitle>
        <CardDescription>选择简历后会自动读取文字，发送到「环境配置」中指定的 AI 服务。支持文字版 PDF、DOCX、UTF-8 TXT，最大 10 MB。</CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="space-y-2">
          <Label htmlFor="resume-file">上传简历</Label>
          <input id="resume-file" type="file" accept=".pdf,.docx,.txt" className="block w-full text-sm"
            disabled={extracting} onChange={event => {
              const next = event.target.files?.[0]
              event.target.value = ''
              if (next) void extract(next)
            }} />
          {file && <p className="text-sm text-muted-foreground">{file.name}</p>}
          <div role="status" className="text-sm">{extracting ? '正在提取技能和原文依据…' : extractMessage}</div>
          {file && !extracting && !skills.length &&
            <Button type="button" size="sm" variant="outline" onClick={() => extract(file)}>重新解析</Button>}
        </div>
        {!!skills.length && <fieldset className="space-y-2">
          <legend className="mb-2 text-sm font-medium">选择要加入技能介绍的项目</legend>
          {skills.map(skill => <label key={skill.name} className="flex items-start gap-2 rounded border p-2 text-sm">
            <input type="checkbox" className="mt-1" checked={selected.includes(skill.name)}
              onChange={event => setSelected(event.target.checked ? [...selected, skill.name] : selected.filter(n => n !== skill.name))} />
            <span><strong>{skill.name}</strong><span className="block text-muted-foreground">{skill.evidence}</span></span>
          </label>)}
          <Button type="button" variant="outline" disabled={!selected.length} onClick={apply}>加入技能介绍</Button>
        </fieldset>}
        <div className="space-y-2 border-t pt-4">
          <Label htmlFor="resume-job-description">职位描述</Label>
          <Textarea id="resume-job-description" maxLength={20000} value={job} onChange={event => { setJob(event.target.value); setMatchMessage('') }}
            placeholder="粘贴岗位职责和任职要求" className="min-h-24" />
          <p className="text-xs text-muted-foreground">使用本页当前的技能介绍进行匹配，可直接试用尚未保存的修改。</p>
          <Button type="button" disabled={matching || !job.trim() || !profile.trim()} onClick={compare}>{matching ? '正在匹配…' : '分析岗位匹配'}</Button>
          {matchMessage && <p role="alert" className="text-sm">{matchMessage}</p>}
        </div>
        {comparison && <div className="grid gap-4 md:grid-cols-2" aria-live="polite">
          <section><h3 className="font-medium">有依据的匹配项</h3>
            {comparison.matches.length ? <ul className="mt-2 space-y-2">{comparison.matches.map((m,i) =>
              <li key={i} className="text-sm"><strong>{m.requirement}</strong><p className="text-muted-foreground">{m.evidence}</p></li>)}</ul> : <p className="text-sm">暂无有依据的匹配项。</p>}
          </section>
          <section><h3 className="font-medium">暂缺依据的要求</h3>
            {comparison.gaps.length ? <ul className="mt-2 list-inside list-disc text-sm">{comparison.gaps.map(gap => <li key={gap}>{gap}</li>)}</ul> : <p className="text-sm">未发现需要补充依据的要求。</p>}
          </section>
        </div>}
      </CardContent>
    </Card>
  )
}
