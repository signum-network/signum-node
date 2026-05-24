import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'
import en from './locales/en'
import es from './locales/es'
import pt from './locales/pt'
import de from './locales/de'
import uk from './locales/uk'
import ru from './locales/ru'
import zh from './locales/zh'
import ja from './locales/ja'
import ko from './locales/ko'
import hi from './locales/hi'

export const LANGUAGES = [
  { code: 'en', flag: '🇬🇧', label: 'English' },
  { code: 'es', flag: '🇪🇸', label: 'Español' },
  { code: 'pt', flag: '🇧🇷', label: 'Português' },
  { code: 'de', flag: '🇩🇪', label: 'Deutsch' },
  { code: 'uk', flag: '🇺🇦', label: 'Українська' },
  { code: 'ru', flag: '🇷🇺', label: 'Русский' },
  { code: 'zh', flag: '🇨🇳', label: '中文' },
  { code: 'ja', flag: '🇯🇵', label: '日本語' },
  { code: 'ko', flag: '🇰🇷', label: '한국어' },
  { code: 'hi', flag: '🇮🇳', label: 'हिन्दी' },
] as const

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: { en: { translation: en }, es: { translation: es }, pt: { translation: pt },
                 de: { translation: de }, uk: { translation: uk }, ru: { translation: ru },
                 zh: { translation: zh }, ja: { translation: ja }, ko: { translation: ko },
                 hi: { translation: hi } },
    fallbackLng: 'en',
    detection: { order: ['localStorage', 'navigator'], caches: ['localStorage'] },
    interpolation: { escapeValue: false },
  })

export default i18n
