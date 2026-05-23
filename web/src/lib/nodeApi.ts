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
  lastBlockReward: string
  requestProcessingTime: number
}

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
  query<BlockchainStatus>('getState')

export const getMiningInfo = () =>
  query<MiningInfo>('getMiningInfo')

export const getPeers = (active = true) =>
  query<PeerList>('getPeers', { active: String(active) })

export const getPeer = (peer: string) =>
  query<PeerDetail>('getPeer', { peer })

export const getUnconfirmedTransactions = () =>
  query<UnconfirmedTransactions>('getUnconfirmedTransactions')
