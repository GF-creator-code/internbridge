'use client'

import { Label } from '@/components/ui/label'

interface ToggleSwitchProps {
  id: string
  label: string
  checked: boolean
  onToggle: () => void
}

/** Boss 配置页同款开关：button[role=switch] + aria-checked，无外部依赖。 */
export default function ToggleSwitch({ id, label, checked, onToggle }: ToggleSwitchProps) {
  return (
    <button
      id={id}
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={onToggle}
      className={`relative inline-flex h-7 w-12 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 ${checked ? 'bg-primary' : 'bg-muted-foreground/40'}`}
    >
      <span aria-hidden="true" className={`pointer-events-none block h-5 w-5 rounded-full bg-background shadow-lg ring-0 transition-transform ${checked ? 'translate-x-5' : 'translate-x-0.5'}`} />
    </button>
  )
}

/** 后端可能返回 1/0（SQLite INTEGER）或 true/false，统一转 boolean；null 时给默认值。 */
export function toBool(v: boolean | number | string | null | undefined, dflt = false): boolean {
  if (v === null || v === undefined) return dflt
  if (typeof v === 'number') return v === 1
  if (typeof v === 'string') return v === 'true' || v === '1'
  return !!v
}
