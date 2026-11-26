'use client'

import { Button } from '@/components/ui/button'

/**
 * 配置加载失败提示。
 *
 * 原先六个平台的 fetchAllData 在 catch 里只写 console.error，
 * 结果是后端没起来时，页面照样渲染一张「正常」的空配置表单 ——
 * 新手会以为自己的配置丢了，或者在这个空表单上填了字覆盖掉真实配置。
 * 这个组件让「加载失败」和「加载成功但确实是空配置」在界面上区分开。
 */
export default function ConfigLoadError({
  message = '无法读取已保存的配置。请确认本地服务已启动，然后重试。',
  onRetry,
}: {
  message?: string
  onRetry?: () => void
}) {
  return (
    <div role="alert" className="rounded-xl border border-destructive/40 bg-destructive/10 p-4">
      <p className="text-sm text-destructive">{message}</p>
      <p className="mt-1 text-xs text-muted-foreground">
        在此页面上填写并保存可能会覆盖已经存在的配置，建议先确认服务状态。
      </p>
      {onRetry && (
        <Button variant="outline" size="sm" className="mt-3" onClick={onRetry}>
          重新读取
        </Button>
      )}
    </div>
  )
}
