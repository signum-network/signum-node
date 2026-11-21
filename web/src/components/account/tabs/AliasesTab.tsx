import { useCallback, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Card, CardSkeleton } from '@/components/ui/Card'
import { AccountLink } from '@/components/shared/AccountLink'
import { Pager } from '@/components/blocks/components/Pager'
import {
  aliasesCacheKey,
  useAccountAliasesCached,
  useBlockchainStatus,
  useNetworkGenesis,
  useRefetchWhenOverdue,
} from '@/hooks/useNodeQuery'
import { signumTimestampToMs } from '@/lib/nodeApi'
import { fmtNqt } from '@/lib/utils'
import { isOverdue, renewalFor } from '../account.utils'
import { EmptyRow, TabTable } from './TabTable'
import { useFrequencyText } from './useFrequencyText'

/** The account's aliases with their renewal, from one cache entry with the subscriptions behind them. */
export function AliasesTab({ accountId }: { accountId: string }) {
  const { t } = useTranslation()
  const frequencyText = useFrequencyText()
  const [page, setPage] = useState(0)
  const genesis = useNetworkGenesis()
  const { data, isLoading, isError, refetch } = useAccountAliasesCached(accountId, page, true)
  const nodeTime = useBlockchainStatus().data?.time
  const aliases = data?.aliases ?? []
  const subscriptions = data?.subscriptions ?? []

  const overdue = (timeNext: number) => nodeTime !== undefined && isOverdue(timeNext, nodeTime)
  const anyOverdue = aliases.some((a) => {
    const renewal = renewalFor(a.alias, subscriptions)
    return renewal !== undefined && overdue(renewal.timeNext)
  })
  const refetchNow = useCallback(() => void refetch(), [refetch])
  useRefetchWhenOverdue(anyOverdue, genesis && aliasesCacheKey(genesis, accountId, page), refetchNow)

  return (
    <Card className="overflow-hidden p-0">
      {isLoading && <div className="mx-5 my-4 h-4 w-48"><CardSkeleton /></div>}
      {isError && <p className="px-5 py-4 text-[11px]" style={{ color: 'var(--gold)' }}>{t('account.loadError')}</p>}
      {data && (
        <TabTable
          heads={[
            { label: t('aliases.colAlias') },
            { label: t('aliases.colUri') },
            { label: t('aliases.colCreated') },
            { label: t('aliases.colOffer') },
            { label: t('aliases.colRenewal') },
          ]}
        >
          {aliases.length === 0 && <EmptyRow colSpan={5} text={t('aliases.empty')} />}
          {aliases.map((alias) => {
            const renewal = renewalFor(alias.alias, subscriptions)
            return (
              <tr key={alias.alias} style={{ borderBottom: '1px solid var(--border)' }}>
                <td className="py-2.5 pl-5 pr-3 font-mono" style={{ color: 'var(--text)' }}>
                  {alias.aliasName}{alias.tldName ? `.${alias.tldName}` : ''}
                </td>
                {/* user-controlled: text only, never a link */}
                <td className="max-w-[16rem] truncate px-3 py-2.5" style={{ color: 'var(--muted)' }} title={alias.aliasURI}>
                  {alias.aliasURI || '—'}
                </td>
                <td className="px-3 py-2.5 whitespace-nowrap" style={{ color: 'var(--muted)' }}>
                  {new Date(signumTimestampToMs(alias.timestamp)).toLocaleDateString()}
                </td>
                <td className="px-3 py-2.5 whitespace-nowrap">
                  {alias.priceNQT ? (
                    <span style={{ color: 'var(--text)' }}>
                      {fmtNqt(alias.priceNQT)}
                      {alias.buyer && <> · <AccountLink id={alias.buyer} rs={alias.buyerRS} /></>}
                    </span>
                  ) : <span style={{ color: 'var(--muted)' }}>—</span>}
                </td>
                <td className="py-2.5 pl-3 pr-5 whitespace-nowrap">
                  {!renewal && <span style={{ color: 'var(--muted)' }}>{t('aliases.noExpiry')}</span>}
                  {renewal && overdue(renewal.timeNext) && <span style={{ color: 'var(--gold)' }}>{t('aliases.renewalDue')}</span>}
                  {renewal && !overdue(renewal.timeNext) && (
                    <span style={{ color: 'var(--text)' }}>
                      {t('aliases.renewsOn', {
                        date: new Date(signumTimestampToMs(renewal.timeNext)).toLocaleDateString(),
                        amount: fmtNqt(renewal.amountNQT),
                        every: frequencyText(renewal.frequency),
                      })}
                    </span>
                  )}
                </td>
              </tr>
            )
          })}
        </TabTable>
      )}
      {(page > 0 || data?.nextIndex !== undefined) && (
        <div className="px-5 py-4">
          <Pager
            page={page + 1}
            hasNewer={page > 0}
            hasOlder={data?.nextIndex !== undefined}
            onNewer={() => setPage(page - 1)}
            onOlder={() => setPage(page + 1)}
          />
        </div>
      )}
    </Card>
  )
}
