import { useQuery, useQueries, type UseQueryOptions } from '@tanstack/react-query'
import {
  getBlockchainStatus,
  getMiningInfo,
  getPeers,
  getPeer,
  getUnconfirmedTransactions,
} from '../lib/nodeApi'
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

export function useUnconfirmedTxCount() {
  const { connected } = useNodeSocket()
  return useQuery({
    queryKey: ['unconfirmedTxCount'],
    queryFn: getUnconfirmedTransactions,
    select: (data) => data.unconfirmedTransactions.length,
    refetchInterval: connected ? false : 10_000,
  })
}
