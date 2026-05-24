import { useState, useRef, useEffect } from 'react'

interface InfoTooltipProps {
  text: string
}

export function InfoTooltip({ text }: InfoTooltipProps) {
  const [visible, setVisible] = useState(false)
  const [above, setAbove] = useState(false)
  const ref = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    if (!visible || !ref.current) return
    const rect = ref.current.getBoundingClientRect()
    setAbove(rect.bottom + 160 > window.innerHeight)
  }, [visible])

  return (
    <span
      ref={ref}
      className="relative inline-flex items-center"
      onMouseEnter={() => setVisible(true)}
      onMouseLeave={() => setVisible(false)}
      onFocus={() => setVisible(true)}
      onBlur={() => setVisible(false)}
    >
      <span
        tabIndex={0}
        role="button"
        aria-label="More information"
        className="inline-flex h-[14px] w-[14px] cursor-default items-center justify-center rounded-full text-[8px] font-bold leading-none select-none"
        style={{
          border: '1px solid var(--muted)',
          color: 'var(--muted)',
          opacity: 0.6,
          transition: 'opacity 0.12s, border-color 0.12s, color 0.12s',
          ...(visible ? { opacity: 1, borderColor: 'var(--blue2)', color: 'var(--blue2)' } : {}),
        }}
      >
        i
      </span>

      {visible && (
        <span
          className="pointer-events-none absolute z-50 w-[240px] p-3 text-[10px] leading-relaxed"
          style={{
            background: 'var(--bg2)',
            border: '1px solid var(--border2)',
            color: 'var(--text)',
            boxShadow: '0 8px 32px rgba(0,0,0,.5)',
            left: '50%',
            transform: 'translateX(-50%)',
            ...(above ? { bottom: 'calc(100% + 6px)' } : { top: 'calc(100% + 6px)' }),
          }}
        >
          {text}
        </span>
      )}
    </span>
  )
}
