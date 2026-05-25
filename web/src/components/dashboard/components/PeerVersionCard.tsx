import { useTranslation } from 'react-i18next'
import { motion } from 'framer-motion'
import { Card, CardLabel } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { categorizeVersion } from '@/lib/utils'

type Category = 'current' | 'outdated' | 'fork-risk'

const CAT_STYLES: Record<Category, { color: string; bg: string; badge: 'green' | 'amber' | 'mag' }> = {
  current:    { color: 'var(--green)', bg: 'linear-gradient(90deg,var(--blue),var(--green))', badge: 'green' },
  outdated:   { color: 'var(--amber)', bg: 'var(--amber)',                                    badge: 'amber' },
  'fork-risk':{ color: 'var(--mag)',   bg: 'var(--mag)',                                      badge: 'mag' },
}

interface VersionGroup {
  version: string
  count: number
  category: Category
}

interface PeerVersionCardProps {
  versions: { version: string }[]
  nodeVersion: string
  outdatedCount: number
}

export function PeerVersionCard({ versions, nodeVersion, outdatedCount }: PeerVersionCardProps) {
  const { t } = useTranslation()

  const catLabel: Record<Category, string> = {
    current: t('dashboard.versionCurrent'),
    outdated: t('dashboard.versionOutdated'),
    'fork-risk': t('dashboard.versionForkRisk'),
  }

  const grouped = versions.reduce<Record<string, VersionGroup>>((acc, p) => {
    const raw = p.version?.trim()
    const v = raw && raw.length > 0 ? raw : 'unknown'
    const category: Category = v === 'unknown' ? 'fork-risk' : (categorizeVersion(v, nodeVersion) as Category)
    if (!acc[v]) acc[v] = { version: v, count: 0, category }
    acc[v].count++
    return acc
  }, {})

  const groups = Object.values(grouped).sort((a, b) => b.count - a.count)
  const total = groups.reduce((s, g) => s + g.count, 0)

  return (
    <Card className="col-span-2">
      <div className="mb-3.5 flex items-center justify-between">
        <CardLabel className="mb-0">{t('dashboard.peerVersionDist')}</CardLabel>
        {outdatedCount > 0 && (
          <Badge variant="amber">{t('dashboard.outdatedCount', { count: outdatedCount })}</Badge>
        )}
      </div>

      <div className="flex flex-col gap-2.5">
        {groups.map((g) => {
          const pct = total > 0 ? (g.count / total) * 100 : 0
          const style = CAT_STYLES[g.category]
          return (
            <div key={g.version} className="flex items-center gap-2.5 text-[11px]">
              <span className="min-w-[48px]" style={{ color: style.color }}>
                {g.version}
              </span>
              <div
                className="relative h-[6px] flex-1 overflow-hidden"
                style={{ background: 'rgba(0,170,255,.10)' }}
              >
                <motion.div
                  className="h-full"
                  initial={{ width: 0 }}
                  animate={{ width: `${pct}%` }}
                  transition={{ duration: 0.7, ease: 'easeOut' }}
                  style={{ background: style.bg }}
                />
              </div>
              <span className="min-w-[20px] text-right text-[var(--muted)]">{g.count}</span>
              <Badge variant={style.badge}>{catLabel[g.category]}</Badge>
            </div>
          )
        })}
        {groups.length === 0 && (
          <p className="text-[10px] text-[var(--muted)]">{t('dashboard.noPeerDataYet')}</p>
        )}
      </div>
    </Card>
  )
}
