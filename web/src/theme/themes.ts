export type ThemeId = 'nexus' | 'solaris' | 'dawn' | 'terminal' | 'aurora'

export interface ThemeMeta {
  id: ThemeId
  label: string
  description: string
  colors: {
    bg: string
    accent: string
    green: string
    mag: string
    text: string
    muted: string
  }
}

export const THEMES: ThemeMeta[] = [
  {
    id: 'nexus',
    label: 'Nexus',
    description: 'Cyberpunk deep blue',
    colors: { bg: '#050810', accent: '#00aaff', green: '#00ffaa', mag: '#ff0055', text: '#d0e4ff', muted: '#5a7090' },
  },
  {
    id: 'solaris',
    label: 'Solaris',
    description: 'Desert amber heat',
    colors: { bg: '#0d0906', accent: '#ffd700', green: '#39d353', mag: '#ff3d6b', text: '#ffe8c0', muted: '#9a7040' },
  },
  {
    id: 'dawn',
    label: 'Dawn',
    description: 'Clean & luminous',
    colors: { bg: '#eef2fb', accent: '#0066ff', green: '#00956b', mag: '#cc0044', text: '#1a2540', muted: '#6678a0' },
  },
  {
    id: 'terminal',
    label: 'Terminal',
    description: 'Green phosphor CRT',
    colors: { bg: '#000a00', accent: '#00ff00', green: '#00ff00', mag: '#ff3300', text: '#90ee90', muted: '#3a7a3a' },
  },
  {
    id: 'aurora',
    label: 'Aurora',
    description: 'Northern violet glow',
    colors: { bg: '#060412', accent: '#aa44ff', green: '#00ffcc', mag: '#ff0088', text: '#e0d0ff', muted: '#6050aa' },
  },
]
