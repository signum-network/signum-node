import { useState } from 'react'
import type { ForkEvent } from '@/lib/nodeApi'
import { useTranslation } from 'react-i18next'
import { Card, CardLabel, CardSkeleton } from '@/components/ui/Card'
import { useForkHistory, useNetworkStatus } from '@/hooks/useNodeQuery'
import { BranchTimeline } from './BranchTimeline'
import { RadialForkMap } from './RadialForkMap'

const MERGE_WINDOW_MS = 5_000
const STORAGE_KEY = 'forkHistory.view'

function mergeReorgs(forks: ForkEvent[]): ForkEvent[] {
  if (forks.length === 0) return forks
  const sorted = [...forks].sort((a, b) => b.detectedAt - a.detectedAt)
  const merged: ForkEvent[] = []
  for (const fork of sorted) {
    const last = merged[merged.length - 1]
    if (
      last &&
      Math.abs(fork.detectedAt - last.detectedAt) <= MERGE_WINDOW_MS &&
      fork.peerSource === last.peerSource
    ) {
      last.rollbackHeight = Math.min(last.rollbackHeight, fork.rollbackHeight)
      last.rollbackDepth += fork.rollbackDepth
    } else {
      merged.push({ ...fork })
    }
  }
  return merged
}

type ViewMode = 'branch' | 'radial' | 'table'

function readStoredView(): ViewMode {
  try {
    const v = localStorage.getItem(STORAGE_KEY)
    if (v === 'radial' || v === 'table') return v
    return 'branch'
  } catch {
    return 'branch'
  }
}

function fmtTs(ms: number) {
  return new Date(ms).toLocaleString()
}

function ForkTable({ forks }: { forks: ForkEvent[] }) {
  const { t } = useTranslation()

  if (forks.length === 0) {
    return (
      <p className="py-4 text-center text-[11px]" style={{ color: 'var(--muted)' }}>
        {t('network.noReorgs')}
      </p>
    )
  }

  return (
    <div className="themed-scroll overflow-y-auto max-h-64 space-y-2 pr-2">
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
              className="h-2 w-2 flex-shrink-0 rounded-full"
              style={{ background: depthColor, boxShadow: `0 0 6px ${depthColor}` }}
            />
          </div>
        )
      })}
    </div>
  )
}

export function ForkHistory() {
  const { data, isLoading } = useForkHistory()
  const { data: networkData } = useNetworkStatus()
  const { t } = useTranslation()
  const [view, setView] = useState<ViewMode>(readStoredView)

  const forks = mergeReorgs(data?.forks ?? [])
  const forkingPeerAddresses = new Set(
    (networkData?.peers ?? [])
      .filter(p => p.status === 'forking' && !p.blacklisted)
      .map(p => p.address),
  )
  const myHeight = networkData?.myHeight ?? 0

  function switchView(v: ViewMode) {
    setView(v)
    try { localStorage.setItem(STORAGE_KEY, v) } catch { /* ignore */ }
  }

  return (
    <Card className="col-span-full">
      <div className="mb-4 flex items-center justify-between gap-4">
        <CardLabel className="mb-0">{t('network.forkHistory')}</CardLabel>
        <div
          className="flex gap-1 rounded-sm p-0.5"
          style={{ background: 'rgba(0,0,0,.4)', border: '1px solid var(--border)' }}
        >
          {(['branch', 'radial', 'table'] as const).map(v => (
            <button
              key={v}
              type="button"
              className="rounded-sm px-3 py-1 text-[9px] uppercase tracking-[1.5px] transition-all"
              style={{
                background: view === v ? 'color-mix(in srgb, var(--blue2) 15%, transparent)' : 'transparent',
                color: view === v ? 'var(--blue2)' : 'var(--muted)',
                boxShadow: view === v ? '0 0 10px color-mix(in srgb, var(--blue2) 20%, transparent)' : 'none',
              }}
              onClick={() => switchView(v)}
            >
              {v === 'branch' ? `⌇ ${t('network.branchView')}` : v === 'radial' ? `⊙ ${t('network.radialView')}` : `☰ ${t('network.tableView')}`}
            </button>
          ))}
        </div>
      </div>

      {isLoading && (
        <div className="space-y-2">
          {[1, 2, 3].map(i => <div key={i} className="h-4 w-full"><CardSkeleton /></div>)}
        </div>
      )}

      {!isLoading && view === 'branch' && (
        <BranchTimeline forks={forks} myHeight={myHeight} forkingPeerAddresses={forkingPeerAddresses} />
      )}

      {!isLoading && view === 'radial' && (
        <RadialForkMap forks={forks} myHeight={myHeight} forkingPeerAddresses={forkingPeerAddresses} />
      )}

      {!isLoading && view === 'table' && (
        <ForkTable forks={forks} />
      )}
    </Card>
  )
}
