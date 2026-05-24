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
} from '@/lib/nodeApi'
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
