import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ScrollableTable } from '@/components/ui/ScrollableTable'
import { useConstants } from '@/hooks/useNodeQuery'
import type { TransactionDetail } from '@/lib/nodeApi'
import { fmtNqt } from '@/lib/utils'
import { txAmountNqt, txTypeName } from '@/components/blocks/blocks.utils'
import { AccountLink } from './AccountLink'

function Th({ children, align = 'left' }: { children: React.ReactNode; align?: 'left' | 'right' }) {
  return (
    <th className={`px-3 py-2 first:pl-5 last:pr-5 text-${align}`}>
      <span className="text-[9px] uppercase tracking-[2px]" style={{ color: 'var(--muted)' }}>{children}</span>
    </th>
  )
}

/** Transactions with links to their detail page and to sender and recipient. */
export function TxTable({ transactions }: { transactions: TransactionDetail[] }) {
  const { t } = useTranslation()
  const { data: constants } = useConstants()

  if (transactions.length === 0) {
    return <p className="px-5 py-4 text-[11px]" style={{ color: 'var(--muted)' }}>{t('block.noTransactions')}</p>
  }

  return (
    <ScrollableTable
      maxHeight="none"
      head={
        <tr style={{ borderBottom: '1px solid var(--border)' }}>
          <Th>{t('block.colId')}</Th>
          <Th>{t('block.colType')}</Th>
          <Th>{t('block.colFrom')}</Th>
          <Th>{t('block.colTo')}</Th>
          <Th align="right">{t('block.colAmount')}</Th>
          <Th align="right">{t('block.colFee')}</Th>
        </tr>
      }
    >
      {transactions.map((tx) => (
        <tr key={tx.transaction} style={{ borderBottom: '1px solid var(--border)' }}>
          <td className="py-2.5 pl-5 pr-3 font-mono">
            <Link to="/tx/$id" params={{ id: tx.transaction }} title={tx.transaction} style={{ color: 'var(--blue2)' }}>
              {tx.transaction.slice(0, 8)}…
            </Link>
          </td>
          <td className="px-3 py-2.5" style={{ color: 'var(--text)' }}>{txTypeName(constants, tx.type, tx.subtype)}</td>
          <td className="px-3 py-2.5"><AccountLink id={tx.sender} rs={tx.senderRS} /></td>
          <td className="px-3 py-2.5">
            {tx.recipient ? <AccountLink id={tx.recipient} rs={tx.recipientRS} /> : <span style={{ color: 'var(--muted)' }}>—</span>}
          </td>
          <td className="px-3 py-2.5 text-right tabular-nums" style={{ color: 'var(--text)' }}>{fmtNqt(txAmountNqt(tx))}</td>
          <td className="py-2.5 pl-3 pr-5 text-right tabular-nums" style={{ color: 'var(--muted)' }}>{fmtNqt(tx.feeNQT)}</td>
        </tr>
      ))}
    </ScrollableTable>
  )
}
