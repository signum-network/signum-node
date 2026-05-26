import { useState } from 'react'
import { AnimatePresence } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import type { ForkEvent } from '@/lib/nodeApi'
import { buildBranchLayout } from './branchTimeline.utils'
import { ForkPointModal } from './ForkPointModal'

const SVG_W = 640
const SVG_H = 200
const AXIS_START_X = 30

interface Props {
  forks: ForkEvent[]
  myHeight: number
  forkingPeerAddresses: Set<string>
}

export function BranchTimeline({ forks, myHeight, forkingPeerAddresses }: Props) {
  const { t } = useTranslation()
  const [hovered, setHovered] = useState<number | null>(null)
  const [forkModalPeer, setForkModalPeer] = useState<string | null>(null)

  if (forks.length === 0) {
    return (
      <p className="py-4 text-center text-[11px]" style={{ color: 'var(--muted)' }}>
        {t('network.noReorgs')}
      </p>
    )
  }

  const layout = buildBranchLayout(forks, myHeight, SVG_W)
  const { arches, axisY, nowX } = layout

  return (
    <>
      <div className="themed-scroll overflow-x-auto">
        <svg
          width={SVG_W}
          height={SVG_H}
          viewBox={`0 0 ${SVG_W} ${SVG_H}`}
          style={{ display: 'block', minWidth: SVG_W }}
        >
          {/* main chain axis */}
          <line x1={AXIS_START_X} y1={axisY} x2={nowX} y2={axisY} stroke="var(--blue2)" strokeWidth={2} />
          <circle cx={AXIS_START_X} cy={axisY} r={3} fill="var(--blue2)" />
          <circle cx={nowX} cy={axisY} r={3} fill="var(--blue2)" />

          {/* NOW label */}
          <text x={nowX} y={axisY - 8} fill="var(--blue2)" fontSize={8} textAnchor="middle" letterSpacing={1}>
            NOW
          </text>
          <text x={nowX} y={axisY - 18} fill="var(--blue2)" fontSize={7} textAnchor="middle" fontFamily="monospace">
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
                onMouseEnter={() => setHovered(i)}
                onMouseLeave={() => setHovered(null)}
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

                {/* depth label at peak */}
                <text
                  x={arch.midX}
                  y={arch.peakY - 6}
                  fill={arch.color}
                  fontSize={9}
                  textAnchor="middle"
                  fontFamily="monospace"
                  fontWeight={isHovered ? 700 : 400}
                >
                  {`−${arch.depth}`}
                </text>

                {/* height label below axis */}
                <text x={arch.rightX} y={axisY + 14} fill={arch.color} fontSize={7} textAnchor="middle" fontFamily="monospace">
                  {arch.fork.rollbackHeight.toLocaleString()}
                </text>

                {/* timestamp + peer below height */}
                <text x={arch.rightX} y={axisY + 24} fill="var(--muted)" fontSize={7} textAnchor="middle">
                  {new Date(arch.fork.detectedAt).toLocaleTimeString()}
                </text>

                {arch.fork.peerSource && (
                  <text x={arch.rightX} y={axisY + 34} fill="var(--muted)" fontSize={6.5} textAnchor="middle" fontFamily="monospace">
                    {arch.fork.peerSource.length > 15
                      ? `${arch.fork.peerSource.slice(0, 15)}…`
                      : arch.fork.peerSource}
                  </text>
                )}

                {/* FORKING PEER badge when peer is still active */}
                {isClickable && (
                  <text x={arch.rightX} y={axisY + 46} fill="var(--red, #ff4444)" fontSize={6.5} textAnchor="middle" letterSpacing={0.5} opacity={0.8}>
                    FORKING PEER ↗
                  </text>
                )}
              </g>
            )
          })}
        </svg>
      </div>

      <AnimatePresence>
        {forkModalPeer && (
          <ForkPointModal peer={forkModalPeer} onClose={() => setForkModalPeer(null)} />
        )}
      </AnimatePresence>
    </>
  )
}
