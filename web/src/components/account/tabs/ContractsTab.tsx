import { useState } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Card, CardSkeleton } from '@/components/ui/Card'
import { AccountLink } from '@/components/shared/AccountLink'
import { Pager } from '@/components/blocks/components/Pager'
import { useAccountContracts } from '@/hooks/useNodeQuery'
import { fmtNqt } from '@/lib/utils'
import { contractState, type ContractState } from '../account.utils'
import { EmptyRow, TabTable } from './TabTable'

const STATE_COLOR: Record<ContractState, string> = {
  running: 'var(--green)',
  stopped: 'var(--muted)',
  finished: 'var(--muted)',
  frozen: 'var(--gold)',
  dead: 'var(--red, #ff4444)',
}

/** Contracts the account deployed, 10 per page (each answer carries its machine code). */
export function ContractsTab({ accountId }: { accountId: string }) {
  const { t } = useTranslation()
  const [page, setPage] = useState(0)
  const { data, isLoading, isError } = useAccountContracts(accountId, page, true)
  const contracts = data?.ats ?? []

  return (
    <Card className="overflow-hidden p-0">
      {isLoading && <div className="mx-5 my-4 h-4 w-48"><CardSkeleton /></div>}
      {isError && <p className="px-5 py-4 text-[11px]" style={{ color: 'var(--gold)' }}>{t('account.loadError')}</p>}
      {data && (
        <TabTable
          heads={[
            { label: t('contracts.colName') },
            { label: t('contracts.colAddress') },
            { label: t('contracts.colState') },
            { label: t('contracts.colBalance'), align: 'right' },
            { label: t('contracts.colCreated'), align: 'right' },
          ]}
        >
          {contracts.length === 0 && <EmptyRow colSpan={5} text={t('contracts.empty')} />}
          {contracts.map((contract) => {
            const state = contractState(contract)
            return (
              <tr key={contract.at} style={{ borderBottom: '1px solid var(--border)' }}>
                <td className="py-2.5 pl-5 pr-3" style={{ color: 'var(--text)' }} title={contract.description}>{contract.name}</td>
                <td className="px-3 py-2.5"><AccountLink id={contract.at} rs={contract.atRS} /></td>
                <td className="px-3 py-2.5">
                  <span
                    className="inline-block rounded-sm px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-[1px]"
                    style={{ color: STATE_COLOR[state], border: `1px solid ${STATE_COLOR[state]}` }}
                  >
                    {t(`contracts.${state}`)}
                  </span>
                </td>
                <td className="px-3 py-2.5 text-right tabular-nums" style={{ color: 'var(--text)' }}>{fmtNqt(contract.balanceNQT)}</td>
                <td className="py-2.5 pl-3 pr-5 text-right tabular-nums">
                  <Link to="/blocks/$height" params={{ height: String(contract.creationBlock) }} style={{ color: 'var(--blue2)' }}>
                    {contract.creationBlock.toLocaleString()}
                  </Link>
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
