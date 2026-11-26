'use client'

import { useEffect, useRef, useState } from 'react'
import { createSSEWithBackoff } from '@/lib/sse'
import { API_BASE } from '@/lib/api'

export type TaskProgress = {
  platform?: string
  type?: string
  message?: string
  current?: number | null
  total?: number | null
  timestamp?: number
}

export type ProgressFeed = {
  /** 最近一条进度 */
  latest: TaskProgress | null
  /** 最近若干条进度，用于展示流水 */
  history: TaskProgress[]
  /** SSE 是否已连接 */
  connected: boolean
  /** 清空历史 */
  clear: () => void
}

const MAX_HISTORY = 200

/**
 * 订阅投递进度。
 *
 * 原先七个平台页面谁都没订阅进度流：后端 /api/boss/stream 明明已经实现并推送，
 * 前端却一个页面都没接。结果是新手点下「开始投递」之后界面完全静止，
 * 不知道跑了几个、成功几个，只能去翻后端控制台。
 *
 * 这里订阅统一的 /api/jobs/progress/stream，按平台过滤，
 * 断线由 sse.ts 的指数退避自动重连。
 */
export function useTaskProgress(platform?: string): ProgressFeed {
  const [latest, setLatest] = useState<TaskProgress | null>(null)
  const [history, setHistory] = useState<TaskProgress[]>([])
  const [connected, setConnected] = useState(false)
  const platformRef = useRef(platform)
  platformRef.current = platform

  useEffect(() => {
    const url = `${API_BASE}/api/jobs/progress/stream${platform ? `?platform=${encodeURIComponent(platform)}` : ''}`

    const client = createSSEWithBackoff(url, {
      listeners: [
        {
          name: 'progress',
          handler: event => {
            try {
              const data = JSON.parse(event.data) as TaskProgress
              setLatest(data)
              setHistory(prev => {
                const next = [...prev, data]
                return next.length > MAX_HISTORY ? next.slice(next.length - MAX_HISTORY) : next
              })
            } catch {
              // 单条消息解析失败不影响后续推送
            }
          },
        },
      ],
      onOpen: () => setConnected(true),
      onError: () => setConnected(false),
    })

    return () => {
      client.close()
      setConnected(false)
    }
  }, [platform])

  return {
    latest,
    history,
    connected,
    clear: () => {
      setLatest(null)
      setHistory([])
    },
  }
}
