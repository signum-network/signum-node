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
  const prevRef = useRef(value)

  useEffect(() => {
    const from = prevRef.current
    prevRef.current = value

    const controls = animate(from, value, {
      duration: 0.7,
      ease: 'easeOut',
      onUpdate(latest) {
        if (ref.current) {
          ref.current.textContent = formatter(latest)
        }
      },
    })
    return controls.stop
  }, [value, formatter])

  return (
    <span ref={ref} className={className}>
      {formatter(value)}
    </span>
  )
}
