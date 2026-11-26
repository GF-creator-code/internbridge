'use client'

import { useCallback, useState } from 'react'
import type { Feedback } from '@/app/components/FeedbackBar'

/**
 * 统一「启动投递 / 停止投递」的调用与失败反馈。
 *
 * 七个平台各自的 start / stop 端点结构是一致的：
 *   - 成功：{ success: true, message: "..." }
 *   - 失败：{ success: false, message: "请先登录xxx" }，HTTP 400
 *
 * 原先每个页面各写一份 handleStartDelivery，只有 Boss 那份会把失败原因显示出来，
 * 其余六个都是 `if (!data.success) setIsDelivering(false)` —— 后端明明返回了
 * 「请先登录实习僧」这样的中文原因，界面上却一个字都不显示。
 * 这个 hook 把反馈逻辑收敛到一处，以后加平台直接复用。
 */
export type TaskControl = {
  isRunning: boolean
  feedback: Feedback | null
  start: (platform: string) => Promise<void>
  stop: (platform: string) => Promise<void>
  /** 主动上报一条提示（例如「有未保存的修改」） */
  report: (feedback: Feedback) => void
  clearFeedback: () => void
}

export function useTaskControl(apiBase: string): TaskControl {
  const [isRunning, setIsRunning] = useState(false)
  const [feedback, setFeedback] = useState<Feedback | null>(null)

  const clearFeedback = useCallback(() => setFeedback(null), [])
  const report = useCallback((next: Feedback) => setFeedback(next), [])

  const call = useCallback(
    async (platform: string, action: 'start' | 'stop'): Promise<boolean> => {
      try {
        const response = await fetch(`${apiBase}/api/${platform}/${action}`, { method: 'POST' })
        const data = await response.json().catch(() => null)
        if (data && data.success === false) {
          setFeedback({
            kind: 'error',
            title: action === 'start' ? '启动失败' : '停止失败',
            message: data.message || `平台未说明原因，请查看后端日志。`,
          })
          return false
        }
        if (!response.ok) {
          setFeedback({
            kind: 'error',
            title: action === 'start' ? '启动失败' : '停止失败',
            message: `服务返回 ${response.status}，请确认本地服务仍在运行。`,
          })
          return false
        }
        return true
      } catch {
        setFeedback({
          kind: 'error',
          title: action === 'start' ? '启动失败' : '停止失败',
          message: `无法连接后端服务（${apiBase}），请确认服务已启动。`,
        })
        return false
      }
    },
    [apiBase],
  )

  const start = useCallback(
    async (platform: string) => {
      setFeedback(null)
      setIsRunning(true)
      const ok = await call(platform, 'start')
      // 失败时复位，界面才不会一直停在「停止投递」上
      if (!ok) setIsRunning(false)
      else setFeedback({ kind: 'info', title: '已开始', message: '任务已启动，进度会显示在页面上。' })
    },
    [call],
  )

  const stop = useCallback(
    async (platform: string) => {
      setFeedback(null)
      const ok = await call(platform, 'stop')
      // 无论成功失败都要复位：停止失败通常意味着任务已经结束了，
      // 界面继续显示「停止投递」会让人以为任务还在跑。
      setIsRunning(false)
      if (!ok) {
        setFeedback({
          kind: 'error',
          title: '停止失败',
          message: '未能确认停止，任务可能已经结束。请查看页面进度或后端日志。',
        })
      }
    },
    [call],
  )

  return { isRunning, feedback, start, stop, report, clearFeedback }
}
