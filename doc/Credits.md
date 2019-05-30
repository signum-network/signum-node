# Contributors

Numerous people have contributed to make this software what it is today. The Nxt-developers before Burst was forked off from the Nxt code base, the initial - yet anonymous - [Creator of Burstcoin](https://github.com/BurstProject) who invented PoC and also many small contributors who helped out with small fixes, typos, translations etc. In case we forgot to mention someone, please do not hesitate to bring this to our attention.

## [Burst Apps Team](https://github.com/burst-apps-team)

[Harry1453](https://github.com/harry1453) (Donation Address: [BURST-W5YR-ZZQC-KUBJ-G78KB](https://explore.burstcoin.network/?action=account&account=16484518239061020631))
* Main developer for releases v2.3.0 - Present
* Introduced gRPC API
* Heavy optimization of sync speed, along with lots of other database optimization work
* Reverse lookup of Multi-out transactions
* Auto pop-off feature
* Lots of internal migration eg. json-simple -> gson, new versioning system
* Lots of bug fixes
* Lightweight basic desktop GUI

## [Proof of Capacity Consortium](https://github.com/poc-consortium) (Previous Developers)

[ac0v](https://github.com/ac0v)
* initial replacement of the H2 wallet against mariaDB in the CG-lineage of the wallet
* introduction of FirebirdDB to the list of supported DB backends, bugfixing, debugging
* streamlining helper scripts (invocation, compilation)
* work on macOS port, testing and release management
* JOOQ migration and many more things

[Brabantian](https://github.com/Brabantian) (Donation Address: [BURST-BRAB-95SM-SH2Y-7JLGR](https://explore.burstcoin.network/?action=account&account=6609683608614788361))
* lots of automated Testing and Code Coverage reporting
* FluxCapacitor - a sound management of hard forks (feature upgrades)
* dealing better with unconfirmed transactions in a solid configurable store
* unconfirmed transaction handling and other improvements

[rico666](https://github.com/rico666)
* moved the wallet from NRS/Nxt to BRS/Burst namespace
* improvements and fixes to the documentation - revival of javadoc references
* general code refactoring and styleguide unification (Google JAVA Styleguide)
* removed obsolete/unused code - tens of thousands of LOCs
* fixes and enhancements to the UI, config streamlining also JS updates

## [Burst Team](https://github.com/burst-team) (Developers before the Proof of Capacity Consortium)

[daWallet](https://github.com/daWallet)
* many bugfixes and stabilization improvements

[de-luxe](https://github.com/de-luxe) 
* took care of the releases between 1.2.5 and 1.2.9

[dcct](https://github.com/dcct)
* support for parallel blockchain downloads
* various bugfixes and lib updates

## Community / Other
 
[4nt1g0](https://github.com/4nt1g0)
* helping with JOOQ migration

[BraindeadOne](https://github.com/BraindeadOne)
* providing a DB abstraction layer to allow for multiple DB backends
* implementing a load/dump mechanism for faster sync (called Quicksync)
* added SQL metrics to the code to be able to spot DB performance problems
* CPU core limit patch, so the wallet can be assigned only part of your resources

[chrulri](https://github.com/chrulri)
* improved wallet behavior when running under https

[fusecavator](https://github.com/fusecavator)
* initial OpenCL code (GPU acceleration support)
* critical fixes to the wallet spam-attack vulnerability

[LithMage](https://github.com/LithMage)
* fixes and enhancements to the UI

[InjectedPie](https://github.com/InjectedPie)
* modularised the whole UI and updated it to AdminLTE 2
* many UI-related fixes and updates

[Quibus](https://github.com/Quibus)
* Download Cache
* PoC2 implementation
* improved peer handling
* lots of bugfixes

Accepted pull requests improving the wallet quality in several areas
were made by [ChrisMaroglou](https://github.com/ChrisMaroglou), [DarkS0il](https://github.com/DarkS0il), [Doncode](https://github.com/Doncode), [HeosSacer](https://github.com/HeosSacer), [jake-b](https://github.com/jake-b) [llybin](https://github.com/llybin), [naiduv](https://github.com/naiduv), [umbrellacorp03](https://github.com/umbrellacorp03), [velmyshanovnyi](https://github.com/velmyshanovnyi)
