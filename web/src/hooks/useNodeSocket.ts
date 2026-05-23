import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react'
import { useQueryClient } from '@tanstack/react-query'

interface SocketContext {
  connected: boolean
}

export const NodeSocketContext = createContext<SocketContext>({ connected: false })

const MAX_RETRIES = 3
const BASE_DELAY_MS = 1_000
const MAX_DELAY_MS = 30_000

type WsMessage = { event: string }

export function useNodeSocketProvider() {
  const queryClient = useQueryClient()
  const [connected, setConnected] = useState(false)
  const wsRef = useRef<WebSocket | null>(null)
  const retriesRef = useRef(0)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const connect = useCallback(() => {
    if (retriesRef.current >= MAX_RETRIES) return

    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
    const ws = new WebSocket(`${proto}//${location.host}/events`)
    wsRef.current = ws

    ws.onopen = () => {
      setConnected(true)
      retriesRef.current = 0
    }

    ws.onmessage = (ev: MessageEvent<string>) => {
      try {
        const msg = JSON.parse(ev.data) as WsMessage
        if (msg.event === 'BLOCK_PUSHED') {
          queryClient.invalidateQueries({ queryKey: ['blockchainStatus'] })
        } else if (msg.event === 'PENDING_TXS_ADDED') {
          queryClient.invalidateQueries({ queryKey: ['unconfirmedTxCount'] })
        }
      } catch {
        // ignore malformed messages
      }
    }

    ws.onerror = () => ws.close()

    ws.onclose = () => {
      setConnected(false)
      if (retriesRef.current < MAX_RETRIES) {
        const delay = Math.min(
          BASE_DELAY_MS * Math.pow(2, retriesRef.current),
          MAX_DELAY_MS,
        )
        retriesRef.current++
        timerRef.current = setTimeout(connect, delay)
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

  return { connected }
}

export function useNodeSocket(): SocketContext {
  return useContext(NodeSocketContext)
}
