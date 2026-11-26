'use client'

import { useEffect, useState } from 'react'
import { API_BASE } from '@/lib/api'

export type ProfileOptions = {
  city: string[]
  degree: string[]
  availableFrom: string[]
}

const EMPTY: ProfileOptions = { city: [], degree: [], availableFrom: [] }

/**
 * 读取后端提供的选项字典（城市 / 学历 / 到岗时间）。
 *
 * 各平台页面原先让用户在 Input 里手打城市名，但手打的名称和平台选项表对不上，
 * 保存了也可能用不了。这里统一从 /api/profile/options 取，所有页面共用同一份字典。
 */
export function useProfileOptions(): ProfileOptions {
  const [options, setOptions] = useState<ProfileOptions>(EMPTY)

  useEffect(() => {
    let active = true
    void (async () => {
      try {
        const res = await fetch(API_BASE + '/api/profile/options')
        if (!res.ok) return
        const body = await res.json()
        if (active && body.success && body.data) {
          setOptions(body.data)
        }
      } catch {
        // 选项加载失败时保持空列表，页面仍可操作
      }
    })()
    return () => { active = false }
  }, [])

  return options
}
