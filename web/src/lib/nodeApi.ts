async function query<T>(
  requestType: string,
  params: Record<string, string> = {},
): Promise<T> {
  const url = new URL('/api', window.location.origin)
  url.searchParams.set('requestType', requestType)
  for (const [k, v] of Object.entries(params)) url.searchParams.set(k, v)
  const res = await fetch(url)
  if (!res.ok) throw new Error(`Node API error ${res.status}: ${requestType}`)
  return res.json() as Promise<T>
}

/** An API answer with an `errorCode` other than "unknown entity". */
export class NodeApiError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'NodeApiError'
    this.code = code
  }
}

const INCORRECT_PARAMETER = 4
const UNKNOWN_ENTITY = 5

/**
 * Like `query`, for requests that look up one block, transaction or account: the node answers an
 * unknown ID (`errorCode 5`) or a malformed one (`errorCode 4`) with HTTP 200; both become `null`,
 * since either way there is nothing to show. Other error codes throw.
 */
export async function queryEntity<T>(requestType: string, params: Record<string, string>): Promise<T | null> {
  const body = await query<T & { errorCode?: number; errorDescription?: string }>(requestType, params)
  if (body.errorCode === undefined) return body
  if (body.errorCode === UNKNOWN_ENTITY || body.errorCode === INCORRECT_PARAMETER) return null
  throw new NodeApiError(body.errorCode, body.errorDescription ?? `Node API error ${body.errorCode}: ${requestType}`)
}

export interface BlockchainStatus {
  application: string
  version: string
  time: number
  lastBlock: string
  cumulativeDifficulty: string
  numberOfBlocks: number
  lastBlockchainFeeder: string
  lastBlockchainFeederHeight: number
  isScanning: boolean
  network?: string
  requestProcessingTime: number
}

export interface MiningInfo {
  generationSignature: string
  baseTarget: string
  height: string
  targetDeadline: number
  averageCommitmentNQT: string
  lastBlockReward: string  // SIGNA integer, already divided by ONE_COIN_NQT
  timestamp: number        // Signum epoch seconds of the last block
  requestProcessingTime: number
}

export interface BlockSummary {
  block: string
  height: number
  timestamp: number         // Signum epoch seconds
  blockReward: string       // SIGNA integer
  totalFeeNQT: string       // NQT
  baseTarget: string
  numberOfTransactions: number
}

export interface TransactionDetail {
  transaction: string
  type: number
  subtype: number
  timestamp: number          // Signum epoch seconds
  sender: string
  senderRS: string
  recipient?: string
  recipientRS?: string
  amountNQT: string
  feeNQT: string
  block?: string             // absent while unconfirmed
  height?: number
  confirmations?: number
  attachment?: Record<string, unknown>
}

export interface BlockDetail {
  block: string
  height: number
  generator: string
  generatorRS: string
  timestamp: number          // Signum epoch seconds
  numberOfTransactions: number
  totalAmountNQT: string
  totalFeeNQT: string
  blockRewardNQT: string
  payloadLength: number
  baseTarget: string
  averageCommitmentNQT: string
  cumulativeDifficulty: string
  previousBlock?: string     // absent for the genesis block
  nextBlock?: string         // absent for the tip
  generationSignature: string
  transactions?: TransactionDetail[]
}

export interface AccountDetail {
  account: string
  accountRS: string
  name?: string
  balanceNQT: string
  unconfirmedBalanceNQT: string
  guaranteedBalanceNQT: string
  committedBalanceNQT?: string
  isAT: boolean
}

export interface AccountBlocksResponse {
  blocks: BlockDetail[]
  nextIndex?: number
}

export interface AccountTransactionsResponse {
  transactions: TransactionDetail[]
  nextIndex?: number
}

export interface Constants {
  addressPrefix: string
  transactionTypes: {
    value: number
    description: string
    subtypes: { value: number; description: string }[]
  }[]
}

export interface BlocksResponse {
  blocks: BlockSummary[]
}

// Signum genesis: 2014-08-11 02:00:00 UTC
export const SIGNUM_EPOCH_MS = Date.UTC(2014, 7, 11, 2, 0, 0, 0)

export const signumTimestampToMs = (epochSecs: number): number =>
  epochSecs * 1000 + SIGNUM_EPOCH_MS

export interface PeerList {
  peers: string[]
  requestProcessingTime: number
}

export interface PeerDetail {
  address: string
  state: number
  announcedAddress: string
  application: string
  version: string
  platform: string
  shareAddress: boolean
  downloadedVolume: number
  uploadedVolume: number
  requestProcessingTime: number
}

export interface UnconfirmedTransactions {
  unconfirmedTransactions: unknown[]
  requestProcessingTime: number
}

export interface FullBlockchainStatus {
  "application": string,
  "version": string,
  "time": number,
  "lastBlock": string,
  "cumulativeDifficulty": string,
  "totalMinedNQT": number,
  "totalBurntNQT": number,
  "circulatingSupplyNQT": number,
  "numberOfBlocks": number,
  "numberOfAccounts": number,
  "numberOfTransactions": number,
  "numberOfATs": number,
  "numberOfAssets": number,
  "numberOfOrders": number,
  "numberOfAskOrders": number,
  "numberOfBidOrders": number,
  "numberOfTrades": number,
  "numberOfTransfers": number,
  "numberOfAliases": number,
  "numberOfSubscriptions": number,
  "numberOfSubscriptionPayments": number,
  "numberOfPeers": number,
  "numberOfUnlockedAccounts": number,
  "lastBlockchainFeeder": string
  "lastBlockchainFeederHeight": number,
  "isScanning": boolean,
  "network"?: string,
  "availableProcessors": number,
  "maxMemory": number,
  "totalMemory": number,
  "freeMemory": number,
  "indirectIncomingServiceEnabled": boolean,
  "databaseTrimmingEnabled": boolean,
  "requestProcessingTime": number
}

export const getBlockchainStatus = () =>
  query<BlockchainStatus>('getBlockchainStatus')

export const getFullBlockchainStatus = () =>
  query<FullBlockchainStatus>('getState')

export const getMiningInfo = () =>
  query<MiningInfo>('getMiningInfo')

export const getPeers = (active = true) =>
  query<PeerList>('getPeers', { active: String(active) })

export const getPeer = (peer: string) =>
  query<PeerDetail>('getPeer', { peer })

export const getUnconfirmedTransactions = () =>
  query<UnconfirmedTransactions>('getUnconfirmedTransactions')

export const getRecentBlocks = () =>
  query<BlocksResponse>('getBlocks', { firstIndex: '0', lastIndex: '99' })

export const getBlocksPage = (firstIndex: number, lastIndex: number) =>
  query<{ blocks: BlockDetail[] }>('getBlocks', { firstIndex: String(firstIndex), lastIndex: String(lastIndex) })

export const getBlockAtHeight = (height: number, includeTransactions: boolean) =>
  queryEntity<BlockDetail>('getBlock', { height: String(height), includeTransactions: String(includeTransactions) })

export const getBlockById = (id: string) =>
  queryEntity<BlockDetail>('getBlock', { block: id })

export const getTransaction = (id: string) =>
  queryEntity<TransactionDetail>('getTransaction', { transaction: id })

export const getAccount = (id: string) =>
  queryEntity<AccountDetail>('getAccount', { account: id })

export const getAccountBlocks = (id: string, firstIndex: number, lastIndex: number) =>
  query<AccountBlocksResponse>('getAccountBlocks', {
    account: id, firstIndex: String(firstIndex), lastIndex: String(lastIndex),
  })

export const getAccountTransactions = (id: string, firstIndex: number, lastIndex: number) =>
  query<AccountTransactionsResponse>('getAccountTransactions', {
    account: id, firstIndex: String(firstIndex), lastIndex: String(lastIndex),
  })

export const getConstants = () =>
  query<Constants>('getConstants')

export type PeerChainStatus = 'on-chain' | 'stale' | 'ahead' | 'forking' | 'unknown'

export interface PeerStatusEntry {
  address: string
  cumulativeDifficulty: string
  height: number
  onOurChain: boolean
  status: PeerChainStatus
  /** round trip of the block check in ms; null if the peer did not answer */
  latencyMs: number | null
  /** when the block check answered (epoch ms); null if the peer did not answer */
  checkedAt: number | null
  connectionFailures: number
  blacklisted: boolean
}

export interface NetworkStatus {
  myBlockId: string
  myCumulativeDifficulty: string
  myHeight: number
  consensusPercent: number
  totalPeers: number
  onChainPeers: number
  stalePeers: number
  aheadPeers: number
  forkingPeers: number
  unknownPeers: number
  cachedAt: number
  /** the node's clock when it answered (epoch ms); base for ages, independent of the browser clock */
  serverTime: number
  peers: PeerStatusEntry[]
}

/** One rollback recorded by the node. */
export interface ForkEvent {
  detectedAt: number
  /** Height of the common block that stayed; the chain was cut above it. */
  rollbackHeight: number
  /** Number of blocks popped off. */
  rollbackDepth: number
  /** Tip before the rollback. */
  oldTopBlockId: string
  /** Tip after a fork switch; null for a pop-off. */
  newTopBlockId: string | null
  /** Peer whose fork we switched to; null for a pop-off. */
  peerSource: string | null
}

export interface ForkHistoryResponse {
  forks: ForkEvent[]
}

export interface ForkPoint {
  peer: string
  forkAtHeight?: number
  forkAtBlockId?: string
  ourBlockIdAtFork?: string
  searchSteps?: number
  error?: string
  forkTooOld?: boolean
  requestProcessingTime: number
}

export interface BlacklistEntry {
  address: string
  reason: string
  connectionFailures: number
}

export interface BlacklistResponse {
  blacklisted: BlacklistEntry[]
  count: number
}

export const getNetworkStatus = () =>
  query<NetworkStatus>('getNetworkStatus')

export const getForkHistory = (limit = 50) =>
  query<ForkHistoryResponse>('getForkHistory', { limit: String(limit) })

export const findForkPoint = (peer: string) =>
  query<ForkPoint>('findForkPoint', { peer })

export const getBlacklist = () =>
  query<BlacklistResponse>('getBlacklist')
