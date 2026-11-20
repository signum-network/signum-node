import { useTranslation } from 'react-i18next'
import { Card, CardLabel, CardSkeleton } from '@/components/ui/Card'
import { useNetworkStatus } from '@/hooks/useNodeQuery'
import type { PeerStatusEntry } from '@/lib/nodeApi'
import { groupIntoLanes, cumulDiffRatio, unansweredPeers } from './chainLanes.utils'
import type { Lane } from './chainLanes.utils'
import { statusColor } from './peerStatus'

function dotColor(peer: PeerStatusEntry): string {
  return statusColor(peer.status)
}

function PeerDot({ peer }: { peer: PeerStatusEntry }) {
  const { t } = useTranslation()
  const color = dotColor(peer)
  const label = `${peer.address} · ${t('common.height')} ${peer.height.toLocaleString()} · ${peer.status}`
  return (
    <div
      title={label}
      className="h-2.5 w-2.5 flex-shrink-0 rounded-full cursor-default transition-transform hover:scale-125"
      style={{ background: color, boxShadow: `0 0 5px ${color}` }}
    />
  )
}

function LaneCard({ lane, lanes }: { lane: Lane; lanes: Lane[] }) {
  const { t } = useTranslation()
  const ratio = cumulDiffRatio(lane, lanes)
  const label = lane.isOurChain
    ? t('network.ourChain')
    : t('network.forkLane', { index: lane.index })

  const borderColor = lane.isOurChain ? 'var(--green)' : 'var(--red, #ff4444)'
  const borderStyle = lane.isOurChain ? '1px solid' : '1px dashed'

  return (
    <div
      className="flex min-w-[140px] flex-col gap-3 rounded-sm p-3"
      style={{
        border: `${borderStyle} ${borderColor}`,
        background: lane.isOurChain
          ? 'color-mix(in srgb, var(--green) 5%, transparent)'
          : 'color-mix(in srgb, var(--red, #ff4444) 5%, transparent)',
      }}
    >
      <div>
        <div
          className="text-[9px] font-semibold uppercase tracking-[2px]"
          style={{ color: borderColor }}
        >
          {label}
        </div>
        <div className="text-[8px]" style={{ color: 'var(--muted)' }}>
          {t('common.height')} {lane.avgHeight.toLocaleString()}
        </div>
      </div>

      <div className="flex flex-wrap gap-1.5">
        {lane.peers.map(p => <PeerDot key={p.address} peer={p} />)}
      </div>

      <div
        className="text-[9px] tabular-nums"
        style={{ color: 'var(--muted)' }}
      >
        {lane.peers.length} {lane.peers.length === 1 ? 'peer' : 'peers'}
      </div>

      <div>
        <div className="text-[8px] mb-1" style={{ color: 'var(--muted)' }}>
          cumul. diff
        </div>
        <div className="h-1 w-full overflow-hidden rounded-full" style={{ background: 'var(--border)' }}>
          <div
            className="h-full rounded-full transition-all duration-700"
            style={{
              width: `${Math.round(ratio * 100)}%`,
              background: borderColor,
              boxShadow: `0 0 4px ${borderColor}`,
            }}
          />
        </div>
      </div>
    </div>
  )
}

/** Peers that did not answer the block check: no chain can be assigned to them. */
function UnansweredCard({ peers }: { peers: PeerStatusEntry[] }) {
  const { t } = useTranslation()
  return (
    <div
      className="flex min-w-[140px] flex-col gap-3 rounded-sm p-3"
      style={{ border: '1px dashed var(--muted)', background: 'color-mix(in srgb, var(--muted) 5%, transparent)' }}
    >
      <div className="text-[9px] font-semibold uppercase tracking-[2px]" style={{ color: 'var(--muted)' }}>
        {t('network.noAnswerLane')}
      </div>
      <div className="flex flex-wrap gap-1.5">
        {peers.map(p => <PeerDot key={p.address} peer={p} />)}
      </div>
      <div className="text-[9px] tabular-nums" style={{ color: 'var(--muted)' }}>
        {peers.length} {peers.length === 1 ? 'peer' : 'peers'}
      </div>
    </div>
  )
}

export function ChainLanes() {
  const { data, isLoading } = useNetworkStatus()
  const { t } = useTranslation()

  const lanes = groupIntoLanes(data?.peers ?? [])
  const unanswered = unansweredPeers(data?.peers ?? [])
  const hasForks = lanes.length > 1

  return (
    <Card className="col-span-full">
      <CardLabel tooltip={t('info.chainLanes')}>{t('network.chainLanes')}</CardLabel>

      {isLoading && (
        <div className="flex gap-3">
          {[1, 2].map(i => (
            <div key={i} className="h-32 w-36 rounded-sm"><CardSkeleton /></div>
          ))}
        </div>
      )}

      {!isLoading && !hasForks && (
        <div className="flex gap-3 items-start">
          <LaneCard lane={lanes[0]} lanes={lanes} />
          {unanswered.length > 0 && <UnansweredCard peers={unanswered} />}
          <div
            className="flex items-center text-[10px] self-center ml-4"
            style={{ color: 'var(--muted)' }}
          >
            {t('network.noActiveForks')}
          </div>
        </div>
      )}

      {!isLoading && hasForks && (
        <div className="themed-scroll overflow-x-auto">
          <div className="flex gap-3 pb-1">
            {lanes.map(lane => (
              <LaneCard key={lane.isOurChain ? 'our' : lane.cumulativeDifficulty} lane={lane} lanes={lanes} />
            ))}
            {unanswered.length > 0 && <UnansweredCard peers={unanswered} />}
          </div>
        </div>
      )}
    </Card>
  )
}
