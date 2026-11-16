# Install MariaDB

> The minimum required version is __10.11!__

__DOWNLOAD FOR WINDOWS__: https://mariadb.com/downloads/?utm_source=header&utm_medium=website
<br>Download the MariaDB Community(FREE) version 10.11.13-GA for Windows.


__WINDOWS__: https://mariadb.com/kb/en/installing-mariadb-msi-packages-on-windows/
<br>(Tuturial on version 10.6 - please download 10.11)

__LINUX__: https://ubuntushell.com/install-mariadb-on-ubuntu/

The MariaDb installation will ask to set up a password for the root user.
This password can be anything you like.

# Initial Set Up

Once installed you need to run a least minimum initialization of your database server:

- Create a database
- Create a user (representing the Signum Node)
- Give user access to the database

Run the `mariadb` (with admin rights, i.e. `sudo mariadb` on linux/mac) command and you should see something like this

```bash 
user@computer:~$ sudo mariadb
[sudo] password for user: 
Welcome to the MariaDB monitor.  Commands end with ; or \g.
Your MariaDB connection id is 42
Server version: 11.2.2-MariaDB-1:11.2.2+maria~ubu2004 mariadb.org binary distribution

Copyright (c) 2000, 2018, Oracle, MariaDB Corporation Ab and others.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

MariaDB [(none)]> 
```

Within this bash run the following SQL commands:

```sql
-- Create the database
CREATE DATABASE IF NOT EXISTS signum;

-- Create the user (choose another password if you want)
CREATE USER IF NOT EXISTS 'signumnode'@'localhost' IDENTIFIED BY 's1gn00m_n0d3';

-- Grant ownership of the database to the user
GRANT ALL PRIVILEGES ON signum.* TO 'signumnode'@'localhost';
FLUSH PRIVILEGES;
```

## Configure Node 

Create a file named `node.properties` in the `./conf` directory. And add the following 

In node.properties file:
```properties
DB.Url=jdbc:mariadb://localhost:3306/signum
DB.Username=signumnode
# The chosen password
DB.Password=s1gn00m_n0d3
```

## For Test Net

> Testnet is usually only for slightly advanced users. The testnet allows you "play" around without having to buy/get real SIGNA. 
> Ask the community to get some "play money" (TSIGNA).   

If you switch often between Main and Testnet networks it might be interesting to set up MariaDB for both networks.
Just create another database and grant the Signum Node access to that database. 
We assume that the user `signumnode` exists already.

```sql
-- Create the database
CREATE DATABASE signum_testnet;

-- Grant ownership of the database to the user
GRANT ALL PRIVILEGES ON signum_testnet.* TO 'signumnode'@'localhost';
FLUSH PRIVILEGES;
```

Then you can switch between both networks by just changing the properties accordingly:

In node.properties file:
```properties
# Tell Signum Node to use Testnet
node.network = signum.net.TestnetNetwork
DB.Url=jdbc:mariadb://localhost:3306/signum_testnet
DB.Username=signumnode
DB.Password=s1gn00m_n0d3
```

# Performance Tuning

With MariaDB's default settings, the database is the bottleneck while the node syncs. By default MariaDB flushes its log to disk on every commit and keeps only 128 MB of data in memory.

Add the following to the server configuration, for example a new file `/etc/mysql/conf.d/signum.cnf` on Linux or `my.ini` on Windows:

```ini
[mariadb]
# Write the log to disk about once per second instead of on every commit
innodb_flush_log_at_trx_commit = 2
# Larger redo log, so fewer checkpoints
innodb_log_file_size = 1G
# Memory for data and indexes; up to about half of the RAM on a dedicated host
innodb_buffer_pool_size = 1G
```

Restart the service afterwards and check the values:

```sql
SHOW VARIABLES WHERE Variable_name IN
  ('innodb_flush_log_at_trx_commit', 'innodb_log_file_size', 'innodb_buffer_pool_size');
```

## What it brings

Mainnet sync from an empty database with Signum Node 3.10 and MariaDB 11.4, blocks synced in 8 minutes (two runs each):

| Settings | Blocks |
|---|---|
| MariaDB defaults | 21,539 / 19,960 |
| Settings above | 28,301 / 27,583 |

## What it costs

With `innodb_flush_log_at_trx_commit = 2`, a power loss or an operating system crash can lose about the last second of commits. A crash of MariaDB alone loses nothing. The database stays consistent either way. The node then starts at a slightly lower height and syncs the missing blocks again.

Prefer `2` over `0`: with `0`, a crash of MariaDB alone can also lose the last second of commits.
