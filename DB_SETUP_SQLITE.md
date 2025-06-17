# Introduction

SQLite is a lightweight, file-based database engine that requires no separate server installation or external dependencies. 
Its simplicity makes it especially well-suited for local setups, embedded systems, and applications where minimal configuration is preferred.

SQLite often outperforms heavier systems like MariaDB and Postgres in scenarios with moderate data volume and limited concurrency. Backups are straightforward, as the entire database is stored in a single file.

However, there are important considerations to keep in mind when using SQLite.

# Initial Set Up

> An example configuration is provided [here](./conf/mainnet/node.sqlite.properties)

In the simplest case, configuring the database file name in the `node.properties` file is sufficient. No username or password is required.

```properties
DB.Url=jdbc:sqlite:file:./db/signum.sqlite.db
DB.Username=
DB.Password=
```

## For Test Net

Testnet is primarily for slightly advanced users, providing an environment to experiment without using real SIGNA. Users can obtain "play money" (TSIGNA) from the community. To switch to testnet, simply change the database file name:

```properties
# Tell Signum Node to use Testnet
node.network = signum.net.TestnetNetwork
DB.Url=jdbc:sqlite:file:./db/signum-testnet.sqlite.db
DB.Username=
DB.Password=
```

# Further Configuration

## Optimization

Setting `DB.Optimize=true` triggers optimization processes on node startup. Defragmentation (via `VACUUM`) may take several minutes, so patience is required.

## Journal Modes

It's highly recommended to use "WAL" mode during syncing. SQLite supports [various journal modes](https://www.sqlite.org/pragma.html#pragma_journal_mode), but WAL mode typically offers better performance.

```properties
## Possible Values are: DELETE,TRUNCATE,PERSIST,WAL (default, recommended)
DB.SqliteJournalMode = WAL
```

Note: `MEMORY` journal mode is not supported.

## Temp store

SQLite can keep temporary tables either in files or in memory. The property
`DB.SqliteTempStore` controls the [`temp_store` pragma](https://www.sqlite.org/pragma.html#pragma_temp_store).

```properties
## Allowed values: 0 = always file (default), 1 = file but memory possible,
## 2 = memory but file possible
# DB.SqliteTempStore = 0
```

# Important Notes

## SQLITE_BUSY errors while syncing

Occasionally, `SQL_BUSY` or `SQL_BUSY_SNAPSHOT` exceptions may occur due to SQLite's single-writer nature. These errors typically don't cause consistency issues or affect synchronization but should be treated as warnings.

## Database optimization

Running the `VACUUM` command periodically is advisable to defragment the database and reduce file size. Ensure the node is shut down before running `VACUUM`. This process can take several minutes to complete.

> When setting properties value `DB.Optimize=on`, this command will be executed on each node start.

## WAL Journal

The default journaling mode, "Write-Ahead-Logging" (WAL), creates an additional `.wal` file. During shutdown, a checkpoint is created to ensure data integrity. If using WAL mode, ensure both the `.db` and `.wal` files are copied together. Other journaling modes may be preferable if disk space is limited, though they may impact performance during syncing. The  `MEMORY` mode is not supported to prevent database corruption issues.
