import { describe, it, expect, vi } from 'vitest'
import {
  ClientCache,
  memoryBackend,
  cachedImmutable,
  cachedExpiring,
  networkKey,
  type KeyValueBackend,
} from './clientCache'

function setup(start = 1_000) {
  let now = start
  const cache = new ClientCache(memoryBackend(), () => now)
  return { cache, advance: (ms: number) => { now += ms }, now: () => now }
}

const failingBackend: KeyValueBackend = {
  get: () => Promise.reject(new Error('blocked')),
  put: () => Promise.reject(new Error('blocked')),
  delete: () => Promise.reject(new Error('blocked')),
}

describe('ClientCache', () => {
  it('keeps immutable entries', async () => {
    const { cache } = setup()
    await cache.putImmutable('assets', 'g:1', { name: 'TOKEN' })
    expect(await cache.getImmutable('assets', 'g:1')).toEqual({ name: 'TOKEN' })
    expect(await cache.getImmutable('assets', 'g:2')).toBeUndefined()
  })

  it('keeps expiring entries until they expire', async () => {
    const { cache, advance } = setup()
    await cache.putExpiring('g:subs', [1], 1_500)
    advance(499)
    expect(await cache.getExpiring('g:subs')).toEqual([1])
    advance(1)
    expect(await cache.getExpiring('g:subs')).toBeUndefined()
  })

  it('forgets an entry on request', async () => {
    const { cache } = setup()
    await cache.putExpiring('g:subs', [1], 9_999)
    await cache.forget('g:subs')
    expect(await cache.getExpiring('g:subs')).toBeUndefined()
  })

  it('falls back to memory when the storage fails', async () => {
    // private windows and full quotas make IndexedDB throw; the page must keep working
    const cache = new ClientCache(failingBackend, () => 0)
    await cache.putImmutable('assets', 'g:1', { name: 'TOKEN' })
    expect(await cache.getImmutable('assets', 'g:1')).toEqual({ name: 'TOKEN' })
  })
})

describe('cachedImmutable', () => {
  it('fetches once and stores only the picked fields', async () => {
    const { cache } = setup()
    const fetch = vi.fn(async () => ({ name: 'TOKEN', decimals: 2, account: 'owner-now' }))
    const pick = ({ name, decimals }: { name: string; decimals: number }) => ({ name, decimals })

    const first = await cachedImmutable(cache, 'assets', 'g:1', fetch, pick)
    const second = await cachedImmutable(cache, 'assets', 'g:1', fetch, pick)

    expect(fetch).toHaveBeenCalledTimes(1)
    expect(first).toEqual({ name: 'TOKEN', decimals: 2 })
    expect(second).toEqual({ name: 'TOKEN', decimals: 2 })
    expect(second).not.toHaveProperty('account')
  })

  it('stores nothing for an unknown entity', async () => {
    const { cache } = setup()
    const fetch = vi.fn(async () => null)

    expect(await cachedImmutable(cache, 'assets', 'g:1', fetch, (x) => x)).toBeNull()
    await cachedImmutable(cache, 'assets', 'g:1', fetch, (x) => x)

    expect(fetch).toHaveBeenCalledTimes(2)
  })
})

describe('cachedExpiring', () => {
  it('reuses an entry while it is valid and fetches again afterwards', async () => {
    const { cache, advance } = setup(1_000)
    const fetch = vi.fn(async () => ['data'])
    const validUntil = vi.fn((_data: string[], storedAt: number) => storedAt + 100)

    await cachedExpiring(cache, 'g:subs', fetch, validUntil)
    advance(99)
    await cachedExpiring(cache, 'g:subs', fetch, validUntil)
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(validUntil).toHaveBeenCalledWith(['data'], 1_000)

    advance(1)
    await cachedExpiring(cache, 'g:subs', fetch, validUntil)
    expect(fetch).toHaveBeenCalledTimes(2)
  })
})

describe('networkKey', () => {
  it('prefixes the network', () => {
    expect(networkKey('g', 'asset', 5)).toBe('g:asset:5')
  })
})
