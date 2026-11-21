import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { queryEntity, NodeApiError, getBlockAtHeight, getNodeHealth } from './nodeApi'

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

  it('queryEntity returns null for a malformed ID (errorCode 4)', async () => {
    // "Incorrect account" for "xyz": for the user that is just not found
    respond({ errorCode: 4, errorDescription: 'Incorrect account' })
    await expect(queryEntity('getAccount', { account: 'xyz' })).resolves.toBeNull()
  })

  it('queryEntity throws NodeApiError for other error codes', async () => {
    respond({ errorCode: 3, errorDescription: '"account" not specified' })
    const error = await queryEntity('getAccount', { account: '' }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(NodeApiError)
    expect((error as NodeApiError).code).toBe(3)
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

  it('getNodeHealth rejects an error body instead of returning it as data', async () => {
    // the node answers errors with HTTP 200; as data they would show NaN and a false "P2P server off"
    respond({ errorCode: 1, errorDescription: 'Incorrect request' })
    await expect(getNodeHealth()).rejects.toBeInstanceOf(NodeApiError)
  })
})
