import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import {
  useFullBlockchainStatus,
  useMiningInfo,
  usePeers,
  usePeerDetails,
  useUnconfirmedTxCount,
} from '@/hooks/useNodeQuery'
import { categorizeVersion } from '@/lib/utils'
import { sfx, useAudio } from '@/audio'
import { PageWrapper } from '@/components/layout/PageWrapper'
import { Banner } from './components/Banner'
import { MetricGrid } from './components/MetricGrid'
import { CumulativeDifficultyCard } from './components/CumulativeDifficultyCard'
import { PeerVersionCard } from './components/PeerVersionCard'
import { ChainActivityRow } from './components/ChainActivityRow'
import { NodeHealthStrip } from './components/NodeHealthStrip'


function SectionHeading({ label }: { label: string }) {
  return (
    <div className="flex items-center gap-3 px-0.5">
      <span
        className="flex-shrink-0 text-[9px] font-semibold uppercase tracking-[3px]"
        style={{ color: 'var(--muted)' }}
      >
        {label}
      </span>
      <div className="flex-1" style={{ borderTop: '1px solid var(--border)' }} />
    </div>
  )
}

export function Dashboard() {
  const { data: fullStatus, isLoading: fullStatusLoading } = useFullBlockchainStatus()
  const { data: mining, isLoading: miningLoading } = useMiningInfo()
  const { data: peers } = usePeers()
  const txCount = useUnconfirmedTxCount()

  const peerAddresses = peers?.peers ?? []
  const peerDetails = usePeerDetails(peerAddresses)
  const resolvedPeers = peerDetails.filter((q) => q.data).map((q) => q.data!)

  const { play } = useAudio()
  const { t } = useTranslation()
  const lastBlockRef = useRef<number | null>(null)
  useEffect(() => {
    if (!fullStatus) return
    const current = fullStatus.numberOfBlocks - 1
    const prev = lastBlockRef.current
    lastBlockRef.current = current
    if (prev != null && current > prev) play(sfx.chime)
  }, [fullStatus, play])

  const nodeVersion = fullStatus?.version ?? 'v0.0.0'
  const outdatedCount = resolvedPeers.filter(
    (p) => categorizeVersion(p.version ?? '', nodeVersion) !== 'current',
  ).length

  const isLoading = fullStatusLoading || miningLoading

  return (
    <PageWrapper>
      <div className="page-layout">

        <Banner status={fullStatus} mining={mining} isLoading={isLoading} />

        <SectionHeading label={t('dashboard.chainActivity')} />
        <div className="grid grid-cols-2 gap-3 md:gap-4 xl:grid-cols-4">
          <ChainActivityRow fullStatus={fullStatus} isLoading={fullStatusLoading} />
        </div>

        <SectionHeading label={t('dashboard.network')} />
        <div className="grid grid-cols-2 gap-3 md:gap-4 xl:grid-cols-4">
          <MetricGrid
            peerCount={peerAddresses.length}
            pendingTxCount={txCount.data ?? 0}
            mining={mining}
            isLoading={isLoading}
          />
          <CumulativeDifficultyCard
            current={fullStatus?.cumulativeDifficulty ?? '0'}
            isLoading={fullStatusLoading}
          />
          <PeerVersionCard
            versions={resolvedPeers.map((p) => ({ version: p.version }))}
            nodeVersion={nodeVersion}
            outdatedCount={outdatedCount}
          />
        </div>

        <SectionHeading label={t('dashboard.nodeHealth')} />
        <NodeHealthStrip fullStatus={fullStatus} isLoading={fullStatusLoading} />

      </div>
    </PageWrapper>
  )
}
