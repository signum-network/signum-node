import { Card, CardLabel, CardSub } from '@/components/ui/Card'
import { Sparkline } from '@/components/ui/Sparkline'
import { useRecentBlocks } from '@/hooks/useNodeQuery'
import { networkPhysicalCapacityPiB, networkEffectiveCapacityPiB } from '@/lib/utils'

interface CumulativeDifficultyCardProps {
  current: string
  isLoading: boolean
}

export function CumulativeDifficultyCard({ current, isLoading }: CumulativeDifficultyCardProps) {
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
          <CardLabel>Network Capacity</CardLabel>
          <div
            className="tabular-nums text-[26px] font-bold leading-none md:text-[32px]"
            style={{ fontFamily: 'var(--font-display)', color: 'var(--blue2)', textShadow: 'var(--glow-b)' }}
          >
            {isLoading || currentPhysical === null ? '—' : `${currentPhysical.toFixed(2)} PiB`}
          </div>
          <CardSub className="mt-1">physical · last {blocks.length} blocks</CardSub>
        </div>
        <div className="text-right">
          <CardLabel>Effective Capacity</CardLabel>
          <div
            className="tabular-nums text-[16px] font-bold leading-none"
            style={{ fontFamily: 'var(--font-display)', color: 'var(--muted)' }}
          >
            {isLoading || currentEffective === null ? '—' : `${currentEffective.toFixed(2)} PiB`}
          </div>
          <CardSub className="mt-1">incl. PoC+ commitment boost</CardSub>
        </div>
      </div>
      <div className="mt-3">
        <Sparkline
          data={physicalHistory.length > 0 ? physicalHistory : Array(20).fill(0)}
          height={48}
          color="var(--blue2)"
        />
      </div>
      <CardSub className="mt-1">
        cumul. difficulty {isLoading ? '—' : current}
      </CardSub>
    </Card>
  )
}
