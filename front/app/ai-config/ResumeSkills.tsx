'use client'

import { useState, type ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { API_BASE } from '@/lib/api'

type Skill = { name: string; evidence: string }
type MatchResult = { matches: { requirement: string; evidence: string }[]; gaps: string[] }

async function analyze<T>(action: 'skills' | 'match', init: RequestInit): Promise<T> {
  let response: Response
  try { response = await fetch(API_BASE + '/api/ai/resume/' + action, { method: 'POST', ...init }) }
  catch { throw new Error('无法连接到本地服务，请确认程序正在运行后重试。') }
  if (response.status === 404) throw new Error('当前运行的后端尚未提供简历分析。请用更新后的版本重新启动本地服务。')
  if (response.status === 413) throw new Error('简历最大支持 10 MB。')
  const body = await response.json().catch(() => null)
  if (!response.ok || body == null) throw new Error(body?.message || '分析失败，请检查模型配置后重试。')
  return body as T
}

type Props = {
  profile: string; saving: boolean; children: ReactNode
  onApply: (profile: string) => void; onSave: (profile: string) => Promise<void>
}

export default function ResumeSkills({ profile, saving, children, onApply, onSave }: Props) {
  const [file, setFile] = useState<File | null>(null)
  const [skills, setSkills] = useState<Skill[]>([])
  const [selected, setSelected] = useState<string[]>([])
  const [extracting, setExtracting] = useState(false)
  const [extractError, setExtractError] = useState('')
  const [extracted, setExtracted] = useState(false)
  const [job, setJob] = useState('')
  const [matching, setMatching] = useState(false)
  const [matchError, setMatchError] = useState('')
  const [result, setResult] = useState<{ profile: string; job: string; comparison: MatchResult } | null>(null)

  async function extract(next: File) {
    setExtractError('')
    if (!next.size || next.size > 10 * 1024 * 1024 || !/\.(pdf|docx|txt)$/i.test(next.name)) {
      setExtractError('请选择非空的文字版 PDF、DOCX 或 UTF-8 TXT 文件，大小不超过 10 MB。')
      return
    }
    setFile(next)
    setSkills([])
    setSelected([])
    setExtracted(false)
    setExtracting(true)
    try {
      const data = new FormData()
      data.set('file', next)
      const items = await analyze<Skill[]>('skills', { body: data })
      setSkills(items)
      setSelected(items.map(item => item.name))
      setExtracted(true)
    } catch (error) { setExtractError(error instanceof Error ? error.message : '简历解析失败。') }
    finally { setExtracting(false) }
  }

  const existing = new Set(profile.split('\n').map(line => line.trim()))
  const additions = skills.filter(skill => selected.includes(skill.name))
    .map(skill => skill.name + '：' + skill.evidence).filter(line => !existing.has(line))
  const combined = [profile.trimEnd(), ...additions].filter(Boolean).join('\n')
  const comparison = result?.profile === profile && result.job === job ? result.comparison : null

  async function compare() {
    setMatching(true)
    setMatchError('')
    setResult(null)
    try {
      const comparison = await analyze<MatchResult>('match', {
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ profile, job }),
      })
      setResult({ profile, job, comparison })
    } catch (error) { setMatchError(error instanceof Error ? error.message : '匹配失败，请重试。') }
    finally { setMatching(false) }
  }

  return <>
    <Card>
      <CardHeader><CardTitle><h2>从简历提取技能</h2></CardTitle>
        <CardDescription>选择文件即开始解析。简历文字会发送给你配置的 AI，应用仅在你点保存时记录技能介绍。</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="resume-file">上传简历</Label>
          <input id="resume-file" type="file" accept=".pdf,.docx,.txt" aria-describedby="resume-formats"
            className="block w-full min-w-0 rounded-md border bg-background p-2 text-sm file:mr-3 file:rounded file:border-0 file:bg-secondary file:px-3 file:py-2 file:text-secondary-foreground"
            disabled={extracting || saving} onChange={event => {
              const next = event.target.files?.[0]
              event.target.value = ''
              if (next) void extract(next)
            }} />
          <p id="resume-formats" className="text-sm text-muted-foreground">文字版 PDF、DOCX、UTF-8 TXT，最大 10 MB。扫描件可先转为可选取文字的文件。</p>
          <p role="status" className="break-words text-sm">{extracting ? '正在提取技能和原文依据…' :
            file && extracted ? file.name + ' · ' + (skills.length ? '已提取 ' + skills.length + ' 项技能' : '没有提取到有原文依据的技能，可以在下方手动填写。') : ''}</p>
          {extractError && <p role="alert" className="text-sm text-destructive">{extractError}</p>}
          {file && !extracted && !extracting && <Button type="button" variant="outline" onClick={() => extract(file)}>重新解析所选文件</Button>}
        </div>
        {!!skills.length && <fieldset className="space-y-2" disabled={saving}>
          <legend className="mb-2 font-medium">核对技能和简历原文</legend>
          {skills.map(skill => <label key={skill.name} className="flex cursor-pointer items-start gap-3 rounded-md border p-3 text-sm">
            <input type="checkbox" className="mt-1" checked={selected.includes(skill.name)}
              onChange={event => setSelected(event.target.checked ? [...selected, skill.name] : selected.filter(name => name !== skill.name))} />
            <span className="min-w-0 break-words"><strong>{skill.name}</strong><span className="mt-1 block text-muted-foreground">{skill.evidence}</span></span>
          </label>)}
          <p className="text-sm text-muted-foreground">已选 {selected.length} 项，可新增 {additions.length} 项。已有介绍和手动修改会一并保留。</p>
          <div className="flex flex-wrap gap-2">
            <Button type="button" disabled={!additions.length || saving} onClick={() => onSave(combined)}>加入并保存所选技能</Button>
            <Button type="button" variant="outline" disabled={!additions.length || saving} onClick={() => onApply(combined)}>先加入草稿</Button>
          </div>
        </fieldset>}
      </CardContent>
    </Card>
    {children}
    <Card>
      <CardHeader><CardTitle><h2>匹配职位描述</h2></CardTitle>
        <CardDescription>可粘贴繁中、简中或英文职位描述。匹配使用上方当前的技能介绍，也可以试用尚未保存的草稿。</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <Label htmlFor="resume-job-description">职位描述</Label>
        <Textarea id="resume-job-description" value={job} onChange={event => { setJob(event.target.value); setMatchError('') }}
          placeholder="粘贴岗位职责和任职要求" className="min-h-28" />
        <div className="flex flex-wrap items-center gap-3">
          <Button type="button" disabled={matching || !job.trim() || !profile.trim() || job.length > 20000 || profile.length > 50000} onClick={compare}>{matching ? '正在匹配…' : '分析岗位匹配'}</Button>
          <span className="text-sm text-muted-foreground">{!profile.trim() ? '先填写技能介绍，或从简历加入技能。' : profile.length > 50000 ? '匹配用技能介绍最多 50000 字。' :
            job.length > 20000 ? '职位描述超过 20000 字，请保留岗位职责和任职要求。' : !job.trim() ? '粘贴职位描述后即可分析。' : ''}</span>
        </div>
        {matchError && <p role="alert" className="text-sm text-destructive">{matchError}</p>}
        {comparison && <div aria-live="polite" className="grid gap-5 border-t pt-4 md:grid-cols-2">
          <section><h3 className="font-semibold">有依据的匹配项（{comparison.matches.length}）</h3>
            {comparison.matches.length ? <ul className="mt-2 space-y-3">{comparison.matches.map((match, i) =>
              <li key={i} className="break-words text-sm"><strong>{match.requirement}</strong><p className="mt-1 text-muted-foreground">{match.evidence}</p></li>)}</ul> : <p className="mt-2 text-sm">暂无有原文支持的匹配项。</p>}
          </section>
          <section><h3 className="font-semibold">暂缺依据的要求（{comparison.gaps.length}）</h3>
            {comparison.gaps.length ? <ul className="mt-2 list-inside list-disc space-y-2 break-words text-sm">{comparison.gaps.map(gap => <li key={gap}>{gap}</li>)}</ul> :
              <p className="mt-2 text-sm">{comparison.matches.length ? '本次分析未发现需要补充依据的要求。' : '未识别出明确要求，可粘贴更完整的职位描述。'}</p>}
          </section>
        </div>}
      </CardContent>
    </Card>
  </>
}
