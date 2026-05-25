import { useTranslation } from 'react-i18next'
import { Card, CardLabel, CardSub, CardSkeleton } from '@/components/ui/Card'
import { AnimatedNumber } from '@/components/ui/AnimatedNumber'
import { SignaAmount } from '@/components/ui/SignaAmount'
import { fmt, fmtSigna } from '@/lib/utils'
import type { MiningInfo } from '@/lib/nodeApi'

interface MetricCardProps {
  label: string
  value: number
  sub: string
  color: string
  glow?: string
  isLoading?: boolean
  signa?: boolean
  formatter?: (n: number) => string
  tooltip?: string
}

function MetricCard({ label, value, sub, color, glow, isLoading, signa, formatter, tooltip }: MetricCardProps) {
  return (
    <Card interactive>
      <CardLabel tooltip={tooltip}>{label}</CardLabel>
      {isLoading ? (
        <div className="mb-2 h-9 w-24">
          <CardSkeleton />
        </div>
      ) : (
        <div
          className="text-[30px] font-bold leading-none md:text-[36px]"
          style={{ fontFamily: 'var(--font-display)', textShadow: glow }}
        >
          {signa
            ? <SignaAmount value={value} style={{ color }} />
            : <AnimatedNumber value={value} formatter={formatter ?? fmt} />}
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
  const { t } = useTranslation()
  const avgCommitment = mining ? Number(fmtSigna(mining.averageCommitmentNQT)) : 0

  return (
    <>
      <MetricCard
        label={t('dashboard.peersConnected')}
        value={peerCount}
        sub={t('dashboard.activeConnections')}
        color="var(--green)"
        glow="var(--glow-g)"
        isLoading={isLoading}
      />
      <MetricCard
        label={t('dashboard.pendingTxs')}
        value={pendingTxCount}
        sub={t('dashboard.unconfirmedInMempool')}
        color="var(--mag)"
        glow="var(--glow-m)"
        isLoading={isLoading}
      />
      <MetricCard
        label={t('dashboard.avgCommitment')}
        value={avgCommitment}
        sub={t('dashboard.signaPerTiB')}
        color="var(--gold)"
        glow="var(--glow-gold)"
        isLoading={isLoading}
        signa
        tooltip={t('info.avgCommitment')}
      />
    </>
  )
}
