import { PageWrapper } from '@/components/layout/PageWrapper'
import { ConsensusBar } from './components/ConsensusBar'
import { PeerTable } from './components/PeerTable'
import { ForkHistory } from './components/ForkHistory'
import { BlacklistPanel } from './components/BlacklistPanel'

export function Network() {
  return (
    <PageWrapper>
      <div className="page-layout">
        <div className="grid grid-cols-1 gap-3 md:gap-4">
          <ConsensusBar />
        </div>

        <div className="grid grid-cols-1 gap-3 md:gap-4">
          <PeerTable />
        </div>

        <div className="grid grid-cols-1 gap-3 md:grid-cols-4 md:gap-4">
          <ForkHistory />
          <BlacklistPanel />
        </div>
      </div>
    </PageWrapper>
  )
}
