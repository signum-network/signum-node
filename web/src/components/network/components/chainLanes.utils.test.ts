import { describe, it, expect } from 'vitest'
import { groupIntoLanes, cumulDiffRatio } from './chainLanes.utils'
import type { PeerStatusEntry } from '@/lib/nodeApi'

function peer(overrides: Partial<PeerStatusEntry>): PeerStatusEntry {
  return {
    address: '1.2.3.4',
    cumulativeDifficulty: '1000',
    height: 100,
    onOurChain: true,
    status: 'on-chain',
    connectionFailures: 0,
    blacklisted: false,
    ...overrides,
  }
}

describe('groupIntoLanes', () => {
  it('returns single our-chain lane when no forking peers', () => {
    const peers = [
      peer({ address: 'a', onOurChain: true, status: 'on-chain', height: 100 }),
      peer({ address: 'b', onOurChain: true, status: 'stale', height: 99 }),
    ]
    const lanes = groupIntoLanes(peers)
    expect(lanes).toHaveLength(1)
    expect(lanes[0].isOurChain).toBe(true)
    expect(lanes[0].peers).toHaveLength(2)
  })

  it('groups forking peers by exact cumulativeDifficulty', () => {
    const peers = [
      peer({ address: 'a', onOurChain: true, status: 'on-chain', cumulativeDifficulty: '9000' }),
      peer({ address: 'b', onOurChain: false, status: 'forking', cumulativeDifficulty: '500', height: 90 }),
      peer({ address: 'c', onOurChain: false, status: 'forking', cumulativeDifficulty: '500', height: 91 }),
      peer({ address: 'd', onOurChain: false, status: 'forking', cumulativeDifficulty: '300', height: 80 }),
    ]
    const lanes = groupIntoLanes(peers)
    expect(lanes).toHaveLength(3)
    expect(lanes[0].isOurChain).toBe(true)
    expect(lanes[1].peers).toHaveLength(2) // larger fork group first
    expect(lanes[2].peers).toHaveLength(1)
  })

  it('excludes blacklisted peers from fork lanes', () => {
    const peers = [
      peer({ address: 'a', onOurChain: true, status: 'on-chain' }),
      peer({ address: 'b', onOurChain: false, status: 'forking', blacklisted: true, cumulativeDifficulty: '500' }),
    ]
    const lanes = groupIntoLanes(peers)
    expect(lanes).toHaveLength(1)
    expect(lanes[0].isOurChain).toBe(true)
  })

  it('computes avgHeight for each lane', () => {
    const peers = [
      peer({ address: 'a', onOurChain: true, status: 'on-chain', height: 100 }),
      peer({ address: 'b', onOurChain: true, status: 'stale', height: 98 }),
      peer({ address: 'c', onOurChain: false, status: 'forking', height: 80, cumulativeDifficulty: '500' }),
    ]
    const lanes = groupIntoLanes(peers)
    expect(lanes[0].avgHeight).toBe(99)
    expect(lanes[1].avgHeight).toBe(80)
  })

  it('fork lane index starts at 1', () => {
    const peers = [
      peer({ address: 'a', onOurChain: true, status: 'on-chain' }),
      peer({ address: 'b', onOurChain: false, status: 'forking', cumulativeDifficulty: '500' }),
    ]
    const lanes = groupIntoLanes(peers)
    expect(lanes[0].index).toBe(0)
    expect(lanes[1].index).toBe(1)
  })
})

describe('cumulDiffRatio', () => {
  it('returns 1 for the highest difficulty lane', () => {
    const lanes = [
      { cumulativeDifficulty: '1000', isOurChain: true, peers: [], avgHeight: 0, index: 0 },
      { cumulativeDifficulty: '500',  isOurChain: false, peers: [], avgHeight: 0, index: 1 },
    ]
    expect(cumulDiffRatio(lanes[0], lanes)).toBe(1)
  })

  it('returns correct ratio for lower difficulty lane', () => {
    const lanes = [
      { cumulativeDifficulty: '1000', isOurChain: true, peers: [], avgHeight: 0, index: 0 },
      { cumulativeDifficulty: '500',  isOurChain: false, peers: [], avgHeight: 0, index: 1 },
    ]
    expect(cumulDiffRatio(lanes[1], lanes)).toBe(0.5)
  })

  it('returns 0 for empty cumulativeDifficulty', () => {
    const lanes = [
      { cumulativeDifficulty: '', isOurChain: true, peers: [], avgHeight: 0, index: 0 },
    ]
    expect(cumulDiffRatio(lanes[0], lanes)).toBe(0)
  })
})
