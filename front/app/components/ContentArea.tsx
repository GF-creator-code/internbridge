"use client"
import { usePathname } from 'next/navigation'
import { ReactNode, useMemo } from 'react'
import { motion } from 'framer-motion'

export default function ContentArea({ children }: { children: ReactNode }) {
  const pathname = usePathname()

  const accentClass = useMemo(() => {
    switch (pathname) {
      case '/boss':
        return 'accent-teal'
      case '/liepin':
        return 'accent-orange'
      case '/51job':
        return 'accent-amber'
      case '/shixiseng':
        return 'accent-emerald'
      case '/offertoday':
        return 'accent-indigo'
      case '/jobsdb':
        return 'accent-violet'
      case '/zhilian':
        return 'accent-sky'
      case '/global-config':
        return 'accent-rose'
      default:
        return ''
    }
  }, [pathname])

  return (
    <main id="main-content" tabIndex={-1} className={`min-w-0 flex-1 pt-14 lg:ml-64 lg:pt-0 bg-background dark:bg-blacksection content-bg ${accentClass} min-h-screen`}>
      <motion.div
        key={pathname}
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -20 }}
        transition={{ duration: 0.4, ease: "easeInOut" }}
        className="mx-auto w-full max-w-[1400px] px-3 py-4 sm:px-6 lg:px-8 lg:py-8"
      >
        {children}
      </motion.div>
    </main>
  )
}