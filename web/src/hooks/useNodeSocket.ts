import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { UnconfirmedTransactions } from '@/lib/nodeApi'

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

const MAX_RETRIES = 3
const BASE_DELAY_MS = 1_000
const MAX_DELAY_MS = 30_000

// Server sends { e: eventName, p: payload }
type WsFrame<T = unknown> = { e: string; p: T }

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
  const wsRef = useRef<WebSocket | null>(null)
  const retriesRef = useRef(0)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const connect = useCallback(() => {
    if (retriesRef.current >= MAX_RETRIES) {
      setWsEnabled(false)
      return
    }

    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
    const ws = new WebSocket(`${proto}//${location.host}/events`)
    wsRef.current = ws

    ws.onopen = () => {
      setConnected(true)
      retriesRef.current = 0
    }

    ws.onmessage = (ev: MessageEvent<string>) => {
      try {
        const frame = JSON.parse(ev.data) as WsFrame

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
      } catch {
        // ignore malformed frames
      }
    }

    ws.onerror = () => ws.close()

    ws.onclose = () => {
      setConnected(false)
      if (retriesRef.current < MAX_RETRIES) {
        const delay = Math.min(BASE_DELAY_MS * Math.pow(2, retriesRef.current), MAX_DELAY_MS)
        retriesRef.current++
        timerRef.current = setTimeout(connect, delay)
      } else {
        setWsEnabled(false)
      }
    }
  }, [queryClient])

  useEffect(() => {
    connect()
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current)
      wsRef.current?.close()
    }
  }, [connect])

  return { connected, wsEnabled, latestBlock }
}

export function useNodeSocket(): SocketContext {
  return useContext(NodeSocketContext)
}
