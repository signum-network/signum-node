import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { PageWrapper } from '@/components/layout/PageWrapper'
import { Card, CardLabel } from '@/components/ui/Card'
import { SectionDivider } from '@/components/ui/SectionDivider'
import { useBlockchainStatus, useBlocksPage, useForkHistory } from '@/hooks/useNodeQuery'
import { BlockSearch } from './components/BlockSearch'
import { BlockTable } from './components/BlockTable'
import { Pager } from './components/Pager'
import { hasOlderPage } from './blocks.utils'

export function Blocks({ page }: { page: number }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { data: status } = useBlockchainStatus()
  const { data, isLoading, isError } = useBlocksPage(page)
  const { data: forkHistory } = useForkHistory(200)
  const tipHeight = status ? status.numberOfBlocks - 1 : 0

  const changePage = (next: number) => void navigate({ to: '/blocks', search: { page: next } })

  return (
    <PageWrapper>
      <div className="page-layout">
        <SectionDivider label={t('blocks.section')} />

        <Card>
          <BlockSearch tipHeight={tipHeight} />
        </Card>

        <Card className="overflow-hidden p-0">
          <div className="flex items-center justify-between px-5 pt-5">
            <CardLabel>{t('blocks.latest')}</CardLabel>
            {page > 0 && (
              <span className="text-[10px]" style={{ color: 'var(--muted)' }}>{t('blocks.liveOnFirstPage')}</span>
            )}
          </div>
          {isError ? (
            <p className="px-5 py-6 text-[11px]" style={{ color: 'var(--gold)' }}>{t('blocks.loadError')}</p>
          ) : (
            <BlockTable blocks={data?.blocks ?? []} forks={forkHistory?.forks ?? []} isLoading={isLoading} />
          )}
          <div className="px-5 py-4">
            <Pager page={page} hasOlder={status ? hasOlderPage(page, tipHeight) : false} onChange={changePage} />
          </div>
        </Card>
      </div>
    </PageWrapper>
  )
}
