import { useLocation, useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useBlockchainStatus } from '@/hooks/useNodeQuery'
import { sfx, useAudio } from '@/audio'
import { AudioToggle } from './components/AudioToggle'
import { LanguageSwitcher } from './components/LanguageSwitcher'
import { NavItem } from './components/NavItem'
import { StatusStrip } from './components/StatusStrip'
import { ThemeSwitcher } from './components/ThemeSwitcher'

/** The blocks item also covers the transaction and account pages reached from it. */
function isActive(path: string, pathname: string): boolean {
  if (pathname === path) return true
  if (path === '/') return false
  if (pathname.startsWith(`${path}/`)) return true
  return path === '/blocks' && (pathname.startsWith('/tx/') || pathname.startsWith('/accounts/'))
}

export function Topbar() {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const { data: status, isFetching } = useBlockchainStatus()
  const { play } = useAudio()
  const { t } = useTranslation()

  const NAV_ITEMS = [
    { label: t('nav.dashboard'), path: '/' },
    { label: t('nav.network'),   path: '/network' },
    { label: t('nav.blocks'),    path: '/blocks' },
    { label: t('nav.wallet'),    path: '/wallet',  future: true },
  ] as const

  return (
    <header
      className="sticky top-0 z-20 grid grid-cols-[auto_1fr] items-center gap-y-1 px-4 pt-2 sm:px-10 md:px-16 xl:h-[64px] xl:grid-cols-[auto_1fr_auto] xl:px-20 xl:pt-0"
      style={{
        background: 'linear-gradient(180deg, rgba(0,0,0,.08) 0%, transparent 100%)',
        borderBottom: '1px solid var(--border)'
      }}
    >
      {/* Logo — Signum + "Node Dashboard" stacked vertically; clearly separate from nav */}
      <div className="mr-4 flex flex-col justify-center xl:mr-8">
        <span
          className="text-[15px] font-black uppercase leading-none tracking-[3px] md:text-[18px] md:tracking-[5px]"
          style={{
            fontFamily: 'var(--font-display)',
            color: 'var(--text)',
            textShadow: '0 0 16px color-mix(in srgb, var(--blue2) 45%, transparent)',
          }}
        >
          Signum
        </span>
        <span
          className="mt-1.5 text-[8px] font-semibold uppercase leading-none tracking-[3px] md:text-[8px] md:tracking-[4px]"
          style={{
            fontFamily: 'var(--font-display)',
            color: 'var(--blue2)',
            opacity: 0.85,
          }}
        >
          Node {status?.version ? `${status.version}` : ''}{' '}
        </span>
      </div>

      {/* Navigation — below xl its own full-width row that scrolls sideways instead of cutting items off */}
      <nav className="themed-scroll order-last col-span-2 flex h-10 min-w-0 items-stretch overflow-x-auto xl:order-none xl:col-span-1 xl:ml-4 xl:h-full">
        {NAV_ITEMS.map((item) => (
          <NavItem
            key={item.path}
            label={item.label}
            active={isActive(item.path, pathname)}
            future={'future' in item && item.future}
            onClick={() => {
              play(sfx.click)
              void navigate({ to: item.path })
            }}
          />
        ))}
      </nav>

      {/* Right side — audio toggle, theme switcher, then status; wraps on narrow screens (dropdowns must not be clipped) */}
      <div className="flex flex-wrap items-center justify-end gap-2 md:gap-5">
        <AudioToggle />
        <LanguageSwitcher />
        <ThemeSwitcher />
        <StatusStrip
          network={status?.network ?? 'Mainnet'}
          isFetching={isFetching}
        />
      </div>
    </header>
  )
}
