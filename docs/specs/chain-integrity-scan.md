# Spec: Chain Integrity Scan

Status: draft · Branch: `feat/chain-integrity-scan` · Related: PR #1095 (pre-verification fix)

## 1. Purpose

A read-only scan of an already synced node database. It re-checks the stored chain against the
consensus rules that need no replay of account state, and it reports every violation it finds.

Immediate use: PR #1095 makes every node reject blocks with invalid transaction signatures. Before
that fix, the default CPU verification path could accept such a block. If the mainnet or testnet
history contains one, a fixed node would stop syncing at that block. The scan finds those
transactions so we can put them on an accept list. It is much faster than a full resync because
it skips PoC, state application and database writes.

Longer term: a general integrity check for node operators and developers. For example, after a
database restore, after a bug like #1095, or to compare database backends.

## 2. Scope

### In scope
- Transaction checks: signature, rebuilding the transaction from the database, consistency with its block
- Block checks: id, payload hash and totals, links to neighbouring blocks, previous block hash, generation signature, block signature
- Optional expensive checks: PoC deadline, base target and cumulative difficulty
- SQLite, MariaDB and PostgreSQL (through jOOQ, like the rest of the node)

### Non-goals
- Replaying state: balances, ATs, assets, aliases and other derived tables. That needs a full resync.
- Repairing anything. The scan never writes to the database.
- Network access. No peers, API server or GUI.

## 3. Usage

A new node mode, using the same jar and config folder:

```
java -jar signum-node.jar --verify-chain [--config <folder>]
     [--from-height <h>] [--to-height <h>]
     [--checks <list>]          # default: tx,block; also: poc,basetarget, or all
     [--threads <n>]            # default: available processors
     [--report <file>]          # default: chain-scan-<network>-<timestamp>.jsonl
```

- The CLI options are added to `Signum.CLI_OPTIONS` (`src/brs/Signum.java:93`). `Launcher` routes `--verify-chain` to the scan, which always runs headless.
- Exit codes: `0` no findings of severity ERROR; `1` at least one ERROR; `2` the scan itself failed (configuration, database or I/O error).

## 4. Safety requirements (must)

1. **Read-only.** Open the database read-only:
   - SQLite: open in read-only mode, e.g. `SQLiteConfig.setReadOnly(true)`.
   - MariaDB and PostgreSQL: `Connection.setReadOnly(true)`; the README recommends a read-only database user.
   - Any write attempt must fail, not be silently skipped.
2. **No migrations.** The node's startup runs `flyway.migrate()` (`src/brs/db/sql/Db.java:43-50`). Scan mode must not migrate. Use `flyway.validate()` or read the schema version instead, and refuse with exit code 2 if the schema is not the version this build expects.
3. **Minimal startup.** Don't use `Signum.loadWallet` (`src/brs/Signum.java:~236`). It starts the thread pool, peers, API and so on. Start only what the checks need:
   - `PropertyService` and the network parameters
   - `Db` in read-only mode
   - `BlockchainImpl` and `FluxCapacitorImpl`, which the height-dependent rules need
   - `Crypto`
4. **Safe on a live database.** Scanning the database of a running node must be safe. SQLite in WAL mode allows a concurrent reader. The docs should still recommend scanning a copy, or stopping the node, to get a consistent snapshot.

## 5. Checks

Every finding has a check id, a severity (`ERROR`, `WARN`, `INFO`), the height, the block id, the transaction id (if any) and a detail text.

### Tier 1: stateless, per block (`--checks tx,block`)

| Id | Rule | Severity |
|---|---|---|
| `TX_REBUILD_MISMATCH` | Rebuild the transaction with `SqlTransactionDb.loadTransaction` (`src/brs/db/sql/SqlTransactionDb.java:67`), but don't preset `id`/`fullHash`. Recompute both from the rebuilt bytes and compare with the stored `id` and `full_hash`. On a mismatch, skip `TX_SIGNATURE` for that transaction: we can't trust the bytes. | ERROR |
| `TX_SIGNATURE` | For every transaction with `signature IS NOT NULL`: `Transaction.verifySignature()` must be true. **This is the main result** and is collected for the accept list (§7). | ERROR |
| `TX_UNSIGNED` | `signature IS NULL` is only valid for system transactions: `AT_PAYMENT`, `ESCROW_RESULT` and `SUBSCRIPTION_PAYMENT`. Any other type without a signature is a finding. | ERROR |
| `TX_BLOCK_CONSISTENCY` | Transaction `height` and `block_timestamp` match the block. Timestamp ≤ block timestamp + 15. Expiration ≥ block timestamp. Same rules as `pushBlock` (`src/brs/BlockchainProcessorImpl.java:~2190`). | ERROR |
| `BLOCK_ID` | Rebuild the block with `id = 0` and recompute the id from `getBytes()` (as in `Block.getId()`, `src/brs/Block.java:419`). Compare with the stored id. | ERROR |
| `BLOCK_PAYLOAD` | SHA-256 over the bytes of the signed transactions **in `db_id` order** equals `payload_hash`. The sum of transaction sizes equals `payload_length`. Sum of amounts ≤ `total_amount` and sum of fees ≤ `total_fee`. Cash back total as in `pushBlock`, when `SMART_FEES` is active at that height. | ERROR |

Note: `SqlTransactionDb.findBlockTransactions` (`:117`) has **no ORDER BY**. The scan must sort by `db_id` explicitly, because PostgreSQL does not guarantee row order.

### Tier 2: needs the previous block (`--checks block`)

| Id | Rule | Severity |
|---|---|---|
| `CHAIN_LINK` | `previous_block_id` = previous block's id. Previous block's `next_block_id` = this block's id. `height` = previous height + 1. `timestamp` > previous timestamp. Exactly one block per height. | ERROR |
| `PREV_BLOCK_HASH` | For version ≠ 1: `sha256(previous.getBytes())` = `previous_block_hash`. | ERROR |
| `GENERATION_SIGNATURE` | `generator.calculateGenerationSignature(previous.generationSignature, previous.generatorId)` = `generation_signature`. Reuse the node's code, see `BlockServiceImpl.verifyGenerationSignature` (`src/brs/services/impl/BlockServiceImpl.java:105`). | ERROR |
| `BLOCK_SIGNATURE` | See below. | ERROR / WARN |

**Block signature.** `BlockServiceImpl.verifyBlockSignature` (`:62`) picks the key from historical state. If the generator account exists, it verifies against the public key of the *reward recipient* that applied at that height; otherwise it uses `generator_public_key`. History in `reward_recip_assign` may be trimmed. The scan therefore checks the signature against:
1. `generator_public_key`;
2. the public keys (from `account.public_key`) of every `recip_id` and `prev_recip_id` in `reward_recip_assign` for that generator.

- No key verifies the signature: `ERROR` if the assignment history for the generator is complete, otherwise `WARN` (`BLOCK_SIGNATURE_UNVERIFIABLE`).
- Known limitation: a signature by a recipient key that was valid at a *different* height passes. Document this in the report header.

### Tier 3: expensive, optional (`--checks poc,basetarget`)

| Id | Rule | Severity |
|---|---|---|
| `POC_DEADLINE` | Hit = `calculateHit(generatorId, nonce, genSig, scoop, height)`, using the PoC version for the height. Deadline as in `GeneratorImpl.calculateDeadline` (`src/brs/GeneratorImpl.java:159`): previous `capacityBaseTarget`, previous `averageCommitment`, and the block's commitment from the `GeneratorImpl.estimateCommitment` logic (`:284`). That logic only reads the `block` and `transaction` tables (`SqlBlockchainStore.getCommittedAmount`), so it works offline. Rule: `timestamp − previous.timestamp > deadline`. | ERROR; `INFO` below `node.checkPointHeight`, where the node itself skips PoC |
| `BASE_TARGET` | Recompute `base_target` and `cumulative_difficulty` with the `BlockServiceImpl.calculateBaseTarget` logic (`:335`), based on stored blocks, and compare. | ERROR |

Tier 3 costs about as much CPU per block as syncing does (Shabal). Run it only on request.

## 6. Architecture

Follow the existing layering (CLAUDE.md):

- `brs.db.store.ChainScanStore` and `brs.db.sql.SqlChainScanStore`: read-only data access.
  - `loadBlocks(fromHeight, toHeight)` and `loadTransactions(fromHeight, toHeight)` load a whole height range with **one query each**, ordered by height and `db_id`, so there are no N+1 queries.
  - Lookups for the reward assignment history and public keys.
- `brs.services.ChainScanService` and `brs.services.impl.ChainScanServiceImpl`: plan the chunks, run them in parallel, collect findings.
- `brs.services.impl.chainscan.ChainCheck`: strategy interface, `List<Finding> check(BlockContext ctx)`. One class per check (or per tier). `BlockContext` holds the block, its transactions in `db_id` order, and the previous block.
- `Finding`: immutable value (`checkId`, `severity`, `height`, `blockId`, `txId`, `detail`).
- `ChainScanReportWriter`: JSON Lines output plus a summary.
- Wiring: a small `ChainScanLauncher` (or a method in `Signum`) does the minimal startup from §4.3 and builds the objects through their constructors.

**Execution:**
- Split `[from, to]` into chunks, e.g. 1000 blocks. Each chunk also loads the block before it, so tier 2 needs no state shared between chunks.
- A thread pool (`--threads`) processes the chunks. Each worker has its own read connection.
- Back-pressure: at most `threads × 2` loaded chunks in memory.
- `CHAIN_LINK` for "one block per height" and "`next_block_id` across chunk boundaries" runs in the chunk that owns the higher block.

**Progress:** log at INFO every 10 s: current height, blocks/s, findings by severity, ETA.

## 7. Output

- `--report` file (JSON Lines). One header line: tool version, network, schema version, height range, checks run, known limitations. Then one line per finding, and a summary line at the end with counts per check and severity, plus the duration.
- Accept-list candidates: also write `TX_SIGNATURE` findings to `<report>.accept-candidates.csv` with columns `network;height;block_id;tx_id;full_hash`. The final accept-list format will be decided when the accept list is built; this file is its input.
- Console: the summary, plus the first 20 findings.

## 8. Performance goals

- Mainnet tiers 1 and 2 in about an hour or less on 8 cores (goal, not a hard requirement). The cost is mostly one Ed25519/Curve25519 verification per transaction plus SHA-256 per block.
- Before tuning, benchmark `Crypto.verify` throughput per core and put the number in the PR.
- Memory stays bounded by the chunk back-pressure, independent of chain length.

## 9. Testing (per CLAUDE.md)

Unit tests, one per check, for both the passing and the failing case. Fixtures are built with a test passphrase via `Crypto`:
- a valid signed transaction; a tampered signature; a tampered amount with the original signature; an unsigned `AT_PAYMENT` (valid); an unsigned ordinary payment (finding); a rebuild mismatch;
- a 3-block chain: valid; broken previous hash; wrong generation signature; block signed by a non-candidate key; payload hash with transactions in the wrong order;
- block signature from a reward recipient key; assignment history missing → `WARN`.

Store tests for `SqlChainScanStore` against SQLite: range queries, ordering, and failure on a write attempt.

Integration:
- After a chain test has forged blocks on the mock network (`conf/junit`), scanning that database reports **zero findings**.
- Same database copy with one transaction signature byte flipped: exactly one `TX_SIGNATURE` finding.
- A SQLite file has the same checksum before and after the scan.

Note: chain tests use ports 6876/6877. They can't run while a local node is running.

## 10. Acceptance criteria

- [ ] `--verify-chain` runs headless, read-only, without migrations, with the options from §3 and the exit codes.
- [ ] All tier 1 and tier 2 checks are implemented with unit tests; tier 3 behind `--checks`.
- [ ] Zero findings on a database freshly produced by the node, as in the integration test.
- [ ] Report, accept-candidates file and progress logging as in §6 and §7.
- [ ] Measured run time and `Crypto.verify` benchmark for testnet (and mainnet if available) in the PR description.
- [ ] The README or a new `CHAIN_SCAN.md` documents usage, safety notes and limitations.

## 11. Open questions

1. Mode in the node jar (proposed) or a separate Gradle tool/jar?
2. Final accept-list format and where it lives: network parameters, or a resource file per network.
3. Is `BLOCK_SIGNATURE` as a heuristic (§5, tier 2) enough, or is exact historical reward recipient resolution needed? That needs untrimmed `reward_recip_assign`, i.e. a node with `DB.trimDerivedTables=false`.
4. Ship tier 3 in the first version, or as a follow-up?
