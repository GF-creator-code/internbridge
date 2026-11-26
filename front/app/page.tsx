'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { BiCog, BiPlay, BiBarChart, BiCheckCircle } from 'react-icons/bi'
import PageHeader from '@/app/components/PageHeader'

/**
 * 首页 = 新手引导页。
 *
 * 原先根路径直接跳到 Boss 配置页 —— 新手一进来就是一张密密麻麻的配置表单，
 * 完全不知道该先做什么。这里改成与设置中心一致的四步引导，每一步都直接链到对应页面。
 *
 * 注：项目是纯静态导出（output: 'export'），不能用服务端 redirect。
 */
const STEPS = [
  {
    icon: BiCog,
    color: 'text-indigo-500 bg-indigo-500/10',
    no: '01',
    title: '在设置中心完成配置',
    desc: '连接 AI 模型、选工作形式与全局城市、维护黑名单、写好技能介绍，四个分区一次填完。',
    href: '/settings',
    label: '打开设置中心',
  },
  {
    icon: BiCheckCircle,
    color: 'text-purple-500 bg-purple-500/10',
    no: '02',
    title: '选平台并登录',
    desc: '从侧边栏选一个招聘平台，点「登录」调出浏览器窗口扫码。登录态保存在本机，后续任务自动复用。',
    href: '/boss',
    label: '选一个平台',
  },
  {
    icon: BiPlay,
    color: 'text-teal-500 bg-teal-500/10',
    no: '03',
    title: '保存配置，开始投递',
    desc: '先点「保存配置」，再点「开始投递」。未保存时会拦截任务，进度在页面上实时显示，多个平台可同时跑。',
    href: '/boss',
    label: '开始投递',
  },
  {
    icon: BiBarChart,
    color: 'text-amber-500 bg-amber-500/10',
    no: '04',
    title: '到投递数据看板复盘',
    desc: '汇总采集、投递、打招呼与回复。每天 21:00 自动生成日报并推送，也可随时手动生成。',
    href: '/dashboard',
    label: '打开投递数据看板',
  },
]

export default function Home() {
  const [showGuide, setShowGuide] = useState(true)

  useEffect(() => {
    // 看过引导后记住选择，不再每次弹出来
    try {
      setShowGuide(localStorage.getItem('internbridge.guideSeen') !== '1')
    } catch {
      // 隐私模式下 localStorage 不可用，保持显示引导
    }
  }, [])

  function dismiss() {
    setShowGuide(false)
    try {
      localStorage.setItem('internbridge.guideSeen', '1')
    } catch {
      // 忽略存储失败
    }
  }

  return (
    <div className="space-y-5">
      <PageHeader
        icon={<BiBarChart className="text-2xl" />}
        title="欢迎使用 InternBridge"
        subtitle="香港和内地的职位，不用在七个平台之间来回切。"
        iconClass="text-white"
        accentBgClass="bg-gradient-to-r from-indigo-500 to-purple-600"
      />

      <div className="flex flex-wrap items-center gap-3">
        <Link href="/settings" className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground">
          打开设置中心
        </Link>
        <Link href="/dashboard" className="rounded-lg border border-border px-4 py-2 text-sm">
          打开投递数据看板
        </Link>
      </div>

      {showGuide && (
        <section className="space-y-3" aria-label="新手引导">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h2 className="text-base font-medium">四步上手</h2>
              <p className="mt-0.5 text-sm text-muted-foreground">按顺序做完这四步，就可以开始自动投递。</p>
            </div>
            <button type="button" onClick={dismiss} className="text-sm text-muted-foreground underline underline-offset-4">
              不再显示
            </button>
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            {STEPS.map(step => (
              <article key={step.no} className="rounded-xl border border-border bg-card p-5">
                <div className="mb-3 flex items-center justify-between">
                  <div className={`inline-flex rounded-lg p-2 ${step.color}`}>
                    <step.icon className="text-xl" />
                  </div>
                  <span className="text-xs font-semibold tabular-nums text-muted-foreground">{step.no} / 04</span>
                </div>
                <h3 className="mb-2 text-base font-medium">{step.title}</h3>
                <p className="mb-4 text-sm leading-relaxed text-muted-foreground">{step.desc}</p>
                <Link href={step.href} className="text-sm font-medium text-primary underline underline-offset-4">
                  {step.label} →
                </Link>
              </article>
            ))}
          </div>
        </section>
      )}

      <section aria-label="关于数据" className="rounded-xl border border-border bg-muted/30 px-4 py-3">
        <p className="mb-1 text-sm font-medium">关于你的数据</p>
        <p className="text-sm leading-relaxed text-muted-foreground">
          所有配置和登录态都保存在本机数据库。使用 AI 功能时，简历文字和职位描述会发送给你配置的服务商。
        </p>
      </section>
    </div>
  )
}
