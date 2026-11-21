import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { Card, CardLabel, CardSkeleton } from '@/components/ui/Card'
import { AccountLink } from '@/components/shared/AccountLink'
import { timeUntil } from '@/lib/format'
import {
  subscriptionsCacheKey,
  useAccountSubscriptionsCached,
  useBlockchainStatus,
  useNetworkGenesis,
  useRefetchWhenOverdue,
} from '@/hooks/useNodeQuery'
import { signumTimestampToMs, type Subscription } from '@/lib/nodeApi'
import { fmtNqt } from '@/lib/utils'
import { isOverdue, splitSubscriptions } from '../account.utils'
import { EmptyRow, TabTable } from './TabTable'
import { useFrequencyText } from './useFrequencyText'

function SubscriptionList({ title, subscriptions, counterpart, nodeTime }: {
  title: string
  subscriptions: Subscription[]
  counterpart: (s: Subscription) => { id: string; rs: string }
  nodeTime: number | undefined
}) {
  const { t, i18n } = useTranslation()
  const frequencyText = useFrequencyText()
  return (
    <Card className="overflow-hidden p-0">
      <div className="px-5 pt-5"><CardLabel>{title}</CardLabel></div>
      <TabTable
        heads={[
          { label: t('subscriptions.colCounterpart') },
          { label: t('subscriptions.colAmount'), align: 'right' },
          { label: t('subscriptions.colFrequency') },
          { label: t('subscriptions.colNext') },
          { label: t('subscriptions.colAlias') },
        ]}
      >
        {subscriptions.length === 0 && <EmptyRow colSpan={5} text={t('subscriptions.empty')} />}
        {subscriptions.map((sub) => {
          const party = counterpart(sub)
          const due = nodeTime !== undefined && isOverdue(sub.timeNext, nodeTime)
          const nextMs = signumTimestampToMs(sub.timeNext)
          return (
            <tr key={sub.id} style={{ borderBottom: '1px solid var(--border)' }}>
              <td className="py-2.5 pl-5 pr-3"><AccountLink id={party.id} rs={party.rs} /></td>
              <td className="px-3 py-2.5 text-right tabular-nums" style={{ color: 'var(--text)' }}>{fmtNqt(sub.amountNQT)}</td>
              <td className="px-3 py-2.5 whitespace-nowrap" style={{ color: 'var(--text)' }}>{frequencyText(sub.frequency)}</td>
              <td className="px-3 py-2.5 whitespace-nowrap" title={new Date(nextMs).toLocaleString()}>
                {due
                  ? <span style={{ color: 'var(--gold)' }}>{t('subscriptions.due')}</span>
                  : <span style={{ color: 'var(--muted)' }}>{new Date(nextMs).toLocaleDateString()} · {timeUntil((nextMs - Date.now()) / 1000, i18n.language)}</span>}
              </td>
              <td className="py-2.5 pl-3 pr-5 font-mono" style={{ color: 'var(--muted)' }}>
                {sub.aliasName ? `${sub.aliasName}${sub.tldName ? `.${sub.tldName}` : ''}` : '—'}
              </td>
            </tr>
          )
        })}
      </TabTable>
    </Card>
  )
}

/** Outgoing and incoming subscriptions from one cached getAccountSubscriptions answer. */
export function SubscriptionsTab({ accountId }: { accountId: string }) {
  const { t } = useTranslation()
  const genesis = useNetworkGenesis()
  const { data, isLoading, isError, refetch } = useAccountSubscriptionsCached(accountId, true)
  const nodeTime = useBlockchainStatus().data?.time
  const { outgoing, incoming } = splitSubscriptions(data ?? [], accountId)
  const anyOverdue = nodeTime !== undefined && (data ?? []).some((s) => isOverdue(s.timeNext, nodeTime))
  const refetchNow = useCallback(() => void refetch(), [refetch])
  useRefetchWhenOverdue(anyOverdue, genesis && subscriptionsCacheKey(genesis, accountId), refetchNow)

  if (isLoading) return <Card><div className="h-4 w-48"><CardSkeleton /></div></Card>
  if (isError) return <Card><p className="text-[11px]" style={{ color: 'var(--gold)' }}>{t('account.loadError')}</p></Card>
  return (
    <div className="grid grid-cols-1 gap-3 md:gap-4">
      <SubscriptionList title={t('subscriptions.outgoing')} subscriptions={outgoing} counterpart={(s) => ({ id: s.recipient, rs: s.recipientRS })} nodeTime={nodeTime} />
      <SubscriptionList title={t('subscriptions.incoming')} subscriptions={incoming} counterpart={(s) => ({ id: s.sender, rs: s.senderRS })} nodeTime={nodeTime} />
    </div>
  )
}
