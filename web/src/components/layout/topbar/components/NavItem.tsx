import { motion } from 'framer-motion'
import { cn } from '@/lib/utils'

interface NavItemProps {
  label: string
  active?: boolean
  future?: boolean
  onClick?: () => void
}

export function NavItem({ label, active = false, future = false, onClick }: NavItemProps) {
  return (
    <motion.button
      type="button"
      className={cn(
        'relative flex h-full cursor-pointer items-center px-5!',
        'text-[10px] font-semibold uppercase tracking-[2px]',
        'border-none outline-none',
        active ? 'text-[var(--blue2)]' : 'text-[var(--muted)]',
        future && 'pointer-events-none opacity-30',
      )}
      style={{ background: active ? 'rgba(0,170,255,.06)' : 'transparent' }}
      whileHover={!active && !future ? { color: 'var(--blue2)', background: 'rgba(0,170,255,.03)' } : undefined}
      whileTap={!future ? { scale: 0.97 } : undefined}
      transition={{ duration: 0.12 }}
      onClick={!future ? onClick : undefined}
    >
      {label}
      {future && (
        <span className="ml-1.5 text-[7px] opacity-60" style={{ color: 'var(--muted)' }}>soon</span>
      )}
      {active && (
        <motion.div
          className="absolute bottom-0 left-3 right-3 h-[2px]"
          style={{ background: 'var(--blue2)', boxShadow: '0 0 8px var(--blue2)' }}
          layoutId="nav-indicator"
          transition={{ type: 'spring', stiffness: 400, damping: 30 }}
        />
      )}
    </motion.button>
  )
}
