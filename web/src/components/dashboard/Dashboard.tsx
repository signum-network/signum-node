import { useEffect, useRef, useState } from 'react'
import {
  useBlockchainStatus,
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

const MAX_SPARKLINE_POINTS = 15

export function Dashboard() {
  const { data: status, isLoading: statusLoading } = useBlockchainStatus()
  const { data: mining, isLoading: miningLoading } = useMiningInfo()
  const { data: peers } = usePeers()
  const txCount = useUnconfirmedTxCount()

  const peerAddresses = peers?.peers ?? []
  const peerDetails = usePeerDetails(peerAddresses)
  const resolvedPeers = peerDetails.filter((q) => q.data).map((q) => q.data!)

  const [diffHistory, setDiffHistory] = useState<number[]>([])
  useEffect(() => {
    if (!status?.cumulativeDifficulty) return
    const val = Number(BigInt(status.cumulativeDifficulty) / BigInt(1e12))
    setDiffHistory((prev) => [...prev.slice(-(MAX_SPARKLINE_POINTS - 1)), val])
  }, [status?.cumulativeDifficulty])

  const { play } = useAudio()
  const lastBlockRef = useRef<number | null>(null)
  useEffect(() => {
    if (!status) return
    const current = status.numberOfBlocks - 1
    const prev = lastBlockRef.current
    lastBlockRef.current = current
    // Skip the first tick (initial mount), only chime on subsequent increases.
    if (prev != null && current > prev) play(sfx.chime)
  }, [status, play])

  const nodeVersion = status?.version ?? 'v0.0.0'
  const outdatedCount = resolvedPeers.filter(
    (p) => categorizeVersion(p.version ?? '', nodeVersion) !== 'current',
  ).length

  const isLoading = statusLoading || miningLoading

  return (
    <PageWrapper>
      <div className="page-layout">
        <Banner status={status} mining={mining} isLoading={isLoading} />

        <div className="grid grid-cols-2 gap-3 md:gap-4 xl:grid-cols-4">
          <MetricGrid
            peerCount={peerAddresses.length}
            pendingTxCount={txCount.data ?? 0}
            mining={mining}
            isLoading={isLoading}
          />
          <CumulativeDifficultyCard
            current={status?.cumulativeDifficulty ?? '0'}
            history={diffHistory}
            isLoading={statusLoading}
          />
          <PeerVersionCard
            versions={resolvedPeers.map((p) => ({ version: p.version }))}
            nodeVersion={nodeVersion}
            outdatedCount={outdatedCount}
          />
        </div>
      </div>
    </PageWrapper>
  )
}
