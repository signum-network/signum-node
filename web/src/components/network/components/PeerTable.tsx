import { Fragment, useState } from 'react'
import { AnimatePresence } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { Card, CardLabel, CardSkeleton } from '@/components/ui/Card'
import { ScrollableTable } from '@/components/ui/ScrollableTable'
import { useNetworkStatus } from '@/hooks/useNodeQuery'
import type { PeerChainStatus } from '@/lib/nodeApi'
import { ForkPointModal } from './ForkPointModal'
import { formatLatency, secondsAgo, statusColor, statusLabelKey, statusSortRank } from './peerStatus'

function StatusBadge({ status }: { status: PeerChainStatus | 'blacklisted' }) {
  const { t } = useTranslation()
  const color = statusColor(status)
  const label = t(statusLabelKey(status))
  return (
    <span
      className="inline-block rounded-sm px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-[1px]"
      style={{ color, border: `1px solid ${color}`, background: `color-mix(in srgb, ${color} 12%, transparent)` }}
    >
      {label}
    </span>
  )
}

const FORK_POINT_MAX_LOOKBACK = 10_000

type SortKey = 'height' | 'status' | 'failures'

interface ThBtnProps {
  label: string
  k: SortKey
  sortKey: SortKey
  onSort: (k: SortKey) => void
}

function ThBtn({ label, k, sortKey, onSort }: ThBtnProps) {
  return (
    <button
      type="button"
      className="text-left text-[9px] uppercase tracking-[2px] transition-opacity hover:opacity-100"
      style={{ color: sortKey === k ? 'var(--blue2)' : 'var(--muted)', opacity: sortKey === k ? 1 : 0.7 }}
      onClick={() => onSort(k)}
    >
      {label}{sortKey === k ? ' ↓' : ''}
    </button>
  )
}

export function PeerTable() {
  const { data, isLoading, dataUpdatedAt } = useNetworkStatus()
  const { t } = useTranslation()
  const [expanded, setExpanded] = useState<string | null>(null)
  const [forkModalPeer, setForkModalPeer] = useState<string | null>(null)
  const [sortKey, setSortKey] = useState<SortKey>('status')

  const peers = [...(data?.peers ?? [])].sort((a, b) => {
    if (sortKey === 'height') return b.height - a.height
    if (sortKey === 'failures') return b.connectionFailures - a.connectionFailures
    return statusSortRank(a.status) - statusSortRank(b.status)
  })

  return (
    <>
      <Card className="col-span-full overflow-hidden p-0">
        <div className="px-5 pt-5">
          <CardLabel tooltip={t('info.peersTable')}>{t('network.peers')}</CardLabel>
        </div>

        <ScrollableTable
          maxHeight="400px"
          head={
            <tr style={{ borderBottom: '1px solid var(--border)' }}>
              <th className="px-5 py-2 text-left">
                <span className="text-[9px] uppercase tracking-[2px]" style={{ color: 'var(--muted)' }}>{t('common.address')}</span>
              </th>
              <th className="px-3 py-2"><ThBtn label={t('common.height')} k="height" sortKey={sortKey} onSort={setSortKey} /></th>
              <th className="px-3 py-2"><ThBtn label={t('network.status')} k="status" sortKey={sortKey} onSort={setSortKey} /></th>
              <th className="px-3 py-2"><ThBtn label={t('common.failures')} k="failures" sortKey={sortKey} onSort={setSortKey} /></th>
              <th className="px-3 py-2">
                <span className="text-[9px] uppercase tracking-[2px]" style={{ color: 'var(--muted)' }}>{t('network.latency')}</span>
              </th>
              <th className="px-5 py-2" />
            </tr>
          }
        >
          {isLoading && (
            <tr>
              <td colSpan={6} className="px-5 py-6">
                <div className="h-4 w-48"><CardSkeleton /></div>
              </td>
            </tr>
          )}
          {!isLoading && peers.length === 0 && (
            <tr>
              <td colSpan={6} className="px-5 py-6 text-center text-[11px]" style={{ color: 'var(--muted)' }}>
                {t('network.noPeerData')}
              </td>
            </tr>
          )}
          {peers.map((peer) => (
            <Fragment key={peer.address}>
              <tr
                className="cursor-pointer transition-colors"
                style={{
                  background: expanded === peer.address ? 'color-mix(in srgb, var(--blue2) 6%, transparent)' : undefined,
                  borderBottom: '1px solid var(--border)',
                }}
                onClick={() => setExpanded(expanded === peer.address ? null : peer.address)}
              >
                <td className="px-5 py-3 font-mono" style={{ color: 'var(--text)' }}>{peer.address}</td>
                <td className="px-3 py-3 text-center tabular-nums" style={{ color: 'var(--muted)' }}>{peer.height.toLocaleString()}</td>
                <td className="px-3 py-3 text-center">
                  <StatusBadge status={peer.blacklisted ? 'blacklisted' : peer.status} />
                </td>
                <td className="px-3 py-3 text-center tabular-nums" style={{ color: peer.connectionFailures > 3 ? 'var(--gold)' : 'var(--muted)' }}>
                  {peer.connectionFailures}
                </td>
                <td className="px-3 py-3 text-center tabular-nums" style={{ color: 'var(--muted)' }}>
                  {formatLatency(peer.latencyMs)}
                </td>
                <td className="px-5 py-3 text-right">
                  {!peer.blacklisted && peer.status === 'forking' && (() => {
                    const tooFarBehind = peer.height < (data?.myHeight ?? 0) - FORK_POINT_MAX_LOOKBACK
                    return (
                      <button
                        type="button"
                        title={tooFarBehind ? t('network.forkPoint.tooFarBehind') : undefined}
                        className="rounded-sm px-2 py-1 text-[9px] uppercase tracking-[1px] transition-opacity"
                        style={{ border: `1px solid var(--blue2)`, color: 'var(--blue2)', opacity: tooFarBehind ? 0.3 : 0.8, cursor: tooFarBehind ? 'not-allowed' : 'pointer' }}
                        onClick={(e) => { e.stopPropagation(); if (!tooFarBehind) setForkModalPeer(peer.address) }}
                      >
                        {t('network.forkPoint.findFork')}
                      </button>
                    )
                  })()}
                </td>
              </tr>
              {expanded === peer.address && (
                <tr key={`${peer.address}-detail`} style={{ background: 'color-mix(in srgb, var(--blue2) 4%, transparent)' }}>
                  <td colSpan={6} className="px-5 py-3">
                    <div className="grid grid-cols-2 gap-x-6 gap-y-1 text-[10px]">
                      <div><span style={{ color: 'var(--muted)' }}>{t('network.cumulDifficulty')}: </span><span className="font-mono" style={{ color: 'var(--text)' }}>{peer.cumulativeDifficulty}</span></div>
                      <div><span style={{ color: 'var(--muted)' }}>{t('common.failures')}: </span><span style={{ color: 'var(--text)' }}>{peer.connectionFailures}</span></div>
                      <div style={{ color: 'var(--muted)' }}>
                        {peer.checkedAt != null
                          ? t('network.checkedAgo', { seconds: secondsAgo(peer.checkedAt, data?.serverTime ?? peer.checkedAt, dataUpdatedAt, Date.now()) })
                          : '—'}
                      </div>
                    </div>
                    {!peer.blacklisted && peer.status === 'forking' && (() => {
                      const tooFarBehind = peer.height < (data?.myHeight ?? 0) - FORK_POINT_MAX_LOOKBACK
                      return (
                        <button
                          type="button"
                          title={tooFarBehind ? t('network.forkPoint.tooFarBehind') : undefined}
                          className="mt-2 rounded-sm px-3 py-1 text-[9px] uppercase tracking-[1px]"
                          style={{ border: '1px solid var(--blue2)', color: 'var(--blue2)', opacity: tooFarBehind ? 0.3 : 1, cursor: tooFarBehind ? 'not-allowed' : 'pointer' }}
                          onClick={() => { if (!tooFarBehind) setForkModalPeer(peer.address) }}
                        >
                          {t('network.forkPoint.findForkFull')}
                        </button>
                      )
                    })()}
                  </td>
                </tr>
              )}
            </Fragment>
          ))}
        </ScrollableTable>
      </Card>

      <AnimatePresence>
        {forkModalPeer && (
          <ForkPointModal peer={forkModalPeer} onClose={() => setForkModalPeer(null)} />
        )}
      </AnimatePresence>
    </>
  )
}
