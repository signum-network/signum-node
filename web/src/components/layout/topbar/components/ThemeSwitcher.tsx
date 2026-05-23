import { useEffect, useRef, useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { useTheme } from '@/theme/ThemeProvider'
import { THEMES, type ThemeMeta } from '@/theme/themes'
import { sfx, useAudio } from '@/audio'

export function ThemeSwitcher() {
  const { theme, setTheme } = useTheme()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const current = THEMES.find((t) => t.id === theme)!
  const { play } = useAudio()

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  return (
    <div ref={ref} className="relative">
      <motion.button
        type="button"
        className="flex items-center gap-2 px-3! py-1.5! text-[9px] font-semibold uppercase tracking-[2px]"
        style={{ border: '1px solid var(--border)', color: 'var(--muted)' }}
        whileHover={{ borderColor: 'var(--border2)', color: 'var(--blue2)' }}
        whileTap={{ scale: 0.96 }}
        title="Change theme"
        onClick={() => {
          setOpen((o) => {
            if (!o) play(sfx.swish)
            return !o
          })
        }}
      >
        <ColorDots colors={current.colors} />
        {current.label}
        <Chevron open={open} />
      </motion.button>

      <AnimatePresence>
        {open && (
          <motion.div
            key="theme-panel"
            initial={{ opacity: 0, y: -6, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -6, scale: 0.97 }}
            transition={{ type: 'spring', stiffness: 420, damping: 28 }}
            className="absolute right-0 top-full z-50 mt-2 p-3"
            style={{
              background: 'var(--bg2)',
              border: '1px solid var(--border2)',
              minWidth: '320px',
              boxShadow: '0 16px 48px rgba(0,0,0,.4)',
            }}
          >
            <p
              className="mb-2 text-[8px] uppercase tracking-[3px]"
              style={{ color: 'var(--muted)' }}
            >
              Select Theme
            </p>
            <div className="grid grid-cols-3 gap-2">
              {THEMES.map((t) => (
                <ThemeCard
                  key={t.id}
                  meta={t}
                  active={theme === t.id}
                  onHover={() => play(sfx.tick)}
                  onClick={() => {
                    if (t.id !== theme) play(sfx.themeChange)
                    setTheme(t.id)
                    setOpen(false)
                  }}
                />
              ))}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

function ColorDots({ colors }: { colors: ThemeMeta['colors'] }) {
  return (
    <span className="flex gap-[3px]">
      {[colors.accent, colors.green, colors.mag].map((c, i) => (
        <span key={i} className="h-[5px] w-[5px] rounded-full" style={{ background: c }} />
      ))}
    </span>
  )
}

function Chevron({ open }: { open: boolean }) {
  return (
    <motion.svg
      width="8" height="8" viewBox="0 0 8 8" fill="none"
      animate={{ rotate: open ? 180 : 0 }}
      transition={{ duration: 0.18 }}
    >
      <path d="M1.5 3L4 5.5L6.5 3" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </motion.svg>
  )
}

interface ThemeCardProps {
  meta: ThemeMeta
  active: boolean
  onClick: () => void
  onHover?: () => void
}

function ThemeCard({ meta, active, onClick, onHover }: ThemeCardProps) {
  return (
    <motion.button
      type="button"
      onClick={onClick}
      onHoverStart={onHover}
      className="flex flex-col gap-1.5 p-2 text-left"
      style={{
        border: `1px solid ${active ? meta.colors.accent : 'rgba(255,255,255,.06)'}`,
        background: active ? `${meta.colors.bg}` : 'rgba(255,255,255,.02)',
        boxShadow: active ? `0 0 14px ${meta.colors.accent}38` : 'none',
      }}
      whileHover={{ scale: 1.04, borderColor: meta.colors.accent }}
      whileTap={{ scale: 0.97 }}
      transition={{ type: 'spring', stiffness: 420, damping: 22 }}
    >
      <MiniPreview meta={meta} />
      <span
        className="text-[8px] font-bold uppercase tracking-[2px]"
        style={{ color: meta.colors.text }}
      >
        {meta.label}
      </span>
      <span className="text-[7px] leading-none" style={{ color: meta.colors.muted }}>
        {meta.description}
      </span>
    </motion.button>
  )
}

function MiniPreview({ meta }: { meta: ThemeMeta }) {
  const { accent, bg, green, mag } = meta.colors
  return (
    <div
      className="relative h-[50px] w-full overflow-hidden"
      style={{ background: bg, border: `1px solid ${accent}28` }}
    >
      {/* Fake topbar */}
      <div
        className="absolute left-0 right-0 top-0 flex h-[9px] items-center gap-1 px-1.5"
        style={{ background: `${accent}18`, borderBottom: `1px solid ${accent}28` }}
      >
        {[accent, `${accent}60`, `${accent}40`].map((c, i) => (
          <div key={i} className="h-[2px]" style={{ width: `${8 + i * 6}px`, background: c }} />
        ))}
      </div>
      {/* Mini sparkline */}
      <div className="absolute bottom-1 left-1.5 right-1.5 flex items-end gap-[2px]">
        {[30, 45, 38, 55, 48, 62, 58, 70, 65, 78, 72, 85, 80, 92, 100].map((h, i) => (
          <div
            key={i}
            className="flex-1"
            style={{ height: `${h * 0.28}px`, background: `${accent}${Math.round(28 + i * 1.5).toString(16)}` }}
          />
        ))}
      </div>
      {/* Metric dots */}
      <div className="absolute left-1.5 top-[13px] flex gap-[3px]">
        {[accent, green, mag].map((c, i) => (
          <div key={i} className="h-[4px] w-[4px] rounded-full" style={{ background: c }} />
        ))}
      </div>
    </div>
  )
}
