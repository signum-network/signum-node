import type { ForkEvent } from '@/lib/nodeApi'
import { useTranslation } from 'react-i18next'
import { Card, CardLabel, CardSkeleton } from '@/components/ui/Card'
import { useForkHistory } from '@/hooks/useNodeQuery'

const MERGE_WINDOW_MS = 5_000

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

function fmtTs(ms: number) {
  return new Date(ms).toLocaleString()
}

export function ForkHistory() {
  const { data, isLoading } = useForkHistory()
  const { t } = useTranslation()
  const forks = mergeReorgs(data?.forks ?? [])

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
    </Card>
  )
}
