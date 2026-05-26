import { describe, it, expect } from 'vitest'
import { buildRadialLayout, depthToRadius } from './radialForkMap.utils'
import type { ForkEvent } from '@/lib/nodeApi'

function fork(overrides: Partial<ForkEvent>): ForkEvent {
  return {
    detectedAt: 1000,
    rollbackHeight: 1000,
    rollbackDepth: 1,
    oldTopBlockId: 'abc',
    newTopBlockId: null,
    peerSource: null,
    ...overrides,
  }
}

describe('depthToRadius', () => {
  it('depth 1 maps to INNER_R', () => {
    expect(depthToRadius(1)).toBe(45)
  })

  it('depth 6+ maps to OUTER_R', () => {
    expect(depthToRadius(6)).toBe(105)
    expect(depthToRadius(100)).toBe(105)
  })

  it('depth 3 is between inner and outer', () => {
    const r = depthToRadius(3)
    expect(r).toBeGreaterThan(45)
    expect(r).toBeLessThan(105)
  })
})

describe('buildRadialLayout', () => {
  const forks = [
    fork({ rollbackDepth: 4, detectedAt: 3000 }),
    fork({ rollbackDepth: 1, detectedAt: 2000 }),
    fork({ rollbackDepth: 7, detectedAt: 1000 }),
  ]
  const layout = buildRadialLayout(forks, 150, 150)

  it('returns one node per fork', () => {
    expect(layout.nodes).toHaveLength(3)
  })

  it('most recent fork gets first angle slot (closest to 12 o\'clock)', () => {
    // sorted by detectedAt desc: 3000, 2000, 1000
    // first node is the most recent (detectedAt: 3000)
    const first = layout.nodes[0]
    // angle at index 0 with 3 items: -90 + 0*(360/3) = -90 degrees
    // y should be center - radius (above center)
    expect(first.y).toBeLessThan(150)
  })

  it('deeper fork has larger node radius', () => {
    const deep = layout.nodes.find(n => n.fork.rollbackDepth === 7)!
    const shallow = layout.nodes.find(n => n.fork.rollbackDepth === 1)!
    expect(deep.nodeR).toBeGreaterThan(shallow.nodeR)
  })
})
