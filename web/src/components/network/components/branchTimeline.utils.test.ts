import { describe, it, expect } from 'vitest'
import { buildBranchLayout, archHeight } from './branchTimeline.utils'
import type { ForkEvent } from '@/lib/nodeApi'

function fork(overrides: Partial<ForkEvent>): ForkEvent {
  return {
    detectedAt: Date.now(),
    rollbackHeight: 1000,
    rollbackDepth: 1,
    oldTopBlockId: 'abc',
    newTopBlockId: null,
    peerSource: null,
    ...overrides,
  }
}

describe('archHeight', () => {
  it('returns minimum height for depth 1', () => {
    expect(archHeight(1)).toBeGreaterThan(0)
  })

  it('returns larger height for deeper forks', () => {
    expect(archHeight(4)).toBeGreaterThan(archHeight(1))
  })

  it('caps at MAX_ARCH_H', () => {
    expect(archHeight(100)).toBe(archHeight(10))
  })
})

describe('buildBranchLayout', () => {
  const SVG_W = 600
  const forks = [
    fork({ rollbackHeight: 1050, rollbackDepth: 4, detectedAt: 2000 }),
    fork({ rollbackHeight: 1080, rollbackDepth: 1, detectedAt: 3000 }),
  ]
  const myHeight = 1100
  const layout = buildBranchLayout(forks, myHeight, SVG_W)

  it('returns one arch per fork', () => {
    expect(layout.arches).toHaveLength(2)
  })

  it('rightX is always > leftX', () => {
    for (const arch of layout.arches) {
      expect(arch.rightX).toBeGreaterThan(arch.leftX)
    }
  })

  it('nowX equals SVG_W minus padding', () => {
    expect(layout.nowX).toBeCloseTo(SVG_W - 50, 0)
  })

  it('deeper fork has taller peak', () => {
    const deep = layout.arches.find(a => a.depth === 4)!
    const shallow = layout.arches.find(a => a.depth === 1)!
    expect(deep.peakH).toBeGreaterThan(shallow.peakH)
  })
})
