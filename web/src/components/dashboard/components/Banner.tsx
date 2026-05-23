import { CardLabel, CardSub, CardSkeleton } from '@/components/ui/Card'
import { Pill } from '@/components/ui/Pill'
import { ProgressBar } from '@/components/ui/ProgressBar'
import { AnimatedNumber } from '@/components/ui/AnimatedNumber'
import { fmt } from '@/lib/utils'
import type { BlockchainStatus, MiningInfo } from '@/lib/nodeApi'

interface BannerProps {
  status?: BlockchainStatus
  mining?: MiningInfo
  isLoading: boolean
}

function BannerLeft({ status, isLoading }: { status?: BlockchainStatus; isLoading: boolean }) {
  const height = status ? status.numberOfBlocks - 1 : 0
  const isSyncing = status
    ? status.isScanning || height < status.lastBlockchainFeederHeight
    : false

  return (
    <div className="flex flex-col justify-center gap-3 px-7! py-7 md:px-9">
      <CardLabel className="mb-0">Current Block Height</CardLabel>
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
          {isSyncing ? 'Syncing' : 'Synced'}
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
  status?: BlockchainStatus
  mining?: MiningInfo
  isLoading: boolean
}) {
  const localHeight = status ? status.numberOfBlocks - 1 : 0
  const globalHeight = status?.lastBlockchainFeederHeight ?? 0
  const syncPct = globalHeight > 0 ? (localHeight / globalHeight) * 100 : 0
  const synced = syncPct >= 99.99
  const reward = mining ? Math.round(Number(mining.lastBlockReward) / 1e8).toString() : '—'

  return (
    <div className="flex flex-col justify-center gap-4 px-7! py-7 md:px-8">
      <div className="flex flex-col gap-1.5">
        <div className="flex items-center justify-between">
          <CardLabel className="mb-0">Sync Progress</CardLabel>
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
          <span>Local {isLoading ? '…' : fmt(localHeight)}</span>
          <span>Global {isLoading ? '…' : fmt(globalHeight)}</span>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <CardLabel>Block Time</CardLabel>
          <div
            className="tabular-nums text-[18px] leading-none md:text-[20px]"
            style={{ fontFamily: 'var(--font-display)', color: 'var(--gold)' }}
          >
            4:00
          </div>
          <CardSub>target</CardSub>
        </div>
        <div>
          <CardLabel>Last Reward</CardLabel>
          <div
            className="tabular-nums text-[18px] leading-none md:text-[20px]"
            style={{ fontFamily: 'var(--font-display)', color: 'var(--gold)' }}
          >
            {isLoading ? '—' : reward}
          </div>
          <CardSub>SIGNA / block</CardSub>
        </div>
      </div>
    </div>
  )
}

function BannerRight({ status, mining, isLoading }: BannerProps) {
  return (
    <div className="flex flex-col justify-center gap-4 px-7! py-7 md:px-9">
      <div>
        <CardLabel>Feeder Peer</CardLabel>
        <div className="truncate text-[12px] leading-tight" style={{ color: 'var(--blue3)' }}>
          {isLoading ? '…' : (status?.lastBlockchainFeeder ?? '—')}
        </div>
        <CardSub>at #{isLoading ? '…' : fmt(status?.lastBlockchainFeederHeight ?? 0)}</CardSub>
      </div>
      <div>
        <CardLabel>Base Target</CardLabel>
        <div
          className="tabular-nums text-[16px] leading-none"
          style={{ fontFamily: 'var(--font-display)', color: 'var(--text)' }}
        >
          {isLoading ? '—' : fmt(Number(mining?.baseTarget ?? 0))}
        </div>
        <CardSub>mining difficulty</CardSub>
      </div>
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
      <BannerRight status={status} mining={mining} isLoading={isLoading} />
    </div>
  )
}
