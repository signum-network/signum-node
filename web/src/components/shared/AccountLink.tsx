import { Link } from '@tanstack/react-router'

/** `S-32UW-QVHD-9CD8-DBFCW` → `S-32UW-…-DBFCW` */
function shorten(address: string): string {
  const parts = address.split('-')
  return parts.length === 5 ? `${parts[0]}-${parts[1]}-…-${parts[4]}` : address
}

/** An account address, shortened, linking to the account page; the full address shows on hover. */
export function AccountLink({ id, rs }: { id: string; rs?: string }) {
  const label = rs ?? id
  return (
    <Link
      to="/accounts/$id"
      params={{ id: rs ?? id }}
      title={label}
      className="font-mono transition-opacity hover:opacity-80"
      style={{ color: 'var(--blue2)' }}
      onClick={(e) => e.stopPropagation()}
    >
      {rs ? shorten(rs) : id}
    </Link>
  )
}
