import { useTranslation } from 'react-i18next'
import { Card, CardLabel, CardSkeleton } from '@/components/ui/Card'
import { cn } from '@/lib/utils'
import { useFindForkPoint } from '@/hooks/useNodeQuery'

interface RowProps {
  label: string
  value: string
  mono?: boolean
}

function Row({ label, value, mono }: RowProps) {
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

interface ForkPointModalProps {
  peer: string
  onClose: () => void
}

export function ForkPointModal({ peer, onClose }: ForkPointModalProps) {
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
        <CardLabel tooltip={t('info.findFork')}>{t('network.forkPoint.title')}</CardLabel>
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
            {data.forkTooOld ? (
              <p className="text-[11px]" style={{ color: 'var(--gold)' }}>
                {t('network.forkPoint.tooOld', { limit: 10000 })}
              </p>
            ) : data.error ? (
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
