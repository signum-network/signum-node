import { useTranslation } from 'react-i18next'
import { Card, CardLabel, CardSkeleton } from '@/components/ui/Card'
import { ProgressBar } from '@/components/ui/ProgressBar'
import { Badge } from '@/components/ui/Badge'
import type { FullBlockchainStatus } from '@/lib/nodeApi'

interface NodeHealthStripProps {
  fullStatus?: FullBlockchainStatus
  isLoading: boolean
}

export function NodeHealthStrip({ fullStatus, isLoading }: NodeHealthStripProps) {
  const { t } = useTranslation()

  const usedMB = fullStatus
    ? Math.round((fullStatus.totalMemory - fullStatus.freeMemory) / 1024 / 1024)
    : 0
  const maxMB = fullStatus ? Math.round(fullStatus.maxMemory / 1024 / 1024) : 0
  const heapPct = maxMB > 0 ? (usedMB / maxMB) * 100 : 0
  const heapSynced = heapPct < 60

  const cores = fullStatus?.availableProcessors ?? 0
  const dbTrimming = fullStatus?.databaseTrimmingEnabled ?? false

  return (
    <Card className="col-span-2 xl:col-span-4">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:gap-8">

        {/* JVM Heap */}
        <div className="flex min-w-0 flex-1 flex-col gap-2">
          <div className="flex items-center justify-between">
            <CardLabel className="mb-0" tooltip={t('info.jvmHeap')}>{t('dashboard.jvmHeap')}</CardLabel>
            {isLoading ? null : (
              <span
                className="tabular-nums text-[10px]"
                style={{ color: heapSynced ? 'var(--green)' : heapPct < 80 ? 'var(--amber)' : 'var(--mag)' }}
              >
                {heapPct.toFixed(0)}%
              </span>
            )}
          </div>
          {isLoading ? (
            <div className="h-4 w-full">
              <CardSkeleton />
            </div>
          ) : (
            <>
              <ProgressBar value={heapPct} synced={heapSynced} />
              <span className="text-[10px] tabular-nums" style={{ color: 'var(--muted)' }}>
                {usedMB} / {maxMB} MB
              </span>
            </>
          )}
        </div>

        <div className="hidden h-8 w-px sm:block" style={{ background: 'var(--border)' }} />

        {/* CPU */}
        <div className="flex flex-col gap-1">
          <CardLabel>{t('dashboard.cpuCores')}</CardLabel>
          {isLoading ? (
            <div className="h-6 w-12"><CardSkeleton /></div>
          ) : (
            <span
              className="tabular-nums text-[22px] font-bold leading-none"
              style={{ fontFamily: 'var(--font-display)', color: 'var(--blue2)' }}
            >
              {cores}
            </span>
          )}
        </div>

        <div className="hidden h-8 w-px sm:block" style={{ background: 'var(--border)' }} />

        {/* DB Trimming */}
        <div className="flex flex-col gap-1">
          <CardLabel tooltip={t('info.dbTrimming')}>{t('dashboard.dbTrimming')}</CardLabel>
          {isLoading ? (
            <div className="h-5 w-20"><CardSkeleton /></div>
          ) : (
            <Badge variant={dbTrimming ? 'green' : 'amber'}>
              {dbTrimming ? t('common.enabled') : t('common.disabled')}
            </Badge>
          )}
        </div>

      </div>
    </Card>
  )
}
