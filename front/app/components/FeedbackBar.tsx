'use client'

/**
 * 统一的操作反馈条。
 *
 * 背景：本项目多个平台的 start/stop 端点都会用中文说明失败原因
 * （例如「请先登录实习僧」「实习僧任务已在运行中」）。前端若只 console.warn，
 * 新手看到的就是「按钮闪一下又变回去」，完全不知道发生了什么。
 * 这个组件让所有平台用同一套反馈方式。
 */
export type FeedbackKind = 'error' | 'success' | 'info'

export type Feedback = {
  kind: FeedbackKind
  message: string
  title?: string
}

const KIND_CLASS: Record<FeedbackKind, string> = {
  error: 'border-destructive/40 bg-destructive/10 text-destructive',
  success: 'border-emerald-500/40 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400',
  info: 'border-border bg-muted/40 text-foreground',
}

const TITLE: Record<FeedbackKind, string> = {
  error: '操作未完成',
  success: '操作成功',
  info: '提示',
}

export default function FeedbackBar({
  feedback,
  onDismiss,
}: {
  feedback: Feedback | null
  onDismiss?: () => void
}) {
  if (!feedback || !feedback.message) return null
  return (
    <div
      role="alert"
      className={`flex items-start justify-between gap-3 rounded-lg border px-3 py-2 text-sm ${KIND_CLASS[feedback.kind]}`}
    >
      <span className="min-w-0 break-words">
        <span className="font-medium">{feedback.title || TITLE[feedback.kind]}：</span>
        {feedback.message}
      </span>
      {onDismiss && (
        <button
          type="button"
          onClick={onDismiss}
          className="shrink-0 opacity-70 transition-opacity hover:opacity-100"
          aria-label="关闭提示"
        >
          ×
        </button>
      )}
    </div>
  )
}
