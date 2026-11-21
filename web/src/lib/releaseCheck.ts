/**
 * Checks GitHub for a newer stable Signum Node release. The browser asks GitHub, never the node, so
 * the node has no third-party dependency. `releases/latest` skips drafts and pre-releases. Results
 * are cached in localStorage (6 h, failures 1 h) to stay far below GitHub's 60 requests/h per IP.
 */

export const RELEASES_URL = 'https://api.github.com/repos/signum-network/signum-node/releases/latest'
export const CACHE_KEY = 'signum.latestRelease'
export const DISMISS_KEY = 'signum.dismissedRelease'
export const OK_TTL_MS = 6 * 3600_000
export const FAIL_TTL_MS = 3600_000

const RELEASE_URL_PREFIX = 'https://github.com/signum-network/'
const VERSION = /^v?(\d+)\.(\d+)\.(\d+)(-.+)?$/

export interface LatestRelease {
  version: string
  /** release notes link, already checked by `safeReleaseUrl` */
  url: string | null
}

export interface KeyValueStore {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
}

interface CacheEntry {
  release: LatestRelease | null
  until: number
}

/** Semver-style: numbers first, then a version with a suffix (`-rc4`, `-dev`) ranks below the plain one. */
export function compareVersions(a: string, b: string): number | null {
  const pa = VERSION.exec(a.trim())
  const pb = VERSION.exec(b.trim())
  if (!pa || !pb) return null
  for (let i = 1; i <= 3; i++) {
    const diff = Number(pa[i]) - Number(pb[i])
    if (diff !== 0) return diff
  }
  return (pa[4] ? 0 : 1) - (pb[4] ? 0 : 1)
}

export function isNewer(release: string, node: string): boolean {
  return (compareVersions(release, node) ?? 0) > 0
}

export function safeReleaseUrl(url: unknown): string | null {
  return typeof url === 'string' && url.startsWith(RELEASE_URL_PREFIX) ? url : null
}

function read(store: KeyValueStore | null, key: string): string | null {
  try {
    return store?.getItem(key) ?? null
  } catch {
    return null
  }
}

function write(store: KeyValueStore | null, key: string, value: string): void {
  try {
    store?.setItem(key, value)
  } catch {
    // blocked storage: the check simply runs again next time
  }
}

function cached(store: KeyValueStore | null, now: number): CacheEntry | undefined {
  const raw = read(store, CACHE_KEY)
  if (raw === null) return undefined
  try {
    const entry = JSON.parse(raw) as { release?: { version?: unknown; url?: unknown } | null; until?: unknown }
    if (typeof entry?.until !== 'number' || now >= entry.until) return undefined
    // an entry of another shape (older/newer UI, hand edit) must not reach the banner
    if (entry.release === null) return { release: null, until: entry.until }
    if (typeof entry.release?.version !== 'string') return undefined
    return { release: { version: entry.release.version, url: safeReleaseUrl(entry.release.url) }, until: entry.until }
  } catch {
    return undefined
  }
}

async function ask(fetchFn: typeof fetch): Promise<LatestRelease | null> {
  try {
    const res = await fetchFn(RELEASES_URL, {
      headers: { Accept: 'application/vnd.github+json' },
      referrerPolicy: 'no-referrer', // GitHub needn't learn which node is being browsed
    })
    if (!res.ok) return null
    const body = (await res.json()) as { tag_name?: unknown; html_url?: unknown }
    if (typeof body?.tag_name !== 'string') return null
    return { version: body.tag_name, url: safeReleaseUrl(body.html_url) }
  } catch {
    return null
  }
}

/**
 * Query options for the check. The interval keeps an open tab (e.g. a dashboard left running) asking;
 * most runs are answered by the localStorage cache, so GitHub sees a request only after it expired.
 */
export const LATEST_RELEASE_QUERY_OPTIONS = {
  staleTime: OK_TTL_MS,
  refetchInterval: FAIL_TTL_MS,
  retry: false,
  refetchOnWindowFocus: false,
} as const

export async function fetchLatestRelease(deps: {
  store: KeyValueStore | null
  fetch: typeof fetch
  now: () => number
}): Promise<LatestRelease | null> {
  const hit = cached(deps.store, deps.now())
  if (hit) return hit.release
  const release = await ask(deps.fetch)
  const until = deps.now() + (release ? OK_TTL_MS : FAIL_TTL_MS)
  write(deps.store, CACHE_KEY, JSON.stringify({ release, until } satisfies CacheEntry))
  return release
}

export function isDismissed(store: KeyValueStore | null, version: string): boolean {
  return read(store, DISMISS_KEY) === version
}

export function dismiss(store: KeyValueStore | null, version: string): void {
  write(store, DISMISS_KEY, version)
}

/** `window.localStorage`, or `null` where the browser blocks it (accessing it can throw). */
export function browserStorage(): KeyValueStore | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}
