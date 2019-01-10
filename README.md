<img align="right" width="120" height="120" title="Burst Logo" src="https://raw.githubusercontent.com/burst-apps-team/Marketing_Resources/master/BURST_LOGO/PNG/icon_blue.png" />

# Burstcoin Wallet
[![Build Status](https://travis-ci.com/burst-apps-team/burstcoin.svg?branch=master)](https://travis-ci.com/burst-apps-team/burstcoin)
[![MIT](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE.txt)
[![Get Support at https://discord.gg/ms6eagX](https://img.shields.io/badge/join-discord-blue.svg)](https://discord.gg/ms6eagX)

The world's first HDD-mined cryptocurrency using an energy efficient
and fair Proof-of-Capacity (PoC) consensus algorithm.

This wallet version is developed and maintained by the PoC consortium
(PoCC) and supports a multitude of database backends. The two builtin
backends are:
- MariaDB (recommended)
- H2 (embedded, easier install)

### Software Installation

#### Linux (Debian, Ubuntu)

- fetch the newest release ZIP file

If running for the first time,

- install Java
- install MariaDB
- run ```burst.sh help```
- probably you want to do ```burst.sh import mariadb```


if upgrading your wallet config from 1.3.6cg

```
burst.sh upgrade
```
will take the old `nxt-default.properties`/`nxt.properties` files and
create `brs-default.properties.converted`/`brs.properties.converted`
files in the conf directory. This should give you a headstart with the
new option naming system.

#### Windows

###### MariaDb

In the conf directory, copy `brs-default.properties` into a new file named `brs.properties`.

Download and install MariaDB <https://mariadb.com/downloads/mariadb-tx>

The MariaDb installation will ask to setup a password for the root user. 
Add this password to the `brs.properties` file created above in the following section:
```
DB.Url=jdbc:mariadb://localhost:3306/brs_master
DB.Username=root
DB.Password=YOUR_PASSWORD
```

The MariaDB installation will also install HeidiSQL, a gui tool to administer MariaDb.
Use it to connect to the newly created mariaDb server and create a new DB called `burstwallet`. 

#### MacOS

BRS can be installed using a [Homebrew](https://brew.sh/) [formula](https://github.com/nixops/homebrew-burstcoind). A short tutorial on how to install BRS using homebrew can be found at [ecomine.earth/macos](https://ecomine.earth/macos/).

A number of other Hombrew formulas (written by [Nixops](https://github.com/nixops)) are also available for plotters and miners.

#### Other Unix-like systems

Please install Java 8 (JRE 1.8) manually and run the wallet by using `burst.sh`
You can get further information calling `burst.sh help`

A good HowTo for running the wallet on a mac can be found here
<https://www.reddit.com/r/burstcoin/comments/7lrdc1/guide_to_getting_the_poc_wallet_running_on_a_mac/>


##### Configure and Initialize MariaDB

The Debian and Ubuntu packages provide an automatic configuration of
your local mariadb server. If you can't use the packages, you have to
initialize your database with these statements:

```
echo "CREATE DATABASE brs_master; 
      CREATE USER 'brs_user'@'localhost' IDENTIFIED BY 'yourpassword';
      GRANT ALL PRIVILEGES ON brs_master.* TO 'brs_user'@'localhost';" | mysql -uroot
mysql -uroot brs_master < init-mysql.sql
```

##### Configure your Wallet

Now you need to add the following stuff to your `conf/brs.properties`:

```
DB.Url=jdbc:mariadb://localhost:3306/brs_master
DB.Username=brs_user
DB.Password=yourpassword
```

### Docker

`latest` : Latest tag of the BRS with H2 database  
`mariadb` : Latest tag of the BRS with MariaDB database  
`2.2.6-h2`, `2.2-h2`, `2-h2` : Version 2.2.6 of the BRS with H2 database  
`2.2.6-mariadb`, `2.2-mariadb`, `2-mariadb` : Version 2.2.6 of the BRS with MariaDB database  


**Note (H2 only):**  
H2 dump username and password has been changed through updates. The current dump has the user 'sa' and password 'sa' which is used in the BRS 2.2.1 image. If you get username errors, please mount your custom config for your H2 file.

Old username and passwords:

- both empty  
  or
- burst - burst

---
##### MariaDB

```
version: '3'

services:
  burstcoin:
    image: pocconsortium/burstcoin:2-mariadb
    restart: always
    depends_on:
     - mariadb
    ports:
     - 8123:8123
     - 8125:8125
  mariadb:
    image: mariadb:10
    environment:
     - MYSQL_ROOT_PASSWORD=burst
     - MYSQL_DATABASE=burst
    command: mysqld --character_set_server=utf8mb4
    volumes:
     - ./burst_db:/var/lib/mysql
```

---
##### H2

```
docker run -p 8123:8123 -p 8125:8125 -v "$(pwd)"/burst_db:/db -d pocconsortium/burstcoin:2-h2
```

## Upgrading

Ensure the PoC-Consortium github is in your list of remotes: 
```
git remote -v
```

If it's not, add it: 
```
git remote add pocc https://github.com/PoC-Consortium/burstcoin.git
```

Replacing `X.X.X` with the latest tag, run these commands:

```
git fetch --all --tags --prune
git checkout tags/X.X.X -b X.X.X 
./burst.sh compile
./burst.sh
```

## Striking Features

- Proof of Capacity - ASIC proof / Energy efficient mining
- Fast sync. with multithread CPU or OpenCL/GPU (optional)
- Turing-complete smart contracts, via Automated Transactions (AT) <https://ciyam.org/at/at.html>
- Asset Exchange and Digital Goods Store
- Encrypted Messaging
- No ICO/Airdrops/Premine

## Specification

- 4 minute block time
- 2,158,812,800 coins total (see <https://burstwiki.org/wiki/Block_Reward>)
- Block reward starts at 10,000/block
- Block Reward Decreases at 5% each month

## [Version History](doc/History.md)

## Tools

To improve scalability and performance, the core development team uses
<a href="https://www.ej-technologies.com/products/jprofiler/overview.html">JProfiler</a>
as its preferred Java Profiler.

## [Known Issues](doc/KnownIssues.md)
## [Development Info](doc/Refactoring.md)
## [Credits](doc/Credits.md)
## [References/Links](doc/References.md)
