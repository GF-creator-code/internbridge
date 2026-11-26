import type { ReactNode } from 'react'
import { BiInfoCircle } from 'react-icons/bi'

/**
 * 平台页顶部的统一操作提示。
 *
 * 原先 7 个平台页各写一份三段式「XX 平台说明」卡片，内容多为重复的操作套话，
 * 引号（「」/ “”）与中英文空格还各不相同，既占版面又和页头、Tab 重复表达
 * 「这是一个配置页」。这里收成一条紧凑提示：只保留该平台真正需要知道的差异。
 */
export default function PlatformTip({
  title = '操作顺序',
  children,
}: {
  title?: string
  children: ReactNode
}) {
  return (
    <section
      aria-label={title}
      className="rounded-xl border border-border bg-muted/30 px-4 py-3"
    >
      <p className="mb-1.5 flex items-center gap-2 text-sm font-medium">
        <BiInfoCircle aria-hidden="true" className="text-primary" />
        {title}
      </p>
      <div className="space-y-1 text-sm leading-relaxed text-muted-foreground">
        {children}
      </div>
    </section>
  )
}
