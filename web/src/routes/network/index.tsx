import { PageWrapper } from '@/components/layout/PageWrapper'
import { Card, CardLabel } from '@/components/ui/Card'

export default function NetworkPage() {
  return (
    <PageWrapper className="flex items-center justify-center p-6" style={{ minHeight: 'calc(100vh - 60px)' }}>
      <Card className="max-w-sm text-center">
        <CardLabel className="justify-center">Network Analyzer</CardLabel>
        <p className="text-[14px] text-[var(--muted)]">Coming Soon</p>
        <p className="mt-2 text-[11px] text-[var(--muted)]">
          Peer topology, routing stats, and latency analysis — separate spec in progress.
        </p>
      </Card>
    </PageWrapper>
  )
}
