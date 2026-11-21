import { useQuery, useQueries, type UseQueryOptions } from '@tanstack/react-query'
import {
  getBlockchainStatus,
  getFullBlockchainStatus,
  getMiningInfo,
  getPeers,
  getPeer,
  getUnconfirmedTransactions,
  getRecentBlocks,
  getNetworkStatus,
  getForkHistory,
  findForkPoint,
  getBlacklist,
  getBlocksPage,
  getBlockAtHeight,
  getTransaction,
  getAccount,
  getAccountBlocks,
  getAccountTransactions,
  getConstants,
  getBlockPeerStatus,
  getAsset,
  getAccountContracts,
  getAccountAliases,
  getAccountSubscriptions,
  type AssetMeta,
  type Contract,
  type Subscription,
} from '@/lib/nodeApi'
import { useEffect, useRef } from 'react'
import { cachedExpiring, cachedImmutable, clientCache, networkKey } from '@/lib/clientCache'
import { computeValidUntil, consistentFetch, limitConcurrency, shouldRefetchOverdue, subscriptionTimesMs } from '@/components/account/account.utils'
import { anchoredRange, blocksFrom, pageRange, PAGE_SIZE } from '@/lib/blocks'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNodeSocket } from './useNodeSocket'

type NodeQueryOptions<TData, TResult = TData> = Omit<
  UseQueryOptions<TData, Error, TResult>,
  'refetchInterval'
> & { pollInterval?: number }

export function useNodeQuery<TData, TResult = TData>({
  pollInterval = 10_000,
  ...options
}: NodeQueryOptions<TData, TResult>) {
  const { connected } = useNodeSocket()
  return useQuery<TData, Error, TResult>({
    ...options,
    refetchInterval: connected ? false : pollInterval,
  })
}

export function useBlockchainStatus() {
  return useNodeQuery({
    queryKey: ['blockchainStatus'],
    queryFn: getBlockchainStatus,
    pollInterval: 10_000,
  })
}

export function useFullBlockchainStatus() {
  return useNodeQuery({
    queryKey: ['fullBlockchainStatus'],
    queryFn: getFullBlockchainStatus,
    pollInterval: 30_000,
  })
}

export function useMiningInfo() {
  return useNodeQuery({
    queryKey: ['miningInfo'],
    queryFn: getMiningInfo,
    pollInterval: 30_000,
  })
}

export function usePeers() {
  return useNodeQuery({
    queryKey: ['peers'],
    queryFn: () => getPeers(true),
    pollInterval: 30_000,
  })
}

export function usePeerDetails(addresses: string[]) {
  const { connected } = useNodeSocket()
  return useQueries({
    queries: addresses.slice(0, 25).map((addr) => ({
      queryKey: ['peer', addr],
      queryFn: () => getPeer(addr),
      staleTime: 30_000,
      refetchInterval: connected ? (false as const) : 30_000,
    })),
  })
}

export function useRecentBlocks() {
  return useNodeQuery({
    queryKey: ['recentBlocks'],
    queryFn: getRecentBlocks,
    pollInterval: 120_000,
  })
}

export function useUnconfirmedTxCount() {
  const { connected } = useNodeSocket()
  return useQuery({
    queryKey: ['unconfirmedTxCount'],
    queryFn: getUnconfirmedTransactions,
    select: (data) => data.unconfirmedTransactions.length,
    refetchInterval: connected ? false : 10_000,
  })
}

export function useNetworkStatus() {
  return useNodeQuery({
    queryKey: ['networkStatus'],
    queryFn: getNetworkStatus,
    pollInterval: 120_000,
  })
}

export function useForkHistory(limit = 50) {
  return useNodeQuery({
    queryKey: ['forkHistory', limit],
    queryFn: () => getForkHistory(limit),
    pollInterval: 60_000,
  })
}

export function useBlacklist() {
  return useNodeQuery({
    queryKey: ['blacklist'],
    queryFn: getBlacklist,
    pollInterval: 30_000,
  })
}

export function useFindForkPoint() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (peerAddress: string) => findForkPoint(peerAddress),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['networkStatus'] })
    },
  })
}

/**
 * A page of the block list. Without `from` it is the live page at the tip and follows new blocks;
 * with `from` it is anchored to that height, so blocks arriving meanwhile do not shift it.
 */
export function useBlocksPage(from: number | undefined, tipHeight: number | undefined) {
  const { connected } = useNodeSocket()
  const live = from === undefined
  return useQuery({
    queryKey: ['blocksPage', live ? 'live' : from],
    queryFn: async () => {
      if (live) return getBlocksPage(0, PAGE_SIZE - 1)
      const { firstIndex, lastIndex } = anchoredRange(from, tipHeight as number)
      const { blocks } = await getBlocksPage(firstIndex, lastIndex)
      return { blocks: blocksFrom(blocks, from) }
    },
    enabled: live || tipHeight !== undefined,
    staleTime: live ? 0 : Infinity,
    refetchInterval: live && !connected ? 30_000 : false,
  })
}

/** One block with its transactions; `null` height disables the query, unknown heights give `null`. */
export function useBlock(height: number | null) {
  return useQuery({
    queryKey: ['block', height],
    queryFn: () => getBlockAtHeight(height as number, true),
    enabled: height !== null,
    staleTime: 60_000,
  })
}

export function useTransaction(id: string) {
  return useQuery({
    queryKey: ['transaction', id],
    queryFn: () => getTransaction(id),
    // an unconfirmed transaction gets its block soon
    refetchInterval: (query) => (query.state.data && !query.state.data.block ? 30_000 : false),
  })
}

export function useAccount(id: string) {
  return useQuery({
    queryKey: ['account', id],
    queryFn: () => getAccount(id),
    staleTime: 60_000,
  })
}

export function useAccountBlocks(id: string, page: number) {
  const { firstIndex, lastIndex } = pageRange(page)
  return useQuery({
    queryKey: ['accountBlocks', id, page],
    queryFn: () => getAccountBlocks(id, firstIndex, lastIndex),
    staleTime: 60_000,
  })
}

export function useAccountTransactions(id: string, page: number) {
  const { firstIndex, lastIndex } = pageRange(page)
  return useQuery({
    queryKey: ['accountTransactions', id, page],
    queryFn: () => getAccountTransactions(id, firstIndex, lastIndex),
    staleTime: 60_000,
  })
}

export function useConstants() {
  return useQuery({
    queryKey: ['constants'],
    queryFn: getConstants,
    staleTime: Infinity,
  })
}

/** Asks the peers about one block on demand (a button), not on page load. */
export function useBlockPeerCheck() {
  return useMutation({ mutationFn: (blockId: string) => getBlockPeerStatus(blockId) })
}

/** The node's network, for cache keys; undefined until the constants are loaded. */
export function useNetworkGenesis(): string | undefined {
  return useConstants().data?.genesisBlockId
}

const assetLimit = limitConcurrency(4)
const pickAsset = ({ asset, name, description, decimals, issuer, issuerRS, mintable }: AssetMeta): AssetMeta =>
  ({ asset, name, description, decimals, issuer, issuerRS, mintable })

/** Token metadata, fetched once per browser and network, at most 4 requests at a time. */
export function useAssetMetas(assetIds: string[], enabled: boolean) {
  const genesis = useNetworkGenesis()
  return useQueries({
    queries: assetIds.map((id) => ({
      queryKey: ['assetMeta', genesis, id],
      queryFn: () => cachedImmutable(clientCache, 'assets', networkKey(genesis as string, id), () => assetLimit(() => getAsset(id)), pickAsset),
      enabled: enabled && genesis !== undefined,
      staleTime: Infinity,
      retry: false,
    })),
  })
}

const CONTRACT_PAGE = 10
const pickContract = ({ at, atRS, name, description, creationBlock, machineCodeHashId, atVersion }: Contract) =>
  ({ at, atRS, name, description, creationBlock, machineCodeHashId, atVersion })

/** Contracts the account deployed; status and balance change, so the list is always fetched. */
export function useAccountContracts(account: string, page: number, enabled: boolean) {
  const genesis = useNetworkGenesis()
  return useQuery({
    queryKey: ['accountContracts', account, page],
    queryFn: async () => {
      const result = await getAccountContracts(account, page * CONTRACT_PAGE, page * CONTRACT_PAGE + CONTRACT_PAGE - 1)
      if (genesis) {
        await Promise.all(result.ats.map((c) => clientCache.putImmutable('contracts', networkKey(genesis, c.at), pickContract(c))))
      }
      return result
    },
    enabled,
    staleTime: 5 * 60_000,
  })
}

const subscriptionsValidUntil = (subs: Subscription[], storedAt: number) =>
  computeValidUntil(storedAt, subscriptionTimesMs(subs))

export function subscriptionsCacheKey(genesis: string, account: string): string {
  return networkKey(genesis, 'subscriptions', account)
}

export function aliasesCacheKey(genesis: string, account: string, page: number): string {
  return networkKey(genesis, 'aliases', account, page)
}

/** Both directions of the account's subscriptions, cached until the earliest due date. */
export function useAccountSubscriptionsCached(account: string, enabled: boolean) {
  const genesis = useNetworkGenesis()
  return useQuery({
    queryKey: ['accountSubscriptions', genesis, account],
    queryFn: () => cachedExpiring(clientCache, subscriptionsCacheKey(genesis as string, account),
      async () => (await getAccountSubscriptions(account)).subscriptions,
      subscriptionsValidUntil),
    enabled: enabled && genesis !== undefined,
    staleTime: 60_000,
  })
}

const ALIAS_PAGE = 25

/** A page of aliases with the subscriptions that renew them, as one cache entry of one age. */
export function useAccountAliasesCached(account: string, page: number, enabled: boolean) {
  const genesis = useNetworkGenesis()
  return useQuery({
    queryKey: ['accountAliases', genesis, account, page],
    queryFn: () => cachedExpiring(clientCache, aliasesCacheKey(genesis as string, account, page),
      // both from the same block: a renewal settled in between would pair an alias with a list
      // that no longer has its subscription ("does not expire")
      () => consistentFetch(async () => (await getBlockchainStatus()).lastBlock, async () => {
        const [aliases, subscriptions] = await Promise.all([
          getAccountAliases(account, page * ALIAS_PAGE, page * ALIAS_PAGE + ALIAS_PAGE - 1),
          getAccountSubscriptions(account),
        ])
        return { aliases: aliases.aliases, nextIndex: aliases.nextIndex, subscriptions: subscriptions.subscriptions }
      }),
      (data, storedAt) => subscriptionsValidUntil(data.subscriptions, storedAt)),
    enabled: enabled && genesis !== undefined,
    staleTime: 60_000,
  })
}

/**
 * While an overdue entry is shown, the next block settles it: forget the entry once and fetch again.
 * Follows the last block of the node status, which advances over the WebSocket and when polling.
 */
export function useRefetchWhenOverdue(hasOverdue: boolean, forgetKey: string | undefined, refetch: () => void) {
  const lastBlock = useBlockchainStatus().data?.lastBlock
  const seenBlock = useRef(lastBlock)
  useEffect(() => {
    const previous = seenBlock.current
    seenBlock.current = lastBlock
    if (!forgetKey || !shouldRefetchOverdue(previous, lastBlock, hasOverdue)) return
    void clientCache.forget(forgetKey).then(refetch)
  }, [lastBlock, hasOverdue, forgetKey, refetch])
}
