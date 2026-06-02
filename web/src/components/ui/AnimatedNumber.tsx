import { useEffect, useRef } from 'react'
import { animate } from 'framer-motion'

interface AnimatedNumberProps {
  value: number
  formatter?: (n: number) => string
  className?: string
}

const defaultFormatter = (n: number) => Math.round(n).toLocaleString()

export function AnimatedNumber({
  value,
  formatter = defaultFormatter,
  className,
}: AnimatedNumberProps) {
  const ref = useRef<HTMLSpanElement>(null)
  const isFirst = useRef(true)

  useEffect(() => {
    if (isFirst.current) {
      isFirst.current = false
      return
    }
    if (!ref.current) return
    animate(ref.current, { opacity: [0.2, 1], y: [5, 0] }, { duration: 0.35, ease: 'easeOut' })
  }, [value])

  return (
    <span ref={ref} className={className}>
      {formatter(value)}
    </span>
  )
}
