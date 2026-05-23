export const tokens = {
  bg:      '#050810',
  bg2:     '#080d1a',
  panel:   'rgba(8,16,40,.85)',
  border:  'rgba(0,102,255,.18)',
  border2: 'rgba(0,170,255,.3)',
  blue:    '#0066ff',
  blue2:   '#00aaff',
  blue3:   '#60c8ff',
  green:   '#00ffaa',
  mag:     '#ff0055',
  gold:    '#ffd700',
  amber:   '#ff9500',
  text:    '#d0e4ff',
  muted:   '#3a5070',
} as const

export type TokenColor = keyof typeof tokens
