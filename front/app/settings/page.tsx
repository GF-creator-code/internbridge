'use client'

import { Suspense, useEffect, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import { BiCog } from 'react-icons/bi'
import PageHeader from '@/app/components/PageHeader'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import ConnectionSection from './sections/ConnectionSection'
import ProfileSection from './sections/ProfileSection'
import RulesSection from './sections/RulesSection'
import MatchSection from './sections/MatchSection'

/**
 * 统一设置中心。
 *
 * 原先「环境配置 / 全局求职档案 / 全局配置 / 简历与 AI 匹配」是四个独立页面，
 * 用户要在侧边栏来回跳，而且同一样东西能在两处编辑（城市、技能介绍、AI 开关），
 * 改完也不知道哪份生效。这里收成一个入口、四个分区：
 * 每份数据只在一个分区里编辑，需要跳到别的分区时用「切换」而不是开新页面。
 *
 * 支持 ?tab=xxx 深链，旧链接 /env-config?xxx 之类的跳转也能落到对应分区。
 */
const TABS = [
  { value: 'connection', label: '连接与通知', hint: '模型地址、密钥与企业微信机器人；简历解析和 AI 打招呼语依赖此项。' },
  { value: 'profile', label: '求职档案', hint: '工作形式、学历、到岗时间与联系方式；意向城市在「全局规则」中设置。' },
  { value: 'rules', label: '全局规则', hint: '意向城市一键同步到各平台，黑名单由所有平台共用。' },
  { value: 'match', label: '简历与匹配', hint: '技能介绍、投递提示词、AI 开关与简历解析。' },
] as const

type TabValue = typeof TABS[number]['value']

function SettingsContent() {
  const params = useSearchParams()
  const initial = params.get('tab') as TabValue | null
  const [tab, setTab] = useState<TabValue>(initial && TABS.some(t => t.value === initial) ? initial : 'connection')
  // 子分区拿到的是 string，这里收窄回合法分区值
  const switchTab = (next: string) => {
    if (TABS.some(t => t.value === next)) setTab(next as TabValue)
  }

  // 同步地址栏，方便把某个分区直接发给别人或加书签
  useEffect(() => {
    const url = new URL(window.location.href)
    url.searchParams.set('tab', tab)
    window.history.replaceState(null, '', url.toString())
  }, [tab])

  const currentIndex = TABS.findIndex(t => t.value === tab)
  const current = TABS[currentIndex] ?? TABS[0]

  return (
    <div className="space-y-5">
      <PageHeader
        icon={<BiCog className="text-2xl" />}
        title="设置中心"
        subtitle="四个分区覆盖全部全局设置，每一项只在自己的分区里编辑。"
        iconClass="text-white"
        accentBgClass="bg-gradient-to-r from-slate-600 to-slate-800"
      />

      <Tabs value={tab} onValueChange={value => setTab(value as TabValue)}>
        <TabsList className="flex h-auto flex-wrap justify-start gap-1 rounded-xl">
          {TABS.map(t => (
            <TabsTrigger key={t.value} value={t.value} className="rounded-lg px-4 py-2">
              {t.label}
            </TabsTrigger>
          ))}
        </TabsList>

        <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border border-border bg-muted/30 px-3 py-2">
          <span className="text-xs font-medium tabular-nums text-muted-foreground">
            {currentIndex + 1} / {TABS.length}
          </span>
          <span className="text-sm font-medium">{current.label}</span>
          <span className="text-sm text-muted-foreground">{current.hint}</span>
        </div>

        <TabsContent value="connection" className="mt-4">
          <ConnectionSection onSwitch={switchTab} />
        </TabsContent>
        <TabsContent value="profile" className="mt-4">
          <ProfileSection onSwitch={switchTab} />
        </TabsContent>
        <TabsContent value="rules" className="mt-4">
          <RulesSection />
        </TabsContent>
        <TabsContent value="match" className="mt-4">
          <MatchSection onSwitch={switchTab} />
        </TabsContent>
      </Tabs>
    </div>
  )
}

export default function SettingsPage() {
  return (
    <Suspense fallback={<p role="status" className="text-sm text-muted-foreground">正在加载设置中心…</p>}>
      <SettingsContent />
    </Suspense>
  )
}
