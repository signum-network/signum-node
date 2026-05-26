import { cn } from '@/lib/utils'

interface ScrollableTableProps {
  head: React.ReactNode
  children: React.ReactNode
  maxHeight?: string
  className?: string
}

export function ScrollableTable({ head, children, maxHeight = '400px', className }: ScrollableTableProps) {
  return (
    <div
      className={cn('themed-scroll overflow-auto w-full', className)}
      style={{ maxHeight }}
    >
      <table className="w-full text-[11px]">
        <thead
          className="sticky top-0 z-10"
          style={{ background: 'var(--bg2)' }}
        >
          {head}
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  )
}
