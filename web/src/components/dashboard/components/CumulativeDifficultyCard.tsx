import { Card, CardLabel, CardSub } from '@/components/ui/Card'
import { Sparkline } from '@/components/ui/Sparkline'

interface CumulativeDifficultyCardProps {
  current: string
  history: number[]
  isLoading: boolean
}

function formatDifficulty(raw: string): string {
  try {
    return BigInt(raw).toLocaleString()
  } catch {
    return '—'
  }
}

export function CumulativeDifficultyCard({
  current,
  history,
  isLoading,
}: CumulativeDifficultyCardProps) {
  return (
    <Card className="col-span-2">
      <CardLabel>Cumulative Difficulty</CardLabel>
      <div
        className="mb-3 tabular-nums text-[20px] font-bold leading-tight md:text-[26px]"
        style={{
          fontFamily: 'var(--font-display)',
          color: 'var(--blue2)',
          textShadow: 'var(--glow-b)',
          wordBreak: 'break-all',
        }}
      >
        {isLoading ? '—' : formatDifficulty(current)}
      </div>
      <Sparkline data={history.length > 0 ? history : Array(15).fill(0)} height={44} />
      <CardSub className="mt-1.5">monotonically increasing · fork detection baseline</CardSub>
    </Card>
  )
}
