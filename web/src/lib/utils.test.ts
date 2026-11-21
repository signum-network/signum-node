import { describe, it, expect } from 'vitest'
import { fmtNqt } from './utils'

describe('fmtNqt', () => {
  it('formats NQT as SIGNA with two decimals', () => {
    expect(fmtNqt('325419758438761', 2, 'en-US')).toBe('3,254,197.58')
  })

  it('stays exact above 2^53 NQT', () => {
    expect(fmtNqt('200000000000000001', 8, 'en-US')).toBe('2,000,000,000.00000001')
  })

  it('truncates instead of rounding', () => {
    expect(fmtNqt('199', 2, 'en-US')).toBe('0.00')
    expect(fmtNqt('1', 8, 'en-US')).toBe('0.00000001')
  })

  it('formats zero', () => {
    expect(fmtNqt('0', 2, 'en-US')).toBe('0.00')
  })

  it('uses the locale separators', () => {
    expect(fmtNqt('325419758438761', 2, 'de-DE')).toBe('3.254.197,58')
  })

  it('keeps the sign of a negative amount', () => {
    expect(fmtNqt('-150000000', 2, 'en-US')).toBe('-1.50')
  })
})
