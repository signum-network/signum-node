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
} from '@/lib/nodeApi'
import { pageRange } from '@/components/blocks/blocks.utils'
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

/** A page of the block list; page 0 (newest) follows new blocks, older pages stay as loaded. */
export function useBlocksPage(page: number) {
  const { connected } = useNodeSocket()
  const { firstIndex, lastIndex } = pageRange(page)
  return useQuery({
    queryKey: ['blocksPage', page],
    queryFn: () => getBlocksPage(firstIndex, lastIndex),
    staleTime: page === 0 ? 0 : Infinity,
    refetchInterval: page === 0 && !connected ? 30_000 : false,
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
