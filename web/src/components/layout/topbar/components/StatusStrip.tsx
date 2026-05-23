import { motion, AnimatePresence } from 'framer-motion'
import { Pill } from '@/components/ui/Pill'

interface StatusStripProps {
  network?: string
  isFetching?: boolean
}

export function StatusStrip({ network = 'Mainnet', isFetching }: StatusStripProps) {
  return (
    <div className="flex items-center">
      <AnimatePresence>
        {isFetching && (
          <motion.span
            className="h-[6px] w-[6px] rounded-full"
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

      <Pill variant="green" dot="ok">
        {network}
      </Pill>
    </div>
  )
}
