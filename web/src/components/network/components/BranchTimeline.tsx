import { useState, useEffect, useRef } from 'react'
import { AnimatePresence } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import type { ForkEvent } from '@/lib/nodeApi'
import { buildBranchLayout } from './branchTimeline.utils'
import { ForkPointModal } from './ForkPointModal'
import { ForkTooltip } from './ForkTooltip'

const SVG_H = 230
const AXIS_START_X = 30
const MIN_SVG_W = 400

interface Props {
  forks: ForkEvent[]
  myHeight: number
  forkingPeerAddresses: Set<string>
}

export function BranchTimeline({ forks, myHeight, forkingPeerAddresses }: Props) {
  const { t } = useTranslation()
  const [hovered, setHovered] = useState<number | null>(null)
  const [forkModalPeer, setForkModalPeer] = useState<string | null>(null)
  const [tooltipPos, setTooltipPos] = useState<{ x: number; y: number } | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const [svgW, setSvgW] = useState(MIN_SVG_W)

  useEffect(() => {
    const el = containerRef.current
    if (!el) return
    const ro = new ResizeObserver(entries => {
      const w = entries[0].contentRect.width
      if (w > 0) setSvgW(Math.max(w, MIN_SVG_W))
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  if (forks.length === 0) {
    return (
      <p className="py-4 text-center text-[11px]" style={{ color: 'var(--muted)' }}>
        {t('network.noReorgs')}
      </p>
    )
  }

  const layout = buildBranchLayout(forks, myHeight, svgW)
  const { arches, axisY, nowX } = layout
  const hoveredArch = hovered !== null ? arches[hovered] : null

  return (
    <>
      <div ref={containerRef} className="w-full overflow-x-auto themed-scroll">
        <svg
          width="100%"
          height={SVG_H}
          viewBox={`0 0 ${svgW} ${SVG_H}`}
          style={{ display: 'block', minWidth: MIN_SVG_W }}
        >
          {/* main chain axis */}
          <line x1={AXIS_START_X} y1={axisY} x2={nowX} y2={axisY} stroke="var(--blue2)" strokeWidth={2} />
          <circle cx={AXIS_START_X} cy={axisY} r={3} fill="var(--blue2)" />
          <circle cx={nowX} cy={axisY} r={3} fill="var(--blue2)" />

          {/* NOW label */}
          <text x={nowX} y={axisY - 10} fill="var(--blue2)" fontSize={10} textAnchor="middle" letterSpacing={1} fontWeight={600}>
            NOW
          </text>
          <text x={nowX} y={axisY - 22} fill="var(--blue2)" fontSize={9} textAnchor="middle" fontFamily="monospace">
            {myHeight.toLocaleString()}
          </text>

          {arches.map((arch, i) => {
            const isHovered = hovered === i
            const isClickable =
              arch.fork.peerSource != null && forkingPeerAddresses.has(arch.fork.peerSource)
            const opacity = hovered !== null && !isHovered ? 0.35 : 1

            return (
              <g
                key={i}
                style={{ opacity, cursor: isClickable ? 'pointer' : 'default' }}
                onMouseEnter={(e) => { setHovered(i); setTooltipPos({ x: e.clientX, y: e.clientY }) }}
                onMouseMove={(e) => setTooltipPos({ x: e.clientX, y: e.clientY })}
                onMouseLeave={() => { setHovered(null); setTooltipPos(null) }}
                onClick={() => {
                  if (isClickable && arch.fork.peerSource) setForkModalPeer(arch.fork.peerSource)
                }}
              >
                {/* arch */}
                <path
                  d={`M ${arch.leftX} ${axisY} Q ${arch.midX} ${arch.peakY} ${arch.rightX} ${axisY}`}
                  stroke={arch.color}
                  strokeWidth={isHovered ? 2 : 1.5}
                  strokeDasharray="5 3"
                  fill="none"
                  opacity={0.85}
                />
                {/* right foot dot (fork detection point) */}
                <circle cx={arch.rightX} cy={axisY} r={isHovered ? 5.5 : 4} fill={arch.color} opacity={0.9} />

                {/* depth label at peak — background rect for contrast */}
                <rect
                  x={arch.midX - 14}
                  y={arch.peakY - 22}
                  width={28}
                  height={16}
                  rx={3}
                  fill="var(--bg2)"
                  opacity={0.75}
                />
                <text
                  x={arch.midX}
                  y={arch.peakY - 10}
                  fill={arch.color}
                  fontSize={11}
                  textAnchor="middle"
                  fontFamily="monospace"
                  fontWeight={isHovered ? 700 : 600}
                >
                  {`−${arch.depth}`}
                </text>

                {/* height label below axis */}
                <text x={arch.rightX} y={axisY + 16} fill={arch.color} fontSize={9} textAnchor="middle" fontFamily="monospace" fontWeight={600}>
                  {arch.fork.rollbackHeight.toLocaleString()}
                </text>

                {/* timestamp + peer below height */}
                <text x={arch.rightX} y={axisY + 29} fill="var(--muted)" fontSize={8} textAnchor="middle">
                  {new Date(arch.fork.detectedAt).toLocaleTimeString()}
                </text>

                {arch.fork.peerSource && (
                  <text x={arch.rightX} y={axisY + 41} fill="var(--muted)" fontSize={8} textAnchor="middle" fontFamily="monospace">
                    {arch.fork.peerSource.length > 15
                      ? `${arch.fork.peerSource.slice(0, 15)}…`
                      : arch.fork.peerSource}
                  </text>
                )}

                {/* FORKING PEER badge when peer is still active */}
                {isClickable && (
                  <text x={arch.rightX} y={axisY + 55} fill="var(--red, #ff4444)" fontSize={7} textAnchor="middle" letterSpacing={0.5} opacity={0.85} fontWeight={600}>
                    FORKING PEER ↗
                  </text>
                )}
              </g>
            )
          })}
        </svg>
      </div>

      {hoveredArch && tooltipPos && (
        <ForkTooltip
          fork={hoveredArch.fork}
          isForking={hoveredArch.fork.peerSource != null && forkingPeerAddresses.has(hoveredArch.fork.peerSource)}
          color={hoveredArch.color}
          x={tooltipPos.x}
          y={tooltipPos.y}
        />
      )}

      <AnimatePresence>
        {forkModalPeer && (
          <ForkPointModal peer={forkModalPeer} onClose={() => setForkModalPeer(null)} />
        )}
      </AnimatePresence>
    </>
  )
}
