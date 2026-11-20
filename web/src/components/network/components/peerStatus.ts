import type { PeerChainStatus } from '@/lib/nodeApi'

type DisplayStatus = PeerChainStatus | 'blacklisted'

const COLORS: Record<DisplayStatus, string> = {
  'on-chain': 'var(--green)',
  stale: 'var(--gold)',
  ahead: 'var(--blue2)',
  forking: 'var(--red, #ff4444)',
  unknown: 'var(--muted)',
  blacklisted: 'var(--muted)',
}

const SORT_RANKS: Record<PeerChainStatus, number> = {
  forking: 0,
  unknown: 1,
  stale: 2,
  ahead: 3,
  'on-chain': 4,
}

const LABEL_KEYS = {
  'on-chain': 'status.onChain',
  stale: 'status.stale',
  ahead: 'status.ahead',
  forking: 'status.forking',
  unknown: 'status.unknown',
  blacklisted: 'status.blacklisted',
} as const

export function statusColor(status: DisplayStatus): string {
  return COLORS[status]
}

/** Lower ranks sort first: problems at the top of the peer table. */
export function statusSortRank(status: PeerChainStatus): number {
  return SORT_RANKS[status]
}

export function statusLabelKey(status: DisplayStatus): (typeof LABEL_KEYS)[DisplayStatus] {
  return LABEL_KEYS[status]
}

export function formatLatency(ms: number | null): string {
  return ms == null ? '—' : `${ms} ms`
}
