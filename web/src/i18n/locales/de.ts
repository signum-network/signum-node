import type { Locale } from './en'

const de: Locale = {
  nav: {
    dashboard: 'Dashboard',
    network: 'Netzwerk',
    blocks: 'Blöcke',
    wallet: 'Wallet',
    soon: 'bald',
  },
  common: {
    refresh: '↻ aktualisieren',
    refreshing: 'aktualisiert…',
    copy: 'Kopieren',
    height: 'Höhe',
    address: 'Adresse',
    failures: 'Fehler',
    cachedAgo: 'gecacht vor {{seconds}}s',
    noData: 'Keine Daten',
    searchAgain: 'Erneut suchen',
    startBinarySearch: 'Binäre Suche starten',
    searching: 'Suche läuft… das kann einige Sekunden dauern',
    selectTheme: 'Thema wählen',
    selectLanguage: 'Sprache wählen',
  },
  status: {
    onChain: 'synchron',
    stale: 'veraltet',
    forking: 'abgezweigt',
    blacklisted: 'gesperrt',
  },
  dashboard: {
    chainActivity: 'Kettenaktivität',
    network: 'Netzwerk',
    nodeHealth: 'Knotenstatus',
  },
  network: {
    chainConsensus: 'Kettenkonsens',
    peers: 'Peers',
    forkHistory: 'Fork- / Reorganisationsverlauf',
    blacklistRecommendations: 'Sperr­empfehlungen',
    agree: '{{on}} einig · {{stale}} veraltet · {{forking}} abgezweigt',
    onOurChain: 'auf unserer Kette',
    noReorgs: 'Keine Reorganisationen seit Knotenstart',
    noRecommendations: 'Keine Empfehlungen im Moment',
    noPeerData: 'Keine Peer-Daten — Status aktualisiert alle 120 Sekunden',
    blacklistHint: 'Adressen in P2P.BlacklistedPeers in node.properties eintragen, um sie zu sperren.',
    peersOpen: '{{count}} Peer ▲',
    peersOpen_other: '{{count}} Peers ▲',
    peersClosed: '{{count}} Peer ▼',
    peersClosed_other: '{{count}} Peers ▼',
    forkPoint: {
      title: 'Fork-Punkt-Suche',
      findFork: 'Fork finden',
      findForkFull: 'Fork-Punkt finden',
      forkHeight: 'Fork-Höhe',
      forkBlockId: 'Block-ID am Fork',
      ourBlock: 'Unser Block am Fork',
      steps: 'Suchschritte',
    },
    rollback: '-{{depth}} Block',
    rollback_other: '-{{depth}} Blöcke',
  },
  info: {
    chainConsensus:
      'Der Prozentsatz der verbundenen Peers, deren kumulierte Schwierigkeit mit unserer übereinstimmt oder höchstens 5 Blöcke abweicht. Werte ≥90% zeigen eine starke Netzwerkeinigkeit über die kanonische Kette.',
    cumulativeDifficulty:
      'Die gesamte Proof-of-Commitment-Arbeit, die seit dem Genesis-Block in die Kette investiert wurde. Die Kette mit der höchsten kumulierten Schwierigkeit gilt stets als kanonisch — das Umschreiben der Geschichte wird damit extrem kostspielig.',
    findFork:
      'Führt eine binäre Suche zwischen diesem Knoten und dem gewählten Peer durch, um die exakte Blockhöhe zu finden, an der die Ketten abgewichen sind. Hilfreich zur Diagnose von Forks.',
    networkCapacity:
      'Das geschätzte gesamte Speicher-Commitment (in TiB/PiB) aller aktiven Miner, abgeleitet aus dem aktuellen Base-Target. Höhere Kapazität bedeutet mehr Netzwerksicherheit.',
    baseTarget:
      'Wird nach jedem Block angepasst, um eine durchschnittliche Blockzeit von 4 Minuten zu halten. Ein niedrigerer Wert bedeutet höhere Schwierigkeit — schwieriger, Mining-Belohnungen zu erhalten.',
    peersTable:
      '„Synchron"-Peers teilen exakt unsere kumulierte Schwierigkeit. „Veraltete" Peers liegen innerhalb von 5 Blöcken. „Abgezweigte" Peers befinden sich auf einer erheblich abweichenden Kette.',
    blacklistRecommendations:
      'Peers, die dauerhaft auf einer anderen Kette sind oder wiederholte Verbindungsfehler aufweisen. Das Eintragen in P2P.BlacklistedPeers verhindert, dass der Knoten Ressourcen an sie verschwendet.',
  },
}

export default de
