/** Label/value rows for a detail panel; a `null` value hides its row. */
export function KeyValueList({ rows }: { rows: [label: string, value: React.ReactNode | null][] }) {
  return (
    <dl className="grid grid-cols-[minmax(0,auto)_minmax(0,1fr)] gap-x-4 gap-y-2 text-[12px]">
      {rows
        .filter(([, value]) => value !== null)
        .map(([label, value]) => (
          <div key={label} className="contents">
            <dt className="whitespace-nowrap" style={{ color: 'var(--muted)' }}>{label}</dt>
            <dd className="min-w-0 break-words" style={{ color: 'var(--text)' }}>{value}</dd>
          </div>
        ))}
    </dl>
  )
}
