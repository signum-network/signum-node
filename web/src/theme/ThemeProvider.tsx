import { createContext, useContext, useEffect, useState } from 'react'
import type { ThemeId } from './themes'

interface ThemeContextValue {
  theme: ThemeId
  setTheme: (id: ThemeId) => void
}

export const ThemeContext = createContext<ThemeContextValue>({
  theme: 'nexus',
  setTheme: () => {},
})

const STORAGE_KEY = 'signum-neo-theme'

function readStoredTheme(): ThemeId {
  try {
    return (localStorage.getItem(STORAGE_KEY) as ThemeId) ?? 'nexus'
  } catch {
    return 'nexus'
  }
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setThemeState] = useState<ThemeId>(readStoredTheme)

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    try { localStorage.setItem(STORAGE_KEY, theme) } catch { /* noop */ }
  }, [theme])

  // Apply immediately on first paint (before React hydration flash)
  useEffect(() => {
    document.documentElement.dataset.theme = readStoredTheme()
  }, [])

  return (
    <ThemeContext.Provider value={{ theme, setTheme: setThemeState }}>
      {children}
    </ThemeContext.Provider>
  )
}

export function useTheme() {
  return useContext(ThemeContext)
}
