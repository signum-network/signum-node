# Fork Visualisation & Network Page Redesign

**Date:** 2026-05-26
**Status:** Approved for implementation

---

## Overview

Two deliverables:

1. **ChainLanes** — a new component showing the current network fork situation (which peers are on which chain, right now).
2. **ForkHistory enhanced** — the existing fork/reorg history card upgraded with a Branch Timeline + Radial Map view, switchable via a pill toggle.

The Network page is reorganised into two labelled scroll sections so node-specific data and network-wide data are visually separated without any routing changes.

---

## Page Layout — Network

Single scroll, top to bottom:

```
┌─────────────────────────────────────────┐
│  NETWORK & PEERS                        │  ← section label (divider style)
├─────────────────────────────────────────┤
│  ConsensusBar          (existing)       │
│  ChainLanes            (new, own row)   │
│  PeerTable             (existing)       │
│  BlacklistPanel        (existing)       │
├──────── ▸ YOUR NODE ────────────────────┤  ← styled divider
│  ForkHistory           (enhanced)       │
└─────────────────────────────────────────┘
```

### Section divider

A full-width horizontal rule with a centred label — e.g. `▸ YOUR NODE` in `var(--muted)` uppercase tracking. No new card wrapper; the existing `page-layout` gap provides spacing.

---

## Component 1 — ChainLanes

### Purpose

Show which peers are on which chain *right now*, grouped by cumulative difficulty. Answers: "Is the network split? How many peers are on a competing chain?"

### Data source

`useNetworkStatus()` — already fetched by `ConsensusBar`, so no additional API call. Uses the `peers: PeerStatusEntry[]` array and `myHeight`.

### Grouping logic

1. **Our chain lane** — peers where `onOurChain === true` (status `on-chain` or `stale`).
2. **Fork lanes** — peers where `status === 'forking'`, grouped by `cumulativeDifficulty`. Peers whose `cumulativeDifficulty` strings match exactly share a lane. If there are multiple distinct values, each gets its own lane.
3. Lanes are sorted by peer count descending (our chain always first).

### Visual structure

Each lane is a vertical column:

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ OUR CHAIN   │  │  FORK A     │  │  FORK B     │
│ 1,543,080   │  │  1,543,046  │  │  1,543,073  │
│             │  │             │  │             │
│  ● ● ● ●   │  │  ● ● ●      │  │  ● ●        │
│  ● ● ● ●   │  │             │  │             │
│             │  │             │  │             │
│  12 peers   │  │   3 peers   │  │   2 peers   │
├─────────────┤  ├─────────────┤  ├─────────────┤
│ ▓▓▓▓▓▓▓▓▓  │  │ ▓▓▓         │  │ ▓           │
│ cumul. diff │  │ cumul. diff │  │ cumul. diff │
└─────────────┘  └─────────────┘  └─────────────┘
```

- Peer dots: green (`--green`) for on-chain, gold (`--gold`) for stale, red (`--red`) for forking.
- Cumulative difficulty bar: relative width across all lanes, showing which chain has the most accumulated work.
- Fork lane borders: dashed red, our chain border is solid green.
- If no fork lanes exist: show only our chain lane with a "No active forks" note.
- Hover on a peer dot: tooltip showing `address`, `height`, `status`.

### Placement

Own `<Card>` in a dedicated grid row between `ConsensusBar` and `PeerTable`.

---

## Component 2 — ForkHistory (enhanced)

### Purpose

Show your node's own reorg history as a diagram. Answers: "What chain switches did my node experience, how deep were they, and when?"

### Data source

`useForkHistory()` — existing hook. Input is the already-merged `ForkEvent[]` (deduplication via `mergeReorgs()` already implemented).

### Pill toggle

In the card header, right-aligned:

```
Fork / Reorg History          ⌇ Branch  ⊙ Radial
```

Selected view persists in `localStorage` key `forkHistory.view` (`'branch'` | `'radial'`). Default: `'branch'`.

---

### View A — Branch Timeline

Horizontal SVG. X-axis = block height (left = older, right = newer / current tip). Each merged `ForkEvent` renders as a closed arch above the axis.

**Layout rules:**
- Main chain: solid horizontal line, `--blue2`, full width.
- `myHeight` at the right edge, labelled "NOW".
- Oldest recorded fork determines left edge (with a small margin).
- Each fork arch:
  - Left foot: `rollbackHeight - rollbackDepth` (where the competing chain diverged).
  - Right foot: `rollbackHeight` (where our node switched back).
  - Arch peak height: proportional to `rollbackDepth` (capped at a max to avoid clipping).
  - Color: `var(--gold)` if `rollbackDepth < 3`, `var(--red, #ff4444)` if `≥ 3`.
  - Stroke: dashed.
  - Dot at the right foot (the fork-detection point).
- Labels below each arch foot: block height, timestamp, peer source (if available).
- If a fork peer is currently still `forking` in peer data: label "FORKING PEER" in red below.
- Scrollable horizontally if forks span a wide height range (`overflow-x: auto` with `themed-scroll`).
- Empty state: "No reorganizations recorded since node start."

---

### View B — Radial Map

Square SVG, centred. Your current tip (`myHeight`) at centre.

**Layout rules:**
- Two dashed concentric rings for visual depth reference.
- Each merged `ForkEvent` is a node:
  - **Distance from centre** = `rollbackDepth` (scaled — depth 1 → inner ring, depth ≥ 6 → outer ring, linear interpolation).
  - **Angle** = evenly distributed, most recent fork at 12 o'clock, clockwise by `detectedAt` descending.
  - **Node radius** = `6 + clamp(rollbackDepth, 0, 6)` px.
  - **Color**: same severity rule as branch view (`var(--gold)` / `var(--red, #ff4444)`).
  - Dashed spoke from centre to each node.
- Labels near each node: depth (`−N`), block height.
- Legend below SVG: "distance = rollback depth · angle = recency".
- Empty state: same as branch view.

---

## Interactions

| Element | Interaction |
|---|---|
| Peer dot in ChainLanes | Hover → tooltip (address, height, status) |
| Fork node in Branch/Radial | Hover → tooltip (height, depth, time, peer source) |
| Fork node (forking peer still active) | Click → open existing `ForkPointModal` for that peer |
| Pill toggle | Click → switch view, persist to `localStorage` |

The `ForkPointModal` connection applies only when the fork's `peerSource` matches a peer currently in `forking` status in the network status data.

---

## Implementation notes

### No new API calls

Both components use existing hooks:
- `useNetworkStatus()` — ConsensusBar already calls this; ChainLanes can share the same query.
- `useForkHistory()` — already used by ForkHistory.

### SVG rendering

Both fork diagram views are plain inline SVG — no chart library dependency. Dimensions are computed from data at render time. SVG viewBox is dynamic based on height range (branch) or fork count (radial).

### Theming

All colours use CSS custom properties (`--blue2`, `--green`, `--gold`, `--red`, `--muted`, `--border`, etc.) so diagrams adapt to all five themes automatically.

### File structure

```
web/src/components/network/
  components/
    ChainLanes.tsx       ← new
    BranchTimeline.tsx   ← new (SVG branch view, used by ForkHistory)
    RadialForkMap.tsx    ← new (SVG radial view, used by ForkHistory)
    ForkHistory.tsx      ← updated: list removed, pill toggle + view switch added
    ConsensusBar.tsx     existing
    PeerTable.tsx        existing
    BlacklistPanel.tsx   existing
    ForkPointModal.tsx   existing
  Network.tsx            ← updated (section divider + ChainLanes row)
```

### i18n

New keys needed (all locales):
- `network.chainLanes` — card label
- `network.ourChain` — lane label
- `network.forkLane` — fork lane label (+ index suffix)
- `network.noActiveForks` — empty state
- `network.sectionNetwork` — section divider label
- `network.sectionYourNode` — section divider label
- `info.chainLanes` — tooltip for CardLabel

---

## Out of scope

- Animating individual peer dots entering/leaving lanes (static render only).
- Historical ChainLanes snapshots (only current peer state).
- Making the radial diagram interactive beyond hover tooltips.
- Any routing or nav changes.
