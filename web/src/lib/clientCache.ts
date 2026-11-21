/**
 * Browser-side cache for node data, so the web UI does not ask (public) nodes for the same data
 * again: immutable entries (token and contract metadata) live forever, expiring entries (aliases,
 * subscriptions) until their `validUntil`. Keys carry the network (genesis block ID), so a testnet
 * node never sees mainnet entries. IndexedDB when available; if it fails (private window, quota),
 * the cache silently continues in memory for the session.
 */

export type StoreName = 'assets' | 'contracts' | 'expiring'

export interface KeyValueBackend {
  get(store: StoreName, key: string): Promise<unknown | undefined>
  put(store: StoreName, key: string, value: unknown): Promise<void>
  delete(store: StoreName, key: string): Promise<void>
}

const STORES: StoreName[] = ['assets', 'contracts', 'expiring']

export function memoryBackend(): KeyValueBackend {
  const maps = new Map<StoreName, Map<string, unknown>>(STORES.map((s) => [s, new Map()]))
  return {
    get: async (store, key) => maps.get(store)!.get(key),
    put: async (store, key, value) => { maps.get(store)!.set(key, value) },
    delete: async (store, key) => { maps.get(store)!.delete(key) },
  }
}

function request<T>(req: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

/** Native IndexedDB, database `signum-web-ui`; every call rejects if IndexedDB is unusable. */
export function indexedDbBackend(): KeyValueBackend {
  let db: Promise<IDBDatabase> | null = null
  const open = () => {
    db ??= new Promise((resolve, reject) => {
      const req = indexedDB.open('signum-web-ui', 1)
      req.onupgradeneeded = () => {
        for (const store of STORES) {
          if (!req.result.objectStoreNames.contains(store)) req.result.createObjectStore(store)
        }
      }
      req.onsuccess = () => resolve(req.result)
      req.onerror = () => reject(req.error)
      req.onblocked = () => reject(new Error('IndexedDB blocked'))
    })
    return db
  }
  const run = async <T>(store: StoreName, mode: IDBTransactionMode, op: (s: IDBObjectStore) => IDBRequest<T>) =>
    request(op((await open()).transaction(store, mode).objectStore(store)))
  return {
    get: (store, key) => run(store, 'readonly', (s) => s.get(key)),
    put: async (store, key, value) => { await run(store, 'readwrite', (s) => s.put(value, key)) },
    delete: async (store, key) => { await run(store, 'readwrite', (s) => s.delete(key)) },
  }
}

interface ExpiringEntry<T> {
  data: T
  validUntil: number
}

export class ClientCache {
  private backend: KeyValueBackend
  private readonly now: () => number

  constructor(primary: KeyValueBackend, now: () => number = Date.now) {
    this.backend = primary
    this.now = now
  }

  /** Runs on the backend; on its first failure switches to memory for good and runs there. */
  private async use<T>(op: (backend: KeyValueBackend) => Promise<T>): Promise<T> {
    try {
      return await op(this.backend)
    } catch {
      this.backend = memoryBackend()
      return op(this.backend)
    }
  }

  getImmutable<T>(store: StoreName, key: string): Promise<T | undefined> {
    return this.use((b) => b.get(store, key)) as Promise<T | undefined>
  }

  putImmutable<T>(store: StoreName, key: string, value: T): Promise<void> {
    return this.use((b) => b.put(store, key, value))
  }

  async getExpiring<T>(key: string): Promise<T | undefined> {
    const entry = (await this.use((b) => b.get('expiring', key))) as ExpiringEntry<T> | undefined
    return entry && this.now() < entry.validUntil ? entry.data : undefined
  }

  putExpiring<T>(key: string, data: T, validUntil: number): Promise<void> {
    return this.use((b) => b.put('expiring', key, { data, validUntil } satisfies ExpiringEntry<T>))
  }

  forget(key: string): Promise<void> {
    return this.use((b) => b.delete('expiring', key))
  }

  currentTime(): number {
    return this.now()
  }
}

/** Fetches once per key and stores only the immutable fields `pick` keeps; `null` is not stored. */
export async function cachedImmutable<T, P>(
  cache: ClientCache,
  store: StoreName,
  key: string,
  fetch: () => Promise<T | null>,
  pick: (full: T) => P,
): Promise<P | null> {
  const cached = await cache.getImmutable<P>(store, key)
  if (cached !== undefined) return cached
  const full = await fetch()
  if (full === null) return null
  const picked = pick(full)
  await cache.putImmutable(store, key, picked)
  return picked
}

/** Reuses a valid entry, otherwise fetches and stores it with the `validUntil` computed from it. */
export async function cachedExpiring<T>(
  cache: ClientCache,
  key: string,
  fetch: () => Promise<T>,
  validUntil: (data: T, storedAt: number) => number,
): Promise<T> {
  const cached = await cache.getExpiring<T>(key)
  if (cached !== undefined) return cached
  const data = await fetch()
  const storedAt = cache.currentTime()
  await cache.putExpiring(key, data, validUntil(data, storedAt))
  return data
}

export function networkKey(genesisBlockId: string, ...parts: (string | number)[]): string {
  return [genesisBlockId, ...parts].join(':')
}

/** The app's cache: IndexedDB where the browser has it, memory otherwise. */
export const clientCache = new ClientCache(typeof indexedDB === 'undefined' ? memoryBackend() : indexedDbBackend())
