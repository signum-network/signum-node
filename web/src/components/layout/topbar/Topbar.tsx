import { useLocation, useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useBlockchainStatus } from '@/hooks/useNodeQuery'
import { sfx, useAudio } from '@/audio'
import { AudioToggle } from './components/AudioToggle'
import { LanguageSwitcher } from './components/LanguageSwitcher'
import { NavItem } from './components/NavItem'
import { StatusStrip } from './components/StatusStrip'
import { ThemeSwitcher } from './components/ThemeSwitcher'

export function Topbar() {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const { data: status, isFetching } = useBlockchainStatus()
  const { play } = useAudio()
  const { t } = useTranslation()

  const NAV_ITEMS = [
    { label: t('nav.dashboard'), path: '/' },
    { label: t('nav.network'),   path: '/network' },
    { label: t('nav.blocks'),    path: '/blocks',  future: true },
    { label: t('nav.wallet'),    path: '/wallet',  future: true },
  ] as const

  return (
    <header
      className="sticky top-0 z-20 grid h-[64px] grid-cols-[auto_1fr_auto] items-center px-10 md:px-16 lg:px-20"
      style={{
        background: 'linear-gradient(180deg, rgba(0,0,0,.08) 0%, transparent 100%)',
        borderBottom: '1px solid var(--border)'
      }}
    >
      {/* Logo — Signum + "Node Dashboard" stacked vertically; clearly separate from nav */}
      <div className="mr-16 flex flex-col justify-center md:mr-24 lg:mr-28">
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

      {/* Navigation — clean rectangular tabs with gap, no clip-path cutting characters */}
      <nav className="flex h-full min-w-0 items-stretch overflow-hidden ml-4">
        {NAV_ITEMS.map((item) => (
          <NavItem
            key={item.path}
            label={item.label}
            active={pathname === item.path}
            future={'future' in item && item.future}
            onClick={() => {
              play(sfx.click)
              void navigate({ to: item.path })
            }}
          />
        ))}
      </nav>

      {/* Right side — audio toggle, theme switcher, then status */}
      <div className="flex items-center gap-3 md:gap-5">
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
