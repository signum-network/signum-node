import type { Locale } from './en'

const ja: Locale = {
  nav: {
    dashboard: 'ダッシュボード',
    network: 'ネットワーク',
    blocks: 'ブロック',
    wallet: 'ウォレット',
    soon: '近日公開',
  },
  common: {
    refresh: '↻ 更新',
    refreshing: '更新中…',
    copy: 'コピー',
    height: '高さ',
    address: 'アドレス',
    failures: '失敗回数',
    cachedAgo: '{{seconds}}秒前にキャッシュ',
    noData: 'データなし',
    searchAgain: '再検索',
    startBinarySearch: '二分探索を開始',
    searching: '検索中…数秒かかる場合があります',
    selectTheme: 'テーマを選択',
    selectLanguage: '言語を選択',
  },
  status: {
    onChain: '同期中',
    stale: '古い',
    forking: 'フォーク中',
    blacklisted: 'ブロック済',
  },
  dashboard: {
    chainActivity: 'チェーン活動',
    network: 'ネットワーク',
    nodeHealth: 'ノード状態',
  },
  network: {
    chainConsensus: 'チェーンコンセンサス',
    peers: 'ピア',
    forkHistory: 'フォーク / 再編成履歴',
    blacklistRecommendations: 'ブロックリスト推奨',
    agree: '{{on}} 一致 · {{stale}} 古い · {{forking}} フォーク中',
    onOurChain: '当チェーン上',
    noReorgs: 'ノード起動以来再編成なし',
    noRecommendations: '現在推奨なし',
    noPeerData: 'ピアデータなし — 120秒ごとに更新',
    blacklistHint: 'node.properties の P2P.BlacklistedPeers にアドレスを追加してブロックします。',
    peersOpen: '{{count}} ピア ▲',
    peersOpen_other: '{{count}} ピア ▲',
    peersClosed: '{{count}} ピア ▼',
    peersClosed_other: '{{count}} ピア ▼',
    forkPoint: {
      title: 'フォークポイント検索',
      findFork: 'フォーク検索',
      findForkFull: 'フォークポイントを探す',
      forkHeight: 'フォーク高さ',
      forkBlockId: 'フォーク時のブロック ID',
      ourBlock: 'フォーク時の当ノードブロック',
      steps: '検索ステップ数',
    },
    rollback: '-{{depth}} ブロック',
    rollback_other: '-{{depth}} ブロック',
  },
  info: {
    chainConsensus:
      '接続中のピアのうち、累積難易度が一致または5ブロック以内に収まる割合。≥90%はネットワークが正規チェーンで強く合意していることを示します。',
    cumulativeDifficulty:
      'ジェネシス以来チェーンに投じられた proof-of-commitment の累積作業量。累積難易度が最も高いチェーンが常に正規チェーンとして選択され、履歴の書き換えは極めてコストがかかります。',
    findFork:
      '当ノードと選択したピアの間で二分探索を行い、チェーンが分岐した正確なブロック高さを特定します。フォークの診断に役立ちます。',
    networkCapacity:
      '現在のベースターゲットから算出した、すべてのアクティブマイナーの推定総ストレージコミットメント（TiB/PiB）。容量が高いほどネットワークの安全性が高まります。',
    baseTarget:
      '平均ブロック時間を4分に維持するため、ブロックごとに調整されます。低い値ほど難易度が高く、マイニング報酬を得るのが困難になります。',
    peersTable:
      '「同期中」ピアは当ノードと全く同じ累積難易度を持ちます。「古い」ピアは5ブロック以内。「フォーク中」ピアは大きく異なるチェーン上にあります。',
    blacklistRecommendations:
      '継続的に異なるチェーン上にあるか、接続失敗が繰り返されるピア。P2P.BlacklistedPeers に追加することでノードのリソース無駄遣いを防げます。',
  },
}

export default ja
