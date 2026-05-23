import { motion, type HTMLMotionProps } from 'framer-motion'
import { cn } from '@/lib/utils'

const SPRING = { type: 'spring' as const, stiffness: 300, damping: 20 }

interface CardProps extends Omit<HTMLMotionProps<'div'>, 'children'> {
  padding?: boolean
  interactive?: boolean
  children?: React.ReactNode
}

export function Card({
  children,
  className,
  padding = true,
  interactive = false,
  ...props
}: CardProps) {
  return (
    <motion.div
      className={cn('relative backdrop-blur-[8px]', padding && 'p-5', className)}
      style={{
        background: 'var(--panel)',
        border: '1px solid var(--border)',
        ...props.style,
      }}
      whileHover={
        interactive
          ? { y: -2, boxShadow: 'var(--card-hover)' }
          : undefined
      }
      transition={SPRING}
      {...props}
    >
      {/* Tactical accent — only TL + BR per Horizon mockup spec */}
      <div className="pointer-events-none absolute -left-px -top-px h-[14px] w-[14px] border-l-2 border-t-2 border-[var(--blue2)]" />
      <div className="pointer-events-none absolute -bottom-px -right-px h-[14px] w-[14px] border-b-2 border-r-2 border-[var(--blue2)]" />
      {children}
    </motion.div>
  )
}

export function CardLabel({
  children,
  className,
}: {
  children: React.ReactNode
  className?: string
}) {
  return (
    <p
      className={cn(
        'mb-2.5 text-[9px] font-semibold uppercase tracking-[3px] text-[var(--blue2)]',
        className,
      )}
    >
      {children}
    </p>
  )
}

export function CardSub({
  children,
  className,
}: {
  children: React.ReactNode
  className?: string
}) {
  return (
    <p
      className={cn(
        'mt-1.5 text-[10px] tracking-[1px] text-[var(--muted)]',
        className,
      )}
    >
      {children}
    </p>
  )
}

export function CardSkeleton({ className }: { className?: string }) {
  return (
    <motion.div
      className={cn('h-full w-full rounded-sm', className)}
      style={{ background: 'var(--border)' }}
      animate={{ opacity: [0.4, 0.8, 0.4] }}
      transition={{ repeat: Infinity, duration: 1.5, ease: 'easeInOut' }}
    />
  )
}
