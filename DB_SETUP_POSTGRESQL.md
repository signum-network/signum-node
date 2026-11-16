# Install PostgreSQL
> The minimum required version is __16.9!__

Download [PostgreSQL](https://www.postgresql.org/download/)

## Linux (Ubuntu/Debian)

```bash
sudo sh -c 'echo "deb https://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list'
wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add -
sudo apt-get update
sudo apt-get -y install postgresql 
```

# Setup Script

After installing PostgreSQL, [`scripts/db/signum-postgres.sql`](scripts/db/signum-postgres.sql) does the initial set-up and the [performance tuning](#performance-tuning) below in one go, on any OS. It is safe to run again, for example to change the password.

```bash
# Linux
sudo -u postgres psql -v password='choose-a-password' < scripts/db/signum-postgres.sql
# macOS (Homebrew)
psql -d postgres -v password='choose-a-password' -f scripts/db/signum-postgres.sql
# Windows
psql -U postgres -v password="choose-a-password" -f scripts\db\signum-postgres.sql
```

Restart PostgreSQL afterwards. For the testnet, add `-v db=signum_testnet`. The header of the script lists all options.

The script is in this repository, not in the release ZIP. The sections below do the same by hand.

# Initial Set up
Once installed you need to run a least minimum initialization of your database server:

- Create a database
- Create a user (representing the Signum Node)
- Give user access to the database

Run `psql` in your command line; You should see something like this:

```
user@computer:~$ psql
psql (16.1 (Ubuntu 16.1-1.pgdg20.04+1), server 12.17 (Ubuntu 12.17-1.pgdg20.04+1))
Type "help" for help.

user=#
```
> This is on Linux, but it might be somehow similar on Windows, or Mac.

Then execute the following commands in the `psql` prompt.

## For Main Net

The main net should be the preferred setup, unless you want to do some development.

```sql
-- Create the user (choose another password if you want)
CREATE USER signumnode WITH PASSWORD 's1gn00m_n0d3';

-- Create the database
CREATE DATABASE signum OWNER=signumnode;
```

Now, you need to configure your database connection in the `./conf/node.properties` file

In node.properties file:
```properties
DB.Url=jdbc:postgresql://localhost:5432/signum
DB.Username=signumnode
# The chosen password
DB.Password=s1gn00m_n0d3
```

## For Test Net

> Testnet is usually only for slightly advanced users. The testnet allows you "play" around without having to buy/get real SIGNA. 
> Ask the community to get some "play money" (TSIGNA).   

If you switch often between Main and Testnet networks it might be interesting to set up Postgres for both networks.
Just create another database and grant the Signum Node access to that database. 
We assume that the user `signumnode` exists already.

```sql
-- Create the database
CREATE DATABASE signum_testnet OWNER=signumnode;
```

Then you can switch between both networks by just changing the properties accordingly:

In node.properties file:
```properties
node.network = signum.net.TestnetNetwork
DB.Url=jdbc:postgresql://localhost:5432/signum_testnet
DB.Username=signumnode
DB.Password=s1gn00m_n0d3
```

# Performance Tuning

With PostgreSQL's default settings, the database is the bottleneck while the node syncs. By default PostgreSQL waits for a disk flush on every commit, keeps only 128 MB of data in memory and starts a checkpoint after every 1 GB of WAL.

Set the following in `postgresql.conf`:

```properties
# Don't wait for the disk on every commit
synchronous_commit = off
# Fewer, larger checkpoints
max_wal_size = 4GB
checkpoint_timeout = 15min
checkpoint_completion_target = 0.9
# Memory for data and indexes; about 25% of the RAM
shared_buffers = 1GB
```

Instead of editing the file, you can also run these as `ALTER SYSTEM SET ...` in `psql`, for example `ALTER SYSTEM SET synchronous_commit = off;`. This is handy in Docker.

Restart the service afterwards and check the values with `SHOW synchronous_commit;` and so on.

On hosts with more than 8 GB RAM, you can give PostgreSQL more memory:

```properties
shared_buffers = 4GB
effective_cache_size = 8GB
work_mem = 64MB
maintenance_work_mem = 512MB
wal_buffers = 16MB
```

`effective_cache_size` should be at most 75% of the RAM.

## What it brings

Mainnet sync from an empty database with Signum Node 3.10 and PostgreSQL 17, blocks synced in 8 minutes (two runs each):

| Settings | Blocks |
|---|---|
| PostgreSQL defaults | 23,835 / 24,704 |
| First block of settings above | 36,719 / 37,814 |

## What it costs

With `synchronous_commit = off`, a power loss or a crash can lose the last fraction of a second of commits (up to three times `wal_writer_delay`, 200 ms by default). The database stays consistent. The node then starts at a slightly lower height and syncs the missing blocks again.
