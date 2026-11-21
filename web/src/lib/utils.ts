import { clsx, type ClassValue } from 'clsx'

export function cn(...inputs: ClassValue[]) {
  return clsx(inputs)
}

export function fmt(n: number): string {
  return new Intl.NumberFormat().format(n)
}

export function fmtSigna(nqt: string | number): string {
  return (Number(nqt) / 1e8).toFixed(2)
}

export function fmtSignaRounded(nqt: string | number): string {
  return Math.round(Number(nqt) / 1e8).toString()
}

export function fmtDuration(secs: number): string {
  const m = Math.floor(secs / 60)
  const s = Math.round(secs % 60)
  return `${m}:${String(s).padStart(2, '0')}`
}

export function fmtCompact(n: number): string {
  if (n >= 1e9) return `${(n / 1e9).toFixed(2)}B`
  if (n >= 1e6) return `${(n / 1e6).toFixed(1)}M`
  if (n >= 1e3) return `${(n / 1e3).toFixed(1)}K`
  return fmt(n)
}

const GENESIS_BASE_TARGET = 18_325_193_796
// Sodium (log-deadline) correction used by the node itself — see GeneratorImpl.java line 373
const SODIUM_CORRECTION = 1.83

/** Physical network capacity in PiB.
 *  The node divides GENESIS_BASE_TARGET by 1.83 to correct for Sodium log-deadlines
 *  before using capacityBaseTarget for capacity estimation (GeneratorImpl.java:373). */
export function networkPhysicalCapacityPiB(baseTarget: string | number): number {
  return GENESIS_BASE_TARGET / SODIUM_CORRECTION / Number(baseTarget) / 1000
}

/** Effective network capacity in PiB (physical × PoC+ commitment boost, no Sodium correction). */
export function networkEffectiveCapacityPiB(baseTarget: string | number): number {
  return GENESIS_BASE_TARGET / Number(baseTarget) / 1000
}


export function categorizeVersion(
  peerVersion: string,
  nodeVersion: string,
): 'current' | 'outdated' | 'fork-risk' {
  const parse = (v: string) => v.replace(/^v/, '').split('.').map(Number)
  const [pMaj, pMin] = parse(peerVersion)
  const [nMaj, nMin] = parse(nodeVersion)
  if (pMaj !== nMaj) return 'fork-risk'
  if (pMin === nMin) return 'current'
  if (nMin - pMin <= 1) return 'outdated'
  return 'fork-risk'
}

/**
 * Formats an NQT amount (a string, may exceed 2^53) as SIGNA, exactly: thousands and decimal
 * separators from the locale, fractional digits truncated to `decimals`.
 */
export function fmtNqt(nqt: string, decimals = 2, locale?: string): string {
  const negative = nqt.startsWith('-')
  const value = BigInt(negative ? nqt.slice(1) : nqt)
  const ONE_SIGNA = 100_000_000n
  const format = new Intl.NumberFormat(locale)
  const integer = format.format(value / ONE_SIGNA)
  const fraction = (value % ONE_SIGNA).toString().padStart(8, '0').slice(0, decimals)
  const decimalSeparator = format.formatToParts(0.5).find((part) => part.type === 'decimal')?.value ?? '.'
  const sign = negative && value !== 0n ? '-' : ''
  return decimals > 0 ? `${sign}${integer}${decimalSeparator}${fraction}` : `${sign}${integer}`
}
