import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { PageWrapper } from '@/components/layout/PageWrapper'
import { Card, CardLabel, CardSkeleton } from '@/components/ui/Card'
import { SectionDivider } from '@/components/ui/SectionDivider'
import { AccountLink } from '@/components/shared/AccountLink'
import { KeyValueList } from '@/components/shared/KeyValueList'
import { NotFound } from '@/components/shared/NotFound'
import { useConstants, useTransaction } from '@/hooks/useNodeQuery'
import { signumTimestampToMs, type TransactionDetail as Tx } from '@/lib/nodeApi'
import { fmtNqt } from '@/lib/utils'
import { txTypeName } from '@/components/blocks/blocks.utils'

function UnconfirmedPill({ label }: { label: string }) {
  return (
    <span
      className="inline-block rounded-sm px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-[1px]"
      style={{ color: 'var(--gold)', border: '1px solid var(--gold)' }}
    >
      {label}
    </span>
  )
}

function Details({ tx }: { tx: Tx }) {
  const { t } = useTranslation()
  const { data: constants } = useConstants()
  const confirmed = tx.block !== undefined && tx.height !== undefined
  const attachment = tx.attachment && Object.keys(tx.attachment).length > 0 ? tx.attachment : null

  return (
    <>
      <Card>
        <CardLabel>{t('tx.title')}</CardLabel>
        <KeyValueList
          rows={[
            [t('tx.id'), <span className="font-mono">{tx.transaction}</span>],
            [t('tx.type'), txTypeName(constants, tx.type, tx.subtype)],
            [t('tx.sender'), <AccountLink id={tx.sender} rs={tx.senderRS} />],
            [t('tx.recipient'), tx.recipient ? <AccountLink id={tx.recipient} rs={tx.recipientRS} /> : null],
            [t('tx.amount'), fmtNqt(tx.amountNQT)],
            [t('tx.fee'), fmtNqt(tx.feeNQT)],
            [t('tx.time'), new Date(signumTimestampToMs(tx.timestamp)).toLocaleString()],
            [t('tx.block'), confirmed ? (
              <Link to="/blocks/$height" params={{ height: String(tx.height) }} style={{ color: 'var(--blue2)' }}>
                {tx.height!.toLocaleString()}
              </Link>
            ) : <UnconfirmedPill label={t('tx.unconfirmed')} />],
            [t('tx.confirmations'), confirmed && tx.confirmations !== undefined ? tx.confirmations.toLocaleString() : null],
          ]}
        />
      </Card>
      {attachment && (
        <Card>
          <details>
            <summary className="cursor-pointer"><CardLabel className="inline">{t('tx.attachment')}</CardLabel></summary>
            <pre
              className="themed-scroll mt-3 overflow-auto text-[11px]"
              style={{ color: 'var(--text)', maxHeight: '320px' }}
            >
              {JSON.stringify(attachment, null, 2)}
            </pre>
          </details>
        </Card>
      )}
    </>
  )
}

export function TransactionDetail({ id }: { id: string }) {
  const { t } = useTranslation()
  const { data: tx, isLoading, isError } = useTransaction(id)

  const body = (() => {
    if (tx === null) return <NotFound message={t('tx.notFound')} backLabel={t('tx.backToList')} />
    if (isError) return <Card><p className="text-[12px]" style={{ color: 'var(--gold)' }}>{t('tx.loadError')}</p></Card>
    if (isLoading || !tx) return <Card><div className="h-24"><CardSkeleton /></div></Card>
    return <Details tx={tx} />
  })()

  return (
    <PageWrapper>
      <div className="page-layout">
        <SectionDivider label={t('tx.section')} />
        {body}
      </div>
    </PageWrapper>
  )
}
