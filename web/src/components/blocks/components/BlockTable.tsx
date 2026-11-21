import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ScrollableTable } from '@/components/ui/ScrollableTable'
import { CardSkeleton } from '@/components/ui/Card'
import { AccountLink } from '@/components/shared/AccountLink'
import { signumTimestampToMs, type BlockDetail, type ForkEvent } from '@/lib/nodeApi'
import { fmtDuration, fmtNqt } from '@/lib/utils'
import { blockDeltaSeconds, deltaTone, relativeTime, rollbackCovering } from '../blocks.utils'

const TONE_COLOR = { fast: 'var(--green)', normal: 'var(--text)', slow: 'var(--gold)' } as const

function Th({ children, align = 'left' }: { children: React.ReactNode; align?: 'left' | 'right' }) {
  return (
    <th className={`px-3 py-2 first:pl-5 last:pr-5 text-${align}`}>
      <span className="text-[9px] uppercase tracking-[2px]" style={{ color: 'var(--muted)' }}>{children}</span>
    </th>
  )
}

interface BlockTableProps {
  blocks: BlockDetail[]
  forks: ForkEvent[]
  isLoading: boolean
}

/** Blocks newest first; the previous block of a row is the row below it. */
export function BlockTable({ blocks, forks, isLoading }: BlockTableProps) {
  const { t, i18n } = useTranslation()
  const nowSeconds = Date.now() / 1000

  return (
    <ScrollableTable
      maxHeight="none"
      head={
        <tr style={{ borderBottom: '1px solid var(--border)' }}>
          <Th>{t('blocks.colHeight')}</Th>
          <Th>{t('blocks.colTime')}</Th>
          <Th align="right">{t('blocks.colDelta')}</Th>
          <Th>{t('blocks.colGenerator')}</Th>
          <Th align="right">{t('blocks.colTxs')}</Th>
          <Th align="right">{t('blocks.colFees')}</Th>
          <Th>{''}</Th>
        </tr>
      }
    >
      {isLoading && (
        <tr>
          <td colSpan={7} className="px-5 py-6"><div className="h-4 w-48"><CardSkeleton /></div></td>
        </tr>
      )}
      {!isLoading && blocks.length === 0 && (
        <tr>
          <td colSpan={7} className="px-5 py-6 text-center" style={{ color: 'var(--muted)' }}>{t('blocks.empty')}</td>
        </tr>
      )}
      {blocks.map((block, i) => {
        const timeMs = signumTimestampToMs(block.timestamp)
        const delta = blockDeltaSeconds(block.timestamp, blocks[i + 1]?.timestamp)
        const rollback = rollbackCovering(block.height, forks)
        return (
          <tr key={block.block} style={{ borderBottom: '1px solid var(--border)' }}>
            <td className="py-2.5 pl-5 pr-3 tabular-nums">
              <Link to="/blocks/$height" params={{ height: String(block.height) }} style={{ color: 'var(--blue2)' }}>
                {block.height.toLocaleString()}
              </Link>
            </td>
            <td className="px-3 py-2.5 whitespace-nowrap" style={{ color: 'var(--muted)' }} title={new Date(timeMs).toLocaleString()}>
              {relativeTime(nowSeconds - timeMs / 1000, i18n.language)}
            </td>
            <td className="px-3 py-2.5 text-right tabular-nums" style={{ color: delta === null ? 'var(--muted)' : TONE_COLOR[deltaTone(delta)] }}>
              {delta === null ? '—' : fmtDuration(delta)}
            </td>
            <td className="px-3 py-2.5"><AccountLink id={block.generator} rs={block.generatorRS} /></td>
            <td className="px-3 py-2.5 text-right tabular-nums" style={{ color: 'var(--text)' }}>{block.numberOfTransactions}</td>
            <td className="px-3 py-2.5 text-right tabular-nums" style={{ color: 'var(--text)' }}>{fmtNqt(block.totalFeeNQT)}</td>
            <td className="py-2.5 pl-3 pr-5 text-right">
              {rollback && (
                <Link
                  to="/network"
                  title={t('blocks.rollbackMarker', { count: rollback.rollbackDepth })}
                  className="font-semibold"
                  style={{ color: 'var(--red, #ff4444)' }}
                >
                  ⑂
                </Link>
              )}
            </td>
          </tr>
        )
      })}
    </ScrollableTable>
  )
}
