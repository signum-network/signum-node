import type { PeerStatusEntry } from '@/lib/nodeApi'

export interface Lane {
  isOurChain: boolean
  peers: PeerStatusEntry[]
  cumulativeDifficulty: string
  avgHeight: number
  index: number
}

export function groupIntoLanes(peers: PeerStatusEntry[]): Lane[] {
  const ourPeers = peers.filter(p => p.onOurChain)
  const forkingPeers = peers.filter(p => p.status === 'forking' && !p.blacklisted)

  const forkGroups = new Map<string, PeerStatusEntry[]>()
  for (const peer of forkingPeers) {
    const existing = forkGroups.get(peer.cumulativeDifficulty) ?? []
    forkGroups.set(peer.cumulativeDifficulty, [...existing, peer])
  }

  const avg = (ps: PeerStatusEntry[]) =>
    ps.length ? Math.round(ps.reduce((s, p) => s + p.height, 0) / ps.length) : 0

  const ourLane: Lane = {
    isOurChain: true,
    peers: ourPeers,
    cumulativeDifficulty: ourPeers[0]?.cumulativeDifficulty ?? '',
    avgHeight: avg(ourPeers),
    index: 0,
  }

  const forkLanes: Lane[] = [...forkGroups.entries()]
    .sort((a, b) => b[1].length - a[1].length)
    .map(([cumDiff, ps], i) => ({
      isOurChain: false,
      peers: ps,
      cumulativeDifficulty: cumDiff,
      avgHeight: avg(ps),
      index: i + 1,
    }))

  return [ourLane, ...forkLanes]
}

export function cumulDiffRatio(lane: Lane, lanes: Lane[]): number {
  const vals = lanes.map(l => BigInt(l.cumulativeDifficulty || '0'))
  const max = vals.reduce((m, v) => (v > m ? v : m), 0n)
  if (max === 0n) return 0
  const val = BigInt(lane.cumulativeDifficulty || '0')
  return Number((val * 10000n) / max) / 10000
}
