function relative(seconds: number, sign: 1 | -1, locale: string): string {
  const format = new Intl.RelativeTimeFormat(locale, { numeric: 'always' })
  if (seconds < 60) return format.format(sign * Math.round(seconds), 'second')
  if (seconds < 3_600) return format.format(sign * Math.round(seconds / 60), 'minute')
  if (seconds < 86_400) return format.format(sign * Math.round(seconds / 3_600), 'hour')
  return format.format(sign * Math.round(seconds / 86_400), 'day')
}

/** "3 minutes ago" in the given locale, for an age in seconds; a clock slightly ahead reads as 0. */
export function relativeTime(ageSeconds: number, locale: string): string {
  return relative(Math.max(0, ageSeconds), -1, locale)
}

/** "in 3 days" in the given locale, for a time ahead in seconds; a time already passed reads as 0. */
export function timeUntil(aheadSeconds: number, locale: string): string {
  return relative(Math.max(0, aheadSeconds), 1, locale)
}
