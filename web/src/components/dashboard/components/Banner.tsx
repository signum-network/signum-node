import { useTranslation } from 'react-i18next'
import { CardLabel, CardSub, CardSkeleton } from '@/components/ui/Card'
import { SignaAmount } from '@/components/ui/SignaAmount'
import { Pill } from '@/components/ui/Pill'
import { ProgressBar } from '@/components/ui/ProgressBar'
import { AnimatedNumber } from '@/components/ui/AnimatedNumber'
import { fmt, fmtDuration } from '@/lib/utils'
import type { FullBlockchainStatus, MiningInfo } from '@/lib/nodeApi'
import { useNodeSocket } from '@/hooks/useNodeSocket'
import { useRecentBlocks } from '@/hooks/useNodeQuery'
import { Sparkline } from '@/components/ui/Sparkline'

interface BannerProps {
  status?: FullBlockchainStatus
  mining?: MiningInfo
  isLoading: boolean
}

function BannerLeft({ status, isLoading }: { status?: FullBlockchainStatus; isLoading: boolean }) {
  const { t } = useTranslation()
  const { latestBlock } = useNodeSocket()
  const height = latestBlock?.localHeight ?? (status ? status.numberOfBlocks - 1 : 0)
  const isSyncing = latestBlock
    ? latestBlock.progress < 1
    : status
      ? status.isScanning || height < status.lastBlockchainFeederHeight
      : false

  return (
    <div className="flex flex-col justify-center gap-3 px-7 py-7 md:px-9">
      <CardLabel className="mb-0">{t('dashboard.currentBlockHeight')}</CardLabel>
      {isLoading ? (
        <div className="h-12 w-44">
          <CardSkeleton />
        </div>
      ) : (
        <div
          className="tabular-nums text-[36px] font-bold leading-none tracking-[-1px] md:text-[44px] md:tracking-[-2px]"
          style={{ fontFamily: 'var(--font-display)', color: 'var(--blue2)', textShadow: 'var(--glow-b)' }}
        >
          <AnimatedNumber value={height} formatter={fmt} />
        </div>
      )}
      <div className="flex flex-wrap gap-2">
        <Pill variant={isSyncing ? 'amber' : 'green'} dot={isSyncing ? 'warn' : 'ok'}>
          {isSyncing ? t('dashboard.syncing') : t('dashboard.synced')}
        </Pill>
        <Pill variant="blue">{status?.network ?? 'Mainnet'}</Pill>
      </div>
    </div>
  )
}

function BannerCenter({
  status,
  mining,
  isLoading,
}: {
  status?: FullBlockchainStatus
  mining?: MiningInfo
  isLoading: boolean
}) {
  const { t } = useTranslation()
  const { latestBlock } = useNodeSocket()
  const { data: recentBlocks } = useRecentBlocks()

  const localHeight = latestBlock?.localHeight ?? (status ? status.numberOfBlocks - 1 : 0)
  const globalHeight = latestBlock?.globalHeight ?? status?.lastBlockchainFeederHeight ?? 0
  const syncPct = latestBlock
    ? latestBlock.progress * 100
    : globalHeight > 0 ? (localHeight / globalHeight) * 100 : 0
  const synced = syncPct >= 99.99

  const blocks = recentBlocks?.blocks ?? []
  const avgBlockTimeSec = blocks.length >= 2
    ? (blocks[0].timestamp - blocks[blocks.length - 1].timestamp) / (blocks.length - 1)
    : null

  const intervalSlice = blocks.slice(0, 31)
  const blockIntervals = intervalSlice.length >= 2
    ? intervalSlice.slice(0, -1).map((b, i) => b.timestamp - intervalSlice[i + 1].timestamp).reverse()
    : []

  const baseReward = mining ? Number(mining.lastBlockReward) : null
  const lastFeeSigna = blocks.length > 0 ? Number(blocks[0].totalFeeNQT) / 1e8 : 0
  const minerReward = baseReward !== null ? baseReward + lastFeeSigna : null

  return (
    <div className="flex flex-col justify-center gap-4 px-7 py-7 md:px-8">
      <div className="flex flex-col gap-1.5">
        <div className="flex items-center justify-between">
          <CardLabel className="mb-0">{t('dashboard.syncProgress')}</CardLabel>
          {!isLoading && (
            <span
              className="tabular-nums text-[11px]"
              style={{
                fontFamily: 'var(--font-display)',
                color: synced ? 'var(--green)' : 'var(--amber)',
              }}
            >
              {syncPct.toFixed(2)}%
            </span>
          )}
        </div>
        <ProgressBar value={syncPct} synced={synced} />
        <div className="flex justify-between text-[10px] tabular-nums tracking-[1px]" style={{ color: 'var(--muted)' }}>
          <span>{isLoading ? '…' : t('dashboard.localHeight', { height: fmt(localHeight) })}</span>
          <span>{isLoading ? '…' : t('dashboard.globalHeight', { height: fmt(globalHeight) })}</span>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <CardLabel>{t('dashboard.avgBlockTime')}</CardLabel>
          <div className="flex items-end gap-3">
            <div
              className="tabular-nums text-[18px] leading-none md:text-[20px]"
              style={{ fontFamily: 'var(--font-display)', color: 'var(--gold)' }}
            >
              {avgBlockTimeSec !== null ? fmtDuration(avgBlockTimeSec) : '—'}
            </div>
            {blockIntervals.length > 0 && (
              <Sparkline data={blockIntervals} height={24} color="var(--gold)" className="flex-1" />
            )}
          </div>
          <CardSub>{t('dashboard.lastNBlocks', { count: blocks.length })}</CardSub>
        </div>
        <div>
          <CardLabel>{t('dashboard.minerReward')}</CardLabel>
          <div className="text-[18px] leading-none md:text-[20px]" style={{ fontFamily: 'var(--font-display)' }}>
            {minerReward !== null
              ? <SignaAmount value={minerReward} style={{ color: 'var(--gold)' }} />
              : <span style={{ color: 'var(--gold)' }}>—</span>}
          </div>
          <CardSub>
            {baseReward !== null
              ? t('dashboard.emissionFees', { base: baseReward, fees: lastFeeSigna.toFixed(2) })
              : t('dashboard.signaPerBlock')}
          </CardSub>
        </div>
      </div>
    </div>
  )
}

function BannerRight({ status, isLoading }: { status?: FullBlockchainStatus; isLoading: boolean }) {
  const { t } = useTranslation()
  const { latestBlock } = useNodeSocket()
  const isSyncing = latestBlock
    ? latestBlock.progress < 1
    : status
      ? status.isScanning || (status.numberOfBlocks - 1) < status.lastBlockchainFeederHeight
      : false

  const circulatingSigna = status ? status.circulatingSupplyNQT / 1e8 : 0
  const burntSigna = status ? status.totalBurntNQT / 1e8 : 0

  return (
    <div className="flex flex-col justify-center gap-4 px-7 py-7 md:px-9">
      <div>
        <CardLabel>{t('dashboard.circulatingSupply')}</CardLabel>
        <div className="text-[20px] font-bold leading-none md:text-[24px]" style={{ fontFamily: 'var(--font-display)', textShadow: 'var(--glow-g)' }}>
          {isLoading ? '—' : <SignaAmount value={circulatingSigna} compact style={{ color: 'var(--green)' }} />}
        </div>
        <CardSub>{t('dashboard.signaInCirculation')}</CardSub>
      </div>
      {isSyncing ? (
        <div>
          <CardLabel>{t('dashboard.feederPeer')}</CardLabel>
          <div className="truncate text-[12px] leading-tight" style={{ color: 'var(--blue3)' }}>
            {isLoading ? '…' : (status?.lastBlockchainFeeder ?? '—')}
          </div>
          <CardSub>{isLoading ? '…' : t('dashboard.feederAt', { height: fmt(status?.lastBlockchainFeederHeight ?? 0) })}</CardSub>
        </div>
      ) : (
        <div>
          <CardLabel>{t('dashboard.totalBurned')}</CardLabel>
          <div className="text-[20px] font-bold leading-none md:text-[24px]" style={{ fontFamily: 'var(--font-display)', textShadow: 'var(--glow-m)' }}>
            {isLoading ? '—' : <SignaAmount value={burntSigna} compact style={{ color: 'var(--mag)' }} />}
          </div>
          <CardSub>{t('dashboard.signaRemovedFromSupply')}</CardSub>
        </div>
      )}
    </div>
  )
}

export function Banner({ status, mining, isLoading }: BannerProps) {
  return (
    <div
      className="banner-grid relative min-h-[140px] overflow-hidden md:min-h-[160px]"
      style={{
        border: '1px solid var(--border2)',
        background: 'var(--surface-tint)',
      }}
    >
      {/* Radial bloom */}
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background: `radial-gradient(ellipse at 30% 50%, var(--bloom) 0%, transparent 60%)`,
        }}
      />
      <BannerLeft status={status} isLoading={isLoading} />
      <div className="banner-divider" />
      <BannerCenter status={status} mining={mining} isLoading={isLoading} />
      <div className="banner-divider" />
      <BannerRight status={status} isLoading={isLoading} />
    </div>
  )
}
