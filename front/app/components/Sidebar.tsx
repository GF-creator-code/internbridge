'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useEffect, useRef, useState } from 'react'
import { BiBarChart, BiBriefcase, BiCog, BiMenu, BiMoon, BiSearch, BiSun, BiTask, BiUserCircle, BiX } from 'react-icons/bi'
import { useTheme } from 'next-themes'
import { API_BASE } from '@/lib/api'

const groups = [
  { title: '工作台', links: [
    { href: '/dashboard', label: '投递数据看板', icon: BiBarChart },
    { href: '/settings', label: '设置中心', icon: BiCog },
  ] },
  { title: '内地', links: [
    { href: '/boss', label: 'Boss直聘', icon: BiBriefcase },
    { href: '/liepin', label: '猎聘', icon: BiSearch },
    { href: '/51job', label: '51job', icon: BiTask },
    { href: '/zhilian', label: '智联招聘', icon: BiUserCircle },
    { href: '/shixiseng', label: '实习僧', icon: BiBriefcase },
  ] },
  { title: '香港', links: [
    { href: '/offertoday', label: 'OfferToday', icon: BiBriefcase },
    { href: '/jobsdb', label: 'JobsDB', icon: BiBriefcase },
  ] },
]

export default function Sidebar() {
  const pathname = usePathname()
  const dialog = useRef<HTMLDialogElement>(null)
  const [open, setOpen] = useState(false)
  const [mounted, setMounted] = useState(false)
  const [health, setHealth] = useState('正在连接本地服务')
  const { theme, setTheme } = useTheme()

  useEffect(() => {
    setMounted(true)
    const media = window.matchMedia('(min-width: 1024px)')
    const closeOnDesktop = () => { if (media.matches) dialog.current?.close() }
    media.addEventListener('change', closeOnDesktop)
    return () => media.removeEventListener('change', closeOnDesktop)
  }, [])

  useEffect(() => { dialog.current?.close() }, [pathname])
  useEffect(() => {
    if (!open) return
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = previous }
  }, [open])

  useEffect(() => {
    let active = true
    let controller: AbortController
    async function check() {
      controller = new AbortController()
      const timeout = setTimeout(() => controller.abort(), 3000)
      try {
        let res = await fetch(API_BASE + '/api/health', { signal: controller.signal })
        if (res.status === 404) res = await fetch(API_BASE + '/actuator/health', { signal: controller.signal })
        if (!res.ok) throw new Error()
        const data = await res.json()
        const status = String(data.status || data.state || '').toUpperCase()
        if (active) setHealth(['UP', 'HEALTHY'].includes(status) ? '本地服务已连接' : '请检查本地服务')
      } catch {
        if (active) setHealth('未连接到本地服务')
      } finally { clearTimeout(timeout) }
    }
    void check()
    const timer = setInterval(check, 30000)
    return () => { active = false; clearInterval(timer); controller?.abort() }
  }, [])

  function navigation() {
    return (
      <nav aria-label="主导航" className="min-h-0 flex-1 overflow-y-auto px-3 pb-3">
        {groups.map(group => <div key={group.title} className="mt-6 first:mt-2">
          <div className="mb-2 flex items-center justify-between px-3">
            <p className="text-xs font-semibold tracking-[0.16em] text-indigo-200/90">{group.title}</p>
            <span className="text-xs tabular-nums text-indigo-200/60">{group.links.length}</span>
          </div>
          {group.links.map(({ href, label, icon: Icon }) => {
            const current = pathname === href || pathname.startsWith(href + '/')
            return <Link key={href} href={href} aria-current={current ? 'page' : undefined}
              onClick={() => dialog.current?.close()}
              className={'flex min-h-11 items-center gap-3 rounded-lg px-3 text-sm text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-white ' + (current ? 'bg-white/20 font-semibold' : 'hover:bg-white/10')}>
              <Icon aria-hidden="true" className="h-5 w-5" />{label}
            </Link>
          })}
        </div>)}
      </nav>
    )
  }

  function settings() {
    return <div className="flex shrink-0 items-center justify-between gap-2 border-t border-white/20 px-4 py-2 text-sm">
      <span>{health}</span>
      {mounted && <button type="button" aria-label={theme === 'dark' ? '切换到浅色' : '切换到深色'}
        onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
        className="grid h-11 w-11 place-items-center rounded-lg hover:bg-white/10">
        {theme === 'dark' ? <BiSun size={20} /> : <BiMoon size={20} />}
      </button>}
    </div>
  }

  const surface = 'bg-gradient-to-b from-blue-700 via-indigo-700 to-purple-700 text-white dark:from-blacksection dark:via-blackho dark:to-black'
  return <>
    <aside className={'fixed inset-y-0 left-0 z-40 hidden w-64 flex-col lg:flex ' + surface}>
      <div className="border-b border-white/20 px-6 py-5">
        <p className="text-xl font-bold">🍀 InternBridge</p><p className="mt-1 text-sm text-indigo-100/80">本地投递工作台</p>
      </div>
      {navigation()}{settings()}
    </aside>
    <header className="fixed inset-x-0 top-0 z-40 flex h-14 items-center justify-between border-b bg-background px-4 lg:hidden">
      <span className="font-semibold">🍀 InternBridge</span>
      <button type="button" aria-expanded={open} aria-haspopup="dialog" aria-controls="mobile-navigation"
        className="flex min-h-11 items-center gap-2 rounded-md px-2"
        onClick={() => { dialog.current?.showModal(); setOpen(true) }}>
        <BiMenu aria-hidden="true" size={22} />导航
      </button>
    </header>
    <dialog ref={dialog} id="mobile-navigation" aria-label="导航菜单" onClose={() => setOpen(false)}
      onClick={event => { if (event.target === event.currentTarget) dialog.current?.close() }}
      className="fixed inset-y-0 left-0 right-auto m-0 h-dvh max-h-full w-72 max-w-[90vw] border-0 bg-transparent p-0 backdrop:bg-black/45">
      <div className={'flex h-full flex-col ' + surface}>
        <div className="flex items-center justify-between border-b border-white/20 px-4 py-2">
          <span className="font-semibold">🍀 InternBridge</span>
          <button type="button" aria-label="关闭导航" onClick={() => dialog.current?.close()} className="grid h-11 w-11 place-items-center rounded-md"><BiX size={24} /></button>
        </div>
        {navigation()}{settings()}
      </div>
    </dialog>
  </>
}
