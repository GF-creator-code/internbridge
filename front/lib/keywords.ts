/**
 * 关键词在数据库里的存储格式不止一种：JSON 数组、括号列表、逗号分隔。
 * 原先六个平台页面各自实现了一遍 parseKeywordsFromDb / serializeKeywordsForDb，
 * 现在统一到这里，避免某一页改了逻辑别的页没跟上。
 */

/** 解析展示用的关键词字符串（逗号分隔） */
export function parseKeywordsForDisplay(raw?: string): string {
  if (!raw) return ''
  const t = raw.trim()
  if (t.startsWith('[') && t.endsWith(']')) {
    try {
      const arr = JSON.parse(t)
      if (Array.isArray(arr)) return arr.filter(Boolean).join(', ')
    } catch {
      // 非严格 JSON，按普通字符串处理
    }
  }
  return t.replace(/，/g, ',')
}

/** 序列化为存库用的 JSON 数组字符串 */
export function serializeKeywords(raw?: string): string {
  const text = (raw || '').trim()
  if (!text) return '[]'
  const tokens = text.replace(/，/g, ',')
    .split(',')
    .map(s => s.trim())
    .filter(Boolean)
  return JSON.stringify(tokens)
}

/** 从关键词里取出单值（部分平台只接受一个关键词） */
export function parseSingleKeyword(raw?: string): string {
  return parseKeywordsForDisplay(raw).split(',')[0]?.trim() || ''
}

/** 从关键词里取出多值数组 */
export function parseKeywordArray(raw?: string): string[] {
  return parseKeywordsForDisplay(raw)
    .split(',')
    .map(s => s.trim())
    .filter(Boolean)
}
