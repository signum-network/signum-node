import { describe, it, expect, vi } from 'vitest'
import {
  compareVersions,
  isNewer,
  safeReleaseUrl,
  fetchLatestRelease,
  isDismissed,
  dismiss,
  browserStorage,
  OK_TTL_MS,
  FAIL_TTL_MS,
  type KeyValueStore,
} from './releaseCheck'

const NOTES = 'https://github.com/signum-network/signum-node/releases/tag/v3.10.0'

function memoryStore(): KeyValueStore & { data: Map<string, string> } {
  const data = new Map<string, string>()
  return { data, getItem: (k) => data.get(k) ?? null, setItem: (k, v) => { data.set(k, v) } }
}

const throwingStore: KeyValueStore = {
  getItem: () => { throw new Error('blocked') },
  setItem: () => { throw new Error('blocked') },
}

function response(body: unknown, ok = true, status = 200) {
  return { ok, status, json: async () => body } as Response
}

function setup(store: KeyValueStore | null = memoryStore()) {
  let now = 1_000_000
  const fetch = vi.fn(async () => response({ tag_name: 'v3.10.0', html_url: NOTES }))
  const deps = { store, fetch: fetch as unknown as typeof globalThis.fetch, now: () => now }
  return { deps, fetch, advance: (ms: number) => { now += ms } }
}

describe('compareVersions', () => {
  it('compares numerically', () => {
    expect(compareVersions('v3.10.0', 'v3.9.11')).toBeGreaterThan(0)
    expect(compareVersions('v3.9.11', 'v3.9.11')).toBe(0)
    expect(compareVersions('3.9.11', 'v3.9.12')).toBeLessThan(0)
  })

  it('ranks a suffixed version below the plain one', () => {
    expect(compareVersions('v3.10.0', 'v3.10.0-rc4')).toBeGreaterThan(0)
    expect(compareVersions('v3.10.0-dev', 'v3.10.0')).toBeLessThan(0)
  })

  it('returns null for unparseable input', () => {
    expect(compareVersions('', 'v3.9.11')).toBeNull()
    expect(compareVersions('v3.9', 'v3.9.11')).toBeNull()
    expect(compareVersions('latest', 'v3.9.11')).toBeNull()
  })
})

describe('isNewer', () => {
  it('ignores a stable release older than a pre-release node', () => {
    expect(isNewer('v3.9.11', 'v3.10.0-rc4')).toBe(false)
  })

  it('flags a newer release', () => {
    expect(isNewer('v3.10.0', 'v3.9.11')).toBe(true)
  })

  it('is false for unparseable versions', () => {
    expect(isNewer('x', 'v3.9.11')).toBe(false)
  })
})

describe('safeReleaseUrl', () => {
  it('keeps signum-network GitHub links only', () => {
    expect(safeReleaseUrl(NOTES)).toBe(NOTES)
    expect(safeReleaseUrl('https://github.com/signum-network.evil.io/x')).toBeNull()
    expect(safeReleaseUrl('javascript:alert(1)')).toBeNull()
    expect(safeReleaseUrl(42)).toBeNull()
  })
})

describe('fetchLatestRelease', () => {
  it('fetches once and reuses the result for 6 h', async () => {
    const { deps, fetch, advance } = setup()
    expect(await fetchLatestRelease(deps)).toEqual({ version: 'v3.10.0', url: NOTES })
    advance(OK_TTL_MS - 1)
    expect(await fetchLatestRelease(deps)).toEqual({ version: 'v3.10.0', url: NOTES })
    expect(fetch).toHaveBeenCalledTimes(1)
    advance(1)
    await fetchLatestRelease(deps)
    expect(fetch).toHaveBeenCalledTimes(2)
  })

  it('remembers a failure for 1 h and returns null', async () => {
    const { deps, fetch, advance } = setup()
    fetch.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    expect(await fetchLatestRelease(deps)).toBeNull()
    advance(FAIL_TTL_MS - 1)
    expect(await fetchLatestRelease(deps)).toBeNull()
    expect(fetch).toHaveBeenCalledTimes(1)
    advance(1)
    expect(await fetchLatestRelease(deps)).toEqual({ version: 'v3.10.0', url: NOTES })
    expect(fetch).toHaveBeenCalledTimes(2)
  })

  it('treats non-200 and missing tag_name as failure', async () => {
    const { deps, fetch, advance } = setup()
    fetch.mockResolvedValueOnce(response({ message: 'rate limited' }, false, 403))
    expect(await fetchLatestRelease(deps)).toBeNull()
    advance(FAIL_TTL_MS)
    fetch.mockResolvedValueOnce(response({ tag_name: 7 }))
    expect(await fetchLatestRelease(deps)).toBeNull()
    advance(FAIL_TTL_MS - 1)
    await fetchLatestRelease(deps)
    expect(fetch).toHaveBeenCalledTimes(2)
  })

  it('works without a store', async () => {
    const { deps, fetch } = setup(null)
    expect(await fetchLatestRelease(deps)).toEqual({ version: 'v3.10.0', url: NOTES })
    await fetchLatestRelease(deps)
    expect(fetch).toHaveBeenCalledTimes(2)
  })

  it('survives a store that throws', async () => {
    const { deps } = setup(throwingStore)
    expect(await fetchLatestRelease(deps)).toEqual({ version: 'v3.10.0', url: NOTES })
  })

  it('ignores a corrupt cache entry', async () => {
    const store = memoryStore()
    store.setItem('signum.latestRelease', 'not json')
    const { deps, fetch } = setup(store)
    expect(await fetchLatestRelease(deps)).toEqual({ version: 'v3.10.0', url: NOTES })
    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('returns the url only when it passes safeReleaseUrl', async () => {
    const { deps, fetch } = setup()
    fetch.mockResolvedValueOnce(response({ tag_name: 'v3.10.0', html_url: 'https://evil.example/x' }))
    expect(await fetchLatestRelease(deps)).toEqual({ version: 'v3.10.0', url: null })
  })
})

describe('dismiss', () => {
  it('hides only the dismissed version', () => {
    const store = memoryStore()
    dismiss(store, 'v3.10.0')
    expect(isDismissed(store, 'v3.10.0')).toBe(true)
    expect(isDismissed(store, 'v3.10.1')).toBe(false)
  })

  it('never throws when storage fails', () => {
    expect(() => dismiss(throwingStore, 'v3.10.0')).not.toThrow()
    expect(isDismissed(throwingStore, 'v3.10.0')).toBe(false)
    expect(isDismissed(null, 'v3.10.0')).toBe(false)
  })
})

describe('browserStorage', () => {
  it('returns null where there is no window storage', () => {
    // vitest runs in node: no window, the access throws and must be caught
    expect(browserStorage()).toBeNull()
  })
})
