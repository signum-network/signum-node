import { Card, CardLabel, CardSub, CardSkeleton } from '../../ui/Card'
import { AnimatedNumber } from '../../ui/AnimatedNumber'
import { fmt, fmtSigna, networkCapacityPiB } from '../../../lib/utils'
import type { MiningInfo } from '../../../lib/nodeApi'

interface MetricCardProps {
  label: string
  value: number
  sub: string
  color: string
  glow?: string
  isLoading?: boolean
  formatter?: (n: number) => string
}

function MetricCard({ label, value, sub, color, glow, isLoading, formatter }: MetricCardProps) {
  return (
    <Card interactive>
      <CardLabel>{label}</CardLabel>
      {isLoading ? (
        <div className="mb-2 h-9 w-24">
          <CardSkeleton />
        </div>
      ) : (
        <div
          className="tabular-nums text-[30px] font-bold leading-none md:text-[36px]"
          style={{ fontFamily: 'var(--font-display)', color, textShadow: glow }}
        >
          <AnimatedNumber value={value} formatter={formatter ?? fmt} />
        </div>
      )}
      <CardSub>{sub}</CardSub>
    </Card>
  )
}

interface MetricGridProps {
  peerCount: number
  pendingTxCount: number
  mining?: MiningInfo
  isLoading: boolean
}

export function MetricGrid({ peerCount, pendingTxCount, mining, isLoading }: MetricGridProps) {
  const avgCommitment = mining ? Number(fmtSigna(mining.averageCommitmentNQT)) : 0
  const capacity = mining ? networkCapacityPiB(mining.baseTarget) : 0

  return (
    <>
      <MetricCard
        label="Peers Connected"
        value={peerCount}
        sub="active connections"
        color="var(--green)"
        glow="var(--glow-g)"
        isLoading={isLoading}
      />
      <MetricCard
        label="Pending TXs"
        value={pendingTxCount}
        sub="unconfirmed in mempool"
        color="var(--mag)"
        glow="var(--glow-m)"
        isLoading={isLoading}
      />
      <MetricCard
        label="Avg Commitment"
        value={avgCommitment}
        sub="SIGNA / TiB · PoC+"
        color="var(--gold)"
        glow="var(--glow-gold)"
        isLoading={isLoading}
        formatter={(n) => n.toFixed(2)}
      />
      <MetricCard
        label="Network Capacity"
        value={capacity}
        sub="PiB · from base target"
        color="var(--blue2)"
        glow="var(--glow-b)"
        isLoading={isLoading}
      />
    </>
  )
}
