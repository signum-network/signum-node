# InfoTooltip Improvements Design

**Date:** 2026-05-25  
**Branch:** feat/new-web-ui  
**Scope:** `web/src/components/ui/InfoTooltip.tsx`, `Card.tsx`, dashboard components, locale files

---

## Problem

Three distinct issues with the current `InfoTooltip` component:

1. **Vertical misalignment** — `CardLabel` is a `<p>` with `mb-2.5`. When placed beside `InfoTooltip` in a flex row, flexbox aligns the *margin box* of the `<p>` at center, making the label text appear slightly high relative to the (i) circle.

2. **Horizontal clipping** — The tooltip box uses `left: 50%; transform: translateX(-50%)` but only measures vertical overflow. On labels near the viewport edge (e.g., "Peers" table header in the Network tab), the tooltip box is cut off.

3. **Missing dashboard tooltips** — Technical concepts on the dashboard (cumulative difficulty, effective/physical capacity, avg commitment, JVM heap, DB trimming) have no explanatory tooltips.

---

## Architecture

### 1. `CardLabel` — new `tooltip` prop

Add an optional `tooltip?: string` prop. When provided:
- The `<p>` renders a nested `<span className="flex items-center gap-1.5">` containing the label text and an `InfoTooltip`.
- No `mb-2.5` misalignment issue because the icon is now *inside* the same block element, aligned via an inner flex span.
- All existing call sites that wrap `CardLabel` + `InfoTooltip` in a manual `<div className="flex items-center gap-1.5">` are migrated to the prop.

```tsx
// Before
<div className="flex items-center gap-1.5">
  <CardLabel>{t('network.chainConsensus')}</CardLabel>
  <InfoTooltip text={t('info.chainConsensus')} />
</div>

// After
<CardLabel tooltip={t('info.chainConsensus')}>{t('network.chainConsensus')}</CardLabel>
```

### 2. `InfoTooltip` — horizontal clamp

Extend the `useEffect` that runs on `visible` change to also compute a horizontal offset:

```
anchorCenter = rect.left + rect.width / 2
idealLeft    = anchorCenter - TOOLTIP_WIDTH / 2
clamped      = clamp(idealLeft, MARGIN, window.innerWidth - TOOLTIP_WIDTH - MARGIN)
offset       = clamped - idealLeft   // px correction from center
```

Store `offset` in state. Apply via `transform: translateX(calc(-50% + {offset}px))`.  
`TOOLTIP_WIDTH = 240`, `MARGIN = 8`.

No ref needed on the tooltip element; measurement uses the existing anchor ref.

### 3. Dashboard tooltip additions

Six `CardLabel` props wired across three components:

| File | Label | i18n key | Status |
|---|---|---|---|
| `CumulativeDifficultyCard.tsx` | Network Capacity | `info.networkCapacity` | exists |
| `CumulativeDifficultyCard.tsx` | Effective Capacity | `info.effectiveCapacity` | **new** |
| `CumulativeDifficultyCard.tsx` | Cumul. Difficulty (footer sub) | `info.cumulativeDifficulty` | exists |
| `MetricGrid.tsx` | Avg Commitment | `info.avgCommitment` | **new** |
| `NodeHealthStrip.tsx` | JVM Heap | `info.jvmHeap` | **new** |
| `NodeHealthStrip.tsx` | DB Trimming | `info.dbTrimming` | **new** |

The cumulative difficulty tooltip is on a `CardSub`, not a `CardLabel` — the `InfoTooltip` will be rendered inline next to the sub text using the same pattern as a standalone `InfoTooltip`.

### 4. New i18n keys (English)

```
info.effectiveCapacity:
  "Effective capacity weights each miner's raw storage by their Signum commitment (PoC+).
   A miner with more SIGNA staked earns a multiplier, so effective capacity can exceed
   physical capacity when the network has strong commitment."

info.avgCommitment:
  "The average amount of SIGNA committed per TiB across all active miners. Higher
   commitment raises the effective capacity multiplier, increases block rewards for
   committed miners, and makes the chain more expensive to attack."

info.jvmHeap:
  "JVM heap memory currently in use vs the maximum allocated to this node process.
   Above 80% sustained usage may cause GC pauses; above 90% risks OutOfMemoryError.
   Increase -Xmx in the startup script if this is consistently high."

info.dbTrimming:
  "When enabled, the node periodically removes old derived-table data (account balances,
   unconfirmed transactions) that can be recalculated from the blockchain. Keeps the
   database compact at the cost of a small background workload."
```

All 10 locale files (de, es, hi, ja, ko, pt, ru, uk, zh) receive the same keys with English text as placeholder (existing pattern in the codebase).

---

## Files Changed

- `web/src/components/ui/InfoTooltip.tsx` — horizontal clamp logic
- `web/src/components/ui/Card.tsx` — `tooltip` prop on `CardLabel`
- `web/src/routes/network/index.tsx` — migrate 4 call sites to `tooltip` prop
- `web/src/components/dashboard/components/CumulativeDifficultyCard.tsx` — 3 tooltips
- `web/src/components/dashboard/components/MetricGrid.tsx` — 1 tooltip
- `web/src/components/dashboard/components/NodeHealthStrip.tsx` — 2 tooltips
- `web/src/i18n/locales/en.ts` — 4 new `info.*` keys + type update
- `web/src/i18n/locales/{de,es,hi,ja,ko,pt,ru,uk,zh}.ts` — same 4 keys

---

## Out of Scope

- Touch/mobile tooltip behaviour (tap to open — existing approach retained)
- Animation on the tooltip box
- Any other component not listed above
