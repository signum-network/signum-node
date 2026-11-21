import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { queryEntity, NodeApiError, getBlockAtHeight } from './nodeApi'

function respond(body: unknown) {
  const fetchMock = vi.fn(async (_input: RequestInfo | URL) => new Response(JSON.stringify(body), { status: 200 }))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('nodeApi', () => {
  beforeEach(() => {
    vi.stubGlobal('window', { location: { origin: 'http://node' } })
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('queryEntity returns null for errorCode 5', async () => {
    respond({ errorCode: 5, errorDescription: 'Unknown block' })
    await expect(queryEntity('getBlock', { block: '1' })).resolves.toBeNull()
  })

  it('queryEntity throws NodeApiError for other error codes', async () => {
    respond({ errorCode: 4, errorDescription: 'Incorrect block' })
    const error = await queryEntity('getBlock', { block: 'x' }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(NodeApiError)
    expect((error as NodeApiError).code).toBe(4)
  })

  it('queryEntity returns the body otherwise', async () => {
    respond({ block: '1', height: 5 })
    await expect(queryEntity('getBlock', { block: '1' })).resolves.toEqual({ block: '1', height: 5 })
  })

  it('getBlockAtHeight sends height and includeTransactions', async () => {
    const fetchMock = respond({ block: '1', height: 5 })
    await getBlockAtHeight(5, true)
    const url = new URL(String(fetchMock.mock.calls[0][0]))
    expect(url.searchParams.get('requestType')).toBe('getBlock')
    expect(url.searchParams.get('height')).toBe('5')
    expect(url.searchParams.get('includeTransactions')).toBe('true')
  })
})
