import type { ForkEvent } from '@/lib/nodeApi'

const INNER_R = 45
const OUTER_R = 105

export function depthToRadius(depth: number): number {
  const t = Math.min(Math.max(depth - 1, 0), 5) / 5
  return INNER_R + t * (OUTER_R - INNER_R)
}

export interface RadialNode {
  x: number
  y: number
  nodeR: number
  color: string
  fork: ForkEvent
  angle: number
  orbitR: number
}

export interface RadialLayout {
  nodes: RadialNode[]
}

export function buildRadialLayout(
  forks: ForkEvent[],
  cx: number,
  cy: number,
): RadialLayout {
  if (forks.length === 0) return { nodes: [] }

  const sorted = [...forks].sort((a, b) => b.detectedAt - a.detectedAt)
  const n = sorted.length

  const nodes: RadialNode[] = sorted.map((fork, i) => {
    const angleDeg = -90 + (i / n) * 360
    const angleRad = (angleDeg * Math.PI) / 180
    const orbitR = depthToRadius(fork.rollbackDepth)
    const x = cx + orbitR * Math.cos(angleRad)
    const y = cy + orbitR * Math.sin(angleRad)
    const nodeR = 6 + Math.min(fork.rollbackDepth, 6)
    const color = fork.rollbackDepth >= 3 ? 'var(--red, #ff4444)' : 'var(--gold)'
    return { x, y, nodeR, color, fork, angle: angleRad, orbitR }
  })

  return { nodes }
}
