<img align="right" width="120" height="120" title="Burst Logo" src="https://raw.githubusercontent.com/burst-apps-team/Marketing_Resources/master/BURST_LOGO/PNG/icon_blue.png" />

# Burstcoin Reference Software (Burstcoin Wallet)
[![example workflow](https://github.com/github/docs/actions/workflows/build.yml/badge.svg)](https://github.com/burst-apps-team/burstcoin/actions/workflows/build.yml)
[![GPLv3](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE.txt)
[![Get Support at https://discord.gg/ms6eagX](https://img.shields.io/badge/join-discord-blue.svg)](https://discord.gg/ms6eagX)

The world's first HDD-mined cryptocurrency using an energy efficient
and fair Proof-of-Capacity (PoC) consensus algorithm.

This wallet version is developed and maintained by the Burst Apps Team (BAT). The two supported database backends are:

- H2 (embedded, recommended)
- MariaDB (advanced users)

## Network Features

- Proof of Capacity - ASIC proof / Energy efficient mining
- No ICO/Airdrops/Premine
- Turing-complete smart contracts, via [BlockTalk](https://github.com/burst-apps-team/blocktalk)
- Asset Exchange; Digital Goods Store; Crowdfunds, NFTs, games, and more (via smart contracts); and Alias system

## Network Specification

- 4 minute block time
- Total Supply: [2,158,812,800 BURST](https://burstwiki.org/en/block-reward/)
- Block reward starts at 10,000/block
- Block Reward Decreases at 5% each month

## BRS Features

- Decentralized Peer-to-Peer network with spam protection
- Built in Java - runs anywhere, from a Raspberry Pi to a Phone
- Fast sync with multithreaded CPU or, optionally, an OpenCL GPU
- HTTP and gRPC API for clients to interact with network

# Installation

## Prerequisites (All Platforms)

**NOTE: `burst.sh` is now deprecated and is not included on this release.**

### Java 64-bit 8 (Recommended) or higher

You need Java 64-bit 8 (recommended) or higher installed. To check your java version, run `java -version`. You should get an output similar to the following:

```text
java version "1.8.0_181"
Java(TM) SE Runtime Environment (build 1.8.0_181-b13)
Java HotSpot(TM) 64-Bit Server VM (build 25.181-b13, mixed mode)
```

The important part is that the Java version starts with `1.8` (Java 8)

If you do not have Java installed, download it from [Oracle's Website](https://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html)

### MariaDB (Optional)

[Download and install MariaDB](https://mariadb.com/downloads/mariadb-tx)

The MariaDb installation will ask to setup a password for the root user. 
Add this password to the `brs.properties` file you will create when installing BRS:

```properties
DB.Url=jdbc:mariadb://localhost:3306/brs_master
DB.Username=root
DB.Password=YOUR_PASSWORD
```

## Installation

Grab the latest [release](https://github.com/burst-apps-team/burstcoin/releases) (or, if you prefer, compile yourself using the instructions below)

In the conf directory, copy `brs-default.properties` into a new file named `brs.properties` and modify this file to suit your needs (See "Configuration" section below)

To run BRS, double click on `burst.exe` (if on Windows) or run `java -jar burst.jar`.
On most systems this will show you a monitoring window and will create a tray icon to show that BRS is running. To disable this, instead run `java -jar burst.jar --headless`.

## Configuration

### Running on mainnet (unless you are developing or running on testnet, you will probably want this)

There is no need to change any configuration. Optionally, if you want to use mariadb (see above), you will need to add the following to your `conf/brs.properties`:

```properties
DB.Url=jdbc:mariadb://localhost:3306/brs_master
DB.Username=brs_user
DB.Password=yourpassword
```

Also look through the existing properties if there is anything you want to change.

### Testnet

Please see the [Wiki article](https://burstwiki.org/en/testnet/) for details on how to setup a testnet node.

### Private Chains

In order to run a private chain, you need the following properties:

```properties
DEV.DB.Url=(Your Database URL)
DEV.DB.Username=(Your Database Username)
DEV.DB.Password=(Your Database Password2)
API.Listen = 0.0.0.0
API.allowed = *
DEV.TestNet = yes
DEV.Offline = yes
DEV.digitalGoodsStore.startBlock = 0
DEV.automatedTransactions.startBlock = 0
DEV.atFixBlock2.startBlock = 0
DEV.atFixBlock3.startBlock = 0
DEV.atFixBlock4.startBlock = 0
DEV.prePoc2.startBlock = 0
DEV.poc2.startBlock = 0
DEV.rewardRecipient.startBlock = 0
```

Optionally, if you want to be able to forge blocks faster, you can add the following properties:

```properties
DEV.mockMining = true
DEV.mockMining.deadline = 10
```

This will cause a block to be forged every 10 seconds. Note that P2P is disabled when running a private chain and is incompatible with mock mining.

# Building

## Building the latest stable release

Run these commands (`master` is always the latest stable release):

```bash
git fetch --all --tags --prune
git checkout origin/master
mvn package
```

Your packaged release will now be available in `dist/burstcoin-2.5.0.zip`

## Building the latest development version

Run these commands (`develop` is always the latest stable release):

```bash
git fetch --all --tags --prune
git checkout origin/develop
mvn package
```

Your packaged release will now be available in `dist/burstcoin-3.0.0.zip`.

**Please note that development builds will refuse to run outside of testnet or a private chain**


## Updating the Phoenix Wallet

Since V3.0 the Phoenix Wallet is available as built-in alternative to the classic wallet. As the Phoenix Wallet is a project apart from this repository one need to update it from time to time.
The update process is semi-automated, i.e. one needs to trigger the update script which sites in `./ci`.

**Inside** `./ci` run `./updatePhoenix.sh`

> This script requires NodeJS V14+ runtime environment installed on your machine. The bash script is tested on Linux only, and may not work on other OSes.

# Releasing

To cut a new (pre)-release just create a tag of the following format `vD.D.D[-suffix]`. Githubs actions automatically creates
a pre-release with entirely build executable as zip.

```bash
git tag v3.0.1-beta
git push --tags
```

# Developers

Main Developer: [jjos2372](https://github.com/jjos2372). Donation address: [BURST-JJQS-MMA4-GHB4-4ZNZU](https://explore.burstcoin.network/?action=account&account=3278233074628313816)

For more information, see [Credits](doc/Credits.md)

# Further Documentation

* [Version History](doc/History.md)

* [Credits](doc/Credits.md)

* [References/Links](doc/References.md)
