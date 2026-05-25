import { motion, AnimatePresence } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { Pill } from '@/components/ui/Pill'
import { useNodeSocket } from '@/hooks/useNodeSocket'

interface StatusStripProps {
  network?: string
  isFetching?: boolean
}

export function StatusStrip({ network = 'Mainnet', isFetching }: StatusStripProps) {
  const { t } = useTranslation()
  const { connected, wsEnabled } = useNodeSocket()

  return (
    <div className="flex items-center gap-2">
      <AnimatePresence>
        {isFetching && !connected && (
          <motion.span
            className="h-[6px] w-[6px] flex-shrink-0 rounded-full"
            style={{ background: 'var(--blue2)' }}
            initial={{ opacity: 0, scale: 0 }}
            animate={{ opacity: [1, 0.25, 1], scale: 1 }}
            exit={{ opacity: 0, scale: 0 }}
            transition={{
              opacity: { repeat: Infinity, duration: 1.2 },
              scale: { duration: 0.15 },
            }}
          />
        )}
      </AnimatePresence>

      {wsEnabled ? (
        <Pill variant={connected ? 'green' : 'amber'} dot={connected ? 'ok' : 'warn'}>
          {connected ? t('common.live') : t('common.connecting')}
        </Pill>
      ) : (
        <Pill variant="amber">{t('common.polling')}</Pill>
      )}

      <Pill variant="green" dot="ok">
        {network}
      </Pill>
    </div>
  )
}
