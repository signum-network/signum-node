# Neo-Page Design Spec
**Date:** 2026-05-21  
**Status:** Approved  
**Scope:** Project setup, Nexus theme system, app shell, dashboard landing page. Network Analyzer sub-page is out of scope — separate spec follows.

---

## Overview

Neo-page is a new node operator UI for Signum Node, built as a standalone React application living inside the `signum-node` repository. It ships with the node, is built by Gradle, and is served at `/ui/v2/`. It replaces neither the classic UI nor the Phoenix wallet — it is a node-specific operator dashboard that those two are not.

The first release delivers two things: a **Nexus-themed app shell** (navigation, routing structure, design system) and a **dashboard landing page** showing real-time node health indicators. Future sub-pages (Network Analyzer, Block Inspector, Wallet) slot into the shell without modification.

---

## Project Structure

```
web/                              # source root
├── src/
│   ├── routes/
│   │   ├── __root.tsx            # root layout: shell, topbar, WS provider
│   │   ├── index.tsx             # dashboard (landing page)
│   │   └── network/
│   │       └── index.tsx         # Network Analyzer (placeholder, future spec)
│   ├── components/
│   │   ├── ui/                   # primitives: Card, Badge, Pill, Sparkline, ProgressBar, RingProgress
│   │   └── layout/               # Shell, Topbar, NavItem, PageWrapper
│   ├── hooks/
│   │   ├── useNodeSocket.ts      # WebSocket connection + event dispatch
│   │   └── useNodeQuery.ts       # TanStack Query wrapper for node HTTP API
│   ├── lib/
│   │   └── nodeApi.ts            # typed fetch functions for each HTTP endpoint
│   ├── theme/
│   │   └── tokens.ts             # design token re-exports (mirrors tailwind config)
│   └── main.tsx
├── package.json
├── vite.config.ts
├── tailwind.config.ts
└── tsconfig.json
```

---

## Stack

| Concern | Choice |
|---------|--------|
| Runtime / package manager | Bun |
| Build tool | Vite |
| Framework | React 19 + TypeScript |
| Routing | TanStack Router (file-based, type-safe) |
| Async data | TanStack Query |
| Styling | Tailwind CSS v4 |
| Animation | Framer Motion |
| Fonts | Orbitron (headings/numbers) + Exo 2 (body) via Google Fonts |

---

## Build Integration

Gradle task `buildWeb` runs in `web/`:

```groovy
task buildWeb(type: Exec) {
    workingDir 'web'
    commandLine 'bun', 'run', 'build'
    inputs.dir('web/src')
    outputs.dir('html/ui/v2')
}

task copyWeb(type: Copy, dependsOn: buildWeb) {
    from 'web/dist'
    into 'html/ui/v2'
}

dist.dependsOn copyWeb
```

**`openapi/` migration:** swap `npm` → `bun` in its Gradle task and in CI. No other changes needed.

**CI prerequisite:** add `oven-sh/setup-bun@v1` step to `build.yml` and `release.yml` before any `buildWeb` or `buildOpenApi` task runs.

**Dev workflow:** `cd web && bun run dev` — Vite proxies `/api` → `http://localhost:8125` (configurable via `VITE_NODE_URL` env var).

---

## Nexus Theme System

### Design Tokens (Tailwind config + CSS variables)

```
Background:    #050810  (--bg)
Surface:       #080d1a  (--bg2)
Panel:         rgba(8,16,40,.85)  (--panel)
Border:        rgba(0,102,255,.18)  (--border)
Border bright: rgba(0,170,255,.30)  (--border2)

Blue primary:  #0066ff  (--blue)
Blue bright:   #00aaff  (--blue2)
Blue light:    #60c8ff  (--blue3)
Green:         #00ffaa  (--green)
Magenta:       #ff0055  (--mag)
Gold:          #ffd700  (--gold)
Amber:         #ff9500  (--amber)
Text:          #d0e4ff  (--text)
Muted:         #3a5070  (--muted)
```

### Grid Background

Applied at the shell root — a subtle CSS grid overlay using linear gradients at 4% opacity, 40px spacing. Gives the "holographic floor" depth without distracting from content.

### Card Component

All content panels use a shared `Card` component:
- Background: `--panel` with `backdrop-filter: blur(8px)`
- Border: 1px solid `--border`
- **Corner brackets:** four pseudo-elements place 14×14px L-shaped brackets at the corners using `--blue2`. Top-left and bottom-right brackets are full brightness; top-right and bottom-left are at 30% opacity for asymmetric depth.
- Padding: 20px default, overridable

### Typography

- **Headings / large numbers:** Orbitron, weights 400–900
- **Body / labels / tables:** Exo 2, weights 300–700
- **Card labels:** 9px, 3px letter-spacing, uppercase, `--blue2`
- **Muted text:** 10px, 1–2px letter-spacing, `--muted`

### Navigation (Topbar)

- Height: 60px, sticky
- Background: linear gradient from `rgba(0,102,255,.07)` → transparent
- Border-bottom: 1px `--border`
- Nav items: `clip-path: polygon(10px 0%, 100% 0%, calc(100% - 10px) 100%, 0% 100%)` — angular cut on both sides
- Active item: `--blue2` text + 2px bottom border with glow, subtle blue background fill
- Future/unbuilt pages: 20% opacity, pointer-events none (visible but locked)

### Page Transitions

Framer Motion `AnimatePresence` on route change: `opacity 0→1`, `y 8→0`, duration 200ms. Applied at `<Outlet />` level in `__root.tsx`.

---

## App Shell

`__root.tsx` renders:

1. **WebSocket provider** — wraps entire tree, one persistent connection
2. **Topbar** — logo, nav tabs, topbar-right status strip
3. **`<Outlet />`** — current page with page transition wrapper

### Topbar Status Strip (right side)

Always-visible quick-glance indicators, fed by the same query cache as the dashboard:
- Network name (Mainnet / Testnet)
- Node version (vX.Y.Z)

### Route Structure

```
/           → Dashboard (index.tsx)
/network    → Network Analyzer (placeholder: "Coming Soon" card)
/blocks     → future
/wallet     → future
```

Unknown routes → redirect to `/`.

---

## Data Layer

### Pattern

HTTP polling is the primary source. WebSocket is a progressive enhancement that reduces poll latency when available.

```
On mount:
  1. Fetch initial data via HTTP (getBlockchainStatus, getPeers)
  2. Attempt WebSocket connection

WebSocket available:
  BLOCK_PUSHED          → queryClient.invalidateQueries(['blockchainStatus'])
  PENDING_TXS_ADDED     → queryClient.invalidateQueries(['unconfirmedTxCount'])
  CONNECTED             → seed isSyncing / globalHeight to query cache

WebSocket unavailable / disabled:
  TanStack Query refetchInterval = 10s (blockchainStatus, peers)
  No user-visible difference — slightly slower updates
```

### HTTP Endpoints Used

| Query key | Endpoint | Poll interval (no WS) |
|-----------|----------|-----------------------|
| `blockchainStatus` | `getBlockchainStatus` | 10s |
| `peers` | `getPeers?active=true` + `getPeer` per address | 30s |
| `miningInfo` | `getMiningInfo` | 30s | baseTarget, averageCommitmentNQT, lastBlockReward |
| `unconfirmedTxCount` | `getUnconfirmedTransactions` (count only) | 10s |

### `useNodeSocket.ts`

- Connects to `ws://{nodeHost}/events`
- Exponential backoff reconnect (1s → 2s → 4s → max 30s)
- Exposes `{ connected: boolean }` to consumers via context
- On each event, calls the appropriate `queryClient.invalidateQueries`
- Silent failure: if WS is unreachable after 3 attempts, stops trying and polling takes over

---

## Dashboard — Horizon Layout

### Banner (top, full-width)

Three sections divided by vertical separators:

**Left block** (largest, ~260px min-width):
- Label: "Current Block Height"  
- Value: block height in Orbitron 56px, `--blue2`, glow
- Status pills: Synced/Syncing (green/amber) + network name

**Center block** (flex-1):
- Sync progress bar (6px height, gradient blue→green when synced, amber when syncing)
- Labels: "Local X / Global Y"
- Two sub-metrics in a 2-col grid: Block Time (current avg vs 4:00 target) + Last Block Reward (SIGNA)

**Right block** (~200px min-width):
- Feeder peer address (which peer last fed blocks)
- Feeder height
- Base Target (from `getMiningInfo.baseTarget`) — mining difficulty indicator

Banner has a subtle radial gradient bloom at left (30% opacity blue) and top/bottom edge highlight lines.

### Metric Grid (below banner)

4-column CSS grid, 16px gap:

| Position | Metric | Size | Color |
|----------|--------|------|-------|
| col 1 | Peers Connected | 42px Orbitron | green |
| col 2 | Pending TXs | 42px Orbitron | magenta |
| col 3 | Avg Commitment (SIGNA/TiB) | 42px Orbitron | gold |
| col 4 | Network Capacity (PiB) — derived from `getMiningInfo.baseTarget` | 42px Orbitron | blue2 |
| col 5–6 (span 2) | Cumulative Difficulty + sparkline | 28px + 40px bars | blue |
| col 7–8 (span 2) | Peer Version Distribution | bar chart | blue/amber/mag |

**Cumulative difficulty** sparkline: 15 bars representing last 15 polling intervals, blue fill, steadily rising = healthy chain.

**Peer version distribution:** horizontal bar chart grouped by version bucket (current / outdated / fork-risk), color-coded green/amber/magenta with peer counts. Feeds from `getPeer` data.

### Empty / Loading States

- On initial load: cards render with skeleton shimmer (Tailwind `animate-pulse`, `--border` color)
- On HTTP error: card shows "Unavailable" in `--muted` with a retry button
- Polling indicator: subtle pulsing dot in topbar-right when a fetch is in flight

---

## Extension Pattern for Future Sub-Pages

Adding a new sub-page requires:

1. Create `src/routes/<name>/index.tsx` — TanStack Router picks it up automatically
2. Add a `NavItem` entry in `__root.tsx` (remove `future` prop to make it active)
3. Export a page component that uses `PageWrapper` for consistent padding and transitions

No changes to theme, shell, or data layer needed.

---

## Testing

- `Card`, `Pill`, `ProgressBar`, `RingProgress`, `Sparkline` — unit tests with React Testing Library
- `useNodeSocket` — unit tested with a mock WebSocket server (`ws` package)
- `useNodeQuery` / `nodeApi.ts` — unit tested with `msw` (Mock Service Worker) intercepting HTTP
- Dashboard layout — one integration test: mount with mocked API responses, assert key values render
- No visual regression tests in scope for this phase

---

## Out of Scope (this spec)

- Network Analyzer sub-page (separate spec)
- Block Inspector, Transaction Inspector, Wallet
- Dark/light theme toggle
- Localisation
- Mobile layout (desktop-first for node operators)
- Authentication / access control (node API has none today)
