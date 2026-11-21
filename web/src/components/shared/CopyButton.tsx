import { useState } from 'react'
import { useTranslation } from 'react-i18next'

/** Copies a value to the clipboard; shows "copied" briefly, does nothing where the clipboard is unavailable. */
export function CopyButton({ value }: { value: string }) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      // no clipboard (insecure context, permission): leave the button as it is
    }
  }
  return (
    <button
      type="button"
      onClick={() => void copy()}
      aria-label={`${t('common.copy')} ${value}`}
      className="ml-1.5 rounded-sm px-1 text-[9px] uppercase tracking-[1px] transition-opacity hover:opacity-100"
      style={{ color: copied ? 'var(--green)' : 'var(--blue2)', opacity: copied ? 1 : 0.7 }}
    >
      {copied ? t('common.copied') : t('common.copy')}
    </button>
  )
}
