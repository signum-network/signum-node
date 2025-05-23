# Install PostgreSQL

Download [PostgreSQL](https://www.postgresql.org/download/)

## Linux (Ubuntu/Debian)

```bash
sudo sh -c 'echo "deb https://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list'
wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add -
sudo apt-get update
sudo apt-get -y install postgresql 
```

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

## Optimization

When the `DB.Optimize` property is enabled the node performs `VACUUM ANALYZE` on shutdown to reclaim disk space and update statistics. Configure this in `conf/node.properties`:

```properties
DB.Optimize = on
```

The optimization step can take several minutes depending on database size.

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
