import { NodeApiError, type BlockDetail, type ForkEvent } from '@/lib/nodeApi'
import { PAGE_SIZE } from '@/lib/blocks'

export type SearchTarget =
  | { kind: 'height'; height: number }
  | { kind: 'account'; id: string }
  | { kind: 'id'; id: string }
  /** a number while the tip is not known yet: tried as a height first, then as an ID */
  | { kind: 'heightOrId'; value: string }
  | { kind: 'invalid' }

export type SearchHit =
  | { to: '/blocks/$height'; params: { height: string } }
  | { to: '/tx/$id'; params: { id: string } }
  | { to: '/accounts/$id'; params: { id: string } }

/** The lookups `resolveSearch` needs; each resolves to `null` for an unknown ID. */
export interface SearchApi {
  blockAtHeight(height: number): Promise<{ height: number } | null>
  blockById(id: string): Promise<{ height: number } | null>
  transaction(id: string): Promise<unknown | null>
  account(id: string): Promise<unknown | null>
}

const ADDRESS = /^[A-Za-z]{1,3}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-[A-Za-z0-9]{5}$/
const MAX_UNSIGNED_64 = 18_446_744_073_709_551_615n

/** Decides what a search input means, without asking the node. */
export function classifySearch(input: string, tipHeight: number | undefined): SearchTarget {
  const text = input.trim()
  if (ADDRESS.test(text)) return { kind: 'account', id: text.toUpperCase() }
  if (!/^\d+$/.test(text)) return { kind: 'invalid' }
  const value = BigInt(text)
  if (value > MAX_UNSIGNED_64) return { kind: 'invalid' }
  if (tipHeight === undefined) return { kind: 'heightOrId', value: value.toString() }
  if (value <= BigInt(tipHeight)) return { kind: 'height', height: Number(value) }
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
    case 'heightOrId': {
      const height = Number(target.value)
      if (Number.isSafeInteger(height) && (await api.blockAtHeight(height))) {
        return { to: '/blocks/$height', params: { height: target.value } }
      }
      return resolveSearch({ kind: 'id', id: target.value }, api)
    }
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

/** A route's height parameter, or `null` unless it is a whole number from 0 to the tip. */
export function parseHeightParam(param: string, tipHeight: number | undefined): number | null {
  if (!/^\d+$/.test(param)) return null
  const height = Number(param)
  if (!Number.isSafeInteger(height)) return null
  if (tipHeight !== undefined && height > tipHeight) return null
  return height
}

/** Anchor of the next older page, or `null` once the page reaches the genesis block. */
export function olderFrom(blocks: BlockDetail[]): number | null {
  const lowest = blocks[blocks.length - 1]?.height
  return lowest === undefined || lowest === 0 ? null : lowest - 1
}

/** Anchor of the next newer page, or `null` for the live page at the tip. */
export function newerFrom(from: number, tipHeight: number): number | null {
  const newer = from + PAGE_SIZE
  return newer >= tipHeight - PAGE_SIZE + 1 ? null : newer
}

/** The node asks its peers only about blocks this close to the tip (`getBlockPeerStatus`). */
export const RECENT_BLOCKS = 360

export function canCheckPeers(height: number, tipHeight: number | undefined): boolean {
  return tipHeight !== undefined && height >= tipHeight - RECENT_BLOCKS
}

/** Colour of a peer check result: positive only if a peer has the block and none misses it. */
export function peerCheckTone(c: { hasIt: number; missing: number; notYet: number; noAnswer: number }): 'ok' | 'warn' | 'muted' {
  if (c.missing > 0) return 'warn'
  return c.hasIt > 0 ? 'ok' : 'muted'
}

/** A node answer (any error code) means nothing matched; anything else means the node was not reached. */
export function searchErrorKind(error: unknown): 'notFound' | 'unavailable' {
  return error instanceof NodeApiError ? 'notFound' : 'unavailable'
}
