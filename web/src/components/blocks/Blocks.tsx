import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { PageWrapper } from '@/components/layout/PageWrapper'
import { Card, CardLabel } from '@/components/ui/Card'
import { SectionDivider } from '@/components/ui/SectionDivider'
import { useBlockchainStatus, useBlocksPage, useForkHistory } from '@/hooks/useNodeQuery'
import { BlockSearch } from './components/BlockSearch'
import { BlockTable } from './components/BlockTable'
import { Pager } from './components/Pager'
import { newerFrom, olderFrom, PAGE_SIZE } from './blocks.utils'

export function Blocks({ from }: { from: number | undefined }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { data: status } = useBlockchainStatus()
  const tipHeight = status ? status.numberOfBlocks - 1 : undefined
  const { data, isLoading, isError } = useBlocksPage(from, tipHeight)
  const { data: forkHistory } = useForkHistory(200)
  const blocks = data?.blocks ?? []
  const older = olderFrom(blocks)
  const live = from === undefined
  const page = live || tipHeight === undefined ? 1 : Math.floor((tipHeight - from) / PAGE_SIZE) + 1

  const goTo = (anchor: number | null) => void navigate({ to: '/blocks', search: anchor === null ? {} : { from: anchor } })

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
            {!live && (
              <span className="text-[10px]" style={{ color: 'var(--muted)' }}>{t('blocks.liveOnFirstPage')}</span>
            )}
          </div>
          {isError ? (
            <p className="px-5 py-6 text-[11px]" style={{ color: 'var(--gold)' }}>{t('blocks.loadError')}</p>
          ) : (
            <BlockTable blocks={blocks} forks={forkHistory?.forks ?? []} isLoading={isLoading} />
          )}
          <div className="px-5 py-4">
            <Pager
              page={page}
              hasNewer={!live}
              hasOlder={older !== null}
              onNewer={() => goTo(live || tipHeight === undefined ? null : newerFrom(from, tipHeight))}
              onOlder={() => goTo(older)}
            />
          </div>
        </Card>
      </div>
    </PageWrapper>
  )
}
