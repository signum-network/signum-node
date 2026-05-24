import type { Locale } from './en'

const ko: Locale = {
  nav: {
    dashboard: '대시보드',
    network: '네트워크',
    blocks: '블록',
    wallet: '지갑',
    soon: '곧 출시',
  },
  common: {
    refresh: '↻ 새로고침',
    refreshing: '새로고침 중…',
    copy: '복사',
    height: '높이',
    address: '주소',
    failures: '실패 횟수',
    cachedAgo: '{{seconds}}초 전 캐시',
    noData: '데이터 없음',
    searchAgain: '다시 검색',
    startBinarySearch: '이진 탐색 시작',
    searching: '검색 중… 몇 초가 걸릴 수 있습니다',
    selectTheme: '테마 선택',
    selectLanguage: '언어 선택',
  },
  status: {
    onChain: '동기화됨',
    stale: '오래됨',
    forking: '포크 중',
    blacklisted: '차단됨',
  },
  dashboard: {
    chainActivity: '체인 활동',
    network: '네트워크',
    nodeHealth: '노드 상태',
  },
  network: {
    chainConsensus: '체인 합의',
    peers: '피어',
    forkHistory: '포크 / 재편성 기록',
    blacklistRecommendations: '차단 목록 추천',
    agree: '{{on}} 동의 · {{stale}} 오래됨 · {{forking}} 포크 중',
    onOurChain: '우리 체인에서',
    noReorgs: '노드 시작 이후 재편성 없음',
    noRecommendations: '현재 추천 없음',
    noPeerData: '피어 데이터 없음 — 120초마다 갱신',
    blacklistHint: 'node.properties의 P2P.BlacklistedPeers에 주소를 추가하여 차단하세요.',
    peersOpen: '{{count}}개 피어 ▲',
    peersOpen_other: '{{count}}개 피어 ▲',
    peersClosed: '{{count}}개 피어 ▼',
    peersClosed_other: '{{count}}개 피어 ▼',
    forkPoint: {
      title: '포크 포인트 탐색',
      findFork: '포크 찾기',
      findForkFull: '포크 포인트 찾기',
      forkHeight: '포크 높이',
      forkBlockId: '포크 블록 ID',
      ourBlock: '포크 지점의 우리 블록',
      steps: '탐색 단계',
    },
    rollback: '-{{depth}} 블록',
    rollback_other: '-{{depth}} 블록',
  },
  info: {
    chainConsensus:
      '연결된 피어 중 누적 난이도가 일치하거나 5블록 이내인 비율. ≥90%는 네트워크가 정규 체인에 강하게 동의하고 있음을 나타냅니다.',
    cumulativeDifficulty:
      '창세 이후 체인에 투입된 총 proof-of-commitment 작업량. 누적 난이도가 가장 높은 체인이 항상 정규 체인으로 선택되어 역사 재작성에 극도로 높은 비용이 듭니다.',
    findFork:
      '현재 노드와 선택한 피어 사이에서 이진 탐색을 수행하여 체인이 분기된 정확한 블록 높이를 찾습니다. 포크 진단에 유용합니다.',
    networkCapacity:
      '현재 기본 목표값으로부터 산출한 모든 활성 채굴자의 추정 총 스토리지 커밋먼트(TiB/PiB). 용량이 클수록 네트워크 보안이 강화됩니다.',
    baseTarget:
      '평균 블록 시간을 4분으로 유지하기 위해 블록마다 조정됩니다. 값이 낮을수록 난이도가 높아 채굴 보상 획득이 어렵습니다.',
    peersTable:
      '"동기화됨" 피어는 우리와 정확히 동일한 누적 난이도를 가집니다. "오래됨" 피어는 5블록 이내. "포크 중" 피어는 상당히 다른 체인에 있습니다.',
    blacklistRecommendations:
      '지속적으로 다른 체인에 있거나 반복적인 연결 실패가 있는 피어. P2P.BlacklistedPeers에 추가하면 노드 리소스 낭비를 막을 수 있습니다.',
  },
}

export default ko
