import type { NodeHealth } from '@/lib/nodeApi'

/** Inbound contact within this window counts as "reachable". */
export const REACH_WINDOW_MS = 30 * 60_000

const GIB = 1024 ** 3

export type Reachability = 'off' | 'reachable' | 'localOnly' | 'waiting' | 'noAddress' | 'noContact'

/** First match wins: the P2P server off, public contact, local contact, just started, no address, no contact. */
export function reachability(h: NodeHealth, nodeNowMs: number): Reachability {
  const recent = (at: number | null) => at != null && nodeNowMs - at < REACH_WINDOW_MS
  if (!h.peerServerRunning) return 'off'
  if (recent(h.lastInboundPeerAt)) return 'reachable'
  if (recent(h.lastLocalInboundPeerAt)) return 'localOnly'
  if (nodeNowMs - h.startedAt < REACH_WINDOW_MS) return 'waiting'
  if (!h.announcesAddress) return 'noAddress'
  return 'noContact'
}

export function diskTone(bytes: number | null): 'ok' | 'warn' | 'bad' | 'unknown' {
  if (bytes == null) return 'unknown'
  if (bytes < 2 * GIB) return 'bad'
  if (bytes < 10 * GIB) return 'warn'
  return 'ok'
}

const UNITS = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']

/** Binary units (1 KB = 1024 B), one decimal above bytes, number format of the locale. */
export function fmtBytes(bytes: number, locale?: string): string {
  let value = bytes
  let unit = 0
  while (value >= 1024 && unit < UNITS.length - 1) {
    value /= 1024
    unit++
  }
  const digits = unit === 0 ? 0 : 1
  const text = new Intl.NumberFormat(locale, { minimumFractionDigits: digits, maximumFractionDigits: digits }).format(value)
  return `${text} ${UNITS[unit]}`
}

export function uptimeParts(ms: number): { days: number; hours: number; minutes: number } {
  const minutesTotal = Math.max(0, Math.floor(ms / 60_000))
  return {
    days: Math.floor(minutesTotal / 1440),
    hours: Math.floor((minutesTotal % 1440) / 60),
    minutes: minutesTotal % 60,
  }
}

/** The node's current time: its time at the response plus what passed in the browser since. */
export function nodeNow(serverTime: number, fetchedAt: number, browserNow: number): number {
  return serverTime + (browserNow - fetchedAt)
}
