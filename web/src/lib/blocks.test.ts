import { describe, it, expect } from 'vitest'
import { clampPage, trackBlockHeight, scrollLeftToShow } from './blocks'

describe('trackBlockHeight', () => {
  it('takes the height of the CONNECTED frame as seen', () => {
    // otherwise the first pushed block after a page load can never count as a replacement
    expect(trackBlockHeight(0, { type: 'connected', height: 1_000 })).toEqual({ highest: 1_000, replaced: false })
  })

  it('treats a pushed block at a height already seen as a replacement', () => {
    expect(trackBlockHeight(1_000, { type: 'pushed', height: 1_000 })).toEqual({ highest: 1_000, replaced: true })
    expect(trackBlockHeight(1_000, { type: 'pushed', height: 999 })).toEqual({ highest: 1_000, replaced: true })
  })

  it('moves on with a new block', () => {
    expect(trackBlockHeight(1_000, { type: 'pushed', height: 1_001 })).toEqual({ highest: 1_001, replaced: false })
  })
})

describe('clampPage', () => {
  it('keeps a page within the list', () => {
    expect(clampPage(2, 10, 25)).toBe(0)
    expect(clampPage(1, 30, 25)).toBe(1)
    expect(clampPage(5, 60, 25)).toBe(2)
    expect(clampPage(0, 0, 25)).toBe(0)
  })
})

describe('scrollLeftToShow', () => {
  it('scrolls a row of tabs just enough to show the item', () => {
    const container = { scrollLeft: 0, clientWidth: 300 }
    expect(scrollLeftToShow(container, { offsetLeft: 100, offsetWidth: 80 })).toBe(0)
    expect(scrollLeftToShow(container, { offsetLeft: 400, offsetWidth: 80 })).toBe(180)
    expect(scrollLeftToShow({ scrollLeft: 300, clientWidth: 300 }, { offsetLeft: 100, offsetWidth: 80 })).toBe(100)
  })
})
