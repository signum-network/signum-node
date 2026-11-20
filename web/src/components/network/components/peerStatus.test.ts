import { describe, it, expect } from 'vitest'
import { statusColor, statusSortRank, statusLabelKey, formatLatency, secondsAgo } from './peerStatus'

describe('peerStatus', () => {
  it('statusColor maps every status', () => {
    expect(statusColor('on-chain')).toBe('var(--green)')
    expect(statusColor('stale')).toBe('var(--gold)')
    expect(statusColor('ahead')).toBe('var(--blue2)')
    expect(statusColor('forking')).toBe('var(--red, #ff4444)')
    expect(statusColor('unknown')).toBe('var(--muted)')
    expect(statusColor('blacklisted')).toBe('var(--muted)')
  })

  it('statusSortRank puts forking first and on-chain last', () => {
    const sorted = (['on-chain', 'ahead', 'stale', 'unknown', 'forking'] as const)
      .slice()
      .sort((a, b) => statusSortRank(a) - statusSortRank(b))
    expect(sorted).toEqual(['forking', 'unknown', 'stale', 'ahead', 'on-chain'])
  })

  it('statusLabelKey maps to status.* keys', () => {
    expect(statusLabelKey('on-chain')).toBe('status.onChain')
    expect(statusLabelKey('ahead')).toBe('status.ahead')
    expect(statusLabelKey('unknown')).toBe('status.unknown')
    expect(statusLabelKey('blacklisted')).toBe('status.blacklisted')
  })

  it('formatLatency shows a dash for unknown', () => {
    expect(formatLatency(null)).toBe('—')
    expect(formatLatency(42)).toBe('42 ms')
    expect(formatLatency(0)).toBe('0 ms')
  })

  it('secondsAgo measures on the node clock plus time since the fetch', () => {
    // event 30 s before the node answered, fetched 5 s ago (browser clock)
    expect(secondsAgo(70_000, 100_000, 1_000_000, 1_005_000)).toBe(35)
  })

  it('secondsAgo ignores a browser clock that is far off the node clock', () => {
    // browser clock one hour behind the node: the age stays the same
    expect(secondsAgo(70_000, 100_000, 1_000_000 - 3_600_000, 1_005_000 - 3_600_000)).toBe(35)
  })

  it('secondsAgo never goes negative', () => {
    expect(secondsAgo(101_000, 100_000, 1_000_000, 1_000_000)).toBe(0)
  })
})
