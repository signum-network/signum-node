# InfoTooltip Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix InfoTooltip vertical alignment and horizontal clipping, then add tooltips to six dashboard concepts across three components.

**Architecture:** The `tooltip` prop is added to `CardLabel` (renders the icon *inside* the label's own flex span, eliminating the cross-axis margin issue). The horizontal clamp is pure math in the existing `useEffect` — no extra refs needed. New i18n keys flow through the typed `Locale` interface so TypeScript enforces coverage in all 10 locale files.

**Tech Stack:** React 18, TypeScript (strict), Tailwind CSS, react-i18next, Vite

**Type-check command (run from `web/`):** `npx tsc --noEmit`

---

### Task 1: Fix InfoTooltip horizontal clipping

**Files:**
- Modify: `web/src/components/ui/InfoTooltip.tsx`

- [ ] **Step 1: Replace the entire file content**

```tsx
import { useState, useRef, useEffect } from 'react'

const TOOLTIP_WIDTH = 240
const VIEWPORT_MARGIN = 8

interface InfoTooltipProps {
  text: string
}

export function InfoTooltip({ text }: InfoTooltipProps) {
  const [visible, setVisible] = useState(false)
  const [above, setAbove] = useState(false)
  const [offset, setOffset] = useState(0)
  const ref = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    if (!visible || !ref.current) return
    const rect = ref.current.getBoundingClientRect()
    setAbove(rect.bottom + 160 > window.innerHeight)
    const center = rect.left + rect.width / 2
    const idealLeft = center - TOOLTIP_WIDTH / 2
    const clamped = Math.max(
      VIEWPORT_MARGIN,
      Math.min(idealLeft, window.innerWidth - TOOLTIP_WIDTH - VIEWPORT_MARGIN),
    )
    setOffset(clamped - idealLeft)
  }, [visible])

  return (
    <span
      ref={ref}
      className="relative inline-flex items-center"
      onMouseEnter={() => setVisible(true)}
      onMouseLeave={() => setVisible(false)}
      onFocus={() => setVisible(true)}
      onBlur={() => setVisible(false)}
    >
      <span
        tabIndex={0}
        role="button"
        aria-label="More information"
        className="inline-flex h-[14px] w-[14px] cursor-default items-center justify-center rounded-full text-[8px] font-bold leading-none select-none"
        style={{
          border: '1px solid var(--muted)',
          color: 'var(--muted)',
          opacity: 0.6,
          transition: 'opacity 0.12s, border-color 0.12s, color 0.12s',
          ...(visible ? { opacity: 1, borderColor: 'var(--blue2)', color: 'var(--blue2)' } : {}),
        }}
      >
        i
      </span>

      {visible && (
        <span
          className="pointer-events-none absolute z-50 w-[240px] p-3 text-[10px] leading-relaxed"
          style={{
            background: 'var(--bg2)',
            border: '1px solid var(--border2)',
            color: 'var(--text)',
            boxShadow: '0 8px 32px rgba(0,0,0,.5)',
            left: '50%',
            transform: `translateX(calc(-50% + ${offset}px))`,
            ...(above ? { bottom: 'calc(100% + 6px)' } : { top: 'calc(100% + 6px)' }),
          }}
        >
          {text}
        </span>
      )}
    </span>
  )
}
```

- [ ] **Step 2: Verify type-check passes**

```bash
cd web && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/ui/InfoTooltip.tsx
git commit -m "fix: clamp InfoTooltip horizontally within viewport"
```

---

### Task 2: Add `tooltip` prop to `CardLabel`

**Files:**
- Modify: `web/src/components/ui/Card.tsx`

- [ ] **Step 1: Add the InfoTooltip import at the top of Card.tsx**

Find the first line of `web/src/components/ui/Card.tsx`:
```tsx
import { motion, type HTMLMotionProps } from 'framer-motion'
```

Replace with:
```tsx
import { motion, type HTMLMotionProps } from 'framer-motion'
import { InfoTooltip } from '@/components/ui/InfoTooltip'
```

- [ ] **Step 2: Replace the `CardLabel` function**

Find:
```tsx
export function CardLabel({
  children,
  className,
}: {
  children: React.ReactNode
  className?: string
}) {
  return (
    <p
      className={cn(
        'mb-2.5 text-[9px] font-semibold uppercase tracking-[3px] text-[var(--blue2)]',
        className,
      )}
    >
      {children}
    </p>
  )
}
```

Replace with:
```tsx
export function CardLabel({
  children,
  className,
  tooltip,
}: {
  children: React.ReactNode
  className?: string
  tooltip?: string
}) {
  return (
    <p
      className={cn(
        'mb-2.5 text-[9px] font-semibold uppercase tracking-[3px] text-[var(--blue2)]',
        className,
      )}
    >
      {tooltip ? (
        <span className="flex items-center gap-1.5">
          {children}
          <InfoTooltip text={tooltip} />
        </span>
      ) : (
        children
      )}
    </p>
  )
}
```

- [ ] **Step 3: Verify type-check passes**

```bash
cd web && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add web/src/components/ui/Card.tsx
git commit -m "feat: add tooltip prop to CardLabel"
```

---

### Task 3: Update i18n — add 4 new info keys to all locale files

**Files:**
- Modify: `web/src/i18n/locales/en.ts`
- Modify: `web/src/i18n/locales/de.ts`
- Modify: `web/src/i18n/locales/es.ts`
- Modify: `web/src/i18n/locales/hi.ts`
- Modify: `web/src/i18n/locales/ja.ts`
- Modify: `web/src/i18n/locales/ko.ts`
- Modify: `web/src/i18n/locales/pt.ts`
- Modify: `web/src/i18n/locales/ru.ts`
- Modify: `web/src/i18n/locales/uk.ts`
- Modify: `web/src/i18n/locales/zh.ts`

> **Why all at once:** All locale files import `type { Locale } from './en'`. Adding keys to the interface makes TypeScript error on every file that is missing them. Update all in one pass so the type-check always passes.

- [ ] **Step 1: Update the `Locale` interface in `en.ts`**

Find this section in `web/src/i18n/locales/en.ts`:
```ts
  info: {
    chainConsensus: string; cumulativeDifficulty: string; findFork: string
    networkCapacity: string; baseTarget: string; peersTable: string; blacklistRecommendations: string
  }
```

Replace with:
```ts
  info: {
    chainConsensus: string; cumulativeDifficulty: string; findFork: string
    networkCapacity: string; effectiveCapacity: string; baseTarget: string
    peersTable: string; blacklistRecommendations: string
    avgCommitment: string; jvmHeap: string; dbTrimming: string
  }
```

- [ ] **Step 2: Add the 4 new English texts to the `en` constant in `en.ts`**

Find the end of the `info` block in the `en` constant:
```ts
    blacklistRecommendations:
      'Peers that are consistently on a different chain or have repeated connection failures. Adding them to P2P.BlacklistedPeers in node.properties prevents your node from wasting resources on them.',
  },
```

Replace with:
```ts
    blacklistRecommendations:
      'Peers that are consistently on a different chain or have repeated connection failures. Adding them to P2P.BlacklistedPeers in node.properties prevents your node from wasting resources on them.',
    effectiveCapacity:
      'Effective capacity weights each miner\'s raw storage by their Signum commitment (PoC+). A miner with more SIGNA staked earns a multiplier, so effective capacity can exceed physical capacity when the network has strong commitment.',
    avgCommitment:
      'The average amount of SIGNA committed per TiB across all active miners. Higher commitment raises the effective capacity multiplier, increases block rewards for committed miners, and makes the chain more expensive to attack.',
    jvmHeap:
      'JVM heap memory currently in use vs the maximum allocated to this process. Sustained usage above 80% may cause GC pauses; above 90% risks OutOfMemoryError. Raise -Xmx in the startup script if this is consistently high.',
    dbTrimming:
      'When enabled, the node periodically removes derived-table data (balances, unconfirmed transactions) that can be recalculated from the chain. Keeps the database compact at the cost of a small background workload.',
  },
```

- [ ] **Step 3: Add the 4 new keys to `de.ts`**

Find the end of the `info` block in `web/src/i18n/locales/de.ts`:
```ts
    blacklistRecommendations:
      'Peers, die dauerhaft auf einer anderen Kette sind oder wiederholte Verbindungsfehler aufweisen. Das Eintragen in P2P.BlacklistedPeers verhindert, dass der Knoten Ressourcen an sie verschwendet.',
  },
```

Replace with:
```ts
    blacklistRecommendations:
      'Peers, die dauerhaft auf einer anderen Kette sind oder wiederholte Verbindungsfehler aufweisen. Das Eintragen in P2P.BlacklistedPeers verhindert, dass der Knoten Ressourcen an sie verschwendet.',
    effectiveCapacity:
      'Effective capacity weights each miner\'s raw storage by their Signum commitment (PoC+). A miner with more SIGNA staked earns a multiplier, so effective capacity can exceed physical capacity when the network has strong commitment.',
    avgCommitment:
      'The average amount of SIGNA committed per TiB across all active miners. Higher commitment raises the effective capacity multiplier, increases block rewards for committed miners, and makes the chain more expensive to attack.',
    jvmHeap:
      'JVM heap memory currently in use vs the maximum allocated to this process. Sustained usage above 80% may cause GC pauses; above 90% risks OutOfMemoryError. Raise -Xmx in the startup script if this is consistently high.',
    dbTrimming:
      'When enabled, the node periodically removes derived-table data (balances, unconfirmed transactions) that can be recalculated from the chain. Keeps the database compact at the cost of a small background workload.',
  },
```

- [ ] **Step 4: Add the 4 new keys to `es.ts`, `hi.ts`, `ja.ts`, `ko.ts`, `pt.ts`, `ru.ts`, `uk.ts`, `zh.ts`**

For each of these files, find the closing `},` of the `info` block (it is always the last block before the closing `}` of the locale constant) and insert the same 4 English-text keys. The `info` block in every non-English file ends with:
```ts
    blacklistRecommendations:
      '<that locale's translation>',
  },
```

After that line (replacing the closing `},` at the end of info), insert:
```ts
    blacklistRecommendations:
      '<keep existing translation unchanged>',
    effectiveCapacity:
      'Effective capacity weights each miner\'s raw storage by their Signum commitment (PoC+). A miner with more SIGNA staked earns a multiplier, so effective capacity can exceed physical capacity when the network has strong commitment.',
    avgCommitment:
      'The average amount of SIGNA committed per TiB across all active miners. Higher commitment raises the effective capacity multiplier, increases block rewards for committed miners, and makes the chain more expensive to attack.',
    jvmHeap:
      'JVM heap memory currently in use vs the maximum allocated to this process. Sustained usage above 80% may cause GC pauses; above 90% risks OutOfMemoryError. Raise -Xmx in the startup script if this is consistently high.',
    dbTrimming:
      'When enabled, the node periodically removes derived-table data (balances, unconfirmed transactions) that can be recalculated from the chain. Keeps the database compact at the cost of a small background workload.',
  },
```

> Do this for: `es.ts`, `hi.ts`, `ja.ts`, `ko.ts`, `pt.ts`, `ru.ts`, `uk.ts`, `zh.ts`

- [ ] **Step 5: Verify type-check passes across all locale files**

```bash
cd web && npx tsc --noEmit
```

Expected: no errors. If you see `Property 'X' is missing in type`, you missed one of the 8 remaining locale files — add the key and re-run.

- [ ] **Step 6: Commit**

```bash
git add web/src/i18n/locales/
git commit -m "feat: add effectiveCapacity, avgCommitment, jvmHeap, dbTrimming info keys to all locales"
```

---

### Task 4: Migrate Network page to use `tooltip` prop

**Files:**
- Modify: `web/src/routes/network/index.tsx`

After this task, the standalone `InfoTooltip` import in `network/index.tsx` will be unused and must be removed.

- [ ] **Step 1: Update `ConsensusBar` (around line 64)**

Find:
```tsx
          <div className="flex items-center gap-1.5">
            <CardLabel>{t('network.chainConsensus')}</CardLabel>
            <InfoTooltip text={t('info.chainConsensus')} />
          </div>
```

Replace with:
```tsx
          <CardLabel tooltip={t('info.chainConsensus')}>{t('network.chainConsensus')}</CardLabel>
```

- [ ] **Step 2: Update `ForkPointModal` (around line 136)**

Find:
```tsx
        <div className="flex items-center gap-1.5">
          <CardLabel>{t('network.forkPoint.title')}</CardLabel>
          <InfoTooltip text={t('info.findFork')} />
        </div>
```

Replace with:
```tsx
        <CardLabel tooltip={t('info.findFork')}>{t('network.forkPoint.title')}</CardLabel>
```

- [ ] **Step 3: Update `PeerTable` (around line 242)**

Find:
```tsx
        <div className="flex items-center gap-1.5 px-5 pt-5">
          <CardLabel>{t('network.peers')}</CardLabel>
          <InfoTooltip text={t('info.peersTable')} />
        </div>
```

Replace with:
```tsx
        <div className="px-5 pt-5">
          <CardLabel tooltip={t('info.peersTable')}>{t('network.peers')}</CardLabel>
        </div>
```

- [ ] **Step 4: Update `BlacklistPanel` (around line 422)**

Find:
```tsx
        <div className="flex items-center gap-1.5">
          <CardLabel className="mb-0">{t('network.blacklistRecommendations')}</CardLabel>
          <InfoTooltip text={t('info.blacklistRecommendations')} />
        </div>
```

Replace with:
```tsx
        <CardLabel className="mb-0" tooltip={t('info.blacklistRecommendations')}>
          {t('network.blacklistRecommendations')}
        </CardLabel>
```

- [ ] **Step 5: Remove the now-unused `InfoTooltip` import**

Find at the top of `network/index.tsx`:
```tsx
import { InfoTooltip } from '@/components/ui/InfoTooltip'
```

Delete that line entirely.

- [ ] **Step 6: Verify type-check passes**

```bash
cd web && npx tsc --noEmit
```

Expected: no errors. If you see `'InfoTooltip' is declared but its value is never read`, the import was not removed.

- [ ] **Step 7: Commit**

```bash
git add web/src/routes/network/index.tsx
git commit -m "refactor: migrate network page to CardLabel tooltip prop"
```

---

### Task 5: Add tooltips to CumulativeDifficultyCard

**Files:**
- Modify: `web/src/components/dashboard/components/CumulativeDifficultyCard.tsx`

- [ ] **Step 1: Add InfoTooltip import**

Find the top of `CumulativeDifficultyCard.tsx`:
```tsx
import { useTranslation } from 'react-i18next'
import { Card, CardLabel, CardSub } from '@/components/ui/Card'
```

Replace with:
```tsx
import { useTranslation } from 'react-i18next'
import { Card, CardLabel, CardSub } from '@/components/ui/Card'
import { InfoTooltip } from '@/components/ui/InfoTooltip'
```

- [ ] **Step 2: Add tooltip to Network Capacity label**

Find:
```tsx
          <CardLabel>{t('dashboard.networkCapacity')}</CardLabel>
```

Replace with:
```tsx
          <CardLabel tooltip={t('info.networkCapacity')}>{t('dashboard.networkCapacity')}</CardLabel>
```

- [ ] **Step 3: Add tooltip to Effective Capacity label**

Find:
```tsx
          <CardLabel>{t('dashboard.effectiveCapacity')}</CardLabel>
```

Replace with:
```tsx
          <CardLabel tooltip={t('info.effectiveCapacity')}>{t('dashboard.effectiveCapacity')}</CardLabel>
```

- [ ] **Step 4: Add tooltip inline next to the Cumulative Difficulty sub-text**

Find:
```tsx
      <CardSub className="mt-1">
        {t('dashboard.cumulDifficulty', { value: isLoading ? '—' : current })}
      </CardSub>
```

Replace with:
```tsx
      <CardSub className="mt-1 flex items-center gap-1.5">
        <span>{t('dashboard.cumulDifficulty', { value: isLoading ? '—' : current })}</span>
        <InfoTooltip text={t('info.cumulativeDifficulty')} />
      </CardSub>
```

- [ ] **Step 5: Verify type-check passes**

```bash
cd web && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add web/src/components/dashboard/components/CumulativeDifficultyCard.tsx
git commit -m "feat: add tooltips to network capacity, effective capacity and cumulative difficulty"
```

---

### Task 6: Add tooltip to MetricGrid (Avg Commitment)

**Files:**
- Modify: `web/src/components/dashboard/components/MetricGrid.tsx`

- [ ] **Step 1: Add `tooltip` prop to `MetricCardProps` and `MetricCard`**

Find:
```tsx
interface MetricCardProps {
  label: string
  value: number
  sub: string
  color: string
  glow?: string
  isLoading?: boolean
  signa?: boolean
  formatter?: (n: number) => string
}

function MetricCard({ label, value, sub, color, glow, isLoading, signa, formatter }: MetricCardProps) {
  return (
    <Card interactive>
      <CardLabel>{label}</CardLabel>
```

Replace with:
```tsx
interface MetricCardProps {
  label: string
  value: number
  sub: string
  color: string
  glow?: string
  isLoading?: boolean
  signa?: boolean
  formatter?: (n: number) => string
  tooltip?: string
}

function MetricCard({ label, value, sub, color, glow, isLoading, signa, formatter, tooltip }: MetricCardProps) {
  return (
    <Card interactive>
      <CardLabel tooltip={tooltip}>{label}</CardLabel>
```

- [ ] **Step 2: Pass `tooltip` to the Avg Commitment card**

Find:
```tsx
      <MetricCard
        label={t('dashboard.avgCommitment')}
        value={avgCommitment}
        sub={t('dashboard.signaPerTiB')}
        color="var(--gold)"
        glow="var(--glow-gold)"
        isLoading={isLoading}
        signa
      />
```

Replace with:
```tsx
      <MetricCard
        label={t('dashboard.avgCommitment')}
        value={avgCommitment}
        sub={t('dashboard.signaPerTiB')}
        color="var(--gold)"
        glow="var(--glow-gold)"
        isLoading={isLoading}
        signa
        tooltip={t('info.avgCommitment')}
      />
```

- [ ] **Step 3: Verify type-check passes**

```bash
cd web && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add web/src/components/dashboard/components/MetricGrid.tsx
git commit -m "feat: add tooltip to avg commitment metric card"
```

---

### Task 7: Add tooltips to NodeHealthStrip

**Files:**
- Modify: `web/src/components/dashboard/components/NodeHealthStrip.tsx`

- [ ] **Step 1: Add tooltip to JVM Heap label**

Find:
```tsx
            <CardLabel className="mb-0">{t('dashboard.jvmHeap')}</CardLabel>
```

Replace with:
```tsx
            <CardLabel className="mb-0" tooltip={t('info.jvmHeap')}>{t('dashboard.jvmHeap')}</CardLabel>
```

- [ ] **Step 2: Add tooltip to DB Trimming label**

Find:
```tsx
          <CardLabel>{t('dashboard.dbTrimming')}</CardLabel>
```

Replace with:
```tsx
          <CardLabel tooltip={t('info.dbTrimming')}>{t('dashboard.dbTrimming')}</CardLabel>
```

- [ ] **Step 3: Verify type-check passes**

```bash
cd web && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add web/src/components/dashboard/components/NodeHealthStrip.tsx
git commit -m "feat: add tooltips to JVM heap and DB trimming in node health strip"
```

---

### Task 8: Final build verification

- [ ] **Step 1: Run full TypeScript + Vite build**

```bash
cd web && npm run build
```

Expected: build succeeds with no TypeScript errors and no import warnings.

- [ ] **Step 2: Confirm in browser (dev server)**

```bash
cd web && npm run dev
```

Open the app and verify:
- Dashboard → hover each new (i) icon: tooltip appears, stays within the viewport, icon is vertically centered with its label
- Network → hover "Peers" table header tooltip: box does not clip off the left edge
- Network → hover "Chain Consensus" tooltip: aligned correctly
- Network → hover "Blacklist Recommendations" tooltip: aligned correctly inside the button row
