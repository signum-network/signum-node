import { useTranslation } from 'react-i18next'
import { CardLabel, CardSkeleton } from '@/components/ui/Card'
import { Badge, type BadgeVariant } from '@/components/ui/Badge'
import { useNodeHealth } from '@/hooks/useNodeQuery'
import { relativeTime } from '@/lib/format'
import type { FullBlockchainStatus, NodeHealth } from '@/lib/nodeApi'
import { diskTone, fmtBytes, nodeNow, reachability, uptimeParts, type Reachability } from '../nodeHealth.utils'

const DB_NAMES: Record<string, string> = { sqlite: 'SQLite', mariadb: 'MariaDB', postgres: 'PostgreSQL' }

const DISK_COLORS = { ok: 'var(--text)', warn: 'var(--amber)', bad: 'var(--mag)', unknown: 'var(--muted)' } as const

const REACH_VARIANTS: Record<Reachability, BadgeVariant> = {
  off: 'muted',
  reachable: 'green',
  localOnly: 'muted',
  waiting: 'muted',
  noAddress: 'amber',
  noContact: 'amber',
}

const REACH_HINTS: Partial<Record<Reachability, 'hintOff' | 'hintLocalOnly' | 'hintNoAddress' | 'hintNoContact'>> = {
  off: 'hintOff',
  localOnly: 'hintLocalOnly',
  noAddress: 'hintNoAddress',
  noContact: 'hintNoContact',
}

function Cell({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-1">
      <CardLabel>{label}</CardLabel>
      <div className="text-[12px] tabular-nums" style={{ color: 'var(--text)' }}>{children}</div>
    </div>
  )
}

interface NodeHealthDetailsProps {
  fullStatus?: FullBlockchainStatus
}

/** Uptime, database, free disk, reachability and clock: the second row of the node-health strip. */
export function NodeHealthDetails({ fullStatus }: NodeHealthDetailsProps) {
  const { t, i18n } = useTranslation()
  const { data, dataUpdatedAt, isLoading } = useNodeHealth()

  if (isLoading) {
    return <div className="h-10 w-full"><CardSkeleton /></div>
  }

  const health: NodeHealth | undefined = data
  const now = health ? nodeNow(health.serverTime, dataUpdatedAt, Date.now()) : 0
  const dash = '—'

  const uptime = () => {
    if (!health) return dash
    const { days, hours, minutes } = uptimeParts(now - health.startedAt)
    return days > 0 ? t('nodeHealth.uptimeDays', { days, hours }) : t('nodeHealth.uptimeHours', { hours, minutes })
  }

  const database = () => {
    if (!health) return dash
    const name = DB_NAMES[health.databaseType] ?? health.databaseType
    return health.databaseSizeBytes != null ? `${name} · ${fmtBytes(health.databaseSizeBytes, i18n.language)}` : name
  }

  const reach = health ? reachability(health, now) : null
  const hintKey = reach ? REACH_HINTS[reach] : undefined
  const clockOwn = fullStatus?.clockOffsetSeconds
  const clockPeers = fullStatus?.peerClockOffsetSeconds

  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 xl:grid-cols-5">
      <Cell label={t('nodeHealth.uptime')}>{uptime()}</Cell>

      <Cell label={t('nodeHealth.database')}>{database()}</Cell>

      <Cell label={t('nodeHealth.freeDisk')}>
        {health?.freeDiskBytes != null ? (
          <span style={{ color: DISK_COLORS[diskTone(health.freeDiskBytes)] }}>
            {fmtBytes(health.freeDiskBytes, i18n.language)}
            <span className="ml-1 text-[10px]" style={{ color: 'var(--muted)' }}>
              ({t(health.freeDiskScope === 'database' ? 'nodeHealth.freeDiskDatabase' : 'nodeHealth.freeDiskNode')})
            </span>
          </span>
        ) : dash}
      </Cell>

      <Cell label={t('nodeHealth.reachability')}>
        {health && reach ? (
          <div className="flex flex-col items-start gap-1" title={hintKey ? t(`nodeHealth.${hintKey}`) : undefined}>
            <Badge variant={REACH_VARIANTS[reach]}>{t(`nodeHealth.${reach}`)}</Badge>
            {reach === 'reachable' && health.lastInboundPeerAt != null && (
              <span className="text-[10px]" style={{ color: 'var(--muted)' }}>
                {t('nodeHealth.lastContact', {
                  ago: relativeTime(Math.round((now - health.lastInboundPeerAt) / 1000), i18n.language),
                })}
              </span>
            )}
            {hintKey && (
              <span className="break-words text-[10px] leading-snug" style={{ color: 'var(--muted)' }}>
                {t(`nodeHealth.${hintKey}`)}
              </span>
            )}
          </div>
        ) : dash}
      </Cell>

      <Cell label={t('nodeHealth.clock')}>
        {clockOwn != null || clockPeers != null ? (
          <span style={{ color: fullStatus?.clockDrifting ? 'var(--amber)' : 'var(--text)' }}>
            {t('nodeHealth.clockValue', { own: clockOwn ?? '?', peers: clockPeers ?? '?' })}
          </span>
        ) : (
          <span style={{ color: 'var(--muted)' }}>{t('nodeHealth.clockUnknown')}</span>
        )}
      </Cell>
    </div>
  )
}
