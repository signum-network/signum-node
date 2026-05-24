# Network Analysis Feature — Specification

## Overview

A new **Network Analysis** page in the web UI that gives node operators deep visibility into peer health, fork detection, chain consensus state, and blacklist management. The feature is gated behind an operator-controlled flag and introduces new server-side API endpoints to support data that cannot be gathered from the browser directly (CORS, peer firewall).

---

## Goals

- Identify peers that are on a different chain (fork divergence)
- Show reorg/fork history to understand chain stability
- Surface "bad peers" (high failure rate, consistently behind, blacklisted)
- Give operators a blacklist recommendation they can act on
- All of this without requiring the operator to run external tooling

## Non-Goals

- Automated blacklisting (operator must decide)
- Real-time fork resolution or chain switching
- Deep transaction-level analysis
- Mobile layout (desktop-first for this page)

---

## Feature Flag

A single property gates the entire feature:

```properties
# node.properties
node.webUI.enabled=true
```

When `false`:
- All endpoints below return `HTTP 403` with `{"errorDescription": "Web UI is disabled"}`
- The web UI hides the Network Analysis nav item entirely
- The capability is advertised via `getState` so the UI can react without making a failed request:
  ```json
  { "webUIEnabled": true }
  ```

This mirrors the existing OpenAPI docs gate (`API.allowed`, `API.UI`). The Java servlet registers (or skips) the handlers at startup based on this flag.

---

## New / Extended API Endpoints

### 1. `getPeer` — extended

**Existing endpoint**, extended with additional fields. New fields are only present when `webUIEnabled=true`.

**Request:** `GET /api?requestType=getPeer&peer=<address>`

**New response fields:**

| Field | Type | Description |
|---|---|---|
| `blacklisted` | boolean | Whether this peer is currently blacklisted |
| `blacklistReason` | string \| null | Reason string if blacklisted |
| `connectionFailures` | number | Consecutive connection failures since last success |
| `latencyMs` | number | Last measured round-trip latency in ms |
| `lastBlockId` | string | Block ID the peer last reported |
| `lastCumulativeDifficulty` | string | Cumulative difficulty the peer last reported |
| `lastSeen` | number | Unix timestamp of last successful communication |

**Cost:** Zero — all fields are already tracked in memory by `PeerImpl`. No DB access.

---

### 2. `getNetworkStatus` — new

Server-side fan-out: queries all connected peers for their current block state, aggregates into a consensus picture.

**Request:** `GET /api?requestType=getNetworkStatus`

**Response:**

```json
{
  "myBlockId": "13647419...",
  "myCumulativeDifficulty": "12345678...",
  "myHeight": 1234567,
  "consensusPercent": 87.5,
  "peers": [
    {
      "address": "123.45.67.89",
      "blockId": "13647419...",
      "cumulativeDifficulty": "12345678...",
      "height": 1234567,
      "onOurChain": true,
      "latencyMs": 42
    }
  ],
  "stalePeers": 3,
  "forkingPeers": 1,
  "cachedAt": 1716000000000
}
```

**Cost:** One HTTP call per connected peer — potentially 100+ calls.
- Response is **cached for 60 seconds** server-side; `cachedAt` tells the client how fresh it is
- Peers are queried with a short timeout (2s per peer) and results are collected concurrently
- The UI polls this at most once per minute and shows the cache age

**`onOurChain`** is `true` when `cumulativeDifficulty` matches ours within a small tolerance (same chain, possibly slightly ahead/behind); `false` when significantly diverged.

---

### 3. `getForkHistory` — new

Returns recent chain reorganizations logged by the node.

**Request:** `GET /api?requestType=getForkHistory&limit=50`

**Response:**

```json
{
  "forks": [
    {
      "detectedAt": 1716000000000,
      "rollbackHeight": 1234500,
      "rollbackDepth": 3,
      "oldTopBlockId": "aabbcc...",
      "newTopBlockId": "ddeeff...",
      "peerSource": "123.45.67.89"
    }
  ]
}
```

**Implementation:** Persisted to a dedicated DB table (`fork_history`) written by `BlockchainProcessorImpl` on each rollback. Rows older than `node.webUI.forkHistory.ttlDays` (default: `30`) are pruned on a daily schedule. An in-memory cache of recent entries avoids hitting the DB on every request.

**Cost:** Near-zero for reads (cached). Writes are one small DB insert per reorg — rare events. Daily pruning is a single indexed `DELETE WHERE timestamp < cutoff`.

---

### 4. `findForkPoint` — new

Binary search to find the divergence block between this node and a specific peer.

**Request:** `GET /api?requestType=findForkPoint&peer=<address>`

**Response:**

```json
{
  "peer": "123.45.67.89",
  "forkAtHeight": 1234450,
  "forkAtBlockId": "aabbcc...",
  "ourBlockIdAtFork": "aabbcc...",
  "peerBlockIdAtFork": "ffee99...",
  "searchSteps": 7,
  "requestProcessingTime": 340
}
```

**Algorithm:** Binary search over `[currentHeight - MAX_LOOKBACK, currentHeight]` — fetch block ID at midpoint height from our DB, ask peer for block ID at same height, bisect. `MAX_LOOKBACK` is configurable via `node.webUI.forkPoint.maxLookback` (default: `1440` blocks ≈ 4 days at 240s block time).

**Cost:** ~log₂(MAX_LOOKBACK) DB lookups + same number of HTTP calls to the peer. At the default of 1440 that's ~11 steps, completing in 1–3 seconds. **On-demand only** — never polled by the UI.

---

### 5. `getBlacklist` — new

Returns the current peer blacklist.

**Request:** `GET /api?requestType=getBlacklist`

**Response:**

```json
{
  "blacklisted": [
    {
      "address": "123.45.67.89",
      "reason": "Too many forks",
      "blacklistedAt": 1716000000000,
      "expiresAt": 1716003600000
    }
  ],
  "count": 1
}
```

**Cost:** Zero — in-memory list, already maintained by `PeersImpl`.

---

## Web UI — Network Analysis Page

**Route:** `/network`

### Layout

Three sections stacked vertically:

#### 1. Consensus Bar

Full-width card showing:
- Large percentage: `87.5% on our chain`
- Sub-text: `42 peers agree · 5 stale · 1 forking`
- Color: green ≥ 90%, amber 70–89%, red < 70%
- Last refreshed timestamp + manual refresh button
- Auto-refreshes every 120 seconds (matches background worker cadence)

#### 2. Peer Table

Sortable table, columns:

| Address | Height | Cumul. Difficulty | Status | Latency | Failures | Actions |
|---|---|---|---|---|---|---|

- **Status** badge: `on-chain` (green) / `stale` (amber, within 5 blocks) / `forking` (red) / `blacklisted` (gray)
- **Actions**: `Find Fork Point` button (opens modal with result), `Blacklist Recommend` (copies recommendation text)
- Rows with `forking` status are highlighted
- Clicking a row expands to show `lastBlockId`, `blacklistReason` if any

#### 3. Fork History

Timeline list of recent reorgs:
- Timestamp, rollback depth, source peer
- Color-coded by depth: 1–2 blocks = amber, 3+ = red
- Empty state: "No reorganizations recorded since node start"

### Blacklist Recommendations Panel

A collapsible panel listing peers that exceed configurable thresholds (all via `node.properties`):
- `connectionFailures >` `node.webUI.blacklistRecommend.maxFailures` (default: `5`)
- `onOurChain = false` for `node.webUI.blacklistRecommend.forkPolls`+ consecutive polls (default: `3`)
- Already blacklisted

Each entry shows the peer address and the reason, with a copy button. Operators can use these addresses in node configuration manually — the UI does not blacklist automatically.

---

## Data Freshness Model

| Data | Source | Freshness |
|---|---|---|
| Peer list | `getPeers` | Live, on load |
| Per-peer details | `getPeer` | Live, on demand |
| Network consensus | `getNetworkStatus` | Background worker, 120s interval |
| Fork history | `getForkHistory` | Persisted DB, in-memory cache |
| Fork point | `findForkPoint` | On-demand, not cached |
| Blacklist | `getBlacklist` | Live |

---

## Security Considerations

- All new endpoints are **read-only** — no state mutation
- Peer IPs are already semi-public (shared in peer propagation protocol); no new leakage
- `getNetworkStatus` fan-out happens server-side; the browser never directly contacts peers (no CORS issues)
- Failure counts and latency metrics are **per-peer aggregates only** — no per-request detail that would help targeted attacks
- The web UI flag provides a clean operator opt-out for minimal-surface deployments

---

## Java Implementation Sketch

### New Handler Classes

```
brs.web.api.http.handler.GetNetworkStatus
brs.web.api.http.handler.GetForkHistory
brs.web.api.http.handler.FindForkPoint
brs.web.api.http.handler.GetBlacklist
```

### New Service / Infrastructure

```
brs.services.NetworkAnalysisService        (interface)
brs.services.impl.NetworkAnalysisServiceImpl
  - networkStatusCache   (snapshot, refreshed by background worker every 120s)
  - forkHistoryStore     (DB-backed, daily TTL pruning)
  - forkHistoryCache     (in-memory cache of recent entries)
```

`BlockchainProcessorImpl` emits to `NetworkAnalysisService.recordFork(...)` on each rollback (hooks into existing `RESCAN_BEGIN` / `BLOCK_POPPED` events). A `ScheduledExecutorService` drives the 120s peer fan-out and the daily pruning job.

### `getState` extension

Add `"webUIEnabled": <boolean>` to the `GetState` response handler.

### Handler Registration

In `APIServlet` (or wherever handlers are registered):

```java
if (propertyService.getBoolean(Props.WEB_UI_ENABLED, true)) {
    addHandler("getNetworkStatus", new GetNetworkStatus(...));
    addHandler("getForkHistory", new GetForkHistory(...));
    addHandler("findForkPoint", new FindForkPoint(...));
    addHandler("getBlacklist", new GetBlacklist(...));
}
```

---

## Configuration Reference

All properties go in `node.properties`. Defaults are shown.

| Property | Default | Description |
|---|---|---|
| `node.webUI.enabled` | `true` | Master switch for all web UI endpoints |
| `node.webUI.networkStatus.intervalSeconds` | `120` | Background worker poll cadence |
| `node.webUI.forkHistory.ttlDays` | `30` | Days to retain fork history rows in DB |
| `node.webUI.forkPoint.maxLookback` | `1440` | Max blocks to search back in `findForkPoint` |
| `node.webUI.blacklistRecommend.maxFailures` | `5` | Connection failure threshold for recommendation |
| `node.webUI.blacklistRecommend.forkPolls` | `3` | Consecutive forking polls before recommendation |
