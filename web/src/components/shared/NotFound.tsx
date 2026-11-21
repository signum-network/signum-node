import { Link } from '@tanstack/react-router'
import { Card } from '@/components/ui/Card'

/** A missing block, transaction or account, with a way back to the block list. */
export function NotFound({ message, backLabel }: { message: string; backLabel: string }) {
  return (
    <Card>
      <p className="text-[13px]" style={{ color: 'var(--gold)' }}>{message}</p>
      <Link to="/blocks" search={{ page: 0 }} className="mt-3 inline-block text-[11px]" style={{ color: 'var(--blue2)' }}>
        ‹ {backLabel}
      </Link>
    </Card>
  )
}
