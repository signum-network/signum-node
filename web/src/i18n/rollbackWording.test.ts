import { describe, it, expect } from 'vitest'
import en from './locales/en'
import de from './locales/de'
import es from './locales/es'
import pt from './locales/pt'
import ru from './locales/ru'
import uk from './locales/uk'

// a rollback of depth 1 is the most common one; the wording must not need a plural form
// hi, ja, ko and zh do not inflect the noun here
const LOCALES = { en, de, es, pt, ru, uk }
const SINGULAR_ERRORS: Record<string, RegExp> = {
  // no \b: it only knows ASCII letters and never matches after Cyrillic or Devanagari text
  en: /1 blocks/, de: /1 Blöcke/, es: /1 bloques/, pt: /1 blocos/,
  ru: /1 блоков/, uk: /1 блоків/,
}

describe('rollback wording', () => {
  for (const [code, locale] of Object.entries(LOCALES)) {
    it(`${code} reads correctly for a rollback of one block`, () => {
      for (const text of [locale.blocks.rollbackMarker, locale.block.rollbackText]) {
        expect(text.replace('{{count}}', '1')).not.toMatch(SINGULAR_ERRORS[code])
        expect(text).toContain('{{count}}')
      }
    })
  }
})
