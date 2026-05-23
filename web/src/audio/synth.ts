/**
 * Low-level Web Audio synth helpers.
 * Sounds are built from sine oscillators + low-pass filters for warmth.
 */

interface ToneOptions {
  freq: number
  duration: number
  type?: OscillatorType
  attack?: number       // seconds — how fast the note swells in
  release?: number      // seconds — how long the note tails out
  volume?: number       // 0–1
  detune?: number       // cents
  startAt?: number      // schedule offset from "now"
  filter?: { type: BiquadFilterType; freq: number; q?: number }
}

export function playTone(ctx: AudioContext, destination: AudioNode, opts: ToneOptions) {
  const {
    freq, duration, type = 'sine',
    attack = 0.004, release, volume = 0.08,
    detune = 0, startAt = 0, filter,
  } = opts

  const osc = ctx.createOscillator()
  const gain = ctx.createGain()
  osc.type = type
  osc.frequency.value = freq
  osc.detune.value = detune

  let last: AudioNode = osc
  if (filter) {
    const f = ctx.createBiquadFilter()
    f.type = filter.type
    f.frequency.value = filter.freq
    if (filter.q !== undefined) f.Q.value = filter.q
    last.connect(f)
    last = f
  }
  last.connect(gain)
  gain.connect(destination)

  const start = ctx.currentTime + startAt
  const rel = release ?? Math.max(duration - attack, 0.05)

  gain.gain.setValueAtTime(0.00001, start)
  gain.gain.exponentialRampToValueAtTime(volume, start + attack)
  gain.gain.exponentialRampToValueAtTime(0.00001, start + attack + rel)

  osc.start(start)
  osc.stop(start + attack + rel + 0.05)
}

export function playNoise(ctx: AudioContext, destination: AudioNode, opts: {
  duration: number
  volume?: number
  startAt?: number
  filter?: { type: BiquadFilterType; freq: number; q?: number }
}) {
  const { duration, volume = 0.04, startAt = 0, filter } = opts
  const bufferSize = Math.max(1, Math.floor(ctx.sampleRate * duration))
  const buffer = ctx.createBuffer(1, bufferSize, ctx.sampleRate)
  const data = buffer.getChannelData(0)
  for (let i = 0; i < bufferSize; i++) data[i] = Math.random() * 2 - 1

  const src = ctx.createBufferSource()
  src.buffer = buffer
  const gain = ctx.createGain()

  let last: AudioNode = src
  if (filter) {
    const f = ctx.createBiquadFilter()
    f.type = filter.type
    f.frequency.value = filter.freq
    if (filter.q !== undefined) f.Q.value = filter.q
    last.connect(f)
    last = f
  }
  last.connect(gain)
  gain.connect(destination)

  const start = ctx.currentTime + startAt
  gain.gain.setValueAtTime(volume, start)
  gain.gain.exponentialRampToValueAtTime(0.00001, start + duration)

  src.start(start)
  src.stop(start + duration + 0.05)
}

/**
 * Space FX: dry signal + feedback-delayed/low-passed echo for an ambient tail.
 * Returns an input node — connect sounds into it and connect its output to destination.
 */
export function createSpaceFx(ctx: AudioContext) {
  const input = ctx.createGain()
  const dry = ctx.createGain()
  const wet = ctx.createGain()
  const delay = ctx.createDelay(1.0)
  const feedback = ctx.createGain()
  const filter = ctx.createBiquadFilter()
  const output = ctx.createGain()

  dry.gain.value = 1.0
  wet.gain.value = 0.32
  delay.delayTime.value = 0.21
  feedback.gain.value = 0.36
  filter.type = 'lowpass'
  filter.frequency.value = 2600

  input.connect(dry)
  dry.connect(output)

  input.connect(delay)
  delay.connect(filter)
  filter.connect(feedback)
  feedback.connect(delay)
  filter.connect(wet)
  wet.connect(output)

  return { input, output }
}
