import { signumTimestampToMs, type Contract, type Subscription } from '@/lib/nodeApi'

const MIN_VALID_MS = 60_000
const MAX_VALID_MS = 300_000

/**
 * Until when a cached alias or subscription list may be shown: never past the earliest due date
 * (a lapsed renewal deletes the alias) and never longer than 5 minutes, but at least 60 seconds,
 * since the node settles a due subscription only in the next block and returns it until then.
 */
export function computeValidUntil(storedAtMs: number, timeNextsMs: number[]): number {
  const cap = storedAtMs + MAX_VALID_MS
  const due = timeNextsMs.length > 0 ? Math.min(cap, ...timeNextsMs) : cap
  return Math.max(storedAtMs + MIN_VALID_MS, due)
}

/** One `getAccountSubscriptions` answer holds both directions. */
export function splitSubscriptions(subs: Subscription[], accountId: string): { outgoing: Subscription[]; incoming: Subscription[] } {
  return {
    outgoing: subs.filter((s) => s.sender === accountId),
    incoming: subs.filter((s) => s.recipient === accountId),
  }
}

export function renewalFor(aliasId: string, subs: Subscription[]): Subscription | undefined {
  return subs.find((s) => s.alias === aliasId)
}

/** Both in Signum epoch seconds; due from `timeNext` on. */
export function isOverdue(timeNextEpochSec: number, nodeNowEpochSec: number): boolean {
  return timeNextEpochSec <= nodeNowEpochSec
}

const UNITS = [
  { unit: 'week', seconds: 604_800 },
  { unit: 'day', seconds: 86_400 },
  { unit: 'hour', seconds: 3_600 },
] as const

/** A subscription interval in the largest unit that fits evenly, else in minutes. */
export function frequencyParts(seconds: number): { unit: 'minute' | 'hour' | 'day' | 'week'; count: number } {
  for (const { unit, seconds: size } of UNITS) {
    if (seconds >= size && seconds % size === 0) return { unit, count: seconds / size }
  }
  return { unit: 'minute', count: Math.round(seconds / 60) }
}

/** The due dates of subscriptions, as epoch milliseconds for `computeValidUntil`. */
export function subscriptionTimesMs(subs: Subscription[]): number[] {
  return subs.map((s) => signumTimestampToMs(s.timeNext))
}

/** Runs at most `max` of the given tasks at a time; the others wait in order. */
export function limitConcurrency(max: number): <T>(task: () => Promise<T>) => Promise<T> {
  let running = 0
  const waiting: (() => void)[] = []
  const next = () => {
    running--
    waiting.shift()?.()
  }
  return async <T>(task: () => Promise<T>): Promise<T> => {
    if (running >= max) await new Promise<void>((resolve) => waiting.push(resolve))
    running++
    try {
      return await task()
    } finally {
      next()
    }
  }
}

export const ACCOUNT_TABS = ['overview', 'tokens', 'contracts', 'aliases', 'subscriptions'] as const
export type AccountTab = (typeof ACCOUNT_TABS)[number]

/** The `?tab=` value of the account page; anything unknown shows the overview. */
export function parseAccountTab(value: unknown): AccountTab {
  return (ACCOUNT_TABS as readonly unknown[]).includes(value) ? (value as AccountTab) : 'overview'
}

export type ContractState = 'dead' | 'finished' | 'frozen' | 'running' | 'stopped'

/** One state for the status flags of a contract, the most final first. */
export function contractState(c: Pick<Contract, 'dead' | 'finished' | 'frozen' | 'running' | 'stopped'>): ContractState {
  if (c.dead) return 'dead'
  if (c.finished) return 'finished'
  if (c.frozen) return 'frozen'
  if (c.running) return 'running'
  return 'stopped'
}

/**
 * Whether to fetch an overdue entry again: once per new block (identified by the chain's last block
 * ID), only while an overdue entry is shown, and not for the first block seen after mounting.
 */
export function shouldRefetchOverdue(previousBlock: string | undefined, currentBlock: string | undefined, hasOverdue: boolean): boolean {
  return hasOverdue && previousBlock !== undefined && currentBlock !== undefined && previousBlock !== currentBlock
}
