import { useTranslation } from 'react-i18next'
import { Card, CardLabel, CardSub } from '@/components/ui/Card'
import { InfoTooltip } from '@/components/ui/InfoTooltip'
import { Sparkline } from '@/components/ui/Sparkline'
import { useRecentBlocks } from '@/hooks/useNodeQuery'
import { networkPhysicalCapacityPiB, networkEffectiveCapacityPiB } from '@/lib/utils'

interface CumulativeDifficultyCardProps {
  current: string
  isLoading: boolean
}

export function CumulativeDifficultyCard({ current, isLoading }: CumulativeDifficultyCardProps) {
  const { t } = useTranslation()
  const { data: recentBlocks } = useRecentBlocks()
  const blocks = recentBlocks?.blocks ?? []

  const physicalHistory = blocks.length > 0
    ? [...blocks].reverse().map((b) => networkPhysicalCapacityPiB(b.baseTarget))
    : []

  const latestBt = blocks[0]?.baseTarget
  const currentPhysical = latestBt ? networkPhysicalCapacityPiB(latestBt) : null
  const currentEffective = latestBt ? networkEffectiveCapacityPiB(latestBt) : null

  return (
    <Card className="col-span-2">
      <div className="flex items-start justify-between gap-4">
        <div>
          <CardLabel tooltip={t('info.networkCapacity')}>{t('dashboard.networkCapacity')}</CardLabel>
          <div
            className="tabular-nums text-[26px] font-bold leading-none md:text-[32px]"
            style={{ fontFamily: 'var(--font-display)', color: 'var(--blue2)', textShadow: 'var(--glow-b)' }}
          >
            {isLoading || currentPhysical === null ? '—' : `${currentPhysical.toFixed(2)} PiB`}
          </div>
          <CardSub className="mt-1">{t('dashboard.physicalLastN', { count: blocks.length })}</CardSub>
        </div>
        <div className="text-right">
          <CardLabel tooltip={t('info.effectiveCapacity')}>{t('dashboard.effectiveCapacity')}</CardLabel>
          <div
            className="tabular-nums text-[16px] font-bold leading-none"
            style={{ fontFamily: 'var(--font-display)', color: 'var(--muted)' }}
          >
            {isLoading || currentEffective === null ? '—' : `${currentEffective.toFixed(2)} PiB`}
          </div>
          <CardSub className="mt-1">{t('dashboard.commitmentBoost')}</CardSub>
        </div>
      </div>
      <div className="mt-3">
        <Sparkline
          data={physicalHistory.length > 0 ? physicalHistory : Array(20).fill(0)}
          height={48}
          color="var(--blue2)"
        />
      </div>
      <CardSub className="mt-1 flex items-center gap-1.5">
        <span>{t('dashboard.cumulDifficulty', { value: isLoading ? '—' : current })}</span>
        <InfoTooltip text={t('info.cumulativeDifficulty')} />
      </CardSub>
    </Card>
  )
}
