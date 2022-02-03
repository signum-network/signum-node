# Signum Node
[![Node Build](https://github.com/signum-network/signum-node/actions/workflows/build.yml/badge.svg)](https://github.com/signum-network/signum-node/actions/workflows/build.yml)
[![GPLv3](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE.txt)
[![Get Support at https://discord.gg/ms6eagX](https://img.shields.io/badge/join-discord-blue.svg)](https://discord.gg/ms6eagX)

The world's first HDD-mined cryptocurrency using an energy efficient
and fair Proof-of-Commitment (PoC+) consensus algorithm running since August 2014.

The two supported database backends are:

- H2 (embedded, recommended)
- MariaDB (advanced users)

## Network Features

- Proof of Commitment - ASIC proof / Energy efficient and sustainable mining
- No ICO/Airdrops/Premine
- Turing-complete smart contracts, via [Signum SmartJ](https://github.com/signum-network/signum-smartj)
- Asset Exchange; Digital Goods Store; Crowdfunds, NFTs, games, and more (via smart contracts); and Alias system

## Network Specification

- 4 minute block time
- Total Supply: [2,138,119,200 SIGNA up to block 972k + 100 SIGNA per block after that](https://github.com/signum-network/CIPs/blob/master/cip-0029.md)
- Block reward starts at 10,000/block
- Block reward decreases at 5% each month with a minimum mining incentive of 100 SIGNA per block

## Features

- Decentralized Peer-to-Peer network with spam protection
- Built in Java - runs anywhere, from a Raspberry Pi to a Phone
- Fast sync with multithreaded CPU or, optionally, an OpenCL GPU
- HTTP API for clients to interact with network

# Installation

## Prerequisites (All Platforms)

### Java 64-bit 11 (Recommended) or higher

You need Java 64-bit 11 (recommended) or higher installed. To check your java version, run `java -version`. You should get an output similar to the following:

```text
openjdk version "11.0.13" 2021-10-19
OpenJDK Runtime Environment (build 11.0.13+8-Ubuntu-0ubuntu1.20.04)
OpenJDK 64-Bit Server VM (build 11.0.13+8-Ubuntu-0ubuntu1.20.04, mixed mode, sharing)
```

The important part is that the Java version starts with `11.` (Java 11)

If you do not have Java installed:
 - Linux: install the `openjdk-11-jre` package
 - Others: download Java 11 or better from [Oracle's Website](https://www.oracle.com/java/technologies/downloads/#java11)
 - A Windows 10 guide is [available here](https://www.shaileshjha.com/download-and-install-latest-java-jdk-on-windows-10/)
 - Download here [Java 11 SE JDK](https://www.techspot.com/downloads/5553-java-jdk.html) without a need for an Oracle account

### MariaDB (Optional)

[Download and install MariaDB](https://mariadb.com/downloads/mariadb-tx)

The MariaDb installation will ask to setup a password for the root user. 
Add this password to the `node.properties` file you will create when installing BRS:

```properties
DB.Url=jdbc:mariadb://localhost:3306/signum
DB.Username=root
DB.Password=YOUR_PASSWORD
```

## Installation

Grab the latest [release](https://github.com/signum-network/signum-node/releases) (or, if you prefer, compile yourself using the instructions below)

In the `conf` directory, copy `node-default.properties` into a new file named `node.properties` and modify this file to suit your needs (See "Configuration" section below)

To run the node, double click on `signum-node.exe` (if on Windows) or run `java -jar signum-node.jar`.
On most systems this will show you a monitoring window and will create a tray icon to show that Signum node is running. To disable this, instead run `java -jar signum-node.jar --headless`.

## Configuration

### Running on mainnet

Starting with the release version 3.3.0 and higher the concept of the config file changed.
To run the node for mainnet with the default options, no configuration change is needed.

All default/recommended parameters are defined in code by the Signum Node but you can overwrite the parameter within the config file to suit your needs.
The default values for the available settings are shown in the config file as commented out. 

### Configuration Hints

**H2/MariaDB**

By default Signum Node is using H2 (file based) as database. 
If you like to use MariaDB you will need to adjust your `conf/node.properties`:

```properties
#### DATABASE ####
DB.Url=jdbc:mariadb://localhost:3306/signum
DB.Username=
DB.Password=
```

Please modify the `DB.Url` to your own specifications (port 3306 is the standard port from MariaDB) and also set the `DB.Username` and `DB.Password` according your setup for the created database.

**UPnP-Portforwarding**

By default the UPnP port forwarding is activated. 
If you run the node on a VPS you can deactivate this by setting it to "no".
```properties
## Port for incoming peer to peer networking requests, if enabled.
# P2P.Port = 8123
## Use UPnP-Portforwarding
P2P.UPnP = no
```

**Enable SNR**

If you set on the `P2P.myPlatform` a valid Signum address and you fulfill the SNR requirements your node aka the set Signum address will be rewarded with the SNR.
The SNR ([Signum Network Reward](https://signum.community/signum-snr-awards/)) is a community driven bounty paid to all node-operators which run continuous (uptime > 80%) and with the newest release a Signum node.
```properties
## My platform, to be announced to peers.
## Enter your Signum address here for SNR rewards, see here: https://signum.community/signum-snr-awards/
P2P.myPlatform = S-ABCD-EFGH-IJKL-MNOP
```
You can check your node using the [explorer](https://explorer.signum.network/peers/).

## Hardware Requirements

The Signum Node is not hardware demanding nor it needs a fast internet connection.
The specifications for the hardware is as follows:

-   Minimum : 1 vCPU, 1 GB RAM, 20 GB HD,  Minimum Swapfile 2GB (Linux)
-   Recommended : 2 vCPU, 2 GB RAM, 20 GB HD, Minimum Swapfile 4GB (Linux)

**Tuning options**

If you run the minimum requirement you can turn off the `indirectIncomingService` in the config file to reduce CPU and file usage. By default this parameter is activated.
```properties
## Enable the indirect incoming tracker service. This allows you to see transactions where you are paid
## but are not the direct recipient eg. Multi-Outs.
node.indirectIncomingService.enable = false
```

## Upgrade from 3.2.1 to 3.3.0 or higher

If you run a Signum node with a version of 3.2.1 you need to do the following steps to keep your current H2 database. Nodes with a MariaDB setup need **no** adjustments.

 1. Install the new Signum-node as described above
 2. Setup your config file `node.properties` as described above
 3. Move the existing H2 file from the folder `/burst_db` to a new folder `/db` 
 4. Rename the current name of the DB file from `burst.h2.mv.db`to `signum.mv.db`
 5. Start the node


## Testnet

Starting with the Signum node version 3.3.0 and higher the node is able to handle different chains in a multiverse manner. All parameters for a different chain are set in the code section of the node and can be activated by pointing to the other chain aka universe in the config file.
No additional setting is needed. 

In order to run a testnet node adjust your `conf/node.properties` to:
```properties
## Run with a different network
# Testnet network
node.network = signum.net.TestnetNetwork
```

If no custom DB is set, a H2 file will be created as `db/signum-testnet.mv.db`.
For a MariaDB setup you need to configure a testnet instance in the config file.

### Private Chains

In order to run a private (local) chain with *mock* mining just select the network:

```properties
node.network = signum.net.MockNetwork
```

This will allow you to forge new blocks as soon as you submit a new nonce.
Note that P2P is disabled when running in this mode.

# Building from sources

## Building the latest stable release

Run these commands (the `main` branch is always the latest stable release):

```bash
git clone https://github.com/signum-network/signum-node.git
cd signum-node
./gradlew dist
```

Your packaged release will now be available in the `build/distribution` directory.

## Building the latest development version

Clone the repository as instructed above and run these commands:

```bash
git switch develop
./gradlew dist
```

Your packaged release will now be available in the `build/distribution` directory.

**Please note that development builds will refuse to run outside of testnet or a private chain**

## Running the automated tests

Clone the repository as instructed above and run:

```bash
./gradlew test
```

## Updating the Phoenix Wallet

Since V3.0 the Phoenix Wallet is available as built-in alternative to the classic wallet. 
Within a release of the node software automatically the latest available release of the Phoenix wallet will be applied.
As the Phoenix Wallet is a project apart from this repository the node and wallet software have different
release cycles. Therefore, an additional update script (at this moment only for Linux/MacOS) is provided.

Just run `./update-phoenix.sh`, which is available in the distribution package 

# Releasing

To cut a new (pre)-release just create a tag of the following format `vD.D.D[-suffix]`. Githubs actions automatically creates
a pre-release with entirely build executable as zip.

```bash
git tag v3.0.1-beta
git push --tags
```

# Docker

See DOCKER.md for information on running and building docker images.

# Developers

Main Developer: [jjos2372](https://github.com/jjos2372). Donation address: [S-JJQS-MMA4-GHB4-4ZNZU](https://explorer.signum.network/?action=account&account=3278233074628313816)
Frequent Contributors: 
- [ohager](https://github.com/ohager). Donation address: [S-9K9L-4CB5-88Y5-F5G4Z](https://explorer.signum.network/?action=account&account=16107620026796983538)
- [damccull](https://github.com/damccull).

For more information, see [Credits](doc/Credits.md)

# Further Documentation

* [Version History](doc/History.md)

* [Credits](doc/Credits.md)

* [References/Links](doc/References.md)
