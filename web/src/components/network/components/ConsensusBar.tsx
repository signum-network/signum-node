import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { Card, CardLabel, CardSub, CardSkeleton } from '@/components/ui/Card'
import { useNetworkStatus } from '@/hooks/useNodeQuery'

function consensusColor(pct: number) {
  if (pct >= 90) return 'var(--green)'
  if (pct >= 70) return 'var(--gold)'
  return 'var(--red, #ff4444)'
}

export function ConsensusBar() {
  const { data, isLoading, refetch, isFetching } = useNetworkStatus()
  const { t } = useTranslation()
  const pct = data?.consensusPercent ?? 0
  const color = consensusColor(pct)

  return (
    <Card className="col-span-full">
      <div className="flex items-start justify-between gap-4">
        <div>
          <CardLabel tooltip={t('info.chainConsensus')}>{t('network.chainConsensus')}</CardLabel>
          {isLoading ? (
            <div className="h-10 w-40"><CardSkeleton /></div>
          ) : (
            <div
              className="text-[40px] font-black leading-none tabular-nums"
              style={{ fontFamily: 'var(--font-display)', color, textShadow: `0 0 20px ${color}` }}
            >
              {pct.toFixed(1)}%
            </div>
          )}
          <CardSub className="mt-1">
            {data
              ? t('network.agree', { on: data.onChainPeers, stale: data.stalePeers, forking: data.forkingPeers })
              : t('network.onOurChain')}
          </CardSub>
        </div>
        <div className="text-right">
          <CardLabel>{t('common.height')}</CardLabel>
          <div
            className="text-[24px] font-bold tabular-nums"
            style={{ fontFamily: 'var(--font-display)', color: 'var(--blue2)' }}
          >
            {data?.myHeight?.toLocaleString() ?? '—'}
          </div>
          {data?.cachedAt && (
            <CardSub className="mt-1">{t('common.cachedAgo', { seconds: Math.round((Date.now() - data.cachedAt) / 1000) })}</CardSub>
          )}
          <button
            type="button"
            className="mt-2 text-[9px] uppercase tracking-[2px] transition-opacity hover:opacity-100"
            style={{ color: 'var(--blue2)', opacity: isFetching ? 0.4 : 0.7 }}
            onClick={() => void refetch()}
            disabled={isFetching}
          >
            {isFetching ? t('common.refreshing') : t('common.refresh')}
          </button>
        </div>
      </div>

      <div className="mt-4 h-1.5 w-full overflow-hidden rounded-full" style={{ background: 'var(--border)' }}>
        <motion.div
          className="h-full rounded-full"
          style={{ background: color, boxShadow: `0 0 8px ${color}` }}
          animate={{ width: `${pct}%` }}
          transition={{ duration: 0.8, ease: 'easeOut' }}
        />
      </div>
    </Card>
  )
}
