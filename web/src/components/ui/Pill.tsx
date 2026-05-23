import { motion } from 'framer-motion'
import { cn } from '../../lib/utils'

export type PillVariant = 'green' | 'blue' | 'amber' | 'mag'
export type DotStatus = 'ok' | 'warn' | 'bad'

const VARIANT_STYLES: Record<PillVariant, string> = {
  green: 'text-[var(--green)] border-[rgba(0,255,170,.25)] bg-[rgba(0,255,170,.08)]',
  blue:  'text-[var(--blue2)] border-[rgba(0,170,255,.25)] bg-[rgba(0,170,255,.08)]',
  amber: 'text-[var(--amber)] border-[rgba(255,149,0,.25)]  bg-[rgba(255,149,0,.08)]',
  mag:   'text-[var(--mag)]   border-[rgba(255,0,85,.25)]   bg-[rgba(255,0,85,.08)]',
}

const DOT_COLORS: Record<DotStatus, string> = {
  ok:   'bg-[var(--green)] shadow-[var(--glow-g)]',
  warn: 'bg-[var(--amber)] shadow-[0_0_6px_var(--amber)]',
  bad:  'bg-[var(--mag)]   shadow-[var(--glow-m)]',
}

interface PillProps {
  variant?: PillVariant
  dot?: DotStatus
  children: React.ReactNode
  className?: string
}

export function Pill({ variant = 'blue', dot, children, className }: PillProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-[5px] border px-[9px]! py-[3px]!',
        'text-[9px] font-semibold uppercase tracking-[2px]',
        VARIANT_STYLES[variant],
        className,
      )}
    >
      {dot && (
        <motion.span
          className={cn('h-[6px] w-[6px] flex-shrink-0 rounded-full', DOT_COLORS[dot])}
          animate={dot === 'ok' ? { scale: [1, 1.35, 1] } : undefined}
          transition={{ repeat: Infinity, duration: 2.4, ease: 'easeInOut' }}
        />
      )}
      {children}
    </span>
  )
}
