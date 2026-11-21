import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useBlockchainStatus, useLatestRelease } from '@/hooks/useNodeQuery'
import { browserStorage, dismiss, isDismissed, isNewer } from '@/lib/releaseCheck'

/** Strip under the top bar when GitHub has a newer stable release than this node runs. */
export function UpdateBanner() {
  const { t } = useTranslation()
  const current = useBlockchainStatus().data?.version
  const latest = useLatestRelease().data
  const [dismissedNow, setDismissedNow] = useState<string | null>(null)

  if (!current || !latest || !isNewer(latest.version, current)) return null
  if (dismissedNow === latest.version || isDismissed(browserStorage(), latest.version)) return null

  const hide = () => {
    dismiss(browserStorage(), latest.version)
    setDismissedNow(latest.version)
  }

  return (
    <div className="px-4 pt-2 sm:px-10 md:px-16 xl:px-20">
      <div
        role="status"
        className="flex items-start gap-3 border px-3 py-2 text-[11px] sm:items-center"
        style={{
          color: 'var(--amber)',
          borderColor: 'var(--amber)',
          borderLeftWidth: 3,
          background: 'var(--panel)',
        }}
      >
        <span className="min-w-0 flex-1 break-words">
          {t('update.available', { latest: latest.version, current })}
          {latest.url && (
            <>
              {' '}
              <a
                href={latest.url}
                target="_blank"
                rel="noopener noreferrer"
                className="whitespace-nowrap font-semibold underline underline-offset-2"
              >
                {t('update.releaseNotes')} ↗
              </a>
            </>
          )}
        </span>
        <button
          type="button"
          onClick={hide}
          aria-label={t('update.dismiss')}
          title={t('update.dismiss')}
          className="flex-shrink-0 px-1 text-[14px] leading-none opacity-80 hover:opacity-100"
        >
          ×
        </button>
      </div>
    </div>
  )
}
