'use client'

interface ToggleSwitchProps {
  id: string
  label: string
  disabled?: boolean
  checked: boolean
  onToggle: () => void
}

/** 通用开关：button[role=switch] + aria-checked，无外部依赖。 */
export default function ToggleSwitch({ id, label, checked, onToggle, disabled }: ToggleSwitchProps) {
  return (
    <button
      id={id}
      type="button"
      role="switch"
      disabled={disabled}
      aria-checked={checked}
      aria-label={label}
      onClick={onToggle}
      className={`relative inline-flex h-7 w-12 shrink-0 cursor-pointer disabled:cursor-default disabled:opacity-50 items-center rounded-full border-2 border-transparent transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 ${checked ? 'bg-primary' : 'bg-muted-foreground/40'}`}
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
