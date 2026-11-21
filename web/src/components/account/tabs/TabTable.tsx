import { ScrollableTable } from '@/components/ui/ScrollableTable'

/** A table in an account tab: column heads in the muted caps style of the other tables. */
export function TabTable({ heads, children }: { heads: { label: string; align?: 'left' | 'right' }[]; children: React.ReactNode }) {
  return (
    <ScrollableTable
      maxHeight="none"
      head={
        <tr style={{ borderBottom: '1px solid var(--border)' }}>
          {heads.map(({ label, align = 'left' }) => (
            <th key={label} className={`px-3 py-2 first:pl-5 last:pr-5 text-${align}`}>
              <span className="text-[9px] uppercase tracking-[2px]" style={{ color: 'var(--muted)' }}>{label}</span>
            </th>
          ))}
        </tr>
      }
    >
      {children}
    </ScrollableTable>
  )
}

export function EmptyRow({ colSpan, text }: { colSpan: number; text: string }) {
  return (
    <tr>
      <td colSpan={colSpan} className="px-5 py-6 text-center text-[11px]" style={{ color: 'var(--muted)' }}>{text}</td>
    </tr>
  )
}
