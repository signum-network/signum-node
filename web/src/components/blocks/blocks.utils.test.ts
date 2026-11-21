import { describe, it, expect, vi } from 'vitest'
import type { ForkEvent } from '@/lib/nodeApi'
import {
  classifySearch,
  resolveSearch,
  blockDeltaSeconds,
  deltaTone,
  rollbackCovering,
  pageRange,
  hasOlderPage,
  parseHeightParam,
  relativeTime,
} from './blocks.utils'

const TIP = 1_607_462

function fork(overrides: Partial<ForkEvent>): ForkEvent {
  return {
    detectedAt: 1_000,
    rollbackHeight: 100,
    rollbackDepth: 2,
    oldTopBlockId: '1',
    newTopBlockId: null,
    peerSource: null,
    ...overrides,
  }
}

describe('classifySearch', () => {
  it('reads a height up to the tip, trimmed', () => {
    expect(classifySearch(' 1607460 ', TIP)).toEqual({ kind: 'height', height: 1_607_460 })
  })

  it('reads an address case-insensitively', () => {
    expect(classifySearch('s-32uw-qvhd-9cd8-dbfcw', TIP)).toEqual({ kind: 'account', id: 'S-32UW-QVHD-9CD8-DBFCW' })
  })

  it('reads a number above the tip as an ID', () => {
    expect(classifySearch('9606976711898429032', TIP)).toEqual({ kind: 'id', id: '9606976711898429032' })
  })

  it('strips leading zeros from an ID', () => {
    expect(classifySearch('000123', 100)).toEqual({ kind: 'id', id: '123' })
  })

  it('rejects a number above the largest unsigned 64-bit value', () => {
    expect(classifySearch('18446744073709551616', TIP)).toEqual({ kind: 'invalid' })
    expect(classifySearch('18446744073709551615', TIP)).toEqual({ kind: 'id', id: '18446744073709551615' })
  })

  it('rejects anything else', () => {
    expect(classifySearch('abc', TIP)).toEqual({ kind: 'invalid' })
    expect(classifySearch('', TIP)).toEqual({ kind: 'invalid' })
    expect(classifySearch('12 34', TIP)).toEqual({ kind: 'invalid' })
  })
})

describe('resolveSearch', () => {
  function api(found: { block?: { height: number }; tx?: object; account?: object }) {
    return {
      blockById: vi.fn(async () => found.block ?? null),
      transaction: vi.fn(async () => found.tx ?? null),
      account: vi.fn(async () => found.account ?? null),
    }
  }

  it('opens a block found by ID at its height', async () => {
    const a = api({ block: { height: 42 }, tx: {} })
    await expect(resolveSearch({ kind: 'id', id: '7' }, a))
      .resolves.toEqual({ to: '/blocks/$height', params: { height: '42' } })
    expect(a.transaction).not.toHaveBeenCalled()
  })

  it('falls back to a transaction, then an account', async () => {
    await expect(resolveSearch({ kind: 'id', id: '7' }, api({ tx: {} })))
      .resolves.toEqual({ to: '/tx/$id', params: { id: '7' } })
    await expect(resolveSearch({ kind: 'id', id: '7' }, api({ account: {} })))
      .resolves.toEqual({ to: '/accounts/$id', params: { id: '7' } })
  })

  it('returns null when nothing matches', async () => {
    await expect(resolveSearch({ kind: 'id', id: '7' }, api({}))).resolves.toBeNull()
    await expect(resolveSearch({ kind: 'invalid' }, api({}))).resolves.toBeNull()
  })

  it('opens heights and addresses without asking the node', async () => {
    const a = api({})
    await expect(resolveSearch({ kind: 'height', height: 5 }, a))
      .resolves.toEqual({ to: '/blocks/$height', params: { height: '5' } })
    await expect(resolveSearch({ kind: 'account', id: 'S-AAAA-BBBB-CCCC-DDDDD' }, a))
      .resolves.toEqual({ to: '/accounts/$id', params: { id: 'S-AAAA-BBBB-CCCC-DDDDD' } })
    expect(a.blockById).not.toHaveBeenCalled()
    expect(a.transaction).not.toHaveBeenCalled()
    expect(a.account).not.toHaveBeenCalled()
  })
})

describe('block time', () => {
  it('is unknown without a previous block', () => {
    expect(blockDeltaSeconds(1_000, undefined)).toBeNull()
    expect(blockDeltaSeconds(1_240, 1_000)).toBe(240)
  })

  it('is fast below 2:00 and slow above 6:00', () => {
    expect(deltaTone(119)).toBe('fast')
    expect(deltaTone(120)).toBe('normal')
    expect(deltaTone(360)).toBe('normal')
    expect(deltaTone(361)).toBe('slow')
  })
})

describe('rollbackCovering', () => {
  it('covers the replaced heights above the common block', () => {
    const forks = [fork({})]
    expect(rollbackCovering(100, forks)).toBeNull()
    expect(rollbackCovering(101, forks)).toBe(forks[0])
    expect(rollbackCovering(102, forks)).toBe(forks[0])
    expect(rollbackCovering(103, forks)).toBeNull()
  })

  it('prefers the newest of several covering rollbacks', () => {
    const older = fork({ detectedAt: 1_000 })
    const newer = fork({ detectedAt: 2_000, rollbackHeight: 99, rollbackDepth: 3 })
    expect(rollbackCovering(101, [older, newer])).toBe(newer)
  })
})

describe('paging', () => {
  it('counts pages from the newest block', () => {
    expect(pageRange(0)).toEqual({ firstIndex: 0, lastIndex: 24 })
    expect(pageRange(2)).toEqual({ firstIndex: 50, lastIndex: 74 })
  })

  it('has no older page once a page reaches height 0', () => {
    expect(hasOlderPage(0, 30)).toBe(true)
    expect(hasOlderPage(1, 30)).toBe(false)
    expect(hasOlderPage(0, 24)).toBe(false)
  })
})

describe('parseHeightParam', () => {
  it('accepts non-negative integers up to the tip', () => {
    expect(parseHeightParam('0', TIP)).toBe(0)
    expect(parseHeightParam(String(TIP), TIP)).toBe(TIP)
    expect(parseHeightParam('5', undefined)).toBe(5)
  })

  it('rejects anything else', () => {
    expect(parseHeightParam('abc', TIP)).toBeNull()
    expect(parseHeightParam('-1', TIP)).toBeNull()
    expect(parseHeightParam('1.5', TIP)).toBeNull()
    expect(parseHeightParam(String(TIP + 1), TIP)).toBeNull()
  })
})

describe('relativeTime', () => {
  it('picks seconds, minutes, hours or days', () => {
    expect(relativeTime(30, 'en')).toBe('30 seconds ago')
    expect(relativeTime(150, 'en')).toBe('3 minutes ago')
    expect(relativeTime(7_200, 'en')).toBe('2 hours ago')
    expect(relativeTime(172_800, 'en')).toBe('2 days ago')
  })

  it('never says "in" for a clock slightly ahead', () => {
    expect(relativeTime(-3, 'en')).toBe('0 seconds ago')
  })
})
