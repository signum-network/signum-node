import { describe, it, expect } from 'vitest'
import { reachability, diskTone, fmtBytes, uptimeParts, nodeNow, REACH_WINDOW_MS } from './nodeHealth.utils'
import type { NodeHealth } from '@/lib/nodeApi'

const NOW = 10_000_000_000
const GIB = 1024 ** 3

function health(over: Partial<NodeHealth> = {}): NodeHealth {
  return {
    startedAt: NOW - 2 * REACH_WINDOW_MS,
    databaseType: 'sqlite',
    databaseSizeBytes: 1,
    freeDiskBytes: 100 * GIB,
    freeDiskScope: 'database',
    lastInboundPeerAt: null,
    lastLocalInboundPeerAt: null,
    peerServerRunning: true,
    announcesAddress: true,
    serverTime: NOW,
    ...over,
  }
}

describe('reachability', () => {
  it('reports the P2P server off before anything else', () => {
    expect(reachability(health({ peerServerRunning: false, lastInboundPeerAt: NOW }), NOW)).toBe('off')
  })

  it('is reachable after public contact within 30 min', () => {
    expect(reachability(health({ lastInboundPeerAt: NOW - REACH_WINDOW_MS + 1 }), NOW)).toBe('reachable')
  })

  it('is not reachable when the contact is exactly 30 min old', () => {
    expect(reachability(health({ lastInboundPeerAt: NOW - REACH_WINDOW_MS }), NOW)).toBe('noContact')
  })

  it('reports local-only contact', () => {
    expect(reachability(health({ lastLocalInboundPeerAt: NOW - 1000 }), NOW)).toBe('localOnly')
  })

  it('waits during the first 30 min', () => {
    expect(reachability(health({ startedAt: NOW - 1000, announcesAddress: false }), NOW)).toBe('waiting')
  })

  it('names a missing public address before blaming the port', () => {
    expect(reachability(health({ announcesAddress: false }), NOW)).toBe('noAddress')
  })

  it('reports no contact otherwise', () => {
    expect(reachability(health(), NOW)).toBe('noContact')
  })
})

describe('diskTone', () => {
  it('grades free space', () => {
    expect(diskTone(null)).toBe('unknown')
    expect(diskTone(9.9 * GIB)).toBe('warn')
    expect(diskTone(1.9 * GIB)).toBe('bad')
    expect(diskTone(10 * GIB)).toBe('ok')
  })
})

describe('fmtBytes', () => {
  it('uses binary units with one decimal', () => {
    expect(fmtBytes(1536, 'en')).toBe('1.5 KB')
    expect(fmtBytes(19_542_101_000, 'en')).toBe('18.2 GB')
    expect(fmtBytes(512, 'en')).toBe('512 B')
  })
})

describe('uptimeParts', () => {
  it('splits into days, hours and minutes', () => {
    expect(uptimeParts(3 * 86_400_000 + 4 * 3_600_000 + 5 * 60_000)).toEqual({ days: 3, hours: 4, minutes: 5 })
    expect(uptimeParts(-5)).toEqual({ days: 0, hours: 0, minutes: 0 })
  })
})

describe('nodeNow', () => {
  it('moves the node time on by the time passed in the browser', () => {
    expect(nodeNow(1000, 5000, 6500)).toBe(2500)
  })
})
