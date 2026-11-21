import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { UnconfirmedTransactions } from '@/lib/nodeApi'
import { NodeSocketClient, type WsFrame } from '@/lib/nodeSocketClient'

export interface BlockPushEvent {
  blockId: string
  localHeight: number
  globalHeight: number
  progress: number
  transactionCount: number
  timestamp: number
}

interface SocketContext {
  connected: boolean
  wsEnabled: boolean
  latestBlock: BlockPushEvent | null
}

export const NodeSocketContext = createContext<SocketContext>({
  connected: false,
  wsEnabled: true,
  latestBlock: null,
})

interface ConnectedPayload {
  version: string
  networkName: string
  globalHeight: number
  localHeight: number
}

interface PendingTxPayload {
  transactionIds: string[]
}

export function useNodeSocketProvider() {
  const queryClient = useQueryClient()
  const [connected, setConnected] = useState(false)
  const [wsEnabled, setWsEnabled] = useState(true)
  const [latestBlock, setLatestBlock] = useState<BlockPushEvent | null>(null)
  const handleFrame = useCallback((frame: WsFrame) => {
    if (frame.e === 'CONNECTED') {
      const p = frame.p as ConnectedPayload
      setLatestBlock({
        blockId: '',
        localHeight: p.localHeight,
        globalHeight: p.globalHeight,
        progress: p.globalHeight > 0 ? Math.min(1, p.localHeight / p.globalHeight) : 1,
        transactionCount: 0,
        timestamp: 0,
      })
    } else if (frame.e === 'BLOCK_PUSHED') {
      const p = frame.p as BlockPushEvent
      setLatestBlock({ ...p, progress: Math.min(1, p.progress) })
      queryClient.invalidateQueries({ queryKey: ['fullBlockchainStatus'] })
      queryClient.invalidateQueries({ queryKey: ['miningInfo'] })
      queryClient.invalidateQueries({ queryKey: ['recentBlocks'] })
      // only the newest page of the block list follows new blocks; older pages stay where the user is
      queryClient.invalidateQueries({ queryKey: ['blocksPage', 'live'], exact: true })
      // Reset pending count — block likely consumed some txs
      queryClient.invalidateQueries({ queryKey: ['unconfirmedTxCount'] })
    } else if (frame.e === 'PENDING_TRANSACTIONS_ADDED') {
      const p = frame.p as PendingTxPayload
      // Optimistic cache update so count increments immediately without a round trip
      queryClient.setQueryData<UnconfirmedTransactions>(['unconfirmedTxCount'], (old) => {
        if (!old) return old
        const fakeEntries = p.transactionIds.map(() => ({}))
        return { ...old, unconfirmedTransactions: [...old.unconfirmedTransactions, ...fakeEntries] }
      })
      queryClient.invalidateQueries({ queryKey: ['unconfirmedTxCount'] })
    }
  }, [queryClient])

  useEffect(() => {
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
    const client = new NodeSocketClient({
      url: `${proto}//${location.host}/events`,
      createSocket: (url) => new WebSocket(url),
      onConnectedChange: setConnected,
      onFrame: handleFrame,
      onGiveUp: () => setWsEnabled(false),
    })
    client.start()
    return () => client.stop()
  }, [handleFrame])

  return { connected, wsEnabled, latestBlock }
}

export function useNodeSocket(): SocketContext {
  return useContext(NodeSocketContext)
}
