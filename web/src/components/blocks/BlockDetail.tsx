import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { PageWrapper } from '@/components/layout/PageWrapper'
import { Card, CardLabel, CardSkeleton } from '@/components/ui/Card'
import { SectionDivider } from '@/components/ui/SectionDivider'
import { NotFound } from '@/components/shared/NotFound'
import { TxTable } from '@/components/shared/TxTable'
import { useBlock, useBlockchainStatus, useForkHistory } from '@/hooks/useNodeQuery'
import { BlockPanel } from './components/BlockPanel'
import { NodePanel } from './components/NodePanel'
import { blockDeltaSeconds, parseHeightParam, rollbackCovering } from './blocks.utils'

function HeightLink({ height, label, enabled }: { height: number; label: string; enabled: boolean }) {
  // nothing before the genesis block or after the tip; the empty span keeps the other link in place
  if (!enabled) return <span />
  return (
    <Link to="/blocks/$height" params={{ height: String(height) }} className="text-[11px]" style={{ color: 'var(--blue2)' }}>
      {label}
    </Link>
  )
}

export function BlockDetail({ heightParam }: { heightParam: string }) {
  const { t } = useTranslation()
  const { data: status } = useBlockchainStatus()
  const tipHeight = status ? status.numberOfBlocks - 1 : undefined
  const height = parseHeightParam(heightParam, tipHeight)
  const { data: block, isLoading, isError } = useBlock(height)
  const { data: previous } = useBlock(height !== null && height > 0 ? height - 1 : null)
  const { data: forkHistory } = useForkHistory(200)

  const body = (() => {
    if (height === null || block === null) {
      return <NotFound message={t('block.notFound')} backLabel={t('block.backToList')} />
    }
    if (isError) {
      return <Card><p className="text-[12px]" style={{ color: 'var(--gold)' }}>{t('block.loadError')}</p></Card>
    }
    if (isLoading || !block) {
      return <Card><div className="h-24"><CardSkeleton /></div></Card>
    }
    return (
      <>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 md:gap-4">
          <BlockPanel block={block} deltaSeconds={blockDeltaSeconds(block.timestamp, previous?.timestamp)} />
          <NodePanel block={block} rollback={rollbackCovering(block.height, forkHistory?.forks ?? [])} />
        </div>
        <Card className="overflow-hidden p-0">
          <div className="px-5 pt-5">
            <CardLabel>{t('block.txTitle', { count: block.numberOfTransactions })}</CardLabel>
          </div>
          <TxTable transactions={block.transactions ?? []} />
        </Card>
      </>
    )
  })()

  return (
    <PageWrapper>
      <div className="page-layout">
        <SectionDivider label={height === null ? t('blocks.section') : t('block.title', { height: height.toLocaleString() })} />
        {height !== null && (
          <div className="flex items-center justify-between">
            <HeightLink height={height - 1} label={`‹ ${(height - 1).toLocaleString()}`} enabled={height > 0} />
            <HeightLink
              height={height + 1}
              label={`${(height + 1).toLocaleString()} ›`}
              enabled={tipHeight !== undefined && height < tipHeight}
            />
          </div>
        )}
        {body}
      </div>
    </PageWrapper>
  )
}
