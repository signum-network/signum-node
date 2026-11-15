# Spec: P2P Keep-Alive and Chained Download Rounds

Status: draft · Branch: `perf/keepalive-chained-rounds` · Builds on: PR #1100 (parallel block download)

## 1. Purpose

Make bulk sync faster where it is latency-bound. Measurements on mainnet (PR #1100) showed:

- Every P2P request opens a new TCP connection (`Connection: close` and `disconnect()` in
  `PeerImpl.send`). That costs about 0.25 s per request to a typical peer.
- Every download round starts with `getCumulativeDifficulty` and `getMilestoneBlockIds` from the
  feeder, about 1 s together. Then the segments take another 1.4–1.8 s.

In a linear bulk round, the next round starts at the tip the feeder has just served itself, so the
two handshake requests confirm nothing new there.

## 2. Scope

### In scope
- HTTP keep-alive for outgoing P2P requests, with a switch to turn it off
- Chaining parallel download rounds without the handshake, within strict limits
- Unit tests and a mainnet measurement against the state of #1100

### Non-goals
- A sliding window that keeps K requests in flight all the time. Possible later.
- Changes to the serving side or the peer protocol
- Overlapping the network fetch of round N+1 with appending round N. Appending is in-memory and
  fast; the handshake is the cost being removed.

## 3. Keep-alive in `PeerImpl.send`

- New setting `P2P.keepAlive` (default true), read once into `Peers.keepAlive` like the timeouts.
- With keep-alive on:
  - the request does not send `Connection: close`;
  - on an HTTP 200 response, the response stream is read to its end and closed, and
    `disconnect()` is **not** called. The JDK keep-alive cache then reuses the connection for the
    next request to the same peer.
- On a non-200 response or an exception, `disconnect()` is called as today, so a connection in an
  unknown state is never reused.
- With keep-alive off, the behaviour is exactly as today.
- The serving side (Jetty, idle timeout `P2P.TimeoutIdle_ms`, default 30 s) already supports
  persistent HTTP/1.1 connections. Peers on older versions need no change.
- A pooled connection the server has already closed is retried once by the JDK for POST
  (`sun.net.http.retryPost`, default true). No extra handling is needed.

## 4. Chained rounds

New class `ChainedRoundDownloader` (package `brs`). After a complete parallel round,
`getMoreBlocks` calls it, and it downloads further rounds from the new tip with the same feeder and
without the handshake.

A round is **complete**, so the next one may follow, when all of these hold:

1. The append did not stop the round.
2. Every block of every segment was appended: the appender's last block is at
   `lastSegment.fromHeight + lastSegment.blocks.size()`.
3. The last segment is full (`blocksPerPeer` blocks). A short segment means the peers had no more.
4. The download cache tip is the appender's last block. If an announced block moved the tip, the
   round's blocks were not added, and chaining from there makes no progress.

Before each further round:

- the feeder must still be at least two segments ahead (`ParallelBlockFetcher.isApplicable`);
- the download cache must not be full;
- the download window must allow more blocks (`maxBlocksAhead > 0`);
- the node must not be shutting down;
- at most `MAX_CHAINED_ROUNDS` = 8 rounds per handshake. After that the next handshake picks a new
  random feeder and checks its cumulative difficulty again.

Each chained round:

- uses our current cumulative difficulty (`downloadCache.getCumulativeDifficulty()`) for the
  helper check;
- takes the importer batch lock only around its own append, so the importer can push blocks
  between rounds (decoupled pipeline);
- skips a helper whose segment did not link, as in the first round;
- ends the chain on an empty fetch result, a stopped append or an incomplete round.

The result of the last append is returned. A stopped append ends the download round, as before.
Fork rounds (`saveInCache == false`) and single-peer rounds never chain.

## 5. Security

- Chained rounds go through the same appender: the link check, attribution to the sending peer,
  and full verification on import are unchanged.
- The cap of 8 rounds bounds how long one feeder leads, at about 3,200 blocks with the defaults.
  A feeder already leads a whole round today; the blocks it serves are verified either way.
- Keep-alive changes only the transport. Request and response handling are unchanged.

## 6. Testing

- `PeerImplKeepAliveTest`, against a local `com.sun.net.httpserver.HttpServer` that records the
  client port of each request:
  - with keep-alive, two requests use the same connection;
  - with keep-alive off, they use different connections;
  - after a non-200 response, the next request uses a new connection;
  - the response is parsed as before.
- `ChainedRoundDownloaderTest`, with `ParallelBlockFetcher` and `DownloadedBlockAppender` mocked:
  - a complete round chains, with the next fetch starting at the new tip;
  - no chaining after a short segment, a stopped append, an incomplete append, a moved cache tip, a
    full cache, a closed window, a feeder that is not far enough ahead, or a shutdown;
  - at most 8 chained rounds;
  - the importer lock is taken and released around each append;
  - an unlinked helper in a chained round is skipped.

## 7. Measurement

Mainnet, release build, empty DB on tmpfs, default P2P port, 8-minute runs interleaved twice:
#1100 (develop) versus this branch. Count the chained rounds from the debug log.
