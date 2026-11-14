'use client'

import InternshipAnalysis from '@/app/components/InternshipAnalysis'

export default function AnalysisContent({ showHeader = false }: { showHeader?: boolean }) {
  return <InternshipAnalysis platform="offertoday" showHeader={showHeader} />
}
