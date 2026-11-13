'use client'

import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/** 后端存储值（JSON 数组字符串 / 逗号分隔 / 数组）→ 输入框展示文本 */
export function parseKeywordList(raw?: string | string[] | null): string {
  if (!raw) return ''
  if (Array.isArray(raw)) return raw.filter(Boolean).join(', ')
  const t = raw.trim()
  if (t.startsWith('[') && t.endsWith(']')) {
    try {
      const arr = JSON.parse(t)
      if (Array.isArray(arr)) return arr.filter(Boolean).join(', ')
    } catch {
      // 非 JSON 时按逗号文本处理
    }
  }
  return t.replace(/，/g, ',')
}

/** 输入框展示文本 → JSON 数组字符串存库 */
export function serializeKeywordList(display?: string): string {
  const tokens = (display || '')
    .replace(/，/g, ',')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
  return JSON.stringify(tokens)
}

interface KeywordListsFieldProps {
  includeValue: string
  excludeValue: string
  onIncludeChange: (v: string) => void
  onExcludeChange: (v: string) => void
  includePlaceholder?: string
  excludePlaceholder?: string
}

/**
 * 通用过滤关键词编辑区：标题必含 + 排除关键词（所有平台配置页共用）。
 * include 为空时不限制岗位标题；exclude 命中标题或公司名即跳过。
 */
export default function KeywordListsField({
  includeValue,
  excludeValue,
  onIncludeChange,
  onExcludeChange,
  includePlaceholder,
  excludePlaceholder,
}: KeywordListsFieldProps) {
  return (
    <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
      <div className="space-y-2">
        <Label>标题必含关键词（逗号分隔，留空不限制）</Label>
        <Input
          placeholder={includePlaceholder ?? '如：实习, 實習, internship'}
          value={includeValue}
          onChange={(e) => onIncludeChange(e.target.value)}
        />
        <p className="text-xs text-muted-foreground">岗位标题必须包含其中任意一个词才会投递。</p>
      </div>
      <div className="space-y-2">
        <Label>排除关键词（逗号分隔）</Label>
        <Input
          placeholder={excludePlaceholder ?? '如：法务, 律师, 腾讯, 字节'}
          value={excludeValue}
          onChange={(e) => onExcludeChange(e.target.value)}
        />
        <p className="text-xs text-muted-foreground">岗位标题或公司名命中其中任意一个词即跳过。</p>
      </div>
    </div>
  )
}
