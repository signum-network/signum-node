import {
  createContext, useCallback, useContext, useEffect, useMemo, useRef, useState,
  type ReactNode,
} from 'react'
import { createSpaceFx } from './synth'
import type { SoundFn } from './sounds'

interface AudioAPI {
  play: (sound: SoundFn) => void
  muted: boolean
  setMuted: (m: boolean) => void
  volume: number
  setVolume: (v: number) => void
}

const Ctx = createContext<AudioAPI | null>(null)

const STORAGE_MUTED = 'signum-audio-muted'
const STORAGE_VOLUME = 'signum-audio-volume'

interface AudioContextLike {
  ctx: AudioContext
  master: GainNode
  fxIn: GainNode
}

export function AudioProvider({ children }: { children: ReactNode }) {
  const [muted, setMutedState] = useState<boolean>(() => {
    if (typeof window === 'undefined') return false
    return window.localStorage.getItem(STORAGE_MUTED) === '1'
  })
  const [volume, setVolumeState] = useState<number>(() => {
    if (typeof window === 'undefined') return 0.7
    const v = window.localStorage.getItem(STORAGE_VOLUME)
    const parsed = v ? parseFloat(v) : 0.7
    return Number.isFinite(parsed) ? Math.min(1, Math.max(0, parsed)) : 0.7
  })

  const audioRef = useRef<AudioContextLike | null>(null)

  // Keep master gain in sync with volume.
  useEffect(() => {
    if (audioRef.current) audioRef.current.master.gain.value = volume
  }, [volume])

  const setMuted = useCallback((m: boolean) => {
    setMutedState(m)
    try { localStorage.setItem(STORAGE_MUTED, m ? '1' : '0') } catch { /* ignore */ }
  }, [])

  const setVolume = useCallback((v: number) => {
    const clamped = Math.min(1, Math.max(0, v))
    setVolumeState(clamped)
    try { localStorage.setItem(STORAGE_VOLUME, clamped.toString()) } catch { /* ignore */ }
  }, [])

  const ensureAudio = useCallback((): AudioContextLike | null => {
    if (audioRef.current) return audioRef.current
    if (typeof window === 'undefined') return null
    const AC =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!AC) return null
    try {
      const ctx = new AC()
      const master = ctx.createGain()
      master.gain.value = volume
      const space = createSpaceFx(ctx)
      space.output.connect(master)
      master.connect(ctx.destination)
      audioRef.current = { ctx, master, fxIn: space.input }
      return audioRef.current
    } catch {
      return null
    }
  }, [volume])

  const play = useCallback((sound: SoundFn) => {
    if (muted) return
    const audio = ensureAudio()
    if (!audio) return
    const { ctx, fxIn } = audio
    const fire = () => {
      try { sound(ctx, fxIn) } catch { /* ignore one-off failure */ }
    }
    if (ctx.state === 'suspended') {
      ctx.resume().then(fire).catch(() => { /* ignore */ })
    } else {
      fire()
    }
  }, [muted, ensureAudio])

  const value = useMemo<AudioAPI>(() => ({
    play, muted, setMuted, volume, setVolume,
  }), [play, muted, setMuted, volume, setVolume])

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useAudio(): AudioAPI {
  const v = useContext(Ctx)
  if (!v) throw new Error('useAudio must be used within <AudioProvider>')
  return v
}
