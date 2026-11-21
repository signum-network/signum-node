import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { getAccount, getBlockById, getTransaction } from '@/lib/nodeApi'
import { classifySearch, resolveSearch } from '../blocks.utils'

const searchApi = { blockById: getBlockById, transaction: getTransaction, account: getAccount }

/** One field for a height, block ID, transaction ID or address; opens the matching page. */
export function BlockSearch({ tipHeight }: { tipHeight: number }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [input, setInput] = useState('')
  const [searching, setSearching] = useState(false)
  const [notFound, setNotFound] = useState<string | null>(null)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    const query = input.trim()
    if (!query) return
    setSearching(true)
    setNotFound(null)
    try {
      const hit = await resolveSearch(classifySearch(query, tipHeight), searchApi)
      if (hit) {
        void navigate(hit)
      } else {
        setNotFound(query)
      }
    } catch {
      // an ID the node rejects (e.g. "Incorrect block") is just not found for the user
      setNotFound(query)
    } finally {
      setSearching(false)
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-2">
      <div className="flex gap-2">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder={t('blocks.searchPlaceholder')}
          aria-label={t('blocks.search')}
          className="min-w-0 flex-1 rounded-sm px-3 py-2 font-mono text-[12px] outline-none"
          style={{ background: 'var(--bg2)', border: '1px solid var(--border)', color: 'var(--text)' }}
        />
        <button
          type="submit"
          disabled={searching}
          className="rounded-sm px-4 py-2 text-[10px] uppercase tracking-[2px] transition-opacity"
          style={{ border: '1px solid var(--blue2)', color: 'var(--blue2)', opacity: searching ? 0.5 : 1 }}
        >
          {searching ? t('blocks.searching') : t('blocks.search')}
        </button>
      </div>
      {notFound && (
        <p className="text-[11px]" style={{ color: 'var(--gold)' }} role="status">
          {t('blocks.notFound', { query: notFound })}
        </p>
      )}
    </form>
  )
}
