import { motion } from 'framer-motion'
import { cn } from '@/lib/utils'

interface ProgressBarProps {
  value: number
  synced?: boolean
  className?: string
}

export function ProgressBar({ value, synced = true, className }: ProgressBarProps) {
  const clamped = Math.min(100, Math.max(0, value))

  return (
    <div
      className={cn('h-[6px] w-full overflow-hidden', className)}
      style={{ background: 'rgba(0,102,255,.10)' }}
    >
      <motion.div
        className="h-full"
        initial={{ width: 0 }}
        animate={{ width: `${clamped}%` }}
        transition={{ duration: 0.8, ease: 'easeOut' }}
        style={{
          background: synced
            ? 'linear-gradient(90deg, var(--blue), var(--green))'
            : 'var(--amber)',
        }}
      />
    </div>
  )
}
