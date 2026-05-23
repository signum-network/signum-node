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

/** Derive approximate network capacity in PiB from baseTarget. */
export function networkCapacityPiB(baseTarget: string | number): number {
  // Empirically calibrated: at baseTarget=56,463 → ~142 PiB
  const K = 8_208_760_704
  return Math.round(K / Number(baseTarget) / 1024)
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
