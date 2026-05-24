import { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { PageWrapper } from '@/components/layout/PageWrapper'
import { Card, CardLabel, CardSub, CardSkeleton } from '@/components/ui/Card'
import { InfoTooltip } from '@/components/ui/InfoTooltip'
import { useNetworkStatus, useForkHistory, useBlacklist, useFindForkPoint } from '@/hooks/useNodeQuery'
import { cn } from '@/lib/utils'
import type { PeerStatusEntry } from '@/lib/nodeApi'

// ─── helpers ────────────────────────────────────────────────────────────────

function statusColor(status: PeerStatusEntry['status'] | 'blacklisted') {
  switch (status) {
    case 'on-chain': return 'var(--green)'
    case 'stale':    return 'var(--gold)'
    case 'forking':  return 'var(--red, #ff4444)'
    case 'blacklisted': return 'var(--muted)'
    default: return 'var(--muted)'
  }
}

function consensusColor(pct: number) {
  if (pct >= 90) return 'var(--green)'
  if (pct >= 70) return 'var(--gold)'
  return 'var(--red, #ff4444)'
}

function fmtTs(ms: number) {
  return new Date(ms).toLocaleString()
}

// ─── StatusBadge ────────────────────────────────────────────────────────────

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

// ─── ConsensusBar ───────────────────────────────────────────────────────────

function ConsensusBar() {
  const { data, isLoading, refetch, isFetching } = useNetworkStatus()
  const { t } = useTranslation()
  const pct = data?.consensusPercent ?? 0
  const color = consensusColor(pct)

  return (
    <Card className="col-span-full">
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-1.5">
            <CardLabel>{t('network.chainConsensus')}</CardLabel>
            <InfoTooltip text={t('info.chainConsensus')} />
          </div>
          {isLoading ? (
            <div className="h-10 w-40"><CardSkeleton /></div>
          ) : (
            <div
              className="text-[40px] font-black leading-none tabular-nums"
              style={{ fontFamily: 'var(--font-display)', color, textShadow: `0 0 20px ${color}` }}
            >
              {pct.toFixed(1)}%
            </div>
          )}
          <CardSub className="mt-1">
            {data
              ? t('network.agree', { on: data.onChainPeers, stale: data.stalePeers, forking: data.forkingPeers })
              : t('network.onOurChain')}
          </CardSub>
        </div>
        <div className="text-right">
          <CardLabel>{t('common.height')}</CardLabel>
          <div
            className="text-[24px] font-bold tabular-nums"
            style={{ fontFamily: 'var(--font-display)', color: 'var(--blue2)' }}
          >
            {data?.myHeight?.toLocaleString() ?? '—'}
          </div>
          {data?.cachedAt && (
            <CardSub className="mt-1">{t('common.cachedAgo', { seconds: Math.round((Date.now() - data.cachedAt) / 1000) })}</CardSub>
          )}
          <button
            type="button"
            className="mt-2 text-[9px] uppercase tracking-[2px] transition-opacity hover:opacity-100"
            style={{ color: 'var(--blue2)', opacity: isFetching ? 0.4 : 0.7 }}
            onClick={() => void refetch()}
            disabled={isFetching}
          >
            {isFetching ? t('common.refreshing') : t('common.refresh')}
          </button>
        </div>
      </div>

      {/* consensus bar */}
      <div className="mt-4 h-1.5 w-full overflow-hidden rounded-full" style={{ background: 'var(--border)' }}>
        <motion.div
          className="h-full rounded-full"
          style={{ background: color, boxShadow: `0 0 8px ${color}` }}
          animate={{ width: `${pct}%` }}
          transition={{ duration: 0.8, ease: 'easeOut' }}
        />
      </div>
    </Card>
  )
}

// ─── ForkPointModal ──────────────────────────────────────────────────────────

function ForkPointModal({ peer, onClose }: { peer: string; onClose: () => void }) {
  const { mutate, data, isPending } = useFindForkPoint()
  const { t } = useTranslation()

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ background: 'rgba(0,0,0,.7)' }}
      onClick={onClose}
    >
      <Card
        className="w-full max-w-md"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center gap-1.5">
          <CardLabel>{t('network.forkPoint.title')}</CardLabel>
          <InfoTooltip text={t('info.findFork')} />
        </div>
        <p className="mb-3 text-[11px]" style={{ color: 'var(--muted)' }}>{peer}</p>

        {!data && !isPending && (
          <button
            type="button"
            className="mt-2 rounded-sm px-4 py-2 text-[11px] font-semibold uppercase tracking-[2px] transition-opacity hover:opacity-90"
            style={{ background: 'var(--blue2)', color: 'var(--bg)' }}
            onClick={() => mutate(peer)}
          >
            {t('common.startBinarySearch')}
          </button>
        )}

        {isPending && (
          <div className="mt-4 space-y-2">
            <div className="h-3 w-3/4"><CardSkeleton /></div>
            <div className="h-3 w-1/2"><CardSkeleton /></div>
            <p className="text-[10px]" style={{ color: 'var(--muted)' }}>{t('common.searching')}</p>
          </div>
        )}

        {data && (
          <div className="mt-2 space-y-2">
            {data.error ? (
              <p className="text-[11px]" style={{ color: 'var(--red, #ff4444)' }}>{data.error}</p>
            ) : (
              <>
                <Row label={t('network.forkPoint.forkHeight')} value={String(data.forkAtHeight ?? '—')} />
                <Row label={t('network.forkPoint.forkBlockId')} value={data.forkAtBlockId ?? '—'} mono />
                <Row label={t('network.forkPoint.ourBlock')} value={data.ourBlockIdAtFork ?? '—'} mono />
                <Row label={t('network.forkPoint.steps')} value={String(data.searchSteps ?? '—')} />
              </>
            )}
            <button
              type="button"
              className="mt-3 text-[9px] uppercase tracking-[2px] opacity-60 hover:opacity-100"
              style={{ color: 'var(--blue2)' }}
              onClick={() => mutate(peer)}
            >
              {t('common.searchAgain')}
            </button>
          </div>
        )}

        <button
          type="button"
          className="absolute right-4 top-4 text-[11px] opacity-40 hover:opacity-80"
          style={{ color: 'var(--muted)' }}
          onClick={onClose}
        >
          ✕
        </button>
      </Card>
    </div>
  )
}

function Row({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex items-center justify-between gap-2 border-b pb-1.5" style={{ borderColor: 'var(--border)' }}>
      <span className="text-[9px] uppercase tracking-[1px]" style={{ color: 'var(--muted)' }}>{label}</span>
      <span
        className={cn('text-[11px]', mono && 'font-mono')}
        style={{ color: 'var(--text)' }}
      >
        {value}
      </span>
    </div>
  )
}

// ─── PeerTable ───────────────────────────────────────────────────────────────

function PeerTable() {
  const { data, isLoading } = useNetworkStatus()
  const { t } = useTranslation()
  const [expanded, setExpanded] = useState<string | null>(null)
  const [forkModalPeer, setForkModalPeer] = useState<string | null>(null)
  const [sortKey, setSortKey] = useState<'height' | 'status' | 'failures'>('status')

  const peers = [...(data?.peers ?? [])].sort((a, b) => {
    if (sortKey === 'height') return b.height - a.height
    if (sortKey === 'failures') return b.connectionFailures - a.connectionFailures
    // status: forking first, then stale, then on-chain
    const order = { forking: 0, stale: 1, 'on-chain': 2 }
    return (order[a.status] ?? 9) - (order[b.status] ?? 9)
  })

  const ThBtn = ({ label, k }: { label: string; k: typeof sortKey }) => (
    <button
      type="button"
      className="text-left text-[9px] uppercase tracking-[2px] transition-opacity hover:opacity-100"
      style={{ color: sortKey === k ? 'var(--blue2)' : 'var(--muted)', opacity: sortKey === k ? 1 : 0.7 }}
      onClick={() => setSortKey(k)}
    >
      {label}{sortKey === k ? ' ↓' : ''}
    </button>
  )

  return (
    <>
      <Card className="col-span-full overflow-hidden p-0">
        <div className="flex items-center gap-1.5 px-5 pt-5">
          <CardLabel>{t('network.peers')}</CardLabel>
          <InfoTooltip text={t('info.peersTable')} />
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-[11px]">
            <thead>
              <tr style={{ borderBottom: '1px solid var(--border)' }}>
                <th className="px-5 py-2 text-left"><span className="text-[9px] uppercase tracking-[2px]" style={{ color: 'var(--muted)' }}>{t('common.address')}</span></th>
                <th className="px-3 py-2"><ThBtn label={t('common.height')} k="height" /></th>
                <th className="px-3 py-2"><ThBtn label="Status" k="status" /></th>
                <th className="px-3 py-2"><ThBtn label={t('common.failures')} k="failures" /></th>
                <th className="px-5 py-2" />
              </tr>
            </thead>
            <tbody>
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
                      {!peer.blacklisted && peer.status === 'forking' && (
                        <button
                          type="button"
                          className="rounded-sm px-2 py-1 text-[9px] uppercase tracking-[1px] transition-opacity hover:opacity-100"
                          style={{ border: '1px solid var(--blue2)', color: 'var(--blue2)', opacity: 0.8 }}
                          onClick={(e) => { e.stopPropagation(); setForkModalPeer(peer.address) }}
                        >
                          {t('network.forkPoint.findFork')}
                        </button>
                      )}
                    </td>
                  </tr>
                  {expanded === peer.address && (
                    <tr key={`${peer.address}-detail`} style={{ background: 'color-mix(in srgb, var(--blue2) 4%, transparent)' }}>
                      <td colSpan={5} className="px-5 py-3">
                        <div className="grid grid-cols-2 gap-x-6 gap-y-1 text-[10px]">
                          <div><span style={{ color: 'var(--muted)' }}>Cumul. difficulty: </span><span className="font-mono" style={{ color: 'var(--text)' }}>{peer.cumulativeDifficulty}</span></div>
                          <div><span style={{ color: 'var(--muted)' }}>{t('common.failures')}: </span><span style={{ color: 'var(--text)' }}>{peer.connectionFailures}</span></div>
                        </div>
                        {!peer.blacklisted && peer.status === 'forking' && (
                          <button
                            type="button"
                            className="mt-2 rounded-sm px-3 py-1 text-[9px] uppercase tracking-[1px]"
                            style={{ border: '1px solid var(--blue2)', color: 'var(--blue2)' }}
                            onClick={() => setForkModalPeer(peer.address)}
                          >
                            {t('network.forkPoint.findForkFull')}
                          </button>
                        )}
                      </td>
                    </tr>
                  )}
                </>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <AnimatePresence>
        {forkModalPeer && (
          <ForkPointModal peer={forkModalPeer} onClose={() => setForkModalPeer(null)} />
        )}
      </AnimatePresence>
    </>
  )
}

// ─── ForkHistory ─────────────────────────────────────────────────────────────

function ForkHistory() {
  const { data, isLoading } = useForkHistory()
  const { t } = useTranslation()
  const forks = data?.forks ?? []

  return (
    <Card className="col-span-full md:col-span-2">
      <CardLabel>{t('network.forkHistory')}</CardLabel>

      {isLoading && (
        <div className="space-y-2">
          {[1, 2, 3].map((i) => <div key={i} className="h-4 w-full"><CardSkeleton /></div>)}
        </div>
      )}

      {!isLoading && forks.length === 0 && (
        <p className="text-[11px]" style={{ color: 'var(--muted)' }}>
          {t('network.noReorgs')}
        </p>
      )}

      <div className="space-y-2">
        {forks.map((f, i) => {
          const depthColor = f.rollbackDepth >= 3 ? 'var(--red, #ff4444)' : 'var(--gold)'
          return (
            <div
              key={i}
              className="flex items-center justify-between gap-3 border-b pb-2"
              style={{ borderColor: 'var(--border)' }}
            >
              <div>
                <div className="text-[11px]" style={{ color: 'var(--text)' }}>
                  {t('common.height')} {f.rollbackHeight.toLocaleString()}
                  <span className="ml-2 text-[10px]" style={{ color: depthColor }}>
                    {t('network.rollback', { depth: f.rollbackDepth, count: f.rollbackDepth })}
                  </span>
                </div>
                <div className="text-[9px]" style={{ color: 'var(--muted)' }}>
                  {fmtTs(f.detectedAt)}{f.peerSource ? ` · ${f.peerSource}` : ''}
                </div>
              </div>
              <div
                className="h-2 w-2 rounded-full flex-shrink-0"
                style={{ background: depthColor, boxShadow: `0 0 6px ${depthColor}` }}
              />
            </div>
          )
        })}
      </div>
    </Card>
  )
}

// ─── BlacklistPanel ──────────────────────────────────────────────────────────

function BlacklistPanel() {
  const { data: statusData } = useNetworkStatus()
  const { data: blacklistData, isLoading } = useBlacklist()
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)

  const blacklisted = blacklistData?.blacklisted ?? []
  const forkingPeers = (statusData?.peers ?? []).filter((p) => p.status === 'forking' && !p.blacklisted)

  const recommendations = [
    ...forkingPeers.map((p) => ({ address: p.address, reason: 'Consistently on different chain' })),
    ...blacklisted.map((p) => ({ address: p.address, reason: p.reason || 'Blacklisted by node' })),
  ].filter((v, i, arr) => arr.findIndex((x) => x.address === v.address) === i)

  const copy = (text: string) => void navigator.clipboard.writeText(text)

  return (
    <Card className="col-span-full md:col-span-2">
      <button
        type="button"
        className="flex w-full items-center justify-between"
        onClick={() => setOpen(!open)}
      >
        <div className="flex items-center gap-1.5">
          <CardLabel className="mb-0">{t('network.blacklistRecommendations')}</CardLabel>
          <InfoTooltip text={t('info.blacklistRecommendations')} />
        </div>
        <span className="text-[10px]" style={{ color: 'var(--muted)' }}>
          {open
            ? t('network.peersOpen', { count: recommendations.length })
            : t('network.peersClosed', { count: recommendations.length })}
        </span>
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2 }}
            className="overflow-hidden"
          >
            <div className="mt-3 space-y-2">
              {isLoading && <div className="h-4 w-40"><CardSkeleton /></div>}
              {!isLoading && recommendations.length === 0 && (
                <p className="text-[11px]" style={{ color: 'var(--muted)' }}>{t('network.noRecommendations')}</p>
              )}
              {recommendations.map((r) => (
                <div
                  key={r.address}
                  className="flex items-center justify-between gap-3 rounded-sm px-3 py-2"
                  style={{ background: 'color-mix(in srgb, var(--border) 60%, transparent)' }}
                >
                  <div>
                    <div className="font-mono text-[11px]" style={{ color: 'var(--text)' }}>{r.address}</div>
                    <div className="text-[9px]" style={{ color: 'var(--muted)' }}>{r.reason}</div>
                  </div>
                  <button
                    type="button"
                    className="flex-shrink-0 text-[9px] uppercase tracking-[1px] opacity-60 hover:opacity-100"
                    style={{ color: 'var(--blue2)' }}
                    onClick={() => copy(r.address)}
                  >
                    {t('common.copy')}
                  </button>
                </div>
              ))}
              {recommendations.length > 0 && (
                <p className="pt-1 text-[9px]" style={{ color: 'var(--muted)' }}>
                  {t('network.blacklistHint')}
                </p>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </Card>
  )
}

// ─── Page ────────────────────────────────────────────────────────────────────

export default function NetworkPage() {
  return (
    <PageWrapper>
      <div className="page-layout">
        <div className="grid grid-cols-1 gap-3 md:gap-4">
          <ConsensusBar />
        </div>

        <div className="grid grid-cols-1 gap-3 md:gap-4">
          <PeerTable />
        </div>

        <div className="grid grid-cols-1 gap-3 md:grid-cols-4 md:gap-4">
          <ForkHistory />
          <BlacklistPanel />
        </div>
      </div>
    </PageWrapper>
  )
}
