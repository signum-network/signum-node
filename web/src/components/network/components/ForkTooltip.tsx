import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ForkEvent } from '@/lib/nodeApi'

const TOOLTIP_W = 220
const OFFSET_X = 14
const OFFSET_Y = -8
const VIEWPORT_MARGIN = 8

interface Props {
  fork: ForkEvent
  isForking: boolean
  color: string
  x: number
  y: number
}

export function ForkTooltip({ fork, isForking, color, x, y }: Props) {
  const { t } = useTranslation()
  const ref = useRef<HTMLDivElement>(null)
  const [left, setLeft] = useState(x + OFFSET_X)
  const [top, setTop] = useState(y + OFFSET_Y)

  useEffect(() => {
    if (!ref.current) return
    const h = ref.current.offsetHeight
    const winW = window.innerWidth
    const winH = window.innerHeight

    const rawLeft = x + OFFSET_X
    const rawTop = y + OFFSET_Y

    setLeft(rawLeft + TOOLTIP_W + VIEWPORT_MARGIN > winW
      ? x - TOOLTIP_W - OFFSET_X
      : rawLeft)
    setTop(rawTop + h + VIEWPORT_MARGIN > winH
      ? y - h - OFFSET_Y
      : rawTop)
  }, [x, y])

  const depth = fork.rollbackDepth

  return (
    <div
      ref={ref}
      className="pointer-events-none fixed z-50 p-3 text-[10px] leading-relaxed"
      style={{
        left,
        top,
        width: TOOLTIP_W,
        background: 'var(--bg2)',
        border: '1px solid var(--border2)',
        color: 'var(--text)',
        boxShadow: '0 8px 32px rgba(0,0,0,.5)',
      }}
    >
      <div className="flex items-baseline justify-between gap-2 mb-2">
        <span className="text-[11px] font-semibold font-mono" style={{ color }}>
          −{depth} {depth === 1 ? 'block' : 'blocks'}
        </span>
        {isForking && (
          <span
            className="text-[8px] uppercase tracking-[1px] px-1.5 py-0.5 rounded-sm"
            style={{ background: 'color-mix(in srgb, var(--red, #ff4444) 12%, transparent)', color: 'var(--red, #ff4444)', border: '1px solid color-mix(in srgb, var(--red, #ff4444) 30%, transparent)' }}
          >
            still forking
          </span>
        )}
      </div>

      <div className="space-y-1" style={{ color: 'var(--muted)' }}>
        <div className="flex justify-between gap-2">
          <span>{t('common.height')}</span>
          <span className="font-mono" style={{ color: 'var(--text)' }}>{fork.rollbackHeight.toLocaleString()}</span>
        </div>
        <div className="flex justify-between gap-2">
          <span>Detected</span>
          <span className="font-mono" style={{ color: 'var(--text)' }}>{new Date(fork.detectedAt).toLocaleString()}</span>
        </div>
        {fork.peerSource && (
          <div className="flex justify-between gap-2">
            <span>Peer</span>
            <span className="font-mono truncate" style={{ color: 'var(--text)', maxWidth: 120 }}>{fork.peerSource}</span>
          </div>
        )}
      </div>
    </div>
  )
}
