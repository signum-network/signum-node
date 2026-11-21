import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { ACCOUNT_TABS, nextTabIndex, type AccountTab } from './account.utils'

const KEYS = ['ArrowRight', 'ArrowLeft', 'Home', 'End'] as const

/**
 * Tab bar of the account page (WAI-ARIA tabs: roving tabindex, arrow keys, Home/End). Every tab
 * loads its data only when it is opened; the active tab is kept in view on narrow screens.
 */
export function AccountTabs({ active, onSelect }: { active: AccountTab; onSelect: (tab: AccountTab) => void }) {
  const { t } = useTranslation()
  const buttons = useRef<(HTMLButtonElement | null)[]>([])
  const activeIndex = ACCOUNT_TABS.indexOf(active)

  useEffect(() => {
    buttons.current[activeIndex]?.scrollIntoView({ inline: 'nearest', block: 'nearest' })
  }, [activeIndex])

  const onKeyDown = (event: React.KeyboardEvent) => {
    const key = KEYS.find((k) => k === event.key)
    if (!key) return
    event.preventDefault()
    const next = nextTabIndex(activeIndex, key, ACCOUNT_TABS.length)
    onSelect(ACCOUNT_TABS[next])
    buttons.current[next]?.focus()
  }

  return (
    <div
      role="tablist"
      aria-label={t('account.section')}
      onKeyDown={onKeyDown}
      className="themed-scroll flex gap-1 overflow-x-auto"
      style={{ borderBottom: '1px solid var(--border)' }}
    >
      {ACCOUNT_TABS.map((tab, i) => (
        <button
          key={tab}
          ref={(el) => { buttons.current[i] = el }}
          type="button"
          role="tab"
          id={`account-tab-${tab}`}
          aria-selected={tab === active}
          aria-controls={`account-panel-${tab}`}
          tabIndex={tab === active ? 0 : -1}
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
