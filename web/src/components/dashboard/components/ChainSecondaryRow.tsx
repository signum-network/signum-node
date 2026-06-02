import { useTranslation } from 'react-i18next'
import { Card, CardLabel, CardSub, CardSkeleton } from '@/components/ui/Card'
import { AnimatedNumber } from '@/components/ui/AnimatedNumber'
import { fmt } from '@/lib/utils'
import type { FullBlockchainStatus } from '@/lib/nodeApi'

interface SecondaryMetricCardProps {
  label: string
  value: number
  sub: string
  color: string
  isLoading?: boolean
}

function SecondaryMetricCard({ label, value, sub, color, isLoading }: SecondaryMetricCardProps) {
  return (
    <Card interactive>
      <CardLabel>{label}</CardLabel>
      {isLoading ? (
        <div className="mb-2 h-7 w-20">
          <CardSkeleton />
        </div>
      ) : (
        <div
          className="tabular-nums text-[22px] font-bold leading-none md:text-[26px]"
          style={{ fontFamily: 'var(--font-display)', color }}
        >
          <AnimatedNumber value={value} formatter={fmt} />
        </div>
      )}
      <CardSub>{sub}</CardSub>
    </Card>
  )
}

interface AssetExchangeCardProps {
  fullStatus?: FullBlockchainStatus
  isLoading: boolean
}

function AssetExchangeCard({ fullStatus, isLoading }: AssetExchangeCardProps) {
  const { t } = useTranslation()

  const stats = [
    { key: 'assets',    value: fullStatus?.numberOfAssets,    label: t('dashboard.digitalAssets'),  sub: t('dashboard.tokensIssued') },
    { key: 'trades',    value: fullStatus?.numberOfTrades,    label: t('dashboard.trades'),          sub: t('dashboard.tradesExecuted') },
    { key: 'transfers', value: fullStatus?.numberOfTransfers, label: t('dashboard.transfers'),       sub: t('dashboard.tokenMovements') },
  ] as const

  return (
    <Card interactive>
      <p className="mb-2.5 text-[9px] font-semibold uppercase tracking-[3px]" style={{ color: 'var(--mag)' }}>
        {t('dashboard.assetExchange')}
      </p>
      <div className="flex gap-5">
        {stats.map(({ key, value, label, sub }, i) => (
          <div
            key={key}
            className="flex min-w-0 flex-col"
            style={i > 0 ? { borderLeft: '1px solid var(--border)', paddingLeft: '20px' } : undefined}
          >
            <span
              className="mb-1 text-[8px] font-semibold uppercase tracking-[2px]"
              style={{ color: 'var(--muted)' }}
            >
              {label}
            </span>
            {isLoading ? (
              <div className="mb-1 h-6 w-16"><CardSkeleton /></div>
            ) : (
              <span
                className="tabular-nums text-[22px] font-bold leading-none md:text-[26px]"
                style={{ fontFamily: 'var(--font-display)', color: 'var(--green)' }}
              >
                <AnimatedNumber value={value ?? 0} formatter={fmt} />
              </span>
            )}
            <span className="mt-1.5 text-[10px] tracking-[1px]" style={{ color: 'var(--muted)' }}>
              {sub}
            </span>
          </div>
        ))}
      </div>
    </Card>
  )
}

interface ChainSecondaryRowProps {
  fullStatus?: FullBlockchainStatus
  isLoading: boolean
}

export function ChainSecondaryRow({ fullStatus, isLoading }: ChainSecondaryRowProps) {
  const { t } = useTranslation()

  return (
    <>
      <SecondaryMetricCard
        label={t('dashboard.aliases')}
        value={fullStatus?.numberOfAliases ?? 0}
        sub="on-chain names"
        color="var(--gold)"
        isLoading={isLoading}
      />
      <SecondaryMetricCard
        label={t('dashboard.subscriptions')}
        value={fullStatus?.numberOfSubscriptions ?? 0}
        sub="recurring payments"
        color="var(--gold)"
        isLoading={isLoading}
      />
      <AssetExchangeCard fullStatus={fullStatus} isLoading={isLoading} />
    </>
  )
}
