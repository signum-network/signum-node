[History of Burst](https://burstwiki.org/en/history-of-burst/)

```
2018-04-04 v2.3.0
           Fix of major security vulnerability where passphrase was sent to node upon login
           gRPC-based V2 API. Currently only contains calls needed for mining, will be expanded in future if well received.
           Migrate to GSON as JSON library
           Significantly improve sync speed, as well as other minor performance improvements
           New Semver-based versioning system
           Fix bug where reward recipient assignments would not go into unconfirmed transactions
           Lightweight Desktop GUI, with tray icon (For windows and mac, can be disabled with "--headless" command line argument)
           Automatically add conf/ directory to classpath
           Configurable TestNet UI/API port
           New getAccountsWithName API call
           UI: Fix 24h timestamp display option
           Allow development versions of wallet to run on TestNet only
           Fixed bug where string validation could fail in certain locales
           Use FlywayDB for database migration management

2018-05-30 2.2.0
           "Pre-Dymaxion" HF1 release (Burst hard fork/upgrade)
           @500k: 4x bigger blocks, multi-out transactions, dynamic fees
           @502k: PoC2

2018-03-15 2.0.0
           BRS - Burst Reference Software:
           Burst namespace, some NXT legacy is in API data sent P2P
           streamlined configuration namespace, more logical and intuitive
           migrated to JOOQ, supports many  DB backends; only H2 and mariaDB
           in-code to prevent bloat, all others via DB-manager
           UPnP functionality to help with router configuration for public nodes
           removed lots of unused code, updated many UI libraries
           significant improvements in P2P handling: re-sync speed, fork-handling
           peer acquisition
           Squashed many bugs and vulnerabilities, using subresource integrity
           test coverage went from 0% to over 20%

2017-10-28 1.3.6cg
           multi-DB support: added Firebird, re-added H2; support for quick
           binary dump and load

2017-09-04 1.3.4cg
           improved database deployment; bugfix: utf8 encoding

2017-08-11 1.3.2cg
           1st official PoCC release: MariaDB backend based on 1.2.9
```

[Versions up to 2.2.7](https://github.com/poc-consortium/burstcoin/releases)

[Versions up to 1.2.9](https://github.com/burst-team/burstcoin/releases)
