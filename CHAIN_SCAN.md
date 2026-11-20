# Chain integrity scan

The node jar can re-check a synced database against the consensus rules that need no replay of
account state, and report every violation. It never writes to the database.

```
java -jar signum-node.jar --verify-chain [--config <folder>]
     [--from-height <h>] [--to-height <h>]
     [--checks tx|block|all]    # default: tx,block
     [--threads <n>]            # default: available processors
     [--report <file>]          # default: chain-scan-<network>-<timestamp>.jsonl
```

Run it from the node's folder, as you start the node: it reads `conf/` and the database configured
there (`DB.Url`, relative paths included).

## Safety

- SQLite is opened read-only. MariaDB and PostgreSQL connections are marked read-only; a read-only
  database user is recommended as well.
- No migrations, peers, API server or GUI are started.
- The scan refuses to run (exit code 2) if the database lacks migrations of this build.
- Scanning the database of a running node works, but stop the node or scan a copy for a consistent
  snapshot.

## What is checked

| Group | Checks |
|---|---|
| `tx` | `TX_LOAD`, `TX_REBUILD_MISMATCH` (id, full hash, sender id), `TX_SIGNATURE`, `TX_UNSIGNED` (info), `TX_BLOCK_CONSISTENCY` |
| `block` | `BLOCK_LOAD`, `BLOCK_ID`, `BLOCK_PAYLOAD`, `BLOCK_PAYLOAD_LENGTH` (warning), `CHAIN_LINK`, `PREV_BLOCK_HASH`, `GENERATION_SIGNATURE` |

Known limitations: balances, ATs, assets and other derived tables are not checked (that needs a
resync); ids of unsigned system transactions are not recomputed; block signatures and PoC deadlines
are not checked yet. See `docs/specs/chain-integrity-scan.md`.

## Output

- `<report>`: JSON Lines. A header (versions, network, schema, range, checks, limitations), one line
  per finding, a summary.
- `<report>.accept-candidates.csv`: the `TX_SIGNATURE` findings
  (`network;height;block_id;tx_id;full_hash`).
- Console: progress every 10 s, the summary and the first 20 findings.

Exit codes: `0` no errors, `1` at least one finding of severity `ERROR`, `2` the scan failed.

Testnet (974,000 blocks, 2.9 million transactions, SQLite, 8 threads): 72 seconds.
