import { useTranslation } from 'react-i18next'
import { useBlockPeerCheck } from '@/hooks/useNodeQuery'
import { peerCheckTone, relativeTime } from '../blocks.utils'

const TONE_COLOR = { ok: 'var(--green)', warn: 'var(--text)', muted: 'var(--muted)' } as const

/** "Ask peers" button and the answer: how many connected peers have this exact block. */
export function PeerCheck({ blockId }: { blockId: string }) {
  const { t, i18n } = useTranslation()
  const check = useBlockPeerCheck()
  const result = check.data

  if (result?.status === 'tooOld') {
    return <span style={{ color: 'var(--muted)' }}>{t('blockPeers.peerTooOld')}</span>
  }

  return (
    <div className="flex flex-col items-start gap-1.5">
      <button
        type="button"
        disabled={check.isPending}
        onClick={() => check.mutate(blockId)}
        className="rounded-sm px-2 py-1 text-[9px] uppercase tracking-[1px] transition-opacity"
        style={{ border: '1px solid var(--blue2)', color: 'var(--blue2)', opacity: check.isPending ? 0.5 : 1 }}
      >
        {check.isPending ? t('blockPeers.peerChecking') : t('blockPeers.peerCheck')}
      </button>
      {result?.status === 'busy' && <span style={{ color: 'var(--gold)' }}>{t('blockPeers.peerBusy')}</span>}
      {result === null && <span style={{ color: 'var(--gold)' }}>{t('blockPeers.peerUnknownBlock')}</span>}
      {check.isError && <span style={{ color: 'var(--gold)' }}>{t('blockPeers.peerError')}</span>}
      {result?.status === 'ok' && (() => {
        const total = result.hasIt + result.missing + result.notYet + result.noAnswer
        const age = relativeTime((result.serverTime - result.checkedAt) / 1000, i18n.language)
        return (
          <span>
            <span style={{ color: TONE_COLOR[peerCheckTone(result)] }}>
              {t('blockPeers.peerResult', { hasIt: result.hasIt, total })}
            </span>
            {result.missing > 0 && <span style={{ color: 'var(--red, #ff4444)' }}> · {t('blockPeers.peerMissing', { count: result.missing })}</span>}
            {result.notYet > 0 && <span style={{ color: 'var(--muted)' }}> · {t('blockPeers.peerNotYet', { count: result.notYet })}</span>}
            {result.noAnswer > 0 && <span style={{ color: 'var(--muted)' }}> · {t('blockPeers.peerNoAnswer', { count: result.noAnswer })}</span>}
            <span style={{ color: 'var(--muted)' }}> · {age}</span>
          </span>
        )
      })()}
    </div>
  )
}
