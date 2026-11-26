'use client'

import type { TaskProgress } from '@/lib/useTaskProgress'

/**
 * 任务进度面板（采集 / 投递共用）。
 *
 * 展示任务当前状态、最近一条进度消息，以及一个可滚动的进度流水。
 * 七个平台共用，靠 feed 传入数据。
 */
const TYPE_STYLE: Record<string, string> = {
  info: 'text-muted-foreground',
  progress: 'text-foreground',
  success: 'text-emerald-600 dark:text-emerald-400',
  error: 'text-destructive',
  warning: 'text-amber-600 dark:text-amber-400',
}

export default function TaskProgressPanel({
  latest,
  history,
  connected,
  isRunning,
  onClear,
}: {
  latest: TaskProgress | null
  history: TaskProgress[]
  connected: boolean
  isRunning: boolean
  onClear?: () => void
}) {
  const hasContent = latest !== null || history.length > 0

  return (
    <section className="rounded-xl border border-border bg-card p-4" aria-label="任务进度">
      <header className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-medium">任务进度</h2>
        <div className="flex items-center gap-3 text-xs text-muted-foreground">
          <span className={connected ? 'text-emerald-600 dark:text-emerald-400' : 'text-amber-600 dark:text-amber-400'}>
            {connected ? '进度已连接' : '进度未连接'}
          </span>
          {isRunning && <span>任务运行中…</span>}
          {onClear && hasContent && (
            <button type="button" onClick={onClear} className="underline underline-offset-4 hover:opacity-80">
              清空
            </button>
          )}
        </div>
      </header>

      {!hasContent ? (
        <p className="text-sm text-muted-foreground">
          启动采集或投递任务后，这里会实时显示进度；任务日志也会同时写入后端控制台。
        </p>
      ) : (
        <>
          {latest && (
            <p
              role="status"
              className={`mb-3 break-words text-sm ${TYPE_STYLE[latest.type || 'info'] || 'text-foreground'}`}
            >
              {latest.message}
              {typeof latest.current === 'number' && typeof latest.total === 'number' && latest.total > 0 && (
                <span className="ml-2 text-muted-foreground">
                  {latest.current}/{latest.total}
                </span>
              )}
            </p>
          )}
          {history.length > 0 && (
            <ol className="max-h-48 space-y-1 overflow-y-auto rounded-lg bg-muted/40 p-3 text-xs">
              {history.slice(-60).reverse().map((item, index) => (
                <li key={`${item.timestamp ?? index}-${index}`} className="flex gap-2">
                  <span className="shrink-0 text-muted-foreground">
                    {item.timestamp ? new Date(item.timestamp).toLocaleTimeString() : ''}
                  </span>
                  <span className={TYPE_STYLE[item.type || 'info'] || 'text-foreground'}>{item.message}</span>
                </li>
              ))}
            </ol>
          )}
        </>
      )}
    </section>
  )
}
