'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'

/**
 * 旧设置页路由，保留给书签和外部链接。
 * 四个设置页已合并到统一「设置中心」，这里直接跳到对应分区。
 */
export default function LegacySettingsRedirect() {
  const router = useRouter()
  useEffect(() => {
    router.replace('/settings?tab=rules')
  }, [router])

  return (
    <div className="space-y-3">
      <p className="text-sm">设置入口已合并到「设置中心」的 <strong>全局规则</strong> 分区。</p>
      <Link href="/settings?tab=rules" className="text-sm font-medium text-primary underline underline-offset-4">
        打开设置中心 →
      </Link>
    </div>
  )
}
