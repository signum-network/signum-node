import type { ForkEvent } from '@/lib/nodeApi'

const PAD_X = 50
const AXIS_Y = 150
const MAX_ARCH_H = 110
const MIN_ARCH_H = 18

export function archHeight(depth: number): number {
  return MIN_ARCH_H + Math.min(depth / 6, 1) * (MAX_ARCH_H - MIN_ARCH_H)
}

export interface ArchLayout {
  leftX: number
  rightX: number
  midX: number
  peakY: number
  peakH: number
  depth: number
  color: string
  fork: ForkEvent
}

export interface BranchLayout {
  arches: ArchLayout[]
  axisY: number
  nowX: number
  minHeight: number
  maxHeight: number
  svgW: number
}

export function buildBranchLayout(
  forks: ForkEvent[],
  myHeight: number,
  svgW: number,
): BranchLayout {
  if (forks.length === 0) {
    return { arches: [], axisY: AXIS_Y, nowX: svgW - PAD_X, minHeight: myHeight, maxHeight: myHeight, svgW }
  }

  const minHeight = Math.min(...forks.map(f => f.rollbackHeight - f.rollbackDepth)) - 5
  const maxHeight = myHeight
  const range = maxHeight - minHeight || 1
  const chartW = svgW - 2 * PAD_X

  const xAt = (h: number) => PAD_X + ((h - minHeight) / range) * chartW

  const arches: ArchLayout[] = forks.map(fork => {
    const leftX = xAt(fork.rollbackHeight - fork.rollbackDepth)
    const rightX = xAt(fork.rollbackHeight)
    const midX = (leftX + rightX) / 2
    const peakH = archHeight(fork.rollbackDepth)
    const color = fork.rollbackDepth >= 3 ? 'var(--red, #ff4444)' : 'var(--gold)'
    return { leftX, rightX, midX, peakY: AXIS_Y - peakH, peakH, depth: fork.rollbackDepth, color, fork }
  })

  return { arches, axisY: AXIS_Y, nowX: xAt(myHeight), minHeight, maxHeight, svgW }
}
