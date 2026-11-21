import { useTranslation } from 'react-i18next'
import { Card, CardLabel } from '@/components/ui/Card'
import { AccountLink } from '@/components/shared/AccountLink'
import { KeyValueList } from '@/components/shared/KeyValueList'
import { signumTimestampToMs, type BlockDetail } from '@/lib/nodeApi'
import { fmtDuration, fmtNqt } from '@/lib/utils'
import { deltaTone } from '../blocks.utils'

const TONE_COLOR = { fast: 'var(--green)', normal: 'var(--text)', slow: 'var(--gold)' } as const

export function BlockPanel({ block, deltaSeconds }: { block: BlockDetail; deltaSeconds: number | null }) {
  const { t } = useTranslation()
  return (
    <Card>
      <CardLabel>{t('block.panelBlock')}</CardLabel>
      <KeyValueList
        rows={[
          [t('block.id'), <span className="font-mono">{block.block}</span>],
          [t('block.time'), new Date(signumTimestampToMs(block.timestamp)).toLocaleString()],
          [t('block.delta'), deltaSeconds === null ? null : (
            <span style={{ color: TONE_COLOR[deltaTone(deltaSeconds)] }}>{fmtDuration(deltaSeconds)}</span>
          )],
          [t('block.generator'), <AccountLink id={block.generator} rs={block.generatorRS} />],
          [t('block.reward'), t('block.rewardValue', { reward: fmtNqt(block.blockRewardNQT), fees: fmtNqt(block.totalFeeNQT) })],
          [t('block.transactions'), block.numberOfTransactions],
          [t('block.payload'), `${block.payloadLength.toLocaleString()} B`],
        ]}
      />
    </Card>
  )
}
