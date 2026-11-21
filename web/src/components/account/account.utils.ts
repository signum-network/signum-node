import { signumTimestampToMs, type Subscription } from '@/lib/nodeApi'

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
