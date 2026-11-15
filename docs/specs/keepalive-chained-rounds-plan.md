# P2P Keep-Alive and Chained Rounds Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the per-request TCP handshake and the per-round download handshake from latency-bound bulk sync.

**Architecture:**
- `PeerImpl.send` keeps HTTP connections alive, so the JDK cache reuses them.
- A new `ChainedRoundDownloader` repeats complete parallel rounds from the new tip without `getCumulativeDifficulty`/`getMilestoneBlockIds`. `getMoreBlocks` calls it after its first parallel round.

**Tech Stack:** Java 21, `HttpURLConnection`, `com.sun.net.httpserver` (tests), JUnit 5, Mockito

**Spec:** `docs/specs/keepalive-chained-rounds.md`

## Global Constraints

- Branch `perf/keepalive-chained-rounds` from `develop` (which includes #1100); one PR against `develop`
- Setting `P2P.keepAlive` (default true); `MAX_CHAINED_ROUNDS` = 8 (constant, no setting)
- With `P2P.keepAlive = false`, `PeerImpl.send` behaves exactly as before
- Non-200 responses and exceptions always `disconnect()`
- Fork rounds and single-peer rounds never chain
- Tests: JUnit 5, AAA, `method_GivenCondition_ExpectedBehavior`; close every `MockedStatic`
- Commits end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; push only when the user asks

## Review Focus

- A pooled connection that the server closed while idle: the next request must still succeed. The JDK retries a POST once; the test closes the server-side connection between requests.
- A gzip response whose trailer is not consumed by the JSON parser: the connection must still be reused. The stream is drained before it is closed.
- Chaining while the importer pushes blocks: the importer lock must be released between rounds (test with lock/unlock counters).
- The download window shrinking during a chain after PoC+: `maxBlocksAhead` is evaluated before every chained round.
- A chained round with an unlinked helper: the helper is skipped and the chain ends.

---

### Task 1: Keep-alive in `PeerImpl.send`

**Files:**
- Modify: `src/brs/props/Props.java` (`P2P_KEEP_ALIVE = new Prop<>("P2P.keepAlive", true)`, next to the P2P timeouts)
- Modify: `src/brs/peer/Peers.java` (`static boolean keepAlive = true;`, set in init next to `readTimeout`)
- Modify: `src/brs/peer/PeerImpl.java` (`send`)
- Modify: `conf/node-default.properties` (commented entry next to the P2P timeouts)
- Test: `test/java/brs/peer/PeerImplKeepAliveTest.java`

Behaviour:
- `keepAlive` true: no `Connection: close` header.
- On HTTP 200: read the response stream to EOF (drain after parsing), close it, and skip `disconnect()`.
- Otherwise: `disconnect()` as today.

- [ ] **Step 1: Write the failing tests.** Start a local `HttpServer` on an ephemeral port with a `/burst` handler that records `exchange.getRemoteAddress().getPort()` and answers `{"ok":true}`, gzip-encoded when the request accepts gzip. Create the peer with `new PeerImpl("127.0.0.1", "127.0.0.1:" + port)`.
  - `send_GivenKeepAlive_ReusesTheConnectionForTheNextRequest`: two sends; the client ports are equal; both responses have `ok == true`.
  - `send_GivenKeepAliveOff_OpensANewConnectionPerRequest`: `Peers.keepAlive = false`; the ports differ.
  - `send_GivenNon200Response_DoesNotReuseTheConnection`: first answer 500, second 200; the ports differ, and the second response is ok.
  - `send_GivenServerClosedIdleConnection_StillSucceeds`: after the first request, close the server-side connection (the handler sets `Connection: close` on that response); the second send returns ok.
  Reset `Peers.keepAlive = true` in `@AfterEach`.
- [ ] **Step 2: Run them and see the reuse test fail.** Run `./gradlew test --tests brs.peer.PeerImplKeepAliveTest`. Expected: the ports differ in `send_GivenKeepAlive_...`.
- [ ] **Step 3: Implement** the prop, `Peers.keepAlive`, the `send` changes and the config entry.
- [ ] **Step 4: Run them and see them pass.** Run the same command; all tests should pass.
- [ ] **Step 5: Commit** — `perf(p2p): HTTP keep-alive for outgoing peer requests`

### Task 2: `ChainedRoundDownloader`

**Files:**
- Create: `src/brs/ChainedRoundDownloader.java`
- Modify: `src/brs/ParallelBlockFetcher.java` (add `int blocksPerPeer()`)
- Test: `test/java/brs/ChainedRoundDownloaderTest.java`

**Interfaces:**
- `ChainedRoundDownloader(ParallelBlockFetcher fetcher, DownloadedBlockAppender appender, DownloadCacheImpl downloadCache, IntUnaryOperator maxBlocksAhead, BooleanSupplier shuttingDown)`
  - `maxBlocksAhead` maps the cache height to the allowed blocks. Production uses `ParallelBlockFetcher.maxBlocksAhead(pocPlus(h), blockchain.getHeight(), h)`.
- `static final int MAX_CHAINED_ROUNDS = 8`
- `DownloadedBlockAppender.Result continueAfter(Peer feeder, int feederHeight, List<ParallelBlockFetcher.Segment> round, DownloadedBlockAppender.Result appended, Runnable lockImporter, Runnable releaseImporter) throws InterruptedException`
  - Loop while the round is complete (spec §4, items 1–4) and the pre-round conditions hold.
  - In each iteration:
    - `fetch(feeder, downloadCache.getCumulativeDifficulty(), tipHeight, feederHeight, maxBlocksAhead.applyAsInt(tipHeight))`;
    - stop if the result is empty;
    - `lockImporter.run()`, then `appender.append(next, tipBlock, true)`, then `releaseImporter.run()` (the release goes in a `finally`);
    - `skipHelper` for an unlinked helper;
    - return when stopped.
  - Returns the last `Result`; that is `appended` if no round followed.

- [ ] **Step 1: Write the failing tests.** Use mocked fetcher, appender and download cache, with `blocksPerPeer` = 100. The helper `segment(peer, fromHeight, count)` makes `count` dummy blocks; the helper `block(id, height)` makes a mocked Block.
  - `continueAfter_GivenCompleteRound_FetchesTheNextRoundFromTheNewTip`: the round is 1000..1200 (2 full segments) and the appender's last block is at 1200. Verify `fetch(feeder, cd, 1200, 5000, max)`, and the second append gets the tip block.
  - `continueAfter_GivenShortLastSegment_DoesNotChain`
  - `continueAfter_GivenStoppedAppend_DoesNotChain`
  - `continueAfter_GivenNotAllBlocksAppended_DoesNotChain` (the last block is below the segment end)
  - `continueAfter_GivenCacheTipMoved_DoesNotChain`
  - `continueAfter_GivenFullCache_DoesNotChain`
  - `continueAfter_GivenClosedWindow_DoesNotChain` (`maxBlocksAhead` returns 0)
  - `continueAfter_GivenFeederNotFarEnoughAhead_DoesNotChain` (`isApplicable` returns false)
  - `continueAfter_GivenShutdown_DoesNotChain`
  - `continueAfter_GivenEndlessCompleteRounds_StopsAfterEightChainedRounds`: `fetch` is called 8 times.
  - `continueAfter_GivenChainedRound_TakesAndReleasesTheImporterLockAroundEachAppend`: the counters are equal and the lock is free during `fetch`.
  - `continueAfter_GivenUnlinkedHelperInChainedRound_SkipsItAndStops`
  - `continueAfter_GivenEmptyFetch_ReturnsThePreviousResult`
- [ ] **Step 2: Run them and see them fail.** Run `./gradlew test --tests brs.ChainedRoundDownloaderTest`. Expected: a compilation failure.
- [ ] **Step 3: Implement** `ChainedRoundDownloader` and `blocksPerPeer()`.
- [ ] **Step 4: Run them and see them pass.**
- [ ] **Step 5: Commit** — `perf(sync): ChainedRoundDownloader for handshake-free follow-up rounds`

### Task 3: Wire into `getMoreBlocks`

**Files:**
- Modify: `src/brs/BlockchainProcessorImpl.java`

Construct the downloader next to `parallelBlockFetcher` (null if the fetcher is null). After the first append, when `saveInCache` holds and the round was parallel (`!parallelSegments.isEmpty()`):
- `appended = chainedRoundDownloader.continueAfter(peer, feederHeight, segments, appended, this::lockImporterForBatch, this::releaseImporterBatchLock)`
- then the existing `if (appended.stopped()) return;` and `lastBlock = appended.lastBlock();`

The lock/release methods live on the runnable, so pass lambdas that call them.

- [ ] **Step 1: Implement the wiring.**
- [ ] **Step 2: Run the whole suite** — `./gradlew test`. Expected: all tests pass, including the chain tests.
- [ ] **Step 3: Commit** — `perf(sync): chain complete parallel rounds without the download handshake`

### Task 4: Measurement, release notes

- [ ] **Step 1:** Build a release jar for develop (#1100) and one for this branch.
- [ ] **Step 2:** Run the interleaved 8-minute runs on mainnet, twice each, with the fetcher at debug level. Count `continueAfter` rounds from a debug line per chained round.
- [ ] **Step 3:** Add the measured factor to the 3.10.0 release notes draft.
- [ ] **Step 4:** Report to the user. The user decides on push and PR.
