import { useTranslation } from 'react-i18next'
import { PageWrapper } from '@/components/layout/PageWrapper'
import { ConsensusBar } from './components/ConsensusBar'
import { PeerTable } from './components/PeerTable'
import { ForkHistory } from './components/ForkHistory'
import { BlacklistPanel } from './components/BlacklistPanel'
import { ChainLanes } from './components/ChainLanes'

function SectionDivider({ label }: { label: string }) {
  return (
    <div className="flex items-center gap-4 py-1">
      <div className="h-px flex-1" style={{ background: 'var(--border)' }} />
      <span
        className="text-[9px] font-semibold uppercase tracking-[3px]"
        style={{ color: 'var(--muted)' }}
      >
        {label}
      </span>
      <div className="h-px flex-1" style={{ background: 'var(--border)' }} />
    </div>
  )
}

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
          <PeerTable />
        </div>

        <div className="grid grid-cols-1 gap-3 md:gap-4">
          <BlacklistPanel />
        </div>

        <SectionDivider label={t('network.sectionYourNode')} />

        <div className="grid grid-cols-1 gap-3 md:gap-4">
          <ForkHistory />
        </div>
      </div>
    </PageWrapper>
  )
}
