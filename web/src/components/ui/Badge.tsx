import { cn } from '../../lib/utils'

export type BadgeVariant = 'green' | 'blue' | 'amber' | 'mag' | 'muted'

const VARIANT_STYLES: Record<BadgeVariant, string> = {
  green: 'text-[var(--green)] border-[rgba(0,255,170,.25)]  bg-[rgba(0,255,170,.08)]',
  blue:  'text-[var(--blue2)] border-[rgba(0,170,255,.25)]  bg-[rgba(0,170,255,.08)]',
  amber: 'text-[var(--amber)] border-[rgba(255,149,0,.25)]   bg-[rgba(255,149,0,.08)]',
  mag:   'text-[var(--mag)]   border-[rgba(255,0,85,.25)]    bg-[rgba(255,0,85,.08)]',
  muted: 'text-[var(--muted)] border-[var(--border)]         bg-transparent',
}

interface BadgeProps {
  variant?: BadgeVariant
  children: React.ReactNode
  className?: string
}

export function Badge({ variant = 'blue', children, className }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center border px-2 py-0.5',
        'text-[8px] font-semibold uppercase tracking-[1.5px]',
        VARIANT_STYLES[variant],
        className,
      )}
    >
      {children}
    </span>
  )
}
