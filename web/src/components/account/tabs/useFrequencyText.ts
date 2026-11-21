import { useTranslation } from 'react-i18next'
import { frequencyParts } from '../account.utils'

const KEYS = { minute: 'frequency.everyMinutes', hour: 'frequency.everyHours', day: 'frequency.everyDays', week: 'frequency.everyWeeks' } as const

/** "every 90 d" for a subscription interval in seconds. */
export function useFrequencyText(): (seconds: number) => string {
  const { t } = useTranslation()
  return (seconds) => {
    const { unit, count } = frequencyParts(seconds)
    return t(KEYS[unit], { count })
  }
}
