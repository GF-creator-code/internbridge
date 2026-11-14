'use client'

import { useEffect, useRef, useState } from 'react'
import { useTheme } from 'next-themes'
import { Chart, ArcElement, BarController, BarElement, CategoryScale, DoughnutController, LinearScale, Legend, Tooltip } from 'chart.js'

Chart.register(ArcElement, BarController, BarElement, CategoryScale, DoughnutController, LinearScale, Legend, Tooltip)

export type ChartValue = { name: string; value: number }

export default function JobChart({ title, values, donut = false }: { title: string; values: ChartValue[]; donut?: boolean }) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const { resolvedTheme } = useTheme()
  const [failed, setFailed] = useState(false)
  useEffect(() => {
    if (!canvas.current || !values.length) return
    const ink = resolvedTheme === 'dark' ? '#e2e8f0' : '#334155'
    let chart: Chart<'bar' | 'doughnut'> | undefined
    try {
      chart = new Chart(canvas.current, {
        type: donut ? 'doughnut' : 'bar',
        data: { labels: values.map(v => v.name), datasets: [{ label: '岗位数', data: values.map(v => v.value),
          backgroundColor: donut ? ['#2563eb', '#047857', '#935c16', '#ab3352', '#7155ae'] : '#4876bb' }] },
        options: { responsive: true, maintainAspectRatio: false, animation: false, color: ink,
          plugins: { legend: { display: donut, position: 'bottom' } },
          scales: donut ? undefined : { x: { ticks: { color: ink } }, y: { beginAtZero: true, ticks: { precision: 0, color: ink } } } },
      })
      setFailed(false)
    } catch { setFailed(true) }
    return () => chart?.destroy()
  }, [donut, resolvedTheme, values])
  if (!values.length) return <p className="text-sm text-muted-foreground">没有可统计的数据。</p>
  return <>
    <div className={failed ? 'hidden' : 'relative h-52 min-w-0'}><canvas ref={canvas} role="img" aria-label={title} aria-description={values.map(v => v.name + ' ' + v.value).join('，')} /></div>
    <details className="mt-2 text-sm" open={failed || undefined}><summary className="cursor-pointer">查看图表数据</summary>
      <ul className="mt-2 space-y-1">{values.map(v => <li key={v.name}>{v.name} · {v.value}</li>)}</ul></details>
  </>
}
