# Fork Visualisation & Network Page Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a ChainLanes network-situation component and replace the ForkHistory list with Branch Timeline + Radial Map diagram views, reorganising the Network page into two labelled scroll sections.

**Architecture:** Three new components (`ChainLanes`, `BranchTimeline`, `RadialForkMap`) consume existing hooks with no new API calls. `ForkHistory.tsx` is refactored to a card shell with a pill toggle. `Network.tsx` gains a section divider. All layout is inline SVG; all colours use CSS custom properties.

**Tech Stack:** React 19, TypeScript, inline SVG, Tailwind, framer-motion, react-i18next, vitest (added), bun.

---

## File Map

| File | Action |
|---|---|
| `web/package.json` | add `vitest` dev dep |
| `web/vitest.config.ts` | create |
| `web/src/components/network/components/chainLanes.utils.ts` | create — pure grouping logic |
| `web/src/components/network/components/chainLanes.utils.test.ts` | create — vitest tests |
| `web/src/components/network/components/ChainLanes.tsx` | create — visual component |
| `web/src/components/network/components/branchTimeline.utils.ts` | create — pure SVG layout math |
| `web/src/components/network/components/branchTimeline.utils.test.ts` | create — vitest tests |
| `web/src/components/network/components/BranchTimeline.tsx` | create — SVG branch view |
| `web/src/components/network/components/radialForkMap.utils.ts` | create — pure SVG layout math |
| `web/src/components/network/components/radialForkMap.utils.test.ts` | create — vitest tests |
| `web/src/components/network/components/RadialForkMap.tsx` | create — SVG radial view |
| `web/src/components/network/components/ForkHistory.tsx` | update — pill toggle + diagram views, remove list |
| `web/src/components/network/Network.tsx` | update — section dividers + ChainLanes row |
| `web/src/i18n/locales/en.ts` | update — new keys in interface + values |
| `web/src/i18n/locales/de.ts` | update — new values |
| `web/src/i18n/locales/es.ts` | update — new values |
| `web/src/i18n/locales/hi.ts` | update — new values |
| `web/src/i18n/locales/ja.ts` | update — new values |
| `web/src/i18n/locales/ko.ts` | update — new values |
| `web/src/i18n/locales/pt.ts` | update — new values |
| `web/src/i18n/locales/ru.ts` | update — new values |
| `web/src/i18n/locales/uk.ts` | update — new values |
| `web/src/i18n/locales/zh.ts` | update — new values |

---

## Task 1: Vitest setup

**Files:**
- Modify: `web/package.json`
- Create: `web/vitest.config.ts`

- [ ] **Step 1: Add vitest**

```bash
cd web && bun add -d vitest
```

Expected: `vitest` appears in `devDependencies` in `package.json`.

- [ ] **Step 2: Create vitest config**

Create `web/vitest.config.ts`:

```ts
import { defineConfig } from 'vitest/config'
import { resolve } from 'path'

export default defineConfig({
  test: {
    environment: 'node',
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, './src'),
    },
  },
})
```

- [ ] **Step 3: Verify vitest runs**

```bash
cd web && bunx vitest run
```

Expected: "No test files found" (no error).

- [ ] **Step 4: Commit**

```bash
git add web/package.json web/bun.lock web/vitest.config.ts
git commit -m "chore(web): add vitest for unit testing pure functions"
```

---

## Task 2: i18n — add new keys

**Files:**
- Modify: `web/src/i18n/locales/en.ts` (Locale interface + en values)
- Modify: all other locale files (de, es, hi, ja, ko, pt, ru, uk, zh)

- [ ] **Step 1: Update Locale interface in `en.ts`**

In `web/src/i18n/locales/en.ts`, update the `network` and `info` type blocks:

```ts
// Replace the network block:
network: {
  chainConsensus: string; peers: string; forkHistory: string; blacklistRecommendations: string
  agree: string; onOurChain: string; noReorgs: string; noRecommendations: string
  noPeerData: string; blacklistHint: string; copyForConfig: string
  peersOpen: string; peersOpen_other: string; peersClosed: string; peersClosed_other: string
  forkPoint: {
    title: string; findFork: string; findForkFull: string
    forkHeight: string; forkBlockId: string; ourBlock: string; steps: string; tooOld: string; tooFarBehind: string
  }
  rollback: string; rollback_other: string
  status: string; cumulDifficulty: string
  chainLanes: string; ourChain: string; forkLane: string; noActiveForks: string
  sectionNetwork: string; sectionYourNode: string
  branchView: string; radialView: string
}
// Replace the info block:
info: {
  chainConsensus: string; cumulativeDifficulty: string; findFork: string
  networkCapacity: string; effectiveCapacity: string; baseTarget: string
  peersTable: string; blacklistRecommendations: string
  avgCommitment: string; jvmHeap: string; dbTrimming: string
  chainLanes: string
}
```

- [ ] **Step 2: Add English values in `en.ts`**

Inside the `const en: Locale` object, add to the `network` section (after `cumulDifficulty`):

```ts
chainLanes: 'Chain Distribution',
ourChain: 'Our Chain',
forkLane: 'Fork {{index}}',
noActiveForks: 'No active forks detected',
sectionNetwork: 'Network & Peers',
sectionYourNode: 'Your Node',
branchView: 'Branch',
radialView: 'Radial',
```

Add to the `info` section (after `dbTrimming`):

```ts
chainLanes: 'Shows which peers are on the same chain as your node and which are on competing chains, grouped by cumulative difficulty. The difficulty bar shows relative chain weight — the chain with the most accumulated work is canonical.',
```

- [ ] **Step 3: Add German values in `de.ts`**

Add after `cumulDifficulty` in the `network` section:

```ts
chainLanes: 'Ketten-Verteilung',
ourChain: 'Unsere Kette',
forkLane: 'Fork {{index}}',
noActiveForks: 'Keine aktiven Forks erkannt',
sectionNetwork: 'Netzwerk & Peers',
sectionYourNode: 'Ihr Knoten',
branchView: 'Zweig',
radialView: 'Radial',
```

Add after `dbTrimming` in `info`:

```ts
chainLanes: 'Zeigt, welche Peers auf derselben Kette wie Ihr Knoten sind und welche auf konkurrierenden Ketten, gruppiert nach kumulativer Schwierigkeit.',
```

- [ ] **Step 4: Add Spanish values in `es.ts`**

```ts
// network section, after cumulDifficulty:
chainLanes: 'Distribución de cadenas',
ourChain: 'Nuestra cadena',
forkLane: 'Fork {{index}}',
noActiveForks: 'No se detectaron bifurcaciones activas',
sectionNetwork: 'Red y pares',
sectionYourNode: 'Tu nodo',
branchView: 'Rama',
radialView: 'Radial',
// info section, after dbTrimming:
chainLanes: 'Muestra qué peers están en la misma cadena que tu nodo y cuáles en cadenas competidoras, agrupados por dificultad acumulada.',
```

- [ ] **Step 5: Add Hindi values in `hi.ts`**

```ts
// network, after cumulDifficulty:
chainLanes: 'चेन वितरण',
ourChain: 'हमारी चेन',
forkLane: 'फोर्क {{index}}',
noActiveForks: 'कोई सक्रिय फोर्क नहीं मिला',
sectionNetwork: 'नेटवर्क और पीयर',
sectionYourNode: 'आपका नोड',
branchView: 'शाखा',
radialView: 'रेडियल',
// info, after dbTrimming:
chainLanes: 'दिखाता है कि कौन से पीयर आपके नोड की चेन पर हैं और कौन से प्रतिस्पर्धी चेन पर, संचित कठिनाई द्वारा समूहीकृत।',
```

- [ ] **Step 6: Add Japanese values in `ja.ts`**

```ts
// network, after cumulDifficulty:
chainLanes: 'チェーン分布',
ourChain: '自ノードのチェーン',
forkLane: 'フォーク {{index}}',
noActiveForks: 'アクティブなフォークなし',
sectionNetwork: 'ネットワーク & ピア',
sectionYourNode: '自ノード',
branchView: 'ブランチ',
radialView: 'ラジアル',
// info, after dbTrimming:
chainLanes: '自ノードと同じチェーン上のピアと競合チェーン上のピアを累積難易度でグループ化して表示します。',
```

- [ ] **Step 7: Add Korean values in `ko.ts`**

```ts
// network, after cumulDifficulty:
chainLanes: '체인 분포',
ourChain: '우리 체인',
forkLane: '포크 {{index}}',
noActiveForks: '활성 포크 없음',
sectionNetwork: '네트워크 & 피어',
sectionYourNode: '내 노드',
branchView: '브랜치',
radialView: '방사형',
// info, after dbTrimming:
chainLanes: '누적 난이도별로 그룹화하여 자신의 노드와 같은 체인의 피어와 경쟁 체인의 피어를 표시합니다.',
```

- [ ] **Step 8: Add Portuguese values in `pt.ts`**

```ts
// network, after cumulDifficulty:
chainLanes: 'Distribuição de cadeias',
ourChain: 'Nossa cadeia',
forkLane: 'Fork {{index}}',
noActiveForks: 'Nenhum fork ativo detectado',
sectionNetwork: 'Rede e pares',
sectionYourNode: 'Seu nó',
branchView: 'Ramo',
radialView: 'Radial',
// info, after dbTrimming:
chainLanes: 'Mostra quais peers estão na mesma cadeia que seu nó e quais estão em cadeias concorrentes, agrupados por dificuldade acumulada.',
```

- [ ] **Step 9: Add Russian values in `ru.ts`**

```ts
// network, after cumulDifficulty:
chainLanes: 'Распределение цепей',
ourChain: 'Наша цепь',
forkLane: 'Форк {{index}}',
noActiveForks: 'Активных форков не обнаружено',
sectionNetwork: 'Сеть и узлы',
sectionYourNode: 'Ваш узел',
branchView: 'Ветка',
radialView: 'Радиальный',
// info, after dbTrimming:
chainLanes: 'Показывает, какие узлы находятся на той же цепи, что и ваш, а какие — на конкурирующих, сгруппированных по накопленной сложности.',
```

- [ ] **Step 10: Add Ukrainian values in `uk.ts`**

```ts
// network, after cumulDifficulty:
chainLanes: 'Розподіл ланцюгів',
ourChain: 'Наш ланцюг',
forkLane: 'Форк {{index}}',
noActiveForks: 'Активних форків не виявлено',
sectionNetwork: 'Мережа та вузли',
sectionYourNode: 'Ваш вузол',
branchView: 'Гілка',
radialView: 'Радіальний',
// info, after dbTrimming:
chainLanes: 'Показує, які вузли знаходяться на тому ж ланцюзі, що й ваш, а які — на конкуруючих, згрупованих за накопиченою складністю.',
```

- [ ] **Step 11: Add Chinese values in `zh.ts`**

```ts
// network, after cumulDifficulty:
chainLanes: '链分布',
ourChain: '我们的链',
forkLane: '分叉 {{index}}',
noActiveForks: '未检测到活跃分叉',
sectionNetwork: '网络与节点',
sectionYourNode: '本节点',
branchView: '分支',
radialView: '环形',
// info, after dbTrimming:
chainLanes: '按累积难度分组显示哪些节点与您的节点在同一链上，哪些在竞争链上。',
```

- [ ] **Step 12: Type-check**

```bash
cd web && bunx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 13: Commit**

```bash
git add web/src/i18n/
git commit -m "feat(web/i18n): add chain lanes and fork diagram translation keys"
```

---

## Task 3: ChainLanes pure grouping logic + tests

**Files:**
- Create: `web/src/components/network/components/chainLanes.utils.ts`
- Create: `web/src/components/network/components/chainLanes.utils.test.ts`

- [ ] **Step 1: Write the failing tests**

Create `web/src/components/network/components/chainLanes.utils.test.ts`:

```ts
import { describe, it, expect } from 'vitest'
import { groupIntoLanes, cumulDiffRatio } from './chainLanes.utils'
import type { PeerStatusEntry } from '@/lib/nodeApi'

function peer(overrides: Partial<PeerStatusEntry>): PeerStatusEntry {
  return {
    address: '1.2.3.4',
    cumulativeDifficulty: '1000',
    height: 100,
    onOurChain: true,
    status: 'on-chain',
    connectionFailures: 0,
    blacklisted: false,
    ...overrides,
  }
}

describe('groupIntoLanes', () => {
  it('returns single our-chain lane when no forking peers', () => {
    const peers = [
      peer({ address: 'a', onOurChain: true, status: 'on-chain', height: 100 }),
      peer({ address: 'b', onOurChain: true, status: 'stale', height: 99 }),
    ]
    const lanes = groupIntoLanes(peers)
    expect(lanes).toHaveLength(1)
    expect(lanes[0].isOurChain).toBe(true)
    expect(lanes[0].peers).toHaveLength(2)
  })

  it('groups forking peers by exact cumulativeDifficulty', () => {
    const peers = [
      peer({ address: 'a', onOurChain: true, status: 'on-chain', cumulativeDifficulty: '9000' }),
      peer({ address: 'b', onOurChain: false, status: 'forking', cumulativeDifficulty: '500', height: 90 }),
      peer({ address: 'c', onOurChain: false, status: 'forking', cumulativeDifficulty: '500', height: 91 }),
      peer({ address: 'd', onOurChain: false, status: 'forking', cumulativeDifficulty: '300', height: 80 }),
    ]
    const lanes = groupIntoLanes(peers)
    expect(lanes).toHaveLength(3)
    expect(lanes[0].isOurChain).toBe(true)
    expect(lanes[1].peers).toHaveLength(2) // larger fork group first
    expect(lanes[2].peers).toHaveLength(1)
  })

  it('excludes blacklisted peers from fork lanes', () => {
    const peers = [
      peer({ address: 'a', onOurChain: true, status: 'on-chain' }),
      peer({ address: 'b', onOurChain: false, status: 'forking', blacklisted: true, cumulativeDifficulty: '500' }),
    ]
    const lanes = groupIntoLanes(peers)
    expect(lanes).toHaveLength(1)
    expect(lanes[0].isOurChain).toBe(true)
  })

  it('computes avgHeight for each lane', () => {
    const peers = [
      peer({ address: 'a', onOurChain: true, status: 'on-chain', height: 100 }),
      peer({ address: 'b', onOurChain: true, status: 'stale', height: 98 }),
      peer({ address: 'c', onOurChain: false, status: 'forking', height: 80, cumulativeDifficulty: '500' }),
    ]
    const lanes = groupIntoLanes(peers)
    expect(lanes[0].avgHeight).toBe(99)
    expect(lanes[1].avgHeight).toBe(80)
  })

  it('fork lane index starts at 1', () => {
    const peers = [
      peer({ address: 'a', onOurChain: true, status: 'on-chain' }),
      peer({ address: 'b', onOurChain: false, status: 'forking', cumulativeDifficulty: '500' }),
    ]
    const lanes = groupIntoLanes(peers)
    expect(lanes[0].index).toBe(0)
    expect(lanes[1].index).toBe(1)
  })
})

describe('cumulDiffRatio', () => {
  it('returns 1 for the highest difficulty lane', () => {
    const lanes = [
      { cumulativeDifficulty: '1000', isOurChain: true, peers: [], avgHeight: 0, index: 0 },
      { cumulativeDifficulty: '500',  isOurChain: false, peers: [], avgHeight: 0, index: 1 },
    ]
    expect(cumulDiffRatio(lanes[0], lanes)).toBe(1)
  })

  it('returns correct ratio for lower difficulty lane', () => {
    const lanes = [
      { cumulativeDifficulty: '1000', isOurChain: true, peers: [], avgHeight: 0, index: 0 },
      { cumulativeDifficulty: '500',  isOurChain: false, peers: [], avgHeight: 0, index: 1 },
    ]
    expect(cumulDiffRatio(lanes[1], lanes)).toBe(0.5)
  })

  it('returns 0 for empty cumulativeDifficulty', () => {
    const lanes = [
      { cumulativeDifficulty: '', isOurChain: true, peers: [], avgHeight: 0, index: 0 },
    ]
    expect(cumulDiffRatio(lanes[0], lanes)).toBe(0)
  })
})
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd web && bunx vitest run src/components/network/components/chainLanes.utils.test.ts
```

Expected: fails with "Cannot find module".

- [ ] **Step 3: Implement `chainLanes.utils.ts`**

Create `web/src/components/network/components/chainLanes.utils.ts`:

```ts
import type { PeerStatusEntry } from '@/lib/nodeApi'

export interface Lane {
  isOurChain: boolean
  peers: PeerStatusEntry[]
  cumulativeDifficulty: string
  avgHeight: number
  index: number
}

export function groupIntoLanes(peers: PeerStatusEntry[]): Lane[] {
  const ourPeers = peers.filter(p => p.onOurChain)
  const forkingPeers = peers.filter(p => p.status === 'forking' && !p.blacklisted)

  const forkGroups = new Map<string, PeerStatusEntry[]>()
  for (const peer of forkingPeers) {
    const existing = forkGroups.get(peer.cumulativeDifficulty) ?? []
    forkGroups.set(peer.cumulativeDifficulty, [...existing, peer])
  }

  const avg = (ps: PeerStatusEntry[]) =>
    ps.length ? Math.round(ps.reduce((s, p) => s + p.height, 0) / ps.length) : 0

  const ourLane: Lane = {
    isOurChain: true,
    peers: ourPeers,
    cumulativeDifficulty: ourPeers[0]?.cumulativeDifficulty ?? '',
    avgHeight: avg(ourPeers),
    index: 0,
  }

  const forkLanes: Lane[] = [...forkGroups.entries()]
    .sort((a, b) => b[1].length - a[1].length)
    .map(([cumDiff, ps], i) => ({
      isOurChain: false,
      peers: ps,
      cumulativeDifficulty: cumDiff,
      avgHeight: avg(ps),
      index: i + 1,
    }))

  return [ourLane, ...forkLanes]
}

export function cumulDiffRatio(lane: Lane, lanes: Lane[]): number {
  const vals = lanes.map(l => BigInt(l.cumulativeDifficulty || '0'))
  const max = vals.reduce((m, v) => (v > m ? v : m), 0n)
  if (max === 0n) return 0
  const val = BigInt(lane.cumulativeDifficulty || '0')
  return Number((val * 10000n) / max) / 10000
}
```

- [ ] **Step 4: Run tests**

```bash
cd web && bunx vitest run src/components/network/components/chainLanes.utils.test.ts
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add web/src/components/network/components/chainLanes.utils.ts web/src/components/network/components/chainLanes.utils.test.ts
git commit -m "feat(web): add ChainLanes grouping logic with tests"
```

---

## Task 4: ChainLanes visual component

**Files:**
- Create: `web/src/components/network/components/ChainLanes.tsx`

- [ ] **Step 1: Create component**

Create `web/src/components/network/components/ChainLanes.tsx`:

```tsx
import { useTranslation } from 'react-i18next'
import { Card, CardLabel, CardSkeleton } from '@/components/ui/Card'
import { useNetworkStatus } from '@/hooks/useNodeQuery'
import type { PeerStatusEntry } from '@/lib/nodeApi'
import { groupIntoLanes, cumulDiffRatio } from './chainLanes.utils'
import type { Lane } from './chainLanes.utils'

function dotColor(peer: PeerStatusEntry): string {
  if (peer.status === 'on-chain') return 'var(--green)'
  if (peer.status === 'stale') return 'var(--gold)'
  return 'var(--red, #ff4444)'
}

function PeerDot({ peer }: { peer: PeerStatusEntry }) {
  const { t } = useTranslation()
  const label = `${peer.address} · ${t('common.height')} ${peer.height.toLocaleString()} · ${peer.status}`
  return (
    <div
      title={label}
      className="h-2.5 w-2.5 flex-shrink-0 rounded-full cursor-default transition-transform hover:scale-125"
      style={{ background: dotColor(peer), boxShadow: `0 0 5px ${dotColor(peer)}` }}
    />
  )
}

function LaneCard({ lane, lanes }: { lane: Lane; lanes: Lane[] }) {
  const { t } = useTranslation()
  const ratio = cumulDiffRatio(lane, lanes)
  const label = lane.isOurChain
    ? t('network.ourChain')
    : t('network.forkLane', { index: lane.index })

  const borderColor = lane.isOurChain ? 'var(--green)' : 'var(--red, #ff4444)'
  const borderStyle = lane.isOurChain ? '1px solid' : '1px dashed'

  return (
    <div
      className="flex min-w-[140px] flex-col gap-3 rounded-sm p-3"
      style={{
        border: `${borderStyle} ${borderColor}`,
        background: lane.isOurChain
          ? 'color-mix(in srgb, var(--green) 5%, transparent)'
          : 'color-mix(in srgb, var(--red, #ff4444) 5%, transparent)',
      }}
    >
      <div>
        <div
          className="text-[9px] font-semibold uppercase tracking-[2px]"
          style={{ color: borderColor }}
        >
          {label}
        </div>
        <div className="text-[8px]" style={{ color: 'var(--muted)' }}>
          {t('common.height')} {lane.avgHeight.toLocaleString()}
        </div>
      </div>

      <div className="flex flex-wrap gap-1.5">
        {lane.peers.map(p => <PeerDot key={p.address} peer={p} />)}
      </div>

      <div
        className="text-[9px] tabular-nums"
        style={{ color: 'var(--muted)' }}
      >
        {lane.peers.length} {lane.peers.length === 1 ? 'peer' : 'peers'}
      </div>

      <div>
        <div className="text-[8px] mb-1" style={{ color: 'var(--muted)' }}>
          cumul. diff
        </div>
        <div className="h-1 w-full overflow-hidden rounded-full" style={{ background: 'var(--border)' }}>
          <div
            className="h-full rounded-full transition-all duration-700"
            style={{
              width: `${Math.round(ratio * 100)}%`,
              background: borderColor,
              boxShadow: `0 0 4px ${borderColor}`,
            }}
          />
        </div>
      </div>
    </div>
  )
}

export function ChainLanes() {
  const { data, isLoading } = useNetworkStatus()
  const { t } = useTranslation()

  const lanes = groupIntoLanes(data?.peers ?? [])
  const hasForks = lanes.length > 1

  return (
    <Card className="col-span-full">
      <CardLabel tooltip={t('info.chainLanes')}>{t('network.chainLanes')}</CardLabel>

      {isLoading && (
        <div className="flex gap-3">
          {[1, 2].map(i => (
            <div key={i} className="h-32 w-36 rounded-sm"><CardSkeleton /></div>
          ))}
        </div>
      )}

      {!isLoading && !hasForks && (
        <div className="flex gap-3 items-start">
          <LaneCard lane={lanes[0]} lanes={lanes} />
          <div
            className="flex items-center text-[10px] self-center ml-4"
            style={{ color: 'var(--muted)' }}
          >
            {t('network.noActiveForks')}
          </div>
        </div>
      )}

      {!isLoading && hasForks && (
        <div className="themed-scroll overflow-x-auto">
          <div className="flex gap-3 pb-1">
            {lanes.map(lane => (
              <LaneCard key={lane.isOurChain ? 'our' : lane.cumulativeDifficulty} lane={lane} lanes={lanes} />
            ))}
          </div>
        </div>
      )}
    </Card>
  )
}
```

- [ ] **Step 2: Type-check**

```bash
cd web && bunx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/network/components/ChainLanes.tsx
git commit -m "feat(web): add ChainLanes component"
```

---

## Task 5: BranchTimeline pure layout logic + tests

**Files:**
- Create: `web/src/components/network/components/branchTimeline.utils.ts`
- Create: `web/src/components/network/components/branchTimeline.utils.test.ts`

- [ ] **Step 1: Write failing tests**

Create `web/src/components/network/components/branchTimeline.utils.test.ts`:

```ts
import { describe, it, expect } from 'vitest'
import { buildBranchLayout, archHeight } from './branchTimeline.utils'
import type { ForkEvent } from '@/lib/nodeApi'

function fork(overrides: Partial<ForkEvent>): ForkEvent {
  return {
    detectedAt: Date.now(),
    rollbackHeight: 1000,
    rollbackDepth: 1,
    oldTopBlockId: 'abc',
    newTopBlockId: null,
    peerSource: null,
    ...overrides,
  }
}

describe('archHeight', () => {
  it('returns minimum height for depth 1', () => {
    expect(archHeight(1)).toBeGreaterThan(0)
  })

  it('returns larger height for deeper forks', () => {
    expect(archHeight(4)).toBeGreaterThan(archHeight(1))
  })

  it('caps at MAX_ARCH_H', () => {
    expect(archHeight(100)).toBe(archHeight(10))
  })
})

describe('buildBranchLayout', () => {
  const SVG_W = 600
  const forks = [
    fork({ rollbackHeight: 1050, rollbackDepth: 4, detectedAt: 2000 }),
    fork({ rollbackHeight: 1080, rollbackDepth: 1, detectedAt: 3000 }),
  ]
  const myHeight = 1100
  const layout = buildBranchLayout(forks, myHeight, SVG_W)

  it('returns one arch per fork', () => {
    expect(layout.arches).toHaveLength(2)
  })

  it('rightX is always > leftX', () => {
    for (const arch of layout.arches) {
      expect(arch.rightX).toBeGreaterThan(arch.leftX)
    }
  })

  it('nowX equals SVG_W minus padding', () => {
    expect(layout.nowX).toBeCloseTo(SVG_W - 50, 0)
  })

  it('deeper fork has taller peak', () => {
    const deep = layout.arches.find(a => a.depth === 4)!
    const shallow = layout.arches.find(a => a.depth === 1)!
    expect(deep.peakH).toBeGreaterThan(shallow.peakH)
  })
})
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd web && bunx vitest run src/components/network/components/branchTimeline.utils.test.ts
```

Expected: fails with "Cannot find module".

- [ ] **Step 3: Implement `branchTimeline.utils.ts`**

Create `web/src/components/network/components/branchTimeline.utils.ts`:

```ts
import type { ForkEvent } from '@/lib/nodeApi'

const PAD_X = 50
const AXIS_Y = 150
const MAX_ARCH_H = 110
const MIN_ARCH_H = 18

export function archHeight(depth: number): number {
  return MIN_ARCH_H + Math.min(depth / 6, 1) * (MAX_ARCH_H - MIN_ARCH_H)
}

export interface ArchLayout {
  leftX: number
  rightX: number
  midX: number
  peakY: number
  peakH: number
  depth: number
  color: string
  fork: ForkEvent
}

export interface BranchLayout {
  arches: ArchLayout[]
  axisY: number
  nowX: number
  minHeight: number
  maxHeight: number
  svgW: number
}

export function buildBranchLayout(
  forks: ForkEvent[],
  myHeight: number,
  svgW: number,
): BranchLayout {
  if (forks.length === 0) {
    return { arches: [], axisY: AXIS_Y, nowX: svgW - PAD_X, minHeight: myHeight, maxHeight: myHeight, svgW }
  }

  const minHeight = Math.min(...forks.map(f => f.rollbackHeight - f.rollbackDepth)) - 5
  const maxHeight = myHeight
  const range = maxHeight - minHeight || 1
  const chartW = svgW - 2 * PAD_X

  const xAt = (h: number) => PAD_X + ((h - minHeight) / range) * chartW

  const arches: ArchLayout[] = forks.map(fork => {
    const leftX = xAt(fork.rollbackHeight - fork.rollbackDepth)
    const rightX = xAt(fork.rollbackHeight)
    const midX = (leftX + rightX) / 2
    const peakH = archHeight(fork.rollbackDepth)
    const color = fork.rollbackDepth >= 3 ? 'var(--red, #ff4444)' : 'var(--gold)'
    return { leftX, rightX, midX, peakY: AXIS_Y - peakH, peakH, depth: fork.rollbackDepth, color, fork }
  })

  return { arches, axisY: AXIS_Y, nowX: xAt(myHeight), minHeight, maxHeight, svgW }
}
```

- [ ] **Step 4: Run tests**

```bash
cd web && bunx vitest run src/components/network/components/branchTimeline.utils.test.ts
```

Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add web/src/components/network/components/branchTimeline.utils.ts web/src/components/network/components/branchTimeline.utils.test.ts
git commit -m "feat(web): add BranchTimeline layout logic with tests"
```

---

## Task 6: BranchTimeline SVG component

**Files:**
- Create: `web/src/components/network/components/BranchTimeline.tsx`

- [ ] **Step 1: Create component**

Create `web/src/components/network/components/BranchTimeline.tsx`:

```tsx
import { useState } from 'react'
import { AnimatePresence } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import type { ForkEvent } from '@/lib/nodeApi'
import { buildBranchLayout } from './branchTimeline.utils'
import { ForkPointModal } from './ForkPointModal'

const SVG_W = 640
const SVG_H = 200

interface Props {
  forks: ForkEvent[]
  myHeight: number
  forkingPeerAddresses: Set<string>
}

export function BranchTimeline({ forks, myHeight, forkingPeerAddresses }: Props) {
  const { t } = useTranslation()
  const [hovered, setHovered] = useState<number | null>(null)
  const [forkModalPeer, setForkModalPeer] = useState<string | null>(null)

  if (forks.length === 0) {
    return (
      <p className="py-4 text-center text-[11px]" style={{ color: 'var(--muted)' }}>
        {t('network.noReorgs')}
      </p>
    )
  }

  const layout = buildBranchLayout(forks, myHeight, SVG_W)
  const { arches, axisY, nowX } = layout

  return (
    <>
      <div className="themed-scroll overflow-x-auto">
        <svg
          width={SVG_W}
          height={SVG_H}
          viewBox={`0 0 ${SVG_W} ${SVG_H}`}
          style={{ display: 'block', minWidth: SVG_W }}
        >
          {/* main chain axis */}
          <line x1={30} y1={axisY} x2={nowX} y2={axisY} stroke="var(--blue2)" strokeWidth={2} />
          <circle cx={30} cy={axisY} r={3} fill="var(--blue2)" />
          <circle cx={nowX} cy={axisY} r={3} fill="var(--blue2)" />

          {/* NOW label */}
          <text x={nowX} y={axisY - 8} fill="var(--blue2)" fontSize={8} textAnchor="middle" letterSpacing={1}>
            NOW
          </text>
          <text x={nowX} y={axisY - 18} fill="var(--blue2)" fontSize={7} textAnchor="middle" fontFamily="monospace">
            {myHeight.toLocaleString()}
          </text>

          {arches.map((arch, i) => {
            const isHovered = hovered === i
            const isClickable =
              arch.fork.peerSource != null && forkingPeerAddresses.has(arch.fork.peerSource)
            const opacity = hovered !== null && !isHovered ? 0.35 : 1

            return (
              <g
                key={i}
                style={{ opacity, cursor: isClickable ? 'pointer' : 'default' }}
                onMouseEnter={() => setHovered(i)}
                onMouseLeave={() => setHovered(null)}
                onClick={() => {
                  if (isClickable && arch.fork.peerSource) setForkModalPeer(arch.fork.peerSource)
                }}
              >
                {/* arch */}
                <path
                  d={`M ${arch.leftX} ${axisY} Q ${arch.midX} ${arch.peakY} ${arch.rightX} ${axisY}`}
                  stroke={arch.color}
                  strokeWidth={isHovered ? 2 : 1.5}
                  strokeDasharray="5 3"
                  fill="none"
                  opacity={0.85}
                />
                {/* right foot dot (fork detection point) */}
                <circle cx={arch.rightX} cy={axisY} r={isHovered ? 5.5 : 4} fill={arch.color} opacity={0.9} />

                {/* depth label at peak */}
                <text
                  x={arch.midX}
                  y={arch.peakY - 6}
                  fill={arch.color}
                  fontSize={9}
                  textAnchor="middle"
                  fontFamily="monospace"
                  fontWeight={isHovered ? 700 : 400}
                >
                  {`−${arch.depth}`}
                </text>

                {/* height label below axis */}
                <text x={arch.rightX} y={axisY + 14} fill={arch.color} fontSize={7} textAnchor="middle" fontFamily="monospace">
                  {arch.fork.rollbackHeight.toLocaleString()}
                </text>

                {/* timestamp + peer below height */}
                <text x={arch.rightX} y={axisY + 24} fill="var(--muted)" fontSize={7} textAnchor="middle">
                  {new Date(arch.fork.detectedAt).toLocaleTimeString()}
                </text>

                {arch.fork.peerSource && (
                  <text x={arch.rightX} y={axisY + 34} fill="var(--muted)" fontSize={6.5} textAnchor="middle" fontFamily="monospace">
                    {arch.fork.peerSource.length > 15
                      ? `${arch.fork.peerSource.slice(0, 14)}…`
                      : arch.fork.peerSource}
                  </text>
                )}

                {/* FORKING PEER badge when peer is still active */}
                {isClickable && (
                  <text x={arch.rightX} y={axisY + 46} fill="var(--red, #ff4444)" fontSize={6.5} textAnchor="middle" letterSpacing={0.5} opacity={0.8}>
                    FORKING PEER ↗
                  </text>
                )}
              </g>
            )
          })}
        </svg>
      </div>

      <AnimatePresence>
        {forkModalPeer && (
          <ForkPointModal peer={forkModalPeer} onClose={() => setForkModalPeer(null)} />
        )}
      </AnimatePresence>
    </>
  )
}
```

- [ ] **Step 2: Type-check**

```bash
cd web && bunx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/network/components/BranchTimeline.tsx
git commit -m "feat(web): add BranchTimeline SVG component"
```

---

## Task 7: RadialForkMap pure layout logic + tests

**Files:**
- Create: `web/src/components/network/components/radialForkMap.utils.ts`
- Create: `web/src/components/network/components/radialForkMap.utils.test.ts`

- [ ] **Step 1: Write failing tests**

Create `web/src/components/network/components/radialForkMap.utils.test.ts`:

```ts
import { describe, it, expect } from 'vitest'
import { buildRadialLayout, depthToRadius } from './radialForkMap.utils'
import type { ForkEvent } from '@/lib/nodeApi'

function fork(overrides: Partial<ForkEvent>): ForkEvent {
  return {
    detectedAt: 1000,
    rollbackHeight: 1000,
    rollbackDepth: 1,
    oldTopBlockId: 'abc',
    newTopBlockId: null,
    peerSource: null,
    ...overrides,
  }
}

describe('depthToRadius', () => {
  it('depth 1 maps to INNER_R', () => {
    expect(depthToRadius(1)).toBe(45)
  })

  it('depth 6+ maps to OUTER_R', () => {
    expect(depthToRadius(6)).toBe(105)
    expect(depthToRadius(100)).toBe(105)
  })

  it('depth 3 is between inner and outer', () => {
    const r = depthToRadius(3)
    expect(r).toBeGreaterThan(45)
    expect(r).toBeLessThan(105)
  })
})

describe('buildRadialLayout', () => {
  const forks = [
    fork({ rollbackDepth: 4, detectedAt: 3000 }),
    fork({ rollbackDepth: 1, detectedAt: 2000 }),
    fork({ rollbackDepth: 7, detectedAt: 1000 }),
  ]
  const layout = buildRadialLayout(forks, 150, 150)

  it('returns one node per fork', () => {
    expect(layout.nodes).toHaveLength(3)
  })

  it('most recent fork gets first angle slot (closest to 12 o\'clock)', () => {
    // sorted by detectedAt desc: 3000, 2000, 1000
    // first node is the most recent (detectedAt: 3000)
    const first = layout.nodes[0]
    // angle at index 0 with 3 items: -90 + 0*(360/3) = -90 degrees
    // y should be center - radius (above center)
    expect(first.y).toBeLessThan(150)
  })

  it('deeper fork has larger node radius', () => {
    const deep = layout.nodes.find(n => n.fork.rollbackDepth === 7)!
    const shallow = layout.nodes.find(n => n.fork.rollbackDepth === 1)!
    expect(deep.nodeR).toBeGreaterThan(shallow.nodeR)
  })
})
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd web && bunx vitest run src/components/network/components/radialForkMap.utils.test.ts
```

Expected: fails with "Cannot find module".

- [ ] **Step 3: Implement `radialForkMap.utils.ts`**

Create `web/src/components/network/components/radialForkMap.utils.ts`:

```ts
import type { ForkEvent } from '@/lib/nodeApi'

const INNER_R = 45
const OUTER_R = 105

export function depthToRadius(depth: number): number {
  const t = Math.min(Math.max(depth - 1, 0), 5) / 5
  return INNER_R + t * (OUTER_R - INNER_R)
}

export interface RadialNode {
  x: number
  y: number
  nodeR: number
  color: string
  fork: ForkEvent
  angle: number
  orbitR: number
}

export interface RadialLayout {
  nodes: RadialNode[]
}

export function buildRadialLayout(
  forks: ForkEvent[],
  cx: number,
  cy: number,
): RadialLayout {
  if (forks.length === 0) return { nodes: [] }

  const sorted = [...forks].sort((a, b) => b.detectedAt - a.detectedAt)
  const n = sorted.length

  const nodes: RadialNode[] = sorted.map((fork, i) => {
    const angleDeg = -90 + (i / n) * 360
    const angleRad = (angleDeg * Math.PI) / 180
    const orbitR = depthToRadius(fork.rollbackDepth)
    const x = cx + orbitR * Math.cos(angleRad)
    const y = cy + orbitR * Math.sin(angleRad)
    const nodeR = 6 + Math.min(fork.rollbackDepth, 6)
    const color = fork.rollbackDepth >= 3 ? 'var(--red, #ff4444)' : 'var(--gold)'
    return { x, y, nodeR, color, fork, angle: angleRad, orbitR }
  })

  return { nodes }
}
```

- [ ] **Step 4: Run tests**

```bash
cd web && bunx vitest run src/components/network/components/radialForkMap.utils.test.ts
```

Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add web/src/components/network/components/radialForkMap.utils.ts web/src/components/network/components/radialForkMap.utils.test.ts
git commit -m "feat(web): add RadialForkMap layout logic with tests"
```

---

## Task 8: RadialForkMap SVG component

**Files:**
- Create: `web/src/components/network/components/RadialForkMap.tsx`

- [ ] **Step 1: Create component**

Create `web/src/components/network/components/RadialForkMap.tsx`:

```tsx
import { useState } from 'react'
import { AnimatePresence } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import type { ForkEvent } from '@/lib/nodeApi'
import { buildRadialLayout } from './radialForkMap.utils'
import { ForkPointModal } from './ForkPointModal'

const SVG_SIZE = 300
const CX = 150
const CY = 150

interface Props {
  forks: ForkEvent[]
  myHeight: number
  forkingPeerAddresses: Set<string>
}

export function RadialForkMap({ forks, myHeight, forkingPeerAddresses }: Props) {
  const { t } = useTranslation()
  const [hovered, setHovered] = useState<number | null>(null)
  const [forkModalPeer, setForkModalPeer] = useState<string | null>(null)

  if (forks.length === 0) {
    return (
      <p className="py-4 text-center text-[11px]" style={{ color: 'var(--muted)' }}>
        {t('network.noReorgs')}
      </p>
    )
  }

  const { nodes } = buildRadialLayout(forks, CX, CY)

  return (
    <>
      <div className="flex flex-col items-center gap-2">
        <svg
          width={SVG_SIZE}
          height={SVG_SIZE}
          viewBox={`0 0 ${SVG_SIZE} ${SVG_SIZE}`}
          style={{ display: 'block' }}
        >
          {/* orbit rings */}
          <circle cx={CX} cy={CY} r={45}  stroke="var(--border2)" strokeWidth={1} strokeDasharray="3 5" fill="none" />
          <circle cx={CX} cy={CY} r={105} stroke="var(--border2)" strokeWidth={1} strokeDasharray="2 6" fill="none" opacity={0.5} />

          {/* center node */}
          <circle cx={CX} cy={CY} r={18} fill="color-mix(in srgb, var(--blue) 25%, transparent)" stroke="var(--blue2)" strokeWidth={1.8} />
          <text x={CX} y={CY - 3} fill="var(--blue2)" fontSize={7} textAnchor="middle" fontFamily="monospace">
            {Math.floor(myHeight / 1000)}K
          </text>
          <text x={CX} y={CY + 7} fill="var(--blue2)" fontSize={6} textAnchor="middle" letterSpacing={1}>
            NOW
          </text>

          {nodes.map((node, i) => {
            const isHovered = hovered === i
            const isClickable =
              node.fork.peerSource != null && forkingPeerAddresses.has(node.fork.peerSource)
            const opacity = hovered !== null && !isHovered ? 0.3 : 1

            return (
              <g
                key={i}
                style={{ opacity, cursor: isClickable ? 'pointer' : 'default' }}
                onMouseEnter={() => setHovered(i)}
                onMouseLeave={() => setHovered(null)}
                onClick={() => {
                  if (isClickable && node.fork.peerSource) setForkModalPeer(node.fork.peerSource)
                }}
              >
                {/* spoke */}
                <line
                  x1={CX} y1={CY} x2={node.x} y2={node.y}
                  stroke={node.color}
                  strokeWidth={1}
                  strokeDasharray="3 3"
                  opacity={0.4}
                />
                {/* node circle */}
                <circle
                  cx={node.x} cy={node.y}
                  r={isHovered ? node.nodeR + 2 : node.nodeR}
                  fill={`color-mix(in srgb, ${node.color} 18%, transparent)`}
                  stroke={node.color}
                  strokeWidth={isHovered ? 2 : 1.5}
                />
                {/* depth label */}
                <text
                  x={node.x} y={node.y + 3.5}
                  fill={node.color}
                  fontSize={8}
                  textAnchor="middle"
                  fontFamily="monospace"
                  fontWeight={isHovered ? 700 : 400}
                >
                  {`−${node.fork.rollbackDepth}`}
                </text>
                {/* height label offset away from center */}
                {isHovered && (
                  <text
                    x={node.x + (node.x > CX ? node.nodeR + 4 : -(node.nodeR + 4))}
                    y={node.y + 3}
                    fill="var(--muted)"
                    fontSize={7}
                    textAnchor={node.x > CX ? 'start' : 'end'}
                    fontFamily="monospace"
                  >
                    {node.fork.rollbackHeight.toLocaleString()}
                  </text>
                )}
              </g>
            )
          })}
        </svg>

        <p className="text-[8px] tracking-[1px]" style={{ color: 'var(--muted)' }}>
          distance = rollback depth · angle = recency
        </p>
      </div>

      <AnimatePresence>
        {forkModalPeer && (
          <ForkPointModal peer={forkModalPeer} onClose={() => setForkModalPeer(null)} />
        )}
      </AnimatePresence>
    </>
  )
}
```

- [ ] **Step 2: Type-check**

```bash
cd web && bunx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/network/components/RadialForkMap.tsx
git commit -m "feat(web): add RadialForkMap SVG component"
```

---

## Task 9: Refactor ForkHistory with pill toggle

**Files:**
- Modify: `web/src/components/network/components/ForkHistory.tsx`

- [ ] **Step 1: Rewrite ForkHistory.tsx**

Replace the full contents of `web/src/components/network/components/ForkHistory.tsx` with:

```tsx
import { useState } from 'react'
import type { ForkEvent } from '@/lib/nodeApi'
import { useTranslation } from 'react-i18next'
import { Card, CardLabel, CardSkeleton } from '@/components/ui/Card'
import { useForkHistory, useNetworkStatus } from '@/hooks/useNodeQuery'
import { BranchTimeline } from './BranchTimeline'
import { RadialForkMap } from './RadialForkMap'

const MERGE_WINDOW_MS = 5_000
const STORAGE_KEY = 'forkHistory.view'

function mergeReorgs(forks: ForkEvent[]): ForkEvent[] {
  if (forks.length === 0) return forks
  const sorted = [...forks].sort((a, b) => b.detectedAt - a.detectedAt)
  const merged: ForkEvent[] = []
  for (const fork of sorted) {
    const last = merged[merged.length - 1]
    if (
      last &&
      Math.abs(fork.detectedAt - last.detectedAt) <= MERGE_WINDOW_MS &&
      fork.peerSource === last.peerSource
    ) {
      last.rollbackHeight = Math.min(last.rollbackHeight, fork.rollbackHeight)
      last.rollbackDepth += fork.rollbackDepth
    } else {
      merged.push({ ...fork })
    }
  }
  return merged
}

type ViewMode = 'branch' | 'radial'

function readStoredView(): ViewMode {
  try {
    const v = localStorage.getItem(STORAGE_KEY)
    return v === 'radial' ? 'radial' : 'branch'
  } catch {
    return 'branch'
  }
}

export function ForkHistory() {
  const { data, isLoading } = useForkHistory()
  const { data: networkData } = useNetworkStatus()
  const { t } = useTranslation()
  const [view, setView] = useState<ViewMode>(readStoredView)

  const forks = mergeReorgs(data?.forks ?? [])
  const forkingPeerAddresses = new Set(
    (networkData?.peers ?? [])
      .filter(p => p.status === 'forking' && !p.blacklisted)
      .map(p => p.address),
  )
  const myHeight = networkData?.myHeight ?? 0

  function switchView(v: ViewMode) {
    setView(v)
    try { localStorage.setItem(STORAGE_KEY, v) } catch { /* ignore */ }
  }

  return (
    <Card className="col-span-full">
      <div className="mb-4 flex items-center justify-between gap-4">
        <CardLabel className="mb-0">{t('network.forkHistory')}</CardLabel>
        <div
          className="flex gap-1 rounded-sm p-0.5"
          style={{ background: 'rgba(0,0,0,.4)', border: '1px solid var(--border)' }}
        >
          {(['branch', 'radial'] as const).map(v => (
            <button
              key={v}
              type="button"
              className="rounded-sm px-3 py-1 text-[9px] uppercase tracking-[1.5px] transition-all"
              style={{
                background: view === v ? 'color-mix(in srgb, var(--blue2) 15%, transparent)' : 'transparent',
                color: view === v ? 'var(--blue2)' : 'var(--muted)',
                boxShadow: view === v ? '0 0 10px color-mix(in srgb, var(--blue2) 20%, transparent)' : 'none',
              }}
              onClick={() => switchView(v)}
            >
              {v === 'branch' ? `⌇ ${t('network.branchView')}` : `⊙ ${t('network.radialView')}`}
            </button>
          ))}
        </div>
      </div>

      {isLoading && (
        <div className="space-y-2">
          {[1, 2, 3].map(i => <div key={i} className="h-4 w-full"><CardSkeleton /></div>)}
        </div>
      )}

      {!isLoading && view === 'branch' && (
        <BranchTimeline forks={forks} myHeight={myHeight} forkingPeerAddresses={forkingPeerAddresses} />
      )}

      {!isLoading && view === 'radial' && (
        <RadialForkMap forks={forks} myHeight={myHeight} forkingPeerAddresses={forkingPeerAddresses} />
      )}
    </Card>
  )
}
```

- [ ] **Step 2: Type-check**

```bash
cd web && bunx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/network/components/ForkHistory.tsx
git commit -m "feat(web): refactor ForkHistory to Branch/Radial diagram views with pill toggle"
```

---

## Task 10: Network page layout — section dividers + ChainLanes

**Files:**
- Modify: `web/src/components/network/Network.tsx`

- [ ] **Step 1: Update Network.tsx**

Replace the full contents of `web/src/components/network/Network.tsx` with:

```tsx
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
```

- [ ] **Step 2: Type-check**

```bash
cd web && bunx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: Run all tests**

```bash
cd web && bunx vitest run
```

Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add web/src/components/network/Network.tsx
git commit -m "feat(web): reorganise Network page with section dividers and ChainLanes"
```
