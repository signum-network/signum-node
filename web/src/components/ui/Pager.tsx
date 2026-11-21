import { useTranslation } from 'react-i18next'

interface PagerProps {
  /** 1-based page number shown between the buttons */
  page: number
  hasNewer: boolean
  hasOlder: boolean
  onNewer: () => void
  onOlder: () => void
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

/** Newer / older paging. */
export function Pager({ page, hasNewer, hasOlder, onNewer, onOlder }: PagerProps) {
  const { t } = useTranslation()
  return (
    <div className="flex items-center justify-between gap-3">
      <PagerButton label={t('blocks.newer')} disabled={!hasNewer} onClick={onNewer} />
      <span className="text-[10px] tabular-nums" style={{ color: 'var(--muted)' }}>
        {t('blocks.page', { page })}
      </span>
      <PagerButton label={t('blocks.older')} disabled={!hasOlder} onClick={onOlder} />
    </div>
  )
}
