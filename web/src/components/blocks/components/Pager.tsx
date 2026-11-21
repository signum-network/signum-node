import { useTranslation } from 'react-i18next'

interface PagerProps {
  page: number
  hasOlder: boolean
  onChange: (page: number) => void
}

function PagerButton({ label, disabled, onClick }: { label: string; disabled: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className="rounded-sm px-3 py-1 text-[10px] uppercase tracking-[1px] transition-opacity"
      style={{
        border: '1px solid var(--border2, var(--border))',
        color: disabled ? 'var(--muted)' : 'var(--blue2)',
        opacity: disabled ? 0.4 : 1,
        cursor: disabled ? 'not-allowed' : 'pointer',
      }}
    >
      {label}
    </button>
  )
}

/** Newer / older paging; page 0 holds the newest entries. */
export function Pager({ page, hasOlder, onChange }: PagerProps) {
  const { t } = useTranslation()
  return (
    <div className="flex items-center justify-between gap-3">
      <PagerButton label={t('blocks.newer')} disabled={page === 0} onClick={() => onChange(page - 1)} />
      <span className="text-[10px] tabular-nums" style={{ color: 'var(--muted)' }}>
        {t('blocks.page', { page: page + 1 })}
      </span>
      <PagerButton label={t('blocks.older')} disabled={!hasOlder} onClick={() => onChange(page + 1)} />
    </div>
  )
}
