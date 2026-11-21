export function SectionDivider({ label }: { label: string }) {
  return (
    <div className="flex items-center gap-4 py-1">
      <div className="h-px flex-1" style={{ background: 'var(--border)' }} />
      <span
        className="text-[9px] font-semibold uppercase tracking-[3px]"
        style={{ color: 'var(--muted)' }}
      >
        {label}
      </span>
      <div className="h-px flex-1" style={{ background: 'var(--border)' }} />
    </div>
  )
}
