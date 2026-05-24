export interface Locale {
  nav: { dashboard: string; network: string; blocks: string; wallet: string; soon: string }
  common: {
    refresh: string; refreshing: string; copy: string; height: string; address: string
    failures: string; cachedAgo: string; noData: string; searchAgain: string
    startBinarySearch: string; searching: string; selectTheme: string; selectLanguage: string
  }
  status: { onChain: string; stale: string; forking: string; blacklisted: string }
  dashboard: { chainActivity: string; network: string; nodeHealth: string }
  network: {
    chainConsensus: string; peers: string; forkHistory: string; blacklistRecommendations: string
    agree: string; onOurChain: string; noReorgs: string; noRecommendations: string
    noPeerData: string; blacklistHint: string
    peersOpen: string; peersOpen_other: string; peersClosed: string; peersClosed_other: string
    forkPoint: {
      title: string; findFork: string; findForkFull: string
      forkHeight: string; forkBlockId: string; ourBlock: string; steps: string
    }
    rollback: string; rollback_other: string
  }
  info: {
    chainConsensus: string; cumulativeDifficulty: string; findFork: string
    networkCapacity: string; baseTarget: string; peersTable: string; blacklistRecommendations: string
  }
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
  },
  status: {
    onChain: 'on-chain',
    stale: 'stale',
    forking: 'forking',
    blacklisted: 'blacklisted',
  },
  dashboard: {
    chainActivity: 'Chain Activity',
    network: 'Network',
    nodeHealth: 'Node Health',
  },
  network: {
    chainConsensus: 'Chain Consensus',
    peers: 'Peers',
    forkHistory: 'Fork / Reorg History',
    blacklistRecommendations: 'Blacklist Recommendations',
    agree: '{{on}} agree · {{stale}} stale · {{forking}} forking',
    onOurChain: 'on our chain',
    noReorgs: 'No reorganizations recorded since node start',
    noRecommendations: 'No recommendations at this time',
    noPeerData: 'No peer data yet — status refreshes every 120 seconds',
    blacklistHint: 'Add addresses to P2P.BlacklistedPeers in node.properties to block them.',
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
    },
    rollback: '-{{depth}} block',
    rollback_other: '-{{depth}} blocks',
  },
  info: {
    chainConsensus:
      'The percentage of connected peers whose cumulative difficulty matches or is within 5 blocks of ours. Values ≥90% indicate the network is in strong agreement on the canonical chain.',
    cumulativeDifficulty:
      'The total proof-of-commitment work invested in the chain since genesis. The chain with the highest cumulative difficulty is always chosen as canonical — making it extremely costly to rewrite history.',
    findFork:
      'Performs a binary search between your node and the selected peer to locate the exact block height where your chains diverged. Useful for diagnosing forks and understanding which peer is on the correct chain.',
    networkCapacity:
      'The estimated total storage commitment (in TiB / PiB) across all active miners, derived from the current base target. A higher capacity means more commitment secures the network.',
    baseTarget:
      'Adjusted every block to keep the average block time at 4 minutes. A lower base target means higher network difficulty — harder to win the mining lottery.',
    peersTable:
      '"On-chain" peers share our exact cumulative difficulty. "Stale" peers are within 5 blocks. "Forking" peers are on a significantly different chain and may indicate a network split.',
    blacklistRecommendations:
      'Peers that are consistently on a different chain or have repeated connection failures. Adding them to P2P.BlacklistedPeers in node.properties prevents your node from wasting resources on them.',
  },
}

export default en
