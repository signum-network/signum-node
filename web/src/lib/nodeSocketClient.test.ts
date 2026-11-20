import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { NodeSocketClient, type SocketLike } from './nodeSocketClient'

class FakeSocket implements SocketLike {
  onopen: SocketLike['onopen'] = null
  onmessage: SocketLike['onmessage'] = null
  onerror: SocketLike['onerror'] = null
  onclose: SocketLike['onclose'] = null
  closed = false
  readyState = 0 // CONNECTING
  constructor(readonly url: string) {}
  close() {
    this.closed = true
  }
  // simulate the browser: close events arrive asynchronously, after close() returned
  fireOpen() { this.readyState = 1; this.onopen?.({} as Event) }
  fireRaw(data: string) { this.onmessage?.({ data } as MessageEvent<string>) }
  fireMessage(data: unknown) { this.fireRaw(JSON.stringify(data)) }
  fireClose() { this.onclose?.({} as CloseEvent) }
}

function setup() {
  const sockets: FakeSocket[] = []
  const onConnectedChange = vi.fn()
  const onFrame = vi.fn()
  const onGiveUp = vi.fn()
  const client = new NodeSocketClient({
    url: 'ws://node/events',
    createSocket: (url) => {
      const s = new FakeSocket(url)
      sockets.push(s)
      return s
    },
    onConnectedChange,
    onFrame,
    onGiveUp,
  })
  return { client, sockets, onConnectedChange, onFrame, onGiveUp }
}

describe('NodeSocketClient', () => {
  beforeEach(() => { vi.useFakeTimers() })
  afterEach(() => { vi.useRealTimers() })

  it('opens one socket on start', () => {
    const { client, sockets } = setup()
    client.start()
    expect(sockets).toHaveLength(1)
    expect(sockets[0].url).toBe('ws://node/events')
  })

  it('does not reconnect when the socket closes after stop', () => {
    // React StrictMode mounts, cleans up and mounts again; the first socket's close event
    // arrives after the cleanup and must not open a socket nobody owns
    const { client, sockets, onConnectedChange } = setup()
    client.start()
    sockets[0].fireOpen()
    onConnectedChange.mockClear()
    client.stop()
    sockets[0].fireClose()
    vi.runAllTimers()
    expect(sockets).toHaveLength(1)
    expect(sockets[0].closed).toBe(true)
    expect(onConnectedChange).not.toHaveBeenCalled()
  })

  it('closes a still-connecting socket only once it opens', () => {
    // closing a CONNECTING socket makes the browser log "closed before the connection is established"
    const { client, sockets, onConnectedChange } = setup()
    client.start()
    client.stop()
    expect(sockets[0].closed).toBe(false)
    sockets[0].fireOpen()
    expect(sockets[0].closed).toBe(true)
    expect(onConnectedChange).not.toHaveBeenCalled()
  })

  it('closes an open socket right away on stop', () => {
    const { client, sockets } = setup()
    client.start()
    sockets[0].fireOpen()
    client.stop()
    expect(sockets[0].closed).toBe(true)
  })

  it('cancels a pending reconnect on stop', () => {
    const { client, sockets } = setup()
    client.start()
    sockets[0].fireClose()
    client.stop()
    vi.runAllTimers()
    expect(sockets).toHaveLength(1)
  })

  it('reports connected on open and disconnected on close', () => {
    const { client, sockets, onConnectedChange } = setup()
    client.start()
    sockets[0].fireOpen()
    sockets[0].fireClose()
    expect(onConnectedChange.mock.calls).toEqual([[true], [false]])
  })

  it('passes parsed frames on and ignores malformed ones', () => {
    const { client, sockets, onFrame } = setup()
    client.start()
    sockets[0].fireMessage({ e: 'BLOCK_PUSHED', p: { blockId: '1' } })
    sockets[0].fireRaw('not json')
    expect(onFrame.mock.calls).toEqual([[{ e: 'BLOCK_PUSHED', p: { blockId: '1' } }]])
  })

  it('reconnects with backoff and gives up after three failed attempts', () => {
    const { client, sockets, onGiveUp } = setup()
    client.start()
    sockets[0].fireClose()
    vi.advanceTimersByTime(999)
    expect(sockets).toHaveLength(1)
    vi.advanceTimersByTime(1)
    expect(sockets).toHaveLength(2)
    sockets[1].fireClose()
    vi.advanceTimersByTime(2_000)
    expect(sockets).toHaveLength(3)
    sockets[2].fireClose()
    vi.advanceTimersByTime(4_000)
    expect(sockets).toHaveLength(4)
    sockets[3].fireClose()
    vi.runAllTimers()
    expect(sockets).toHaveLength(4)
    expect(onGiveUp).toHaveBeenCalledTimes(1)
  })

  it('resets the retry budget after a successful open', () => {
    const { client, sockets, onGiveUp } = setup()
    client.start()
    sockets[0].fireClose()
    vi.advanceTimersByTime(1_000)
    sockets[1].fireOpen()
    sockets[1].fireClose()
    vi.advanceTimersByTime(1_000)
    expect(sockets).toHaveLength(3)
    expect(onGiveUp).not.toHaveBeenCalled()
  })
})
