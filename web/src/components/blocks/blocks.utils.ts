import type { ForkEvent } from '@/lib/nodeApi'

export type SearchTarget =
  | { kind: 'height'; height: number }
  | { kind: 'account'; id: string }
  | { kind: 'id'; id: string }
  | { kind: 'invalid' }

export type SearchHit =
  | { to: '/blocks/$height'; params: { height: string } }
  | { to: '/tx/$id'; params: { id: string } }
  | { to: '/accounts/$id'; params: { id: string } }

/** The lookups `resolveSearch` needs; each resolves to `null` for an unknown ID. */
export interface SearchApi {
  blockById(id: string): Promise<{ height: number } | null>
  transaction(id: string): Promise<unknown | null>
  account(id: string): Promise<unknown | null>
}

const ADDRESS = /^[A-Za-z]{1,3}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-[A-Za-z0-9]{5}$/
const MAX_UNSIGNED_64 = 18_446_744_073_709_551_615n

/** Decides what a search input means, without asking the node. */
export function classifySearch(input: string, tipHeight: number): SearchTarget {
  const text = input.trim()
  if (ADDRESS.test(text)) return { kind: 'account', id: text.toUpperCase() }
  if (!/^\d+$/.test(text)) return { kind: 'invalid' }
  const value = BigInt(text)
  if (value <= BigInt(tipHeight)) return { kind: 'height', height: Number(value) }
  if (value > MAX_UNSIGNED_64) return { kind: 'invalid' }
  return { kind: 'id', id: value.toString() }
}

/** Finds the page for a search target: an ID is tried as block, then transaction, then account. */
export async function resolveSearch(target: SearchTarget, api: SearchApi): Promise<SearchHit | null> {
  switch (target.kind) {
    case 'height':
      return { to: '/blocks/$height', params: { height: String(target.height) } }
    case 'account':
      return { to: '/accounts/$id', params: { id: target.id } }
    case 'invalid':
      return null
    case 'id': {
      const block = await api.blockById(target.id)
      if (block) return { to: '/blocks/$height', params: { height: String(block.height) } }
      if (await api.transaction(target.id)) return { to: '/tx/$id', params: { id: target.id } }
      if (await api.account(target.id)) return { to: '/accounts/$id', params: { id: target.id } }
      return null
    }
  }
}

export const TARGET_BLOCK_SECONDS = 240

export function blockDeltaSeconds(timestamp: number, previousTimestamp: number | undefined): number | null {
  return previousTimestamp === undefined ? null : timestamp - previousTimestamp
}

/** Below half the target block time is fast, above one and a half times is slow. */
export function deltaTone(seconds: number): 'fast' | 'normal' | 'slow' {
  if (seconds < TARGET_BLOCK_SECONDS / 2) return 'fast'
  if (seconds > TARGET_BLOCK_SECONDS * 1.5) return 'slow'
  return 'normal'
}

/** The newest rollback that replaced the block at `height` (the heights above its common block). */
export function rollbackCovering(height: number, forks: ForkEvent[]): ForkEvent | null {
  let newest: ForkEvent | null = null
  for (const fork of forks) {
    const covers = fork.rollbackHeight < height && height <= fork.rollbackHeight + fork.rollbackDepth
    if (covers && (newest === null || fork.detectedAt > newest.detectedAt)) newest = fork
  }
  return newest
}

export const PAGE_SIZE = 25

/** API indices of a page; page 0 holds the newest blocks. */
export function pageRange(page: number): { firstIndex: number; lastIndex: number } {
  const firstIndex = page * PAGE_SIZE
  return { firstIndex, lastIndex: firstIndex + PAGE_SIZE - 1 }
}

export function hasOlderPage(page: number, tipHeight: number): boolean {
  return tipHeight - pageRange(page).lastIndex > 0
}

/** A route's height parameter, or `null` unless it is a whole number from 0 to the tip. */
export function parseHeightParam(param: string, tipHeight: number | undefined): number | null {
  if (!/^\d+$/.test(param)) return null
  const height = Number(param)
  if (!Number.isSafeInteger(height)) return null
  if (tipHeight !== undefined && height > tipHeight) return null
  return height
}
