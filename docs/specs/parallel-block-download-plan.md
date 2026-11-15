# Parallel Block Download Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** During bulk sync, download consecutive block ranges from up to K peers at the same time
instead of one peer per round.

**Architecture:** A new `ParallelBlockFetcher` requests `getBlocksFromHeight` segments from the
feeder and K−1 helper peers concurrently and returns them in height order, cut at the first gap.
The block loop of `getMoreBlocks` moves into a testable `DownloadedBlockAppender`. It handles both
the parallel segments and the existing single `getNextBlocks` result, and it attributes each block
to the peer that sent it.

**Tech Stack:** Java 21, Gson, JUnit 5, Mockito (no PowerMock), Gradle

**Spec:** `docs/specs/parallel-block-download.md`

## Global Constraints

- Branch `perf/parallel-download` (from `develop`); one PR against `develop`, for 3.10.0
- No peer protocol change, no change to `GetBlocksFromHeight` / `GetNextBlocks`
- Fork rounds (`commonBlockId != downloadCache.getLastBlockId()`) never use the fetcher
- Settings: `node.experimental.parallelDownload` (default decided in Task 5), `node.parallelDownload.peers` = 4, `node.parallelDownload.blocksPerPeer` = 100, clamped to 1..100
- Request timeout per round: `2 × (P2P.TimeoutConnect_ms + P2P.TimeoutRead_ms)`
- A slow, silent or non-qualifying helper is never blacklisted; a helper returning more blocks than requested is
- Constructor injection; no new static access inside the new classes except `ThreadPool.running`
- Tests: JUnit 5, AAA, names `method_GivenCondition_ExpectedBehavior`; close every `MockedStatic`
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; never push without the user asking

## Review Focus

- A helper whose `send` hangs past the timeout (`HttpURLConnection` is not interruptible): `fetch` must return in time, and the next round must not wait behind the hung thread for longer than its own timeout → tests in Task 1
- The download window after `POC_PLUS` is full (`maxBlocks <= 0`): no requests, empty result, so the old path's own window check takes over → test in Task 1
- Shutdown during a fetch (thread interrupted): `InterruptedException` propagates and outstanding requests are cancelled → test in Task 1
- A peer answers with an error object (`{"error": "..."}`) instead of `nextBlocks`: the segment is missing, nobody is blacklisted → test in Task 1
- A short segment followed by a full one: the result is cut after the short one, even though the later segment has blocks → test in Task 1

---

### Task 0: Commit spec and plan

- [ ] **Step 1: Commit**

```bash
git add docs/specs/parallel-block-download.md docs/specs/parallel-block-download-plan.md
git commit -m "docs(sync): spec and plan for parallel block download"
```

### Task 1: `ParallelBlockFetcher`

**Files:**
- Create: `src/brs/DownloadPeers.java`, `src/brs/ParallelBlockFetcher.java`
- Test: `test/java/brs/ParallelBlockFetcherTest.java`

**Interfaces:**
- Produces:
  - `interface DownloadPeers { List<Peer> candidates(); }` (package `brs`, package-private)
  - `final class ParallelBlockFetcher` with:
    - `ParallelBlockFetcher(DownloadPeers downloadPeers, int peers, int blocksPerPeer, long timeoutMs)`: clamps `blocksPerPeer` to 1..100 and `peers` to ≥ 1. If `peers > 1` it creates a fixed pool of `peers` daemon threads named `ParallelBlockFetcher-<n>`.
    - `record Segment(Peer peer, int fromHeight, JsonArray blocks)`, nested, package-private
    - `boolean isApplicable(boolean saveInCache, int cacheHeight, int feederHeight)`: true iff `peers > 1 && saveInCache && feederHeight >= cacheHeight + 2 * blocksPerPeer`
    - `static int maxBlocksAhead(boolean pocPlus, int chainHeight, int cacheHeight)`: `chainHeight + Constants.MAX_ROLLBACK / 2 - cacheHeight` if `pocPlus`, else `Integer.MAX_VALUE`
    - `List<Segment> fetch(Peer feeder, BigInteger ourCumulativeDifficulty, int cacheHeight, int feederHeight, int maxBlocks) throws InterruptedException`
    - `void shutdown()`: `shutdownNow()` on the pool

Algorithm for `fetch` (spec 4.2–4.4):

```
total = min(peers * n, feederHeight - cacheHeight, maxBlocks); if total <= 0 → []
helpers = shuffled(candidates() without feeder), first (ceil(total / n) - 1); if empty → []
segment i: height h_i = cacheHeight + i*n, count c_i = min(n, total - i*n)
task 0 (feeder): getBlocksFromHeight(h_0, c_0)
task i (helper i): probe getCumulativeDifficulty; qualifies iff cumulativeDifficulty > ours
                   && blockchainHeight >= h_i + c_i; then getBlocksFromHeight(h_i, c_i)
deadline = now + timeoutMs; walk the futures in order with get(remaining):
  failure / timeout / not qualified / no "nextBlocks" array / empty → cut before (cancel the rest)
  size > c_i → peer.blacklist("sends too many blocks"), cut before
  size < c_i → keep, cut after
on InterruptedException: cancel all futures, rethrow
```

Request JSON: `{"requestType":"getBlocksFromHeight","height":h,"numBlocks":c}` and
`{"requestType":"getCumulativeDifficulty"}`, both built with `JSON.prepareRequest`. The probe
response has `cumulativeDifficulty` (string) and `blockchainHeight` (int); the blocks response has
`nextBlocks`. One debug line per round (segments, peers, blocks, ms) and one per cut reason.

- [ ] **Step 1: Write the failing tests** in `ParallelBlockFetcherTest`. Use mocked `Peer`s whose `send` answers by `requestType`, with a test helper `peer(BigInteger cumDiff, int height, int blocksReturned)` that returns `blocksReturned` dummy `JsonObject`s. Our cumulative difficulty is `BigInteger.TEN`, `n = 100`, `K = 4`, timeout 500 ms.
  - `fetch_GivenThreeQualifiedHelpers_ReturnsFourSegmentsInHeightOrder`: cacheHeight 1000, feederHeight 5000. Expect segment `fromHeight`s `[1000, 1100, 1200, 1300]`, segment 0's peer is the feeder, the helpers are distinct, every segment has 100 blocks.
  - `fetch_GivenResponsesArriveOutOfOrder_KeepsHeightOrder`: the feeder answers after a 100 ms delay, the helpers immediately; same expected order.
  - `fetch_GivenHelperWithLowerCumulativeDifficulty_CutsBeforeItsSegmentAndDoesNotRequestBlocks`: with a single candidate whose cumDiff is `BigInteger.ONE`, the result is the feeder segment only, the helper gets no `getBlocksFromHeight`, and nobody is blacklisted.
  - `fetch_GivenHelperBelowSegmentEnd_CutsBeforeItsSegment`: helper height 1150 for segment 1100..1200.
  - `fetch_GivenNoCandidates_ReturnsEmptyAndSendsNothing`
  - `fetch_GivenFailedOrEmptySegment_CutsBeforeIt`: parameterised over `send` returning null, `{"error":"x"}`, or an empty `nextBlocks`. The result is 1 segment and no blacklist (Review Focus).
  - `fetch_GivenShortSegmentFollowedByFullOne_CutsAfterTheShortOne`: helper 1 returns 40 blocks, helper 2 returns 100. Result: 2 segments, the second with 40 blocks (Review Focus).
  - `fetch_GivenTooManyBlocks_BlacklistsPeerAndCutsBeforeIt`: helper 1 returns 101 blocks. `verify(helper1).blacklist(anyString())`, result 1 segment.
  - `fetch_GivenMaxBlocksBelowRound_LimitsSegments`: `maxBlocks = 250` gives segments of 100, 100 and 50 blocks; the last request has `numBlocks = 50`.
  - `fetch_GivenMaxBlocksZero_ReturnsEmptyAndSendsNothing` (Review Focus)
  - `fetch_GivenHelperThatNeverAnswers_ReturnsWithinTimeout`: helper 1's `send` blocks on a latch that is never released. `fetch` returns the feeder segment only in < 1000 ms (Review Focus).
  - `fetch_GivenThreadsStillBusyFromPreviousRound_NextRoundReturnsWithinTimeout`: after the previous test's situation (latch still held), a second `fetch` also returns in < 1000 ms (Review Focus). Release the latch in a `finally`.
  - `fetch_GivenInterrupt_ThrowsAndCancelsRequests`: interrupt the calling thread before `fetch`; `assertThrows(InterruptedException.class, ...)` (Review Focus).
  - `isApplicable_GivenForkOrSmallGapOrSinglePeer_ReturnsFalse` and `isApplicable_GivenLinearExtensionWithLargeGap_ReturnsTrue` (feederHeight = cacheHeight + 200 → true, + 199 → false)
  - `maxBlocksAhead_GivenPocPlus_ReturnsHalfRollbackWindow`: `(true, 1000, 1500)` → 220; `(false, 1000, 1500)` → `Integer.MAX_VALUE`

- [ ] **Step 2: Run them and see them fail**

Run: `./gradlew test --tests brs.ParallelBlockFetcherTest`
Expected: compilation failure (`ParallelBlockFetcher` does not exist)

- [ ] **Step 3: Implement `DownloadPeers` and `ParallelBlockFetcher`** as specified above. Tests close the fetcher with `shutdown()` in `@AfterEach`.

- [ ] **Step 4: Run them and see them pass**

Run: `./gradlew test --tests brs.ParallelBlockFetcherTest`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 5: Commit** — `perf(sync): ParallelBlockFetcher for concurrent height-range downloads`

### Task 2: `ConnectedDownloadPeers`

**Files:**
- Create: `src/brs/ConnectedDownloadPeers.java`
- Test: `test/java/brs/ConnectedDownloadPeersTest.java`

**Interfaces:**
- Consumes: `DownloadPeers` (Task 1)
- Produces: `ConnectedDownloadPeers(PropertyService propertyService, Supplier<FluxCapacitor> fluxCapacitor) implements DownloadPeers`. `candidates()` returns `Peers.getActivePeers()` filtered to `getState() == CONNECTED`, `!isBlacklisted()`, `isHigherOrEqualVersionThan(flux MIN_PEER_VERSION)`, and `getNetworkName() == null || equals(Props.NETWORK_NAME)`. This is the same filter as the feeder loop in `getMoreBlocks`.

- [ ] **Step 1: Write the failing test** `candidates_GivenMixedPeers_ReturnsOnlyConnectedCurrentPeersOnOurNetwork`. Mock `Peers.getActivePeers()` with `mockStatic` to return five peers: OK, disconnected, blacklisted, old version, other network. Expect only the OK peer.
- [ ] **Step 2: Run it and see it fail** — `./gradlew test --tests brs.ConnectedDownloadPeersTest`, compilation failure
- [ ] **Step 3: Implement `ConnectedDownloadPeers`**
- [ ] **Step 4: Run it and see it pass**
- [ ] **Step 5: Commit** — `perf(sync): candidate peer source for parallel download`

### Task 3: Extract `DownloadedBlockAppender` (behaviour-preserving, plus per-segment peer)

**Files:**
- Create: `src/brs/DownloadedBlockAppender.java`
- Modify: `src/brs/BlockchainProcessorImpl.java` (block loop in `getMoreBlocksThread.run()`, currently around lines 780–852)
- Test: `test/java/brs/DownloadedBlockAppenderTest.java`

**Interfaces:**
- Consumes: `ParallelBlockFetcher.Segment` (Task 1)
- Produces:
  - `DownloadedBlockAppender(DownloadCacheImpl downloadCache, Blockchain blockchain, BlockService blockService, Supplier<FluxCapacitor> fluxCapacitor, BlockParser blockParser, ReentrantReadWriteLock blockImporterLock, boolean decoupledSync)`
  - `interface BlockParser { Block parse(JsonObject blockData, int height) throws SignumException.ValidationException; }`, nested; production passes `Block::parseBlock`
  - `record Result(Block lastBlock, boolean stopped)`, nested. `stopped == true` means the caller ends the round with `return`, exactly where the old loop had `return`.
  - `Result append(List<ParallelBlockFetcher.Segment> segments, Block commonBlock, boolean saveInCache)`

The body is the existing loop, moved over unchanged, with three changes:
- It iterates over all blocks of all segments.
- `block.setPeer(segment.peer())` replaces the feeder peer, and a parse error blacklists `segment.peer()`.
- If the first block of segment i ≥ 1 does not link, the debug message names the segment peer and height. The result is still `stopped`, with no blacklist.

The window `break` becomes "stop iterating, `stopped = false`". In `getMoreBlocks` the single-peer path wraps `getNextBlocks`' result as `List.of(new Segment(peer, lastBlock.getHeight(), nextBlocks))`. If `result.stopped()` it returns; otherwise it continues with `lastBlock = result.lastBlock()` into the existing `!saveInCache` fork check.

- [ ] **Step 1: Write the failing tests** in `DownloadedBlockAppenderTest`. The block JSON is `{"id":N,"prev":M}`; the stub `BlockParser` returns a mocked `Block` with those ids. `ThreadPool.running` is true by default.
  - `append_GivenTwoLinkedSegments_AddsAllBlocksWithTheirSegmentPeer`: `verify(block).setPeer(peerA)` for segment 0 blocks and `peerB` for segment 1; `addBlock` is called 4 times in order; `stopped == false`; `lastBlock` is the 4th block.
  - `append_GivenSegmentThatDoesNotLink_KeepsEarlierBlocksAndBlacklistsNobody`: segment 1 starts with `prev` ≠ the last id of segment 0. The 2 blocks of segment 0 are added, `stopped == true`, `verify(peerB, never()).blacklist(any(...))`.
  - `append_GivenParseError_BlacklistsPeerOfThatSegment`: the parser throws `ValidationException` for segment 1's first block. `verify(peerB).blacklist(any(Exception.class), anyString())`, `verify(peerA, never())...`, `stopped == true`.
  - `append_GivenBlockOutOfOrder_ResetsCacheAndStops`: `calculateBaseTarget` throws `BlockOutOfOrderException`; `verify(downloadCache).resetCache()`.
  - `append_GivenLockedCache_Stops`: `addBlock` returns false → `stopped == true`.
  - `append_GivenForkRound_AddsForkBlocks`: `saveInCache == false` → `addForkBlock` for each block, never `addBlock`.
  - `append_GivenDownloadWindowReached_StopsIteratingWithoutStoppingRound`: the flux mock gives `POC_PLUS` true and the chain height is such that the 2nd block reaches `MAX_ROLLBACK`. 1 block is added, `stopped == false`.
  - `append_GivenNonDecoupledMode_TakesImporterReadLockPerBlock`: with `decoupledSync == false`, a spy or real `ReentrantReadWriteLock` shows a read-lock count of 1 inside `calculateBaseTarget` (check via `doAnswer`).
- [ ] **Step 2: Run them and see them fail** — `./gradlew test --tests brs.DownloadedBlockAppenderTest`, compilation failure
- [ ] **Step 3: Implement `DownloadedBlockAppender`** and replace the loop in `BlockchainProcessorImpl` with the call. The appender is a field created in the constructor with `Signum::getFluxCapacitor`. `lockImporterForBatch()` / `releaseImporterBatchLock()` stay in the runnable around the call.
- [ ] **Step 4: Run the new tests and the processor tests**

Run: `./gradlew test --tests brs.DownloadedBlockAppenderTest --tests brs.BlockchainProcessorImplTest --tests brs.SyncPreVerifierTest`
Expected: all pass

- [ ] **Step 5: Commit** — `refactor(sync): move the download block loop into DownloadedBlockAppender`

### Task 4: Wire the fetcher into `getMoreBlocks`, settings, shutdown

**Files:**
- Modify: `src/brs/props/Props.java` (next to `SYNC_DECOUPLED_PIPELINE`), `conf/node-default.properties` (after `node.experimental.decoupledSync`), `src/brs/BlockchainProcessorImpl.java`
- Test: `test/java/brs/BlockchainProcessorImplTest.java`

**Interfaces:**
- Consumes: `ParallelBlockFetcher` (Task 1), `ConnectedDownloadPeers` (Task 2), `DownloadedBlockAppender` (Task 3)
- Produces:
  - `Props.SYNC_PARALLEL_DOWNLOAD = new Prop<>("node.experimental.parallelDownload", true)`
  - `Props.SYNC_PARALLEL_DOWNLOAD_PEERS = new Prop<>("node.parallelDownload.peers", 4)`
  - `Props.SYNC_PARALLEL_DOWNLOAD_BLOCKS_PER_PEER = new Prop<>("node.parallelDownload.blocksPerPeer", 100)`

Changes in `BlockchainProcessorImpl`:
- Field `private final ParallelBlockFetcher parallelBlockFetcher;`. It is null when the flag is false; otherwise it is built in the constructor with `new ConnectedDownloadPeers(propertyService, Signum::getFluxCapacitor)`, the two settings and the timeout from the constraints.
- `shutdown()` calls `parallelBlockFetcher.shutdown()` if it is not null.
- In the feeder loop, remember `feederHeight` from `blockchainHeight`.
- Replace `JsonArray nextBlocks = getNextBlocks(peer, commonBlockId)` with a `List<Segment> segments`:
  - If `parallelBlockFetcher != null && parallelBlockFetcher.isApplicable(saveInCache, downloadCache.getLastBlock().getHeight(), feederHeight)`, use `fetch(peer, curCumulativeDifficulty, cacheHeight, feederHeight, ParallelBlockFetcher.maxBlocksAhead(pocPlus(cacheHeight), blockchain.getHeight(), cacheHeight))`.
  - If that is empty, or the fetcher is not applicable, wrap `getNextBlocks` as in Task 3.
  - An empty or null result keeps the existing "Peer did not feed us any blocks" return.

`conf/node-default.properties` gets a commented block in the style of `decoupledSync`: what it does, that it only applies during bulk sync, how to switch it off, and the two tuning settings.

- [ ] **Step 1: Write the failing test** `constructor_GivenParallelDownloadEnabled_ShutdownStopsFetcherThreads`. With the flag true, construct the processor (reuse the `createImporter()` construction) and call `shutdown()`. Afterwards no live thread is named `ParallelBlockFetcher-*` (`Thread.getAllStackTraces().keySet()`). Add `constructor_GivenParallelDownloadDisabled_CreatesNoFetcherThreads`.
- [ ] **Step 2: Run it and see it fail** — `./gradlew test --tests brs.BlockchainProcessorImplTest`
- [ ] **Step 3: Implement the props, the config entry and the wiring**
- [ ] **Step 4: Run the whole suite**

Run: `./gradlew test` (if a local testnet node holds ports 6876/7123, stop it first; the running mainnet diagnostic node does not conflict)
Expected: BUILD SUCCESSFUL, including `test/java/chain`

- [ ] **Step 5: Commit** — `perf(sync): parallel block download during bulk sync`

### Task 5: Mainnet A/B, default, release notes

**Files:**
- Modify: `src/brs/props/Props.java` and `conf/node-default.properties` (default only if the A/B says so)
- Modify: `/tmp/claude-1000/-home-ohager-code-signum-signum-node/ee48ca07-0874-4382-80b8-78f313895083/scratchpad/release-notes-3.10.0.md`

- [ ] **Step 1: Build** — `./gradlew clean shadowJar -PreleaseBuild`. Copy the jar to `signum-node-3.10.0-pre-parallel.jar` (gitignored).
- [ ] **Step 2: Set up two configs** in the scratchpad (`ab-conf-a`, `ab-conf-b`) based on `mainnet-conf/node.properties`:
  - own `DB.Url`
  - `P2P.Port` 18123
  - `API.Port` 18125
  - `API.Listen = 127.0.0.1`
  - `P2P.shareMyAddress = no`
  - A: `node.experimental.parallelDownload = false`; B: `true`
  - `node.measurement.active` / the CSV progress log if available, otherwise height from `getBlockchainStatus` every 60 s
- [ ] **Step 3: Run A for 15 min from an empty DB, then B for 15 min.** Record the height every 5 min and take a 60 s JFR of B. Stop each node by PID, not with `pkill -f`.
- [ ] **Step 4: Evaluate.** Compare blocks/s per window. Grep B's log for `blacklist` and for `ParallelBlockFetcher` cut reasons. Accept if B is clearly faster with no blacklisting storm. Otherwise set the default to false.
- [ ] **Step 5: Release notes.** Under "Faster sync", add parallel download from up to 4 peers during initial sync, with the measured factor and `node.experimental.parallelDownload` to switch it off.
- [ ] **Step 6: Commit** (only the default change, if any) — `perf(sync): parallel download default from mainnet A/B`. Then report to the user, who decides on push and PR.
