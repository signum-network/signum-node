import { useState } from 'react'
import { AnimatePresence } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { Card, CardLabel, CardSkeleton } from '@/components/ui/Card'
import { ScrollableTable } from '@/components/ui/ScrollableTable'
import { useNetworkStatus } from '@/hooks/useNodeQuery'
import type { PeerStatusEntry } from '@/lib/nodeApi'
import { ForkPointModal } from './ForkPointModal'

function statusColor(status: PeerStatusEntry['status'] | 'blacklisted') {
  switch (status) {
    case 'on-chain': return 'var(--green)'
    case 'stale':    return 'var(--gold)'
    case 'forking':  return 'var(--red, #ff4444)'
    case 'blacklisted': return 'var(--muted)'
    default: return 'var(--muted)'
  }
}

function StatusBadge({ status }: { status: string }) {
  const { t } = useTranslation()
  const color = statusColor(status as PeerStatusEntry['status'])
  const label = status === 'on-chain' ? t('status.onChain')
    : status === 'stale' ? t('status.stale')
    : status === 'forking' ? t('status.forking')
    : t('status.blacklisted')
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
  const { data, isLoading } = useNetworkStatus()
  const { t } = useTranslation()
  const [expanded, setExpanded] = useState<string | null>(null)
  const [forkModalPeer, setForkModalPeer] = useState<string | null>(null)
  const [sortKey, setSortKey] = useState<SortKey>('status')

  const peers = [...(data?.peers ?? [])].sort((a, b) => {
    if (sortKey === 'height') return b.height - a.height
    if (sortKey === 'failures') return b.connectionFailures - a.connectionFailures
    const order = { forking: 0, stale: 1, 'on-chain': 2 }
    return (order[a.status] ?? 9) - (order[b.status] ?? 9)
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
              <th className="px-5 py-2" />
            </tr>
          }
        >
          {isLoading && (
            <tr>
              <td colSpan={5} className="px-5 py-6">
                <div className="h-4 w-48"><CardSkeleton /></div>
              </td>
            </tr>
          )}
          {!isLoading && peers.length === 0 && (
            <tr>
              <td colSpan={5} className="px-5 py-6 text-center text-[11px]" style={{ color: 'var(--muted)' }}>
                {t('network.noPeerData')}
              </td>
            </tr>
          )}
          {peers.map((peer) => (
            <>
              <tr
                key={peer.address}
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
                  <td colSpan={5} className="px-5 py-3">
                    <div className="grid grid-cols-2 gap-x-6 gap-y-1 text-[10px]">
                      <div><span style={{ color: 'var(--muted)' }}>{t('network.cumulDifficulty')}: </span><span className="font-mono" style={{ color: 'var(--text)' }}>{peer.cumulativeDifficulty}</span></div>
                      <div><span style={{ color: 'var(--muted)' }}>{t('common.failures')}: </span><span style={{ color: 'var(--text)' }}>{peer.connectionFailures}</span></div>
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
            </>
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
