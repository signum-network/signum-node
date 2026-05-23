import { Card, CardLabel, CardSub, CardSkeleton } from '@/components/ui/Card'
import { AnimatedNumber } from '@/components/ui/AnimatedNumber'
import { fmt } from '@/lib/utils'
import type { FullBlockchainStatus } from '@/lib/nodeApi'

interface ChainMetricCardProps {
  label: string
  value: number
  sub: string
  color: string
  glow?: string
  isLoading?: boolean
}

function ChainMetricCard({ label, value, sub, color, glow, isLoading }: ChainMetricCardProps) {
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
          <AnimatedNumber value={value} formatter={fmt} />
        </div>
      )}
      <CardSub>{sub}</CardSub>
    </Card>
  )
}

interface ChainActivityRowProps {
  fullStatus?: FullBlockchainStatus
  isLoading: boolean
}

export function ChainActivityRow({ fullStatus, isLoading }: ChainActivityRowProps) {
  const liveOrders = (fullStatus?.numberOfAskOrders ?? 0) + (fullStatus?.numberOfBidOrders ?? 0)

  return (
    <>
      <ChainMetricCard
        label="All-time Transactions"
        value={fullStatus?.numberOfTransactions ?? 0}
        sub="confirmed on-chain"
        color="var(--gold)"
        glow="var(--glow-gold)"
        isLoading={isLoading}
      />
      <ChainMetricCard
        label="Smart Contracts"
        value={fullStatus?.numberOfATs ?? 0}
        sub="ATs deployed"
        color="var(--blue2)"
        glow="var(--glow-b)"
        isLoading={isLoading}
      />
      <ChainMetricCard
        label="Digital Assets"
        value={fullStatus?.numberOfAssets ?? 0}
        sub="tokens issued"
        color="var(--green)"
        glow="var(--glow-g)"
        isLoading={isLoading}
      />
      <ChainMetricCard
        label="Live DEX Orders"
        value={liveOrders}
        sub={`${fullStatus?.numberOfAskOrders ?? 0} ask · ${fullStatus?.numberOfBidOrders ?? 0} bid`}
        color="var(--mag)"
        glow="var(--glow-m)"
        isLoading={isLoading}
      />
    </>
  )
}
