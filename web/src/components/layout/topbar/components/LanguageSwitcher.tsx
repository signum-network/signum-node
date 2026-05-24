import { useEffect, useRef, useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { LANGUAGES } from '@/i18n'
import { sfx, useAudio } from '@/audio'

export function LanguageSwitcher() {
  const { i18n, t } = useTranslation()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const { play } = useAudio()

  const current = LANGUAGES.find((l) => l.code === i18n.language) ?? LANGUAGES[0]

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
        className="flex items-center gap-1.5 px-3 py-1.5 text-[9px] font-semibold uppercase tracking-[2px]"
        style={{ border: '1px solid var(--border)', color: 'var(--muted)' }}
        whileHover={{ borderColor: 'var(--border2)', color: 'var(--blue2)' }}
        whileTap={{ scale: 0.96 }}
        title={t('common.selectLanguage')}
        onClick={() => {
          setOpen((o) => {
            if (!o) play(sfx.swish)
            return !o
          })
        }}
      >
        <span className="text-[12px] leading-none">{current.flag}</span>
        <span>{current.code.toUpperCase()}</span>
        <Chevron open={open} />
      </motion.button>

      <AnimatePresence>
        {open && (
          <motion.div
            key="lang-panel"
            initial={{ opacity: 0, y: -6, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -6, scale: 0.97 }}
            transition={{ type: 'spring', stiffness: 420, damping: 28 }}
            className="absolute right-0 top-full z-50 mt-2 p-3"
            style={{
              background: 'var(--bg2)',
              border: '1px solid var(--border2)',
              minWidth: '220px',
              boxShadow: '0 16px 48px rgba(0,0,0,.4)',
            }}
          >
            <p className="mb-2 text-[8px] uppercase tracking-[3px]" style={{ color: 'var(--muted)' }}>
              {t('common.selectLanguage')}
            </p>
            <div className="grid grid-cols-2 gap-1.5">
              {LANGUAGES.map((lang) => (
                <motion.button
                  key={lang.code}
                  type="button"
                  className="flex items-center gap-2 px-2 py-1.5 text-left text-[10px]"
                  style={{
                    border: `1px solid ${lang.code === i18n.language ? 'var(--blue2)' : 'rgba(255,255,255,.06)'}`,
                    background: lang.code === i18n.language ? 'rgba(0,170,255,.08)' : 'rgba(255,255,255,.02)',
                    color: lang.code === i18n.language ? 'var(--blue2)' : 'var(--muted)',
                  }}
                  whileHover={{ borderColor: 'var(--blue2)', color: 'var(--blue2)' }}
                  whileTap={{ scale: 0.97 }}
                  onClick={() => {
                    play(sfx.tick)
                    void i18n.changeLanguage(lang.code)
                    setOpen(false)
                  }}
                >
                  <span className="text-[14px] leading-none">{lang.flag}</span>
                  <span className="truncate">{lang.label}</span>
                </motion.button>
              ))}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
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
