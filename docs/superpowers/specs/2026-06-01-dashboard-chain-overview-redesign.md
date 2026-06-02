# Dashboard Chain Overview Redesign

**Date:** 2026-06-01
**Branch:** feat/new-web-ui
**Status:** Approved

## Problem

The dashboard has two adjacent sections — "Chain Activity" and "Ecosystem" — that lack a clear mental model separating them. DEX-related metrics (Digital Assets, Live DEX Orders, Trades, Transfers) are scattered across both sections alongside unrelated core chain stats. The ask/bid breakdown is buried in a sub-label. The overall layout is noisy and hard to scan.

## Decision

Replace "Chain Activity" and "Ecosystem" with a single **"Chain Overview"** section containing two symmetric rows of three cards each.

## New Layout

### Section: Chain Overview

**Row 1 — Primary metrics** (large cards, existing `ChainMetricCard` style)

| Card | Value | Sub-label | Color |
|---|---|---|---|
| Transactions | `numberOfTransactions` | confirmed on-chain | gold |
| Accounts | `numberOfAccounts` | registered on-chain | blue |
| Smart Contracts | `numberOfATs` | ATs deployed | blue |

**Row 2 — Secondary metrics** (same card component, reduced number font size ~22px)

| Card | Value | Sub-label | Color |
|---|---|---|---|
| Aliases | `numberOfAliases` | on-chain names | gold |
| Subscriptions | `numberOfSubscriptions` | recurring payments | gold |
| Asset Exchange | `numberOfAssets` · `numberOfTrades` · `numberOfTransfers` | built-in DEX activity | green, labeled "Asset Exchange" |

Both rows use a `grid grid-cols-1 sm:grid-cols-3 gap-3 md:gap-4` layout (3 cards don't fit the existing `xl:grid-cols-4` pattern — switch to a fixed 3-column grid that collapses to 1 on small screens).

## Removed from Dashboard

The following metrics are removed entirely from the dashboard. They will belong on a dedicated Asset Exchange page in the future:

- `numberOfAssets` as a standalone large card
- `numberOfAskOrders` + `numberOfBidOrders` (Live DEX Orders card)
- `numberOfOrders` (unused directly)

## Asset Exchange Card (Row 2, slot 3)

Shows three inline stats inside a single card:

- **Assets** — `numberOfAssets` — tokens issued
- **Trades** — `numberOfTrades` — executed trades
- **Transfers** — `numberOfTransfers` — token movements

Labeled "Asset Exchange" in place of a standard `CardLabel`, styled with a distinct accent color (e.g. `var(--mag)`) to signal it's a subsystem. No ask/bid breakdown shown.

## Component Changes

### `Dashboard.tsx`
- Remove `SectionHeading` for `dashboard.chainActivity` and `dashboard.ecosystem`
- Add single `SectionHeading` for `dashboard.chainOverview` (new i18n key)
- Render two `grid grid-cols-3` rows under that heading
- Row 1: updated `ChainActivityRow` (3 cards)
- Row 2: new `ChainSecondaryRow` component (3 cards)

### `ChainActivityRow.tsx`
- Remove: Digital Assets card (`numberOfAssets`)
- Remove: Live DEX Orders card (`numberOfAskOrders + numberOfBidOrders`)
- Add: Accounts card (`numberOfAccounts`, sub: "registered on-chain", color: blue)
- Result: Transactions · Accounts · Smart Contracts

### `EcosystemStrip.tsx`
- Retire this component (replaced by `ChainSecondaryRow`)

### New: `ChainSecondaryRow.tsx`
- Three cards using the same `Card` container as row 1 but with smaller number font size
- Card 1: Aliases
- Card 2: Subscriptions
- Card 3: Asset Exchange inline stats (Assets · Trades · Transfers)

## i18n Keys Required

New key: `dashboard.chainOverview`
Keys no longer needed on dashboard: `dashboard.digitalAssets`, `dashboard.liveDexOrders`, `dashboard.askBid`, `dashboard.trades`, `dashboard.transfers` (can stay for future DEX page reuse)

## Out of Scope

- No changes to Network, Node Health sections
- No new data fetching — all values already exist in `FullBlockchainStatus`
- No dedicated DEX page (future work)
