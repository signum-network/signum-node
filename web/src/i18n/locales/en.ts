export interface Locale {
  nav: { dashboard: string; network: string; blocks: string; wallet: string; soon: string }
  common: {
    refresh: string; refreshing: string; copy: string; height: string; address: string
    failures: string; cachedAgo: string; noData: string; searchAgain: string
    startBinarySearch: string; searching: string; selectTheme: string; selectLanguage: string
    enabled: string; disabled: string; live: string; connecting: string; polling: string
  }
  status: { onChain: string; stale: string; ahead: string; forking: string; unknown: string; blacklisted: string }
  dashboard: {
    chainOverview: string; network: string; nodeHealth: string
    currentBlockHeight: string; syncing: string; synced: string
    syncProgress: string; localHeight: string; globalHeight: string
    avgBlockTime: string; lastNBlocks: string; minerReward: string
    emissionFees: string; signaPerBlock: string; circulatingSupply: string
    signaInCirculation: string; feederPeer: string; feederAt: string
    totalBurned: string; signaRemovedFromSupply: string
    allTimeTransactions: string; confirmedOnChain: string
    avgTxPerBlock: string
    smartContracts: string; atsDeployed: string; digitalAssets: string
    tokensIssued: string; liveDexOrders: string; askBid: string
    peersConnected: string; activeConnections: string; pendingTxs: string
    unconfirmedInMempool: string; avgCommitment: string; signaPerTiB: string
    jvmHeap: string; cpuCores: string; dbTrimming: string
    networkCapacity: string; physicalLastN: string; effectiveCapacity: string
    commitmentBoost: string; cumulDifficulty: string
    peerVersionDist: string; outdatedCount: string; noPeerDataYet: string
    versionCurrent: string; versionOutdated: string; versionForkRisk: string
    accounts: string; registeredOnChain: string; trades: string; transfers: string
    aliases: string; subscriptions: string
    assetExchange: string; tradesExecuted: string; tokenMovements: string
  }
  network: {
    chainConsensus: string; peers: string; forkHistory: string; blacklistRecommendations: string
    agree: string; onOurChain: string; latency: string; checkedAgo: string; noAnswerLane: string; noReorgs: string; noRecommendations: string
    noPeerData: string; blacklistHint: string; copyForConfig: string
    peersOpen: string; peersOpen_other: string; peersClosed: string; peersClosed_other: string
    forkPoint: {
      title: string; findFork: string; findForkFull: string
      forkHeight: string; forkBlockId: string; ourBlock: string; steps: string; tooOld: string; tooFarBehind: string
    }
    rollback: string; rollback_other: string
    status: string; cumulDifficulty: string
    chainLanes: string; ourChain: string; forkLane: string; noActiveForks: string
    sectionNetwork: string; sectionYourNode: string
    branchView: string; radialView: string; tableView: string
  }
  info: {
    chainConsensus: string; cumulativeDifficulty: string; findFork: string
    networkCapacity: string; effectiveCapacity: string; baseTarget: string
    peersTable: string; blacklistRecommendations: string
    avgCommitment: string; jvmHeap: string; dbTrimming: string
    chainLanes: string
  }
  blocks: { section: string; latest: string; searchPlaceholder: string; search: string; searching: string; notFound: string; colHeight: string; colTime: string; colDelta: string; colGenerator: string; colTxs: string; colFees: string; newer: string; older: string; page: string; liveOnFirstPage: string; rollbackMarker: string; loadError: string; empty: string }
  block: { title: string; notFound: string; backToList: string; panelBlock: string; panelNode: string; id: string; time: string; delta: string; generator: string; reward: string; rewardValue: string; transactions: string; payload: string; baseTarget: string; avgCommitment: string; cumulativeDifficulty: string; generationSignature: string; previousBlock: string; rollback: string; rollbackText: string; rollbackFrom: string; txTitle: string; colId: string; colType: string; colFrom: string; colTo: string; colAmount: string; colFee: string; noTransactions: string; loadError: string }
  tx: { section: string; title: string; notFound: string; backToList: string; id: string; type: string; sender: string; recipient: string; amount: string; fee: string; block: string; confirmations: string; time: string; unconfirmed: string; attachment: string; loadError: string }
  account: { section: string; overview: string; notFound: string; backToList: string; address: string; id: string; name: string; balance: string; committed: string; guaranteed: string; smartContract: string; forgedBlocks: string; transactions: string; colHeight: string; colTime: string; colFees: string; noBlocks: string; loadError: string }
  blockPeers: { peers: string; peerCheck: string; peerChecking: string; peerResult: string; peerMissing: string; peerNotYet: string; peerNoAnswer: string; peerBusy: string; peerTooOld: string; peerError: string }
  accountTabs: { overview: string; tokens: string; contracts: string; aliases: string; subscriptions: string }
  tokens: { empty: string; colName: string; colBalance: string; colToken: string; colIssuer: string; metaMissing: string }
}

const en: Locale = {
  nav: {
    dashboard: 'Dashboard',
    network: 'Network',
    blocks: 'Blocks',
    wallet: 'Wallet',
    soon: 'soon',
  },
  common: {
    refresh: '↻ refresh',
    refreshing: 'refreshing…',
    copy: 'Copy',
    height: 'Height',
    address: 'Address',
    failures: 'Failures',
    cachedAgo: 'cached {{seconds}}s ago',
    noData: 'No data',
    searchAgain: 'Search again',
    startBinarySearch: 'Start Binary Search',
    searching: 'Searching… this may take a few seconds',
    selectTheme: 'Select Theme',
    selectLanguage: 'Select Language',
    enabled: 'Enabled',
    disabled: 'Disabled',
    live: 'Live',
    connecting: 'Connecting',
    polling: 'Polling',
  },
  status: {
    onChain: 'on-chain',
    stale: 'stale',
    forking: 'forking',
    ahead: 'ahead',
    unknown: 'unknown',
    blacklisted: 'blacklisted',
  },
  dashboard: {
    chainOverview: 'Chain Overview',
    network: 'Network',
    nodeHealth: 'Node Health',
    currentBlockHeight: 'Current Block Height',
    syncing: 'Syncing',
    synced: 'Synced',
    syncProgress: 'Sync Progress',
    localHeight: 'Local {{height}}',
    globalHeight: 'Global {{height}}',
    avgBlockTime: 'Avg Block Time',
    lastNBlocks: 'last {{count}} blocks · target 4:00',
    minerReward: 'Miner Reward',
    emissionFees: '{{base}} emission · {{fees}} fees',
    signaPerBlock: 'SIGNA / block',
    circulatingSupply: 'Circulating Supply',
    signaInCirculation: 'SIGNA in circulation',
    feederPeer: 'Feeder Peer',
    feederAt: 'at #{{height}}',
    totalBurned: 'Total Burned',
    signaRemovedFromSupply: 'SIGNA removed from supply',
    allTimeTransactions: 'All-time Transactions',
    confirmedOnChain: 'confirmed on-chain',
    avgTxPerBlock: 'Avg TX / Block',
    smartContracts: 'Smart Contracts',
    atsDeployed: 'ATs deployed',
    digitalAssets: 'Digital Assets',
    tokensIssued: 'tokens issued',
    liveDexOrders: 'Live DEX Orders',
    askBid: '{{ask}} ask · {{bid}} bid',
    peersConnected: 'Peers Connected',
    activeConnections: 'active connections',
    pendingTxs: 'Pending TXs',
    unconfirmedInMempool: 'unconfirmed in mempool',
    avgCommitment: 'Avg Commitment',
    signaPerTiB: 'SIGNA / TiB · PoC+',
    jvmHeap: 'JVM Heap',
    cpuCores: 'CPU Cores',
    dbTrimming: 'DB Trimming',
    networkCapacity: 'Network Capacity',
    physicalLastN: 'physical · last {{count}} blocks',
    effectiveCapacity: 'Effective Capacity',
    commitmentBoost: 'incl. PoC+ commitment boost',
    cumulDifficulty: 'cumul. difficulty {{value}}',
    peerVersionDist: 'Peer Version Distribution',
    outdatedCount: '{{count}} outdated',
    noPeerDataYet: 'No peer data yet',
    versionCurrent: 'current',
    versionOutdated: 'outdated',
    versionForkRisk: 'fork risk',
    accounts: 'Accounts',
    registeredOnChain: 'registered on-chain',
    trades: 'Trades',
    transfers: 'Transfers',
    aliases: 'Aliases',
    subscriptions: 'Subscriptions',
    assetExchange: 'Asset Exchange',
    tradesExecuted: 'trades executed',
    tokenMovements: 'token movements',
  },
  network: {
    chainConsensus: 'Chain Consensus',
    peers: 'Peers',
    forkHistory: 'Fork / Reorg History',
    blacklistRecommendations: 'Blacklist Recommendations',
    agree: '{{on}} agree · {{stale}} stale · {{ahead}} ahead · {{forking}} forking · {{unknown}} unknown',
    onOurChain: 'on our chain',
    latency: 'Latency',
    checkedAgo: 'checked {{seconds}}s ago',
    noAnswerLane: 'No answer',
    noReorgs: 'No reorganizations recorded since node start',
    noRecommendations: 'No recommendations at this time',
    noPeerData: 'No peer data yet — status refreshes every 120 seconds',
    blacklistHint: 'Add addresses to P2P.BlacklistedPeers in node.properties to block them.',
    copyForConfig: 'Copy for config',
    peersOpen: '{{count}} peer ▲',
    peersOpen_other: '{{count}} peers ▲',
    peersClosed: '{{count}} peer ▼',
    peersClosed_other: '{{count}} peers ▼',
    forkPoint: {
      title: 'Fork Point Search',
      findFork: 'Find fork',
      findForkFull: 'Find fork point',
      forkHeight: 'Fork height',
      forkBlockId: 'Fork block ID',
      ourBlock: 'Our block at fork',
      steps: 'Search steps',
      tooOld: 'This peer diverged more than {{limit}} blocks ago and is on a permanently different chain.',
      tooFarBehind: 'Peer is more than 10,000 blocks behind — fork search would exceed the lookback window.',
    },
    rollback: '-{{depth}} block',
    rollback_other: '-{{depth}} blocks',
    status: 'Status',
    cumulDifficulty: 'Cumul. difficulty',
    chainLanes: 'Chain Distribution',
    ourChain: 'Our Chain',
    forkLane: 'Fork {{index}}',
    noActiveForks: 'No active forks detected',
    sectionNetwork: 'Network & Peers',
    sectionYourNode: 'Your Node',
    branchView: 'Branch',
    radialView: 'Radial',
    tableView: 'Table',
  },
  info: {
    chainConsensus:
      'The share of checked peers on our chain: their newest block is our newest block (on-chain), comes before it (stale) or builds on it (ahead). Peers that did not answer are not counted. Values ≥90% indicate strong agreement on our chain.',
    cumulativeDifficulty:
      'The total proof-of-commitment work invested in the chain since genesis. The chain with the highest cumulative difficulty is always chosen as canonical — making it extremely costly to rewrite history.',
    findFork:
      'Performs a binary search between your node and the selected peer to locate the exact block height where your chains diverged. Useful for diagnosing forks and understanding which peer is on the correct chain.',
    networkCapacity:
      'The estimated total storage commitment (in TiB / PiB) across all active miners, derived from the current base target. A higher capacity means more commitment secures the network.',
    baseTarget:
      'Adjusted every block to keep the average block time at 4 minutes. A lower base target means higher network difficulty — harder to win the mining lottery.',
    peersTable:
      '"On-chain" peers have our newest block as theirs. "Ahead" peers have it and more. "Stale" peers lack it, but their newest block is on our chain. "Forking" peers lack it, and their newest block is not on our chain. "Unknown" peers did not answer the check.',
    blacklistRecommendations:
      'Peers that are consistently on a different chain or have repeated connection failures. Adding them to P2P.BlacklistedPeers in node.properties prevents your node from wasting resources on them.',
    effectiveCapacity:
      'Effective capacity weights each miner\'s raw storage by their Signum commitment (PoC+). A miner with more SIGNA staked earns a multiplier, so effective capacity can exceed physical capacity when the network has strong commitment.',
    avgCommitment:
      'The average amount of SIGNA committed per TiB across all active miners. Higher commitment raises the effective capacity multiplier, increases block rewards for committed miners, and makes the chain more expensive to attack.',
    jvmHeap:
      'JVM heap memory currently in use vs the maximum allocated to this process. Sustained usage above 80% may cause GC pauses; above 90% risks OutOfMemoryError. Raise -Xmx in the startup script if this is consistently high.',
    dbTrimming:
      'When enabled, the node periodically removes derived-table data (balances, unconfirmed transactions) that can be recalculated from the chain. Keeps the database compact at the cost of a small background workload.',
    chainLanes:
      'Shows which peers are on the same chain as your node and which are on competing chains, grouped by cumulative difficulty. The difficulty bar shows relative chain weight — the chain with the most accumulated work is canonical.',
  },
  blocks: {
    section: 'Blocks',
    latest: 'Latest blocks',
    searchPlaceholder: 'Height, block ID, transaction ID or address (S-…)',
    search: 'Search',
    searching: 'Searching…',
    notFound: 'Nothing found for “{{query}}”',
    colHeight: 'Height',
    colTime: 'Time',
    colDelta: 'Δt',
    colGenerator: 'Generator',
    colTxs: 'Txs',
    colFees: 'Fees',
    newer: '‹ newer',
    older: 'older ›',
    page: 'Page {{page}}',
    liveOnFirstPage: 'New blocks appear on page 1',
    rollbackMarker: 'Replaced on this node by a rollback (depth {{count}}) – see the network page',
    loadError: 'Could not load the blocks',
    empty: 'No blocks',
  },
  block: {
    title: 'Block {{height}}',
    notFound: 'Block not found',
    backToList: 'Back to the block list',
    panelBlock: 'Block',
    panelNode: 'Node view',
    id: 'ID',
    time: 'Time',
    delta: 'Δt',
    generator: 'Generator',
    reward: 'Reward',
    rewardValue: '{{reward}} + {{fees}} fees',
    transactions: 'Transactions',
    payload: 'Payload',
    baseTarget: 'Base target',
    avgCommitment: 'Ø Commitment',
    cumulativeDifficulty: 'Cumulative difficulty',
    generationSignature: 'Generation signature',
    previousBlock: 'Previous block',
    rollback: 'Rollback',
    rollbackText: 'Replaced by a rollback (depth {{count}}), {{ago}}',
    rollbackFrom: 'from {{peer}}',
    txTitle: 'Transactions ({{count}})',
    colId: 'ID',
    colType: 'Type',
    colFrom: 'From',
    colTo: 'To',
    colAmount: 'Amount',
    colFee: 'Fee',
    noTransactions: 'No transactions',
    loadError: 'Could not load the data',
  },
  tx: {
    section: 'Transaction',
    title: 'Details',
    notFound: 'Transaction not found',
    backToList: 'Back to the block list',
    id: 'ID',
    type: 'Type',
    sender: 'Sender',
    recipient: 'Recipient',
    amount: 'Amount',
    fee: 'Fee',
    block: 'Block',
    confirmations: 'Confirmations',
    time: 'Time',
    unconfirmed: 'unconfirmed',
    attachment: 'Attachment',
    loadError: 'Could not load the transaction',
  },
  account: {
    section: 'Account',
    overview: 'Overview',
    notFound: 'Account not found',
    backToList: 'Back to the block list',
    address: 'Address',
    id: 'Numeric ID',
    name: 'Name',
    balance: 'Balance',
    committed: 'Committed',
    guaranteed: 'Guaranteed balance',
    smartContract: 'Smart contract',
    forgedBlocks: 'Forged blocks',
    transactions: 'Latest transactions',
    colHeight: 'Height',
    colTime: 'Time',
    colFees: 'Fees',
    noBlocks: 'No forged blocks',
    loadError: 'Could not load the account',
  },
  blockPeers: {
    peers: 'Peers',
    peerCheck: 'Ask peers',
    peerChecking: 'Asking peers…',
    peerResult: '{{hasIt}} of {{total}} peers have this block',
    peerMissing: 'missing: {{count}}',
    peerNotYet: 'not that far yet: {{count}}',
    peerNoAnswer: 'no answer: {{count}}',
    peerBusy: 'Another check is running – try again shortly',
    peerTooOld: 'Too old to ask the peers',
    peerError: 'The peers could not be asked',
  },
  accountTabs: {
    overview: 'Overview',
    tokens: 'Tokens',
    contracts: 'Contracts',
    aliases: 'Aliases',
    subscriptions: 'Subscriptions',
  },
  tokens: {
    empty: 'No tokens',
    colName: 'Name',
    colBalance: 'Balance',
    colToken: 'Token ID',
    colIssuer: 'Issuer',
    metaMissing: 'details unavailable – raw quantity',
  },
}

export default en
