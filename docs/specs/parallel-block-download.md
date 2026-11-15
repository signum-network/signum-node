# Spec: Parallel Block Download

Status: draft · Branch: `perf/parallel-download` · Target: 3.10.0 · Related: PR #1097 (batched PoC, decoupled sync)

## 1. Purpose

Make the initial sync, and catching up after a long time offline, faster wherever block download is
the bottleneck.

The download thread (`getMoreBlocks` in `BlockchainProcessorImpl`) fetches from a single peer per
round, one step after the other:

1. `getCumulativeDifficulty`
2. `getMilestoneBlockIds`
3. `getNextBlocks` (up to 720 blocks or 1 MB)
4. parse and append the blocks to the download cache

Only then does the next round start. Network and processing never overlap, and only one peer is
ever asked for blocks at a time.

On mainnet below the checkpoint (`node.checkPointHeight`, 1,418,000) no PoC is computed, so the
work of #1097 has no effect there. A 60 s JFR profile of a mainnet resync at around height 20k
spent about 49 s of 60 s waiting on peer socket reads (`CountingInputStream.read`,
`PeerImpl.send`), with almost no CPU use. The node synced about 20–40 blocks/s.

Peers have served `getBlocksFromHeight(height, numBlocks)` since 2014. It returns blocks by
height, not by block id, so consecutive ranges can be requested from several peers at the same
time without knowing their ids first. No protocol change and no fork are needed.

## 2. Scope

### In scope
- Parallel download of consecutive height ranges from up to K peers during bulk sync
- Per-segment peer attribution, so a bad block is blamed on the peer that sent it
- Settings to switch it off and to tune K and the range size
- Unit tests and an A/B measurement on mainnet

### Non-goals
- Fork handling. Forks keep using the existing sequential path.
- Pipelining across rounds (fetching round N+1 while round N is processed). Possible follow-up.
- Changes to the peer protocol or to the serving side (`GetBlocksFromHeight`, `GetNextBlocks`)
- A general limit on peer response size in `PeerImpl.send`. Pre-existing gap, separate issue.

## 3. When the parallel path is used

All of these must hold in a round:

1. `node.experimental.parallelDownload` is true.
2. The common block with the feeder peer is the download cache tip (`commonBlockId ==
   downloadCache.getLastBlockId()`, so `saveInCache` is true). We only extend our chain linearly.
3. The feeder's reported `blockchainHeight` is at least `cacheHeight + 2 × blocksPerPeer`.
4. At least one helper candidate exists (see 4.2). Otherwise the round uses `getNextBlocks` as today.

Every other case runs unchanged: forks, being near the chain tip, and blocks announced or forged
while syncing.

## 4. Design

### 4.1 `ParallelBlockFetcher` (package `brs`)

A separate class, so the anonymous `getMoreBlocks` runnable does not grow further. Constructor
injection:

- `DownloadPeers`: a small interface that returns candidate peers. The production implementation
  wraps `Peers.getActivePeers()` and keeps peers that are `CONNECTED`, not blacklisted, at least
  `MIN_PEER_VERSION` and on our network name. That is the same filter the feeder loop uses today.
- a pool of K−1 daemon threads for the helpers, named `ParallelBlockFetcher-n`, shut down with
  the node. The feeder's segment is downloaded on the calling download thread, so it never waits
  behind helper requests that are still hanging on a socket.
- `peers` (K), `blocksPerPeer` (n) and the request timeout

Main method:

```java
List<Segment> fetch(Peer feeder, BigInteger ourCumulativeDifficulty, int cacheHeight,
        int feederHeight, int maxBlocks)
```

`Segment` is a record `(Peer peer, int fromHeight, JsonArray blocks)`. The method returns the
segments in height order. The list may be shorter than requested, and it is empty if there is no
helper candidate.

### 4.2 Helper selection

- Pick up to K−1 candidates other than the feeder, in random order. If there is no candidate,
  `fetch` returns an empty list and the round falls back to `getNextBlocks`.
- Segment 0 goes to the feeder and is requested right away on the calling thread.
- At the same time each helper task probes its candidate with `getCumulativeDifficulty`. A
  candidate qualifies if its cumulative difficulty is higher than `ourCumulativeDifficulty` and
  its `blockchainHeight` reaches the last height of the round.
- A qualified helper then takes the next free segment (1, 2, …) and downloads it. Helpers that do
  not qualify, do not answer or answer with garbage take no segment, so they leave no gap. They
  are not blacklisted: a slow peer is not a malicious peer.
- When every helper has finished its probe, the segments nobody took are marked missing, and the
  round is cut there (4.4).

Taking segments only after the probe means one unsuitable helper does not shrink the round to
the feeder's segment. A candidate whose fresh probe result already shows that it cannot serve the
round (lower cumulative difficulty, or a height below the round's end, so it is itself behind) is
not picked at all. If no candidate is left, the round falls back to `getNextBlocks`, which
returns more blocks than the feeder's segment alone.

A probe result (cumulative difficulty and height) is reused for 60 s, as long as its height still
covers the round. Each probe is a separate request with its own TCP connection; measured on
mainnet, that is about 0.5 s on the critical path of every helper.

A helper that gives no usable probe answer, whose segment fails, or that misses the round's
helper deadline is skipped for 60 s. Without that, a dead peer that is still listed as connected
would hold up every round until its connect timeout. The helper deadline is twice the time the
feeder needed for its segment, at least 3 s after it arrived, and never more than the round
timeout. `node.parallelDownload.peers` is capped at 16.

### 4.3 Segments

- Round size: `total = min(K × n, feederHeight − cacheHeight, maxBlocks)`
  - `maxBlocks` comes from the existing download window. After `POC_PLUS` it is `blockchainHeight +
    MAX_ROLLBACK / 2 − cacheHeight`, the same limit the round already checks at the start. Before
    `POC_PLUS` it is `K × n`.
- Segment i asks for `getBlocksFromHeight(height = cacheHeight + i × n, numBlocks = n)`. The server
  returns the blocks after the block at `height`, so heights `cacheHeight + i × n + 1` up to
  `cacheHeight + (i + 1) × n`.
- With fewer candidates than K−1, or fewer qualified helpers, there are fewer segments. There are
  no retries within a round.
- All segment requests run concurrently. The fetcher waits for each in order up to the request
  timeout (default `2 × (P2P.TimeoutConnect_ms + P2P.TimeoutRead_ms)`). A late response is thrown
  away.

### 4.4 Truncation

Walking the segments in order, the result is cut before the first segment that:

- failed, timed out, or had no `nextBlocks` array
- was empty

and it is cut after the first segment that was short (fewer than n blocks, so the next segment
would leave a gap).

A segment with more than n blocks is a protocol violation. The peer is blacklisted ("sends too many
blocks") and the result is cut before that segment.

### 4.5 Integration into `getMoreBlocks`

- If section 3 holds, the round calls `fetcher.fetch(...)` instead of `getNextBlocks(peer,
  commonBlockId)`. If the result is empty, it falls back to `getNextBlocks`.
- The existing block loop runs over all blocks of all segments in order: parsing, the link check
  (`lastBlock.getId() == block.getPreviousBlockId()`), `calculateBaseTarget` and `addBlock`. The
  download window check and the decoupled batch lock stay as they are.
- Change 1, attribution: `block.setPeer(segment.peer())` instead of the feeder. Parse errors, and
  later import or PoC failures (`blacklistClean`), blame the peer that sent the block.
- Change 2, link break at a segment boundary: if the first block of segment i ≥ 1 does not link to
  the last block of segment i−1, the round ends. Blocks already added stay in the cache and the
  rest of the round is dropped. The helper is skipped for 60 s (4.2) but not blacklisted, because
  it may honestly be on a fork; the debug log names the peer and the height. Without the skip, a
  peer on another fork that keeps being picked would cut every round right after the feeder's
  segment. A link break inside a segment is handled as
  today: the round ends.

### 4.6 Security

- Same trust model as today. Any single peer can already feed us its chain. Every block still goes
  through full verification on import, and fork resolution by cumulative difficulty is unchanged.
- Helpers must report a higher cumulative difficulty than ours, the same rule as for the feeder.
- Bounded work per round: at most K × n blocks, checked per segment (4.4). The executor is fixed
  at K threads, so peers cannot make us open more connections.
- A helper on a fork can at worst waste one segment of download, which the link check discards.
  If it forks inside its segment, the blocks before the fork point are main chain blocks. The
  blocks after it end up in the cache like blocks from a forked feeder would today. Import
  verification and later fork resolution handle them.

### 4.7 Settings

In `Props` and `conf/node-default.properties`:

| Property | Default | Meaning |
|---|---|---|
| `node.experimental.parallelDownload` | true, if the A/B is clean (else false) | Enables the parallel path |
| `node.parallelDownload.peers` | 4 | K, peers per round including the feeder (1 means off) |
| `node.parallelDownload.blocksPerPeer` | 100 | n, blocks per segment (clamped to 1..100) |

The upper bound of 100 for n matches the serving node's default and keeps a single response small.

### 4.8 Logging

- At debug level, one line per parallel round with the number of segments, the peers, the block
  count and the elapsed ms.
- Truncation reasons at debug level.
- No new info-level output.

## 5. Testing

Unit tests (JUnit 5, Mockito, AAA, naming `method_GivenCondition_ExpectedBehavior`):

`ParallelBlockFetcherTest`:
- segments come back in height order even when the responses arrive out of order
- segment 0 goes to the feeder, the others to distinct helpers, with the right heights and
  `numBlocks`
- a helper with lower or equal cumulative difficulty, or too low a height, takes no segment; the
  qualified helpers still fill consecutive segments
- when every pool thread is held by hung helper requests, the feeder's segment is still returned
- no helper candidate gives an empty result
- a failed, empty or timed-out segment cuts the result before it
- a short segment cuts the result after it
- a segment with more than n blocks gets its peer blacklisted and cuts the result before it
- the round size respects `maxBlocks` and the feeder height

`DownloadedBlockAppender` (the block loop of the download round, extracted from
`getMoreBlocks` so it can be tested without static peer and flux state):
- each block gets the peer of its segment
- a link break at a segment boundary keeps the earlier blocks and blacklists no one
- a parse error blacklists the peer of that segment
- the existing single-peer behaviour is kept (the `getNextBlocks` result as one segment)

`ParallelBlockFetcher.isApplicable` covers section 3: a fork case (common block is not the cache
tip), a small gap or the flag being off all mean the fetcher is not used.

Chain tests (`test/java/chain`) must stay green. They run with a single mock peer, so they cover
the fallback path.

## 6. Measurement

An A/B run on mainnet on this machine, while the diagnostic resync keeps running:

- release build (`-PreleaseBuild`), with its own DB, `P2P.Port`, `API.Port` and `P2P.shareMyAddress
  = no`
- run A with `node.experimental.parallelDownload = false`, run B with true; both from an empty DB,
  15 minutes each, one after the other
- record the reached height and blocks/s per 5 minute window, plus a short JFR profile of run B
- accept if B is clearly faster with no errors or blacklisting storms in the log. This decides the
  default in 4.7.

## 7. Rollout

- One PR against `develop`, merged before the 3.10.0 pre-release
- Release notes: an entry under "Faster sync" with the setting to switch it off
