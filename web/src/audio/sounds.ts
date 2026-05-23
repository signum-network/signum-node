/**
 * Sound effect palette — sci-fi, subtle, harmonizing.
 * Built on C major pentatonic (C D E G A) so any combination is consonant.
 * All sounds are sine-wave based with soft attacks and low-pass filtering.
 */

import { playNoise, playTone } from './synth'

// C major pentatonic across octaves 4–6
const C4 = 261.63
const E4 = 329.63
const G4 = 392.0
const A4 = 440.0
const C5 = 523.25
const D5 = 587.33
const E5 = 659.25
const G5 = 783.99
const A5 = 880.0
const C6 = 1046.5
const E6 = 1318.51
const G6 = 1568.0

export type SoundFn = (ctx: AudioContext, dest: AudioNode) => void

/** Soft pluck on click — perfect-fifth dyad (E5 + B5), very brief. */
export const click: SoundFn = (ctx, dest) => {
  playTone(ctx, dest, {
    freq: E5, duration: 0.16, attack: 0.002, release: 0.14,
    volume: 0.05, filter: { type: 'lowpass', freq: 2200 },
  })
  playTone(ctx, dest, {
    freq: G5, duration: 0.16, attack: 0.002, release: 0.12,
    volume: 0.03, filter: { type: 'lowpass', freq: 2400 },
  })
}

/** Brief hover blip — almost subliminal. */
export const hover: SoundFn = (ctx, dest) => {
  playTone(ctx, dest, {
    freq: G6, duration: 0.04, attack: 0.001, release: 0.035,
    volume: 0.012, filter: { type: 'lowpass', freq: 3200 },
  })
}

/** Ascending pentatonic confirmation — used for unmute & successful action. */
export const confirm: SoundFn = (ctx, dest) => {
  const seq = [C5, E5, G5, C6]
  seq.forEach((freq, i) => {
    playTone(ctx, dest, {
      freq, duration: 0.16, attack: 0.003, release: 0.14,
      volume: 0.045, startAt: i * 0.055,
      filter: { type: 'lowpass', freq: 3200 },
    })
  })
}

/** Soft bell chime — single A5 with quiet harmonic stack, used for new-block notification. */
export const chime: SoundFn = (ctx, dest) => {
  playTone(ctx, dest, {
    freq: A5, duration: 1.2, attack: 0.004, release: 1.1,
    volume: 0.07, filter: { type: 'lowpass', freq: 3000 },
  })
  playTone(ctx, dest, {
    freq: A5 * 2, duration: 0.9, attack: 0.004, release: 0.85,
    volume: 0.022, filter: { type: 'lowpass', freq: 4500 },
  })
  playTone(ctx, dest, {
    freq: A5 * 3, duration: 0.6, attack: 0.004, release: 0.55,
    volume: 0.01, filter: { type: 'lowpass', freq: 6000 },
  })
}

/** Whoosh — filtered noise burst, used for popovers / menu open. */
export const swish: SoundFn = (ctx, dest) => {
  playNoise(ctx, dest, {
    duration: 0.14, volume: 0.05,
    filter: { type: 'bandpass', freq: 2400, q: 1.8 },
  })
}

/** Theme-change arpeggio — broader, more rewarding chord. */
export const themeChange: SoundFn = (ctx, dest) => {
  const voices: Array<[number, number, number]> = [
    [C4, 0.00, 0.04],
    [G4, 0.025, 0.045],
    [C5, 0.05, 0.05],
    [E5, 0.075, 0.045],
    [G5, 0.1, 0.04],
    [C6, 0.13, 0.035],
  ]
  for (const [freq, startAt, vol] of voices) {
    playTone(ctx, dest, {
      freq, duration: 0.7, attack: 0.008, release: 0.65,
      volume: vol, startAt,
      filter: { type: 'lowpass', freq: 3400 },
    })
  }
}

/** Gentle descending minor — used for errors / warnings. Still smooth, never harsh. */
export const warn: SoundFn = (ctx, dest) => {
  playTone(ctx, dest, {
    freq: A4, duration: 0.22, attack: 0.004, release: 0.2,
    volume: 0.05, filter: { type: 'lowpass', freq: 2200 },
  })
  playTone(ctx, dest, {
    freq: E4, duration: 0.28, attack: 0.004, release: 0.26,
    volume: 0.05, startAt: 0.12,
    filter: { type: 'lowpass', freq: 1800 },
  })
}

/** Tiny tick used for theme card hover. Even more subtle than the global hover. */
export const tick: SoundFn = (ctx, dest) => {
  playTone(ctx, dest, {
    freq: E6, duration: 0.045, attack: 0.001, release: 0.04,
    volume: 0.018, filter: { type: 'lowpass', freq: 3600 },
  })
}

// Silence unused-export warnings — these are part of the public palette.
void D5
void G5
