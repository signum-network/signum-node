import { describe, it, expect } from 'vitest'
import { fmtNqt, fmtNqtFull, fmtUnits } from './utils'

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

describe('fmtNqtFull', () => {
  it('shows every significant decimal for detail pages', () => {
    expect(fmtNqtFull('500000', 'en-US')).toBe('0.005')
    expect(fmtNqtFull('735000', 'en-US')).toBe('0.00735')
    expect(fmtNqtFull('1', 'en-US')).toBe('0.00000001')
  })

  it('keeps at least two decimals', () => {
    expect(fmtNqtFull('100000000', 'en-US')).toBe('1.00')
    expect(fmtNqtFull('0', 'en-US')).toBe('0.00')
  })
})

describe('fmtUnits', () => {
  it('formats a token without decimals as an integer', () => {
    expect(fmtUnits('123', 0, undefined, 'en-US')).toBe('123')
  })

  it('formats all decimals by default', () => {
    expect(fmtUnits('100000000', 8, undefined, 'en-US')).toBe('1.00000000')
  })

  it('stays exact far above 2^53', () => {
    expect(fmtUnits('900719925474099312345', 4, 4, 'en-US')).toBe('90,071,992,547,409,931.2345')
  })
})
