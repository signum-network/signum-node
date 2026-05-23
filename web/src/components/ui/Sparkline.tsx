import { motion } from 'framer-motion'
import { cn } from '@/lib/utils'

interface SparklineProps {
  data: number[]
  height?: number
  className?: string
}

export function Sparkline({ data, height = 40, className }: SparklineProps) {
  const max = Math.max(...data, 1)
  const allZero = data.every((v) => v === 0)

  return (
    <div
      className={cn('flex items-end gap-[2px]', className)}
      style={{ height }}
    >
      {data.map((v, i) => {
        const pct = allZero ? 18 + (i % 4) * 5 : Math.max((v / max) * 100, 4)
        const tint = allZero ? 'var(--muted)' : 'var(--blue2)'
        return (
          <motion.div
            key={i}
            className="min-w-[4px] flex-1"
            style={{
              height: `${pct}%`,
              background: `color-mix(in srgb, ${tint} 16%, transparent)`,
              borderTop: `1px solid color-mix(in srgb, ${tint} 40%, transparent)`,
              transformOrigin: 'bottom',
            }}
            initial={{ scaleY: 0 }}
            animate={{ scaleY: 1 }}
            transition={{ delay: i * 0.025, duration: 0.35, ease: 'easeOut' }}
          />
        )
      })}
    </div>
  )
}
