import { useState } from 'react'
import { Link, useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { PageWrapper } from '@/components/layout/PageWrapper'
import { Card, CardLabel, CardSkeleton } from '@/components/ui/Card'
import { ScrollableTable } from '@/components/ui/ScrollableTable'
import { SectionDivider } from '@/components/ui/SectionDivider'
import { KeyValueList } from '@/components/shared/KeyValueList'
import { NotFound } from '@/components/shared/NotFound'
import { TxTable } from '@/components/shared/TxTable'
import { Pager } from '@/components/ui/Pager'
import { relativeTime } from '@/lib/format'
import { useAccount, useAccountBlocks, useAccountTransactions } from '@/hooks/useNodeQuery'
import { signumTimestampToMs, type AccountDetail as Account, type BlockDetail } from '@/lib/nodeApi'
import { fmtNqt } from '@/lib/utils'
import { AccountTabs } from './AccountTabs'
import { TokensTab } from './tabs/TokensTab'
import { ContractsTab } from './tabs/ContractsTab'
import { AliasesTab } from './tabs/AliasesTab'
import { SubscriptionsTab } from './tabs/SubscriptionsTab'
import { showErrorInsteadOfData, type AccountTab } from './account.utils'

function Overview({ account }: { account: Account }) {
  const { t } = useTranslation()
  return (
    <Card>
      <div className="flex items-center justify-between">
        <CardLabel>{t('account.overview')}</CardLabel>
        {account.isAT && (
          <span className="rounded-sm px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-[1px]"
            style={{ color: 'var(--mag)', border: '1px solid var(--mag)' }}>
            {t('account.smartContract')}
          </span>
        )}
      </div>
      <KeyValueList
        rows={[
          [t('account.address'), <span className="font-mono">{account.accountRS}</span>],
          [t('account.id'), <span className="font-mono">{account.account}</span>],
          [t('account.name'), account.name ?? null],
          [t('account.balance'), fmtNqt(account.balanceNQT)],
          [t('account.committed'), account.committedBalanceNQT ? fmtNqt(account.committedBalanceNQT) : null],
          [t('account.guaranteed'), fmtNqt(account.guaranteedBalanceNQT)],
        ]}
      />
    </Card>
  )
}

function ForgedBlocks({ blocks }: { blocks: BlockDetail[] }) {
  const { t, i18n } = useTranslation()
  if (blocks.length === 0) {
    return <p className="px-5 py-4 text-[11px]" style={{ color: 'var(--muted)' }}>{t('account.noBlocks')}</p>
  }
  const nowSeconds = Date.now() / 1000
  const th = (label: string, align: 'left' | 'right' = 'left') => (
    <th className={`px-3 py-2 first:pl-5 last:pr-5 text-${align}`}>
      <span className="text-[9px] uppercase tracking-[2px]" style={{ color: 'var(--muted)' }}>{label}</span>
    </th>
  )
  return (
    <ScrollableTable
      maxHeight="none"
      head={<tr style={{ borderBottom: '1px solid var(--border)' }}>{th(t('account.colHeight'))}{th(t('account.colTime'))}{th(t('account.colFees'), 'right')}</tr>}
    >
      {blocks.map((block) => {
        const timeMs = signumTimestampToMs(block.timestamp)
        return (
          <tr key={block.block} style={{ borderBottom: '1px solid var(--border)' }}>
            <td className="py-2.5 pl-5 pr-3 tabular-nums">
              <Link to="/blocks/$height" params={{ height: String(block.height) }} style={{ color: 'var(--blue2)' }}>
                {block.height.toLocaleString()}
              </Link>
            </td>
            <td className="px-3 py-2.5" style={{ color: 'var(--muted)' }} title={new Date(timeMs).toLocaleString()}>
              {relativeTime(nowSeconds - timeMs / 1000, i18n.language)}
            </td>
            <td className="py-2.5 pl-3 pr-5 text-right tabular-nums" style={{ color: 'var(--text)' }}>{fmtNqt(block.totalFeeNQT)}</td>
          </tr>
        )
      })}
    </ScrollableTable>
  )
}

function PagedSection({ title, page, hasOlder, onPage, loading, children }: {
  title: string; page: number; hasOlder: boolean; onPage: (p: number) => void; loading: boolean; children: React.ReactNode
}) {
  return (
    <Card className="overflow-hidden p-0">
      <div className="px-5 pt-5"><CardLabel>{title}</CardLabel></div>
      {loading ? <div className="mx-5 my-4 h-4 w-48"><CardSkeleton /></div> : children}
      <div className="px-5 py-4">
        <Pager page={page + 1} hasNewer={page > 0} hasOlder={hasOlder} onNewer={() => onPage(page - 1)} onOlder={() => onPage(page + 1)} />
      </div>
    </Card>
  )
}

/** Forged blocks and transactions; mounted only on the overview tab, so other tabs do not load them. */
function OverviewTab({ id }: { id: string }) {
  const { t } = useTranslation()
  const [blockPage, setBlockPage] = useState(0)
  const [txPage, setTxPage] = useState(0)
  const blocks = useAccountBlocks(id, blockPage)
  const transactions = useAccountTransactions(id, txPage)
  return (
    <div className="grid grid-cols-1 gap-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)] md:gap-4">
      <PagedSection
        title={t('account.forgedBlocks')}
        page={blockPage}
        hasOlder={blocks.data?.nextIndex !== undefined}
        onPage={setBlockPage}
        loading={blocks.isLoading}
      >
        <ForgedBlocks blocks={blocks.data?.blocks ?? []} />
      </PagedSection>
      <PagedSection
        title={t('account.transactions')}
        page={txPage}
        hasOlder={transactions.data?.nextIndex !== undefined}
        onPage={setTxPage}
        loading={transactions.isLoading}
      >
        <TxTable transactions={transactions.data?.transactions ?? []} />
      </PagedSection>
    </div>
  )
}

export function AccountDetail({ id, tab, page }: { id: string; tab: AccountTab; page: number }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { data: account, isLoading, isError } = useAccount(id)
  const selectTab = (next: AccountTab) =>
    void navigate({ to: '/accounts/$id', params: { id }, search: next === 'overview' ? {} : { tab: next } })
  // the tab's list page lives in the URL, so Back and reload keep it
  const selectPage = (next: number) =>
    void navigate({ to: '/accounts/$id', params: { id }, search: { tab: tab === 'overview' ? undefined : tab, page: next > 0 ? next : undefined } })

  const body = (() => {
    if (account === null) return <NotFound message={t('account.notFound')} backLabel={t('account.backToList')} />
    if (showErrorInsteadOfData(isError, account)) return <Card><p className="text-[12px]" style={{ color: 'var(--gold)' }}>{t('account.loadError')}</p></Card>
    if (isLoading || !account) return <Card><div className="h-24"><CardSkeleton /></div></Card>
    return (
      <>
        <Overview account={account} />
        <AccountTabs active={tab} onSelect={selectTab} />
        <div role="tabpanel" id={`account-panel-${tab}`} aria-labelledby={`account-tab-${tab}`}>
          {tab === 'overview' && <OverviewTab id={id} />}
          {tab === 'tokens' && <TokensTab account={account} page={page} onPage={selectPage} />}
          {tab === 'contracts' && <ContractsTab accountId={account.account} page={page} onPage={selectPage} />}
          {tab === 'aliases' && <AliasesTab accountId={account.account} page={page} onPage={selectPage} />}
          {tab === 'subscriptions' && <SubscriptionsTab accountId={account.account} />}
        </div>
      </>
    )
  })()

  return (
    <PageWrapper>
      <div className="page-layout">
        <SectionDivider label={t('account.section')} />
        {body}
      </div>
    </PageWrapper>
  )
}
