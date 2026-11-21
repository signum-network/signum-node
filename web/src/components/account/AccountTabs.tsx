import { useTranslation } from 'react-i18next'
import { ACCOUNT_TABS, type AccountTab } from './account.utils'

/** Tab bar of the account page; every tab loads its data only when it is opened. */
export function AccountTabs({ active, onSelect }: { active: AccountTab; onSelect: (tab: AccountTab) => void }) {
  const { t } = useTranslation()
  return (
    <div role="tablist" className="themed-scroll flex gap-1 overflow-x-auto" style={{ borderBottom: '1px solid var(--border)' }}>
      {ACCOUNT_TABS.map((tab) => (
        <button
          key={tab}
          type="button"
          role="tab"
          aria-selected={tab === active}
          onClick={() => onSelect(tab)}
          className="whitespace-nowrap px-4 py-2 text-[10px] font-semibold uppercase tracking-[2px] transition-colors"
          style={{
            color: tab === active ? 'var(--blue2)' : 'var(--muted)',
            borderBottom: `2px solid ${tab === active ? 'var(--blue2)' : 'transparent'}`,
          }}
        >
          {t(`accountTabs.${tab}`)}
        </button>
      ))}
    </div>
  )
}
