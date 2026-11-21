import { useTranslation } from 'react-i18next'
import { Card } from '@/components/ui/Card'
import { AccountLink } from '@/components/shared/AccountLink'
import { CopyButton } from '@/components/shared/CopyButton'
import { Pager } from '@/components/blocks/components/Pager'
import { useAssetMetas } from '@/hooks/useNodeQuery'
import type { AccountDetail } from '@/lib/nodeApi'
import { fmtUnits } from '@/lib/utils'
import { EmptyRow, TabTable } from './TabTable'

const PAGE_SIZE = 25

/** The account's tokens: balances come with the account, metadata only for the visible page. */
export function TokensTab({ account, page, onPage }: { account: AccountDetail; page: number; onPage: (page: number) => void }) {
  const { t } = useTranslation()
  const setPage = onPage
  const balances = account.assetBalances ?? []
  const visible = balances.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)
  const metas = useAssetMetas(visible.map((b) => b.asset), true)

  return (
    <Card className="overflow-hidden p-0">
      <TabTable
        heads={[
          { label: t('tokens.colName') },
          { label: t('tokens.colBalance'), align: 'right' },
          { label: t('tokens.colToken') },
          { label: t('tokens.colIssuer') },
        ]}
      >
        {visible.length === 0 && <EmptyRow colSpan={4} text={t('tokens.empty')} />}
        {visible.map((balance, i) => {
          const meta = metas[i]?.data ?? null
          const failed = metas[i]?.isError || (metas[i]?.isSuccess && meta === null)
          return (
            <tr key={balance.asset} style={{ borderBottom: '1px solid var(--border)' }}>
              <td className="py-2.5 pl-5 pr-3" style={{ color: 'var(--text)' }} title={meta?.description}>
                {meta?.name ?? '—'}
              </td>
              <td className="px-3 py-2.5 text-right tabular-nums" style={{ color: 'var(--text)' }}>
                {meta ? fmtUnits(balance.balanceQNT, meta.decimals) : balance.balanceQNT}
                {failed && <div className="text-[9px]" style={{ color: 'var(--muted)' }}>{t('tokens.metaMissing')}</div>}
              </td>
              <td className="px-3 py-2.5 font-mono" style={{ color: 'var(--muted)' }} title={balance.asset}>
                {balance.asset.slice(0, 8)}…
                <CopyButton value={balance.asset} />
              </td>
              <td className="py-2.5 pl-3 pr-5">
                {meta ? <AccountLink id={meta.issuer} rs={meta.issuerRS} /> : <span style={{ color: 'var(--muted)' }}>—</span>}
              </td>
            </tr>
          )
        })}
      </TabTable>
      {balances.length > PAGE_SIZE && (
        <div className="px-5 py-4">
          <Pager
            page={page + 1}
            hasNewer={page > 0}
            hasOlder={(page + 1) * PAGE_SIZE < balances.length}
            onNewer={() => setPage(page - 1)}
            onOlder={() => setPage(page + 1)}
          />
        </div>
      )}
    </Card>
  )
}
