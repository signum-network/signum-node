import { cn } from '@/lib/utils'

interface SignaAmountProps {
  value: number
  decimals?: number
  compact?: boolean
  className?: string
  style?: React.CSSProperties
}

function splitCompact(n: number): [string, string] {
  if (n >= 1e9) {
    const [i, f] = (n / 1e9).toFixed(1).split('.')
    return [i, `${f}B`]
  }
  if (n >= 1e6) {
    const [i, f] = (n / 1e6).toFixed(1).split('.')
    return [i, `${f}M`]
  }
  if (n >= 1e3) {
    const [i, f] = (n / 1e3).toFixed(1).split('.')
    return [i, `${f}K`]
  }
  const [i, f] = n.toFixed(2).split('.')
  return [new Intl.NumberFormat().format(Number(i)), f]
}

export function SignaAmount({ value, decimals = 2, compact = false, className, style }: SignaAmountProps) {
  let intPart: string
  let fracPart: string

  if (compact) {
    ;[intPart, fracPart] = splitCompact(value)
  } else {
    const [i, f] = value.toFixed(decimals).split('.')
    intPart = new Intl.NumberFormat().format(Number(i))
    fracPart = f
  }

  return (
    <span className={cn('tabular-nums', className)} style={style}>
      {intPart}
      <span style={{ fontSize: '0.65em', opacity: 0.6 }}>.{fracPart}</span>
    </span>
  )
}
