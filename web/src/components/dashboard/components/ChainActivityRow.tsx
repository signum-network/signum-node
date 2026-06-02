import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation()

  return (
    <>
      <ChainMetricCard
        label={t('dashboard.allTimeTransactions')}
        value={fullStatus?.numberOfTransactions ?? 0}
        sub={t('dashboard.confirmedOnChain')}
        color="var(--gold)"
        glow="var(--glow-gold)"
        isLoading={isLoading}
      />
      <ChainMetricCard
        label={t('dashboard.accounts')}
        value={fullStatus?.numberOfAccounts ?? 0}
        sub={t('dashboard.registeredOnChain')}
        color="var(--blue2)"
        glow="var(--glow-b)"
        isLoading={isLoading}
      />
      <ChainMetricCard
        label={t('dashboard.smartContracts')}
        value={fullStatus?.numberOfATs ?? 0}
        sub={t('dashboard.atsDeployed')}
        color="var(--blue2)"
        glow="var(--glow-b)"
        isLoading={isLoading}
      />
    </>
  )
}
