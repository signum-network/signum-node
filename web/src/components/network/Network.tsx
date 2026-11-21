import { useTranslation } from 'react-i18next'
import { PageWrapper } from '@/components/layout/PageWrapper'
import { SectionDivider } from '@/components/ui/SectionDivider'
import { ConsensusBar } from './components/ConsensusBar'
import { PeerTable } from './components/PeerTable'
import { ForkHistory } from './components/ForkHistory'
import { BlacklistPanel } from './components/BlacklistPanel'
import { ChainLanes } from './components/ChainLanes'

export function Network() {
  const { t } = useTranslation()

  return (
    <PageWrapper>
      <div className="page-layout">
        <SectionDivider label={t('network.sectionNetwork')} />

        <div className="grid grid-cols-1 gap-3 md:gap-4">
          <ConsensusBar />
        </div>

        <div className="grid grid-cols-1 gap-3 md:gap-4">
          <ChainLanes />
        </div>

        <div className="grid grid-cols-1 gap-3 md:gap-4">
          <ForkHistory />
        </div>

        <div className="grid grid-cols-1 gap-3 md:gap-4">
          <PeerTable />
        </div>

        <div className="grid grid-cols-1 gap-3 md:gap-4">
          <BlacklistPanel />
        </div>


      </div>
    </PageWrapper>
  )
}
