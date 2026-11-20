/** The part of the browser WebSocket the client uses, so tests can pass a fake. */
export interface SocketLike {
  onopen: ((ev: Event) => unknown) | null
  onmessage: ((ev: MessageEvent<string>) => unknown) | null
  onerror: ((ev: Event) => unknown) | null
  onclose: ((ev: CloseEvent) => unknown) | null
  readonly readyState: number
  close(): void
}

const CONNECTING = 0

/** The node sends `{ e: eventName, p: payload }`. */
export type WsFrame<T = unknown> = { e: string; p: T }

export interface NodeSocketClientOptions {
  url: string
  createSocket: (url: string) => SocketLike
  onConnectedChange: (connected: boolean) => void
  onFrame: (frame: WsFrame) => void
  /** Called once when the retries are used up; the caller falls back to polling. */
  onGiveUp: () => void
}

const MAX_RETRIES = 3
const BASE_DELAY_MS = 1_000
const MAX_DELAY_MS = 30_000

/**
 * One connection to the node's `/events` socket, reconnecting with backoff. After `stop()` it is
 * inert: events of the socket it closed are ignored, so a late `close` cannot open a new socket
 * (React StrictMode stops and restarts effects in development). A socket that is still connecting
 * is closed once it opens.
 */
export class NodeSocketClient {
  private socket: SocketLike | null = null
  private timer: ReturnType<typeof setTimeout> | null = null
  private retries = 0
  private stopped = false

  constructor(private readonly options: NodeSocketClientOptions) {}

  start(): void {
    this.stopped = false
    this.connect()
  }

  stop(): void {
    this.stopped = true
    if (this.timer) clearTimeout(this.timer)
    this.timer = null
    const socket = this.socket
    this.socket = null
    if (!socket) return
    socket.onmessage = socket.onerror = socket.onclose = null
    if (socket.readyState === CONNECTING) {
      // closing now makes the browser log "closed before the connection is established"
      socket.onopen = () => socket.close()
    } else {
      socket.onopen = null
      socket.close()
    }
  }

  private connect(): void {
    const socket = this.options.createSocket(this.options.url)
    this.socket = socket

    socket.onopen = () => {
      this.retries = 0
      this.options.onConnectedChange(true)
    }
    socket.onmessage = (ev) => {
      let frame: WsFrame
      try {
        frame = JSON.parse(ev.data) as WsFrame
      } catch {
        return // ignore malformed frames
      }
      this.options.onFrame(frame)
    }
    socket.onerror = () => socket.close()
    socket.onclose = () => this.handleClose(socket)
  }

  private handleClose(socket: SocketLike): void {
    if (this.stopped || socket !== this.socket) return
    this.socket = null
    this.options.onConnectedChange(false)
    if (this.retries >= MAX_RETRIES) {
      this.options.onGiveUp()
      return
    }
    const delay = Math.min(BASE_DELAY_MS * 2 ** this.retries, MAX_DELAY_MS)
    this.retries++
    this.timer = setTimeout(() => {
      this.timer = null
      this.connect()
    }, delay)
  }
}
