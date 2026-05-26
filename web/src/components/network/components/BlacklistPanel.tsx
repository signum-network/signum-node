import { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { Card, CardLabel, CardSkeleton } from '@/components/ui/Card'
import { useNetworkStatus, useBlacklist } from '@/hooks/useNodeQuery'

export function BlacklistPanel() {
  const { data: statusData } = useNetworkStatus()
  const { data: blacklistData, isLoading } = useBlacklist()
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [copiedAll, setCopiedAll] = useState(false)

  const blacklisted = blacklistData?.blacklisted ?? []
  const forkingPeers = (statusData?.peers ?? []).filter((p) => p.status === 'forking' && !p.blacklisted)

  const recommendations = [
    ...forkingPeers.map((p) => ({ address: p.address, reason: 'Consistently on different chain' })),
    ...blacklisted.map((p) => ({ address: p.address, reason: p.reason || 'Blacklisted by node' })),
  ].filter((v, i, arr) => arr.findIndex((x) => x.address === v.address) === i)

  const copy = (text: string) => void navigator.clipboard.writeText(text)

  const copyForConfig = () => {
    const value = recommendations.map((r) => r.address).join(';')
    void navigator.clipboard.writeText(value)
    setCopiedAll(true)
    setTimeout(() => setCopiedAll(false), 2000)
  }

  return (
    <Card className="col-span-full md:col-span-2">
      <button
        type="button"
        className="flex w-full items-center justify-between"
        onClick={() => setOpen(!open)}
      >
        <CardLabel className="mb-0" tooltip={t('info.blacklistRecommendations')}>
          {t('network.blacklistRecommendations')}
        </CardLabel>
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
                <div className="flex items-start justify-between gap-3 pt-1">
                  <p className="text-[9px]" style={{ color: 'var(--muted)' }}>
                    {t('network.blacklistHint')}
                  </p>
                  <button
                    type="button"
                    className="flex-shrink-0 text-[9px] uppercase tracking-[1px] transition-opacity hover:opacity-100"
                    style={{ color: copiedAll ? 'var(--green)' : 'var(--blue2)', opacity: copiedAll ? 1 : 0.7 }}
                    onClick={copyForConfig}
                  >
                    {copiedAll ? '✓ copied' : t('network.copyForConfig')}
                  </button>
                </div>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </Card>
  )
}
