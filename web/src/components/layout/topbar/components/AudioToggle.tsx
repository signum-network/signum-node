import { motion, AnimatePresence } from 'framer-motion'
import { sfx, useAudio } from '../../../../audio'

export function AudioToggle() {
  const { muted, setMuted, play } = useAudio()

  const handle = () => {
    if (muted) {
      setMuted(false)
      // Briefly defer so the new state propagates before we play.
      setTimeout(() => play(sfx.confirm), 30)
    } else {
      play(sfx.tick)
      setMuted(true)
    }
  }

  return (
    <motion.button
      type="button"
      onClick={handle}
      title={muted ? 'Audio off — click to enable' : 'Audio on — click to mute'}
      className="flex h-7 w-7 items-center justify-center"
      style={{
        border: '1px solid var(--border)',
        color: muted ? 'var(--muted)' : 'var(--blue2)',
      }}
      whileHover={{ borderColor: 'var(--border2)', scale: 1.05 }}
      whileTap={{ scale: 0.92 }}
      transition={{ type: 'spring', stiffness: 420, damping: 22 }}
    >
      <AnimatePresence mode="wait" initial={false}>
        {muted ? (
          <motion.svg
            key="muted"
            width="14" height="14" viewBox="0 0 16 16" fill="none"
            initial={{ opacity: 0, scale: 0.6 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.6 }}
            transition={{ duration: 0.12 }}
          >
            <path d="M3 5.5h2L8 3v10L5 10.5H3z" fill="currentColor" />
            <path d="M11 6l3 4M14 6l-3 4" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
          </motion.svg>
        ) : (
          <motion.svg
            key="on"
            width="14" height="14" viewBox="0 0 16 16" fill="none"
            initial={{ opacity: 0, scale: 0.6 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.6 }}
            transition={{ duration: 0.12 }}
          >
            <path d="M3 5.5h2L8 3v10L5 10.5H3z" fill="currentColor" />
            <path d="M10.5 5.5C11.4 6.1 12 7 12 8s-.6 1.9-1.5 2.5" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" fill="none" />
            <path d="M12.5 4C14 5 14.5 6.5 14.5 8s-.5 3-2 4" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" fill="none" opacity="0.7" />
          </motion.svg>
        )}
      </AnimatePresence>
    </motion.button>
  )
}
