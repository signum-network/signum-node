import { useState } from 'react'
import { AnimatePresence } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import type { ForkEvent } from '@/lib/nodeApi'
import { buildRadialLayout } from './radialForkMap.utils'
import { ForkPointModal } from './ForkPointModal'

const SVG_SIZE = 300
const CX = 150
const CY = 150

interface Props {
  forks: ForkEvent[]
  myHeight: number
  forkingPeerAddresses: Set<string>
}

export function RadialForkMap({ forks, myHeight, forkingPeerAddresses }: Props) {
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

  const { nodes } = buildRadialLayout(forks, CX, CY)

  return (
    <>
      <div className="flex flex-col items-center gap-2">
        <svg
          width={SVG_SIZE}
          height={SVG_SIZE}
          viewBox={`0 0 ${SVG_SIZE} ${SVG_SIZE}`}
          style={{ display: 'block' }}
        >
          {/* orbit rings */}
          <circle cx={CX} cy={CY} r={45}  stroke="var(--border2)" strokeWidth={1} strokeDasharray="3 5" fill="none" />
          <circle cx={CX} cy={CY} r={105} stroke="var(--border2)" strokeWidth={1} strokeDasharray="2 6" fill="none" opacity={0.5} />

          {/* center node */}
          <circle cx={CX} cy={CY} r={18} fill="color-mix(in srgb, var(--blue) 25%, transparent)" stroke="var(--blue2)" strokeWidth={1.8} />
          <text x={CX} y={CY - 3} fill="var(--blue2)" fontSize={7} textAnchor="middle" fontFamily="monospace">
            {Math.floor(myHeight / 1000)}K
          </text>
          <text x={CX} y={CY + 7} fill="var(--blue2)" fontSize={6} textAnchor="middle" letterSpacing={1}>
            NOW
          </text>

          {nodes.map((node, i) => {
            const isHovered = hovered === i
            const isClickable =
              node.fork.peerSource != null && forkingPeerAddresses.has(node.fork.peerSource)
            const opacity = hovered !== null && !isHovered ? 0.3 : 1

            return (
              <g
                key={i}
                style={{ opacity, cursor: isClickable ? 'pointer' : 'default' }}
                onMouseEnter={() => setHovered(i)}
                onMouseLeave={() => setHovered(null)}
                onClick={() => {
                  if (isClickable && node.fork.peerSource) setForkModalPeer(node.fork.peerSource)
                }}
              >
                {/* spoke */}
                <line
                  x1={CX} y1={CY} x2={node.x} y2={node.y}
                  stroke={node.color}
                  strokeWidth={1}
                  strokeDasharray="3 3"
                  opacity={0.4}
                />
                {/* node circle */}
                <circle
                  cx={node.x} cy={node.y}
                  r={isHovered ? node.nodeR + 2 : node.nodeR}
                  fill={`color-mix(in srgb, ${node.color} 18%, transparent)`}
                  stroke={node.color}
                  strokeWidth={isHovered ? 2 : 1.5}
                />
                {/* depth label */}
                <text
                  x={node.x} y={node.y + 3.5}
                  fill={node.color}
                  fontSize={8}
                  textAnchor="middle"
                  fontFamily="monospace"
                  fontWeight={isHovered ? 700 : 400}
                >
                  {`−${node.fork.rollbackDepth}`}
                </text>
                {/* height label offset away from center — shown on hover */}
                {isHovered && (
                  <text
                    x={node.x + (node.x > CX ? node.nodeR + 4 : -(node.nodeR + 4))}
                    y={node.y + 3}
                    fill="var(--muted)"
                    fontSize={7}
                    textAnchor={node.x > CX ? 'start' : 'end'}
                    fontFamily="monospace"
                  >
                    {node.fork.rollbackHeight.toLocaleString()}
                  </text>
                )}
              </g>
            )
          })}
        </svg>

        <p className="text-[8px] tracking-[1px]" style={{ color: 'var(--muted)' }}>
          distance = rollback depth · angle = recency
        </p>
      </div>

      <AnimatePresence>
        {forkModalPeer && (
          <ForkPointModal peer={forkModalPeer} onClose={() => setForkModalPeer(null)} />
        )}
      </AnimatePresence>
    </>
  )
}
