# Install MariaDB

> The minimum required version is __10.6!__

__WINDOWS__: https://mariadb.com/kb/en/installing-mariadb-msi-packages-on-windows/

__LINUX__: https://www.digitalocean.com/community/tutorials/how-to-install-mariadb-on-ubuntu-20-04

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

# Faster Sync time

Do reduce I/O times and though causing significant speedup while syncing, one may run the following command:   

```sql
SET GLOBAL innodb_flush_log_at_trx_commit = 0;
```

See more details [here](https://mariadb.com/docs/server/ref/mdb/system-variables/innodb_flush_log_at_trx_commit/).

## Optimization

To reduce disk usage the node can optimize all MariaDB tables on shutdown. Enable this behaviour in `conf/node.properties`:

```properties
DB.Optimize = on
```

With this setting the node issues `OPTIMIZE TABLE` for every table before closing. The process may take a while depending on database size.
