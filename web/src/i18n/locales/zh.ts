import type { Locale } from './en'

const zh: Locale = {
  nav: {
    dashboard: '仪表板',
    network: '网络',
    blocks: '区块',
    wallet: '钱包',
    soon: '即将推出',
  },
  common: {
    refresh: '↻ 刷新',
    refreshing: '刷新中…',
    copy: '复制',
    height: '高度',
    address: '地址',
    failures: '失败次数',
    cachedAgo: '缓存于 {{seconds}}s 前',
    noData: '暂无数据',
    searchAgain: '重新搜索',
    startBinarySearch: '开始二分搜索',
    searching: '搜索中…可能需要几秒钟',
    selectTheme: '选择主题',
    selectLanguage: '选择语言',
  },
  status: {
    onChain: '同步',
    stale: '过时',
    forking: '分叉',
    blacklisted: '已屏蔽',
  },
  dashboard: {
    chainActivity: '链活动',
    network: '网络',
    nodeHealth: '节点健康',
  },
  network: {
    chainConsensus: '链共识',
    peers: '节点',
    forkHistory: '分叉 / 重组历史',
    blacklistRecommendations: '屏蔽建议',
    agree: '{{on}} 一致 · {{stale}} 过时 · {{forking}} 分叉',
    onOurChain: '在我们的链上',
    noReorgs: '节点启动以来无重组记录',
    noRecommendations: '暂无建议',
    noPeerData: '暂无节点数据 — 状态每 120 秒刷新',
    blacklistHint: '将地址添加到 node.properties 中的 P2P.BlacklistedPeers 以屏蔽它们。',
    peersOpen: '{{count}} 个节点 ▲',
    peersOpen_other: '{{count}} 个节点 ▲',
    peersClosed: '{{count}} 个节点 ▼',
    peersClosed_other: '{{count}} 个节点 ▼',
    forkPoint: {
      title: '分叉点搜索',
      findFork: '查找分叉',
      findForkFull: '查找分叉点',
      forkHeight: '分叉高度',
      forkBlockId: '分叉块 ID',
      ourBlock: '我们在分叉处的块',
      steps: '搜索步骤',
    },
    rollback: '-{{depth}} 个区块',
    rollback_other: '-{{depth}} 个区块',
  },
  info: {
    chainConsensus:
      '连接节点中累积难度与我们相符或相差不超过 5 个区块的百分比。≥90% 表示网络在规范链上高度一致。',
    cumulativeDifficulty:
      '自创世以来投入链中的总 proof-of-commitment 工作量。具有最高累积难度的链始终被视为规范链——这使重写历史的代价极为高昂。',
    findFork:
      '在本节点与所选节点之间执行二分搜索，以找到链发生分叉的确切区块高度。有助于诊断分叉问题。',
    networkCapacity:
      '根据当前基准目标推算出的所有活跃矿工的估计总存储承诺量（TiB/PiB）。容量越大，网络越安全。',
    baseTarget:
      '每个区块后自动调整，以将平均出块时间维持在 4 分钟。基准目标越低，难度越大，越难获得挖矿奖励。',
    peersTable:
      '"同步"节点与我们拥有完全相同的累积难度。"过时"节点相差不超过 5 个区块。"分叉"节点处于明显不同的链上。',
    blacklistRecommendations:
      '持续处于不同链上或多次连接失败的节点。将其添加到 P2P.BlacklistedPeers 可防止节点浪费资源与之通信。',
  },
}

export default zh
