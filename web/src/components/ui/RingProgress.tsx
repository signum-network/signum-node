import { motion } from 'framer-motion'

interface RingProgressProps {
  value: number
  size?: number
  strokeWidth?: number
  color?: string
  className?: string
}

export function RingProgress({
  value,
  size = 60,
  strokeWidth = 4,
  color = 'var(--blue2)',
  className,
}: RingProgressProps) {
  const radius = (size - strokeWidth) / 2
  const circumference = 2 * Math.PI * radius
  const clamped = Math.min(100, Math.max(0, value))

  return (
    <svg width={size} height={size} className={className}>
      <circle
        cx={size / 2}
        cy={size / 2}
        r={radius}
        fill="none"
        stroke="rgba(0,102,255,.10)"
        strokeWidth={strokeWidth}
      />
      <motion.circle
        cx={size / 2}
        cy={size / 2}
        r={radius}
        fill="none"
        stroke={color}
        strokeWidth={strokeWidth}
        strokeLinecap="round"
        strokeDasharray={circumference}
        initial={{ pathLength: 0, rotate: -90 }}
        animate={{ pathLength: clamped / 100 }}
        transition={{ duration: 1, ease: 'easeOut' }}
        style={{ transformOrigin: 'center', rotate: '-90deg' }}
      />
    </svg>
  )
}
