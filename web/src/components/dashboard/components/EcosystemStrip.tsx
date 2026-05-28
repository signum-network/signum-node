import { useTranslation } from 'react-i18next'
import { Card, CardLabel, CardSkeleton } from '@/components/ui/Card'
import type { FullBlockchainStatus } from '@/lib/nodeApi'

interface EcosystemStripProps {
  fullStatus?: FullBlockchainStatus
  isLoading: boolean
}

interface StatItemProps {
  label: string
  value: number | undefined
  isLoading: boolean
  color?: string
}

function StatItem({ label, value, isLoading, color = 'var(--blue2)' }: StatItemProps) {
  return (
    <div className="flex min-w-0 flex-1 flex-col gap-1">
      <CardLabel className="mb-0">{label}</CardLabel>
      {isLoading ? (
        <div className="h-6 w-16"><CardSkeleton /></div>
      ) : (
        <span
          className="tabular-nums text-[20px] font-bold leading-none"
          style={{ fontFamily: 'var(--font-display)', color }}
        >
          {(value ?? 0).toLocaleString()}
        </span>
      )}
    </div>
  )
}

export function EcosystemStrip({ fullStatus, isLoading }: EcosystemStripProps) {
  const { t } = useTranslation()

  const stats = [
    { key: 'accounts', value: fullStatus?.numberOfUnlockedAccounts, color: 'var(--blue2)' },
    { key: 'trades', value: fullStatus?.numberOfTrades, color: 'var(--green)' },
    { key: 'transfers', value: fullStatus?.numberOfTransfers, color: 'var(--green)' },
    { key: 'aliases', value: fullStatus?.numberOfAliases, color: 'var(--gold)' },
    { key: 'subscriptions', value: fullStatus?.numberOfSubscriptions, color: 'var(--gold)' },
  ] as const

  return (
    <Card className="col-span-2 xl:col-span-4">
      <div className="flex flex-wrap gap-6 sm:flex-nowrap sm:items-center sm:gap-8">
        {stats.flatMap(({ key, value, color }, i) => [
          i > 0 ? (
            <div key={`div-${i}`} className="hidden h-8 w-px shrink-0 sm:block" style={{ background: 'var(--border)' }} />
          ) : null,
          <StatItem
            key={key}
            label={t(`dashboard.${key}`)}
            value={value}
            isLoading={isLoading}
            color={color}
          />,
        ])}
      </div>
    </Card>
  )
}
