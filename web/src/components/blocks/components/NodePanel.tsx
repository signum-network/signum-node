import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Card, CardLabel } from '@/components/ui/Card'
import { KeyValueList } from '@/components/shared/KeyValueList'
import type { BlockDetail, ForkEvent } from '@/lib/nodeApi'
import { fmtNqt } from '@/lib/utils'
import { canCheckPeers, relativeTime } from '../blocks.utils'
import { PeerCheck } from './PeerCheck'

function shortHash(hash: string): string {
  return hash.length > 20 ? `${hash.slice(0, 10)}…${hash.slice(-8)}` : hash
}

/** What this node knows about the block beyond its content: chain values and a rollback that replaced it. */
export function NodePanel({ block, rollback, tipHeight }: { block: BlockDetail; rollback: ForkEvent | null; tipHeight: number | undefined }) {
  const { t, i18n } = useTranslation()
  const rollbackText = rollback && [
    t('block.rollbackText', {
      count: rollback.rollbackDepth,
      ago: relativeTime((Date.now() - rollback.detectedAt) / 1000, i18n.language),
    }),
    rollback.peerSource ? t('block.rollbackFrom', { peer: rollback.peerSource }) : null,
  ].filter(Boolean).join(' ')

  return (
    <Card>
      <CardLabel>{t('block.panelNode')}</CardLabel>
      <KeyValueList
        rows={[
          [t('block.baseTarget'), Number(block.baseTarget).toLocaleString()],
          [t('block.avgCommitment'), fmtNqt(block.averageCommitmentNQT)],
          [t('block.cumulativeDifficulty'), <span className="font-mono">{block.cumulativeDifficulty}</span>],
          [t('block.generationSignature'), <span className="font-mono" title={block.generationSignature}>{shortHash(block.generationSignature)}</span>],
          [t('block.previousBlock'), block.previousBlock ? <span className="font-mono">{block.previousBlock}</span> : null],
          [t('block.rollback'), rollbackText ? (
            <Link to="/network" style={{ color: 'var(--red, #ff4444)' }}>⑂ {rollbackText}</Link>
          ) : null],
          [t('blockPeers.peers'), canCheckPeers(block.height, tipHeight) ? <PeerCheck key={block.block} blockId={block.block} /> : null],
        ]}
      />
    </Card>
  )
}
