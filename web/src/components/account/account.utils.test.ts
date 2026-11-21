import { describe, it, expect } from 'vitest'
import type { Subscription } from '@/lib/nodeApi'
import {
  computeValidUntil,
  splitSubscriptions,
  renewalFor,
  isOverdue,
  frequencyParts,
  subscriptionTimesMs,
  limitConcurrency,
  parseAccountTab,
} from './account.utils'
import { signumTimestampToMs } from '@/lib/nodeApi'

function sub(overrides: Partial<Subscription>): Subscription {
  return {
    id: '1', sender: '1', senderRS: 'S-A', recipient: '2', recipientRS: 'S-B',
    amountNQT: '100', frequency: 3600, timeNext: 0, ...overrides,
  }
}

describe('computeValidUntil', () => {
  it('keeps an entry 5 minutes without due dates', () => {
    expect(computeValidUntil(0, [])).toBe(300_000)
  })

  it('ends at the earliest due date', () => {
    expect(computeValidUntil(0, [120_000])).toBe(120_000)
    expect(computeValidUntil(0, [200_000, 90_000])).toBe(90_000)
  })

  it('keeps at least 60 seconds, also for an entry already overdue', () => {
    expect(computeValidUntil(0, [30_000])).toBe(60_000)
    expect(computeValidUntil(0, [-5_000])).toBe(60_000)
  })

  it('never keeps longer than 5 minutes', () => {
    expect(computeValidUntil(0, [900_000])).toBe(300_000)
  })
})

describe('subscriptions', () => {
  it('splits by direction', () => {
    const sent = sub({ id: 'a', sender: '1', recipient: '2' })
    const received = sub({ id: 'b', sender: '3', recipient: '1' })
    expect(splitSubscriptions([sent, received], '1')).toEqual({ outgoing: [sent], incoming: [received] })
  })

  it('lists a subscription to oneself in both directions', () => {
    const self = sub({ id: 's', sender: '1', recipient: '1' })
    expect(splitSubscriptions([self], '1')).toEqual({ outgoing: [self], incoming: [self] })
  })

  it('finds the subscription that renews an alias', () => {
    const renewal = sub({ id: 'r', alias: '42' })
    expect(renewalFor('42', [sub({}), renewal])).toBe(renewal)
    expect(renewalFor('43', [renewal])).toBeUndefined()
  })

  it('is overdue from its due time on', () => {
    expect(isOverdue(100, 100)).toBe(true)
    expect(isOverdue(101, 100)).toBe(false)
  })
})

describe('frequencyParts', () => {
  it('uses the largest unit that fits evenly', () => {
    expect(frequencyParts(3_600)).toEqual({ unit: 'hour', count: 1 })
    expect(frequencyParts(86_400 * 7)).toEqual({ unit: 'week', count: 1 })
    expect(frequencyParts(86_400 * 3)).toEqual({ unit: 'day', count: 3 })
    expect(frequencyParts(5_400)).toEqual({ unit: 'minute', count: 90 })
  })
})

describe('subscriptionTimesMs', () => {
  it('turns the due dates into epoch milliseconds', () => {
    expect(subscriptionTimesMs([sub({ timeNext: 10 }), sub({ timeNext: 20 })]))
      .toEqual([signumTimestampToMs(10), signumTimestampToMs(20)])
  })
})

describe('limitConcurrency', () => {
  function deferred<T>() {
    let resolve!: (v: T) => void
    let reject!: (e: unknown) => void
    const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej })
    return { promise, resolve, reject }
  }

  it('runs at most the given number of tasks at once, each with its own result', async () => {
    const limit = limitConcurrency(2)
    const gates = Array.from({ length: 5 }, () => deferred<number>())
    let running = 0
    let maxRunning = 0
    const results = gates.map((gate, i) => limit(async () => {
      running++
      maxRunning = Math.max(maxRunning, running)
      try {
        await gate.promise
        return i
      } finally {
        running--
      }
    }))
    for (const gate of gates) {
      await Promise.resolve()
      gate.resolve(0)
      await new Promise((r) => setTimeout(r, 0))
    }
    expect(await Promise.all(results)).toEqual([0, 1, 2, 3, 4])
    expect(maxRunning).toBe(2)
  })

  it('frees the slot of a failed task', async () => {
    const limit = limitConcurrency(1)
    await expect(limit(async () => { throw new Error('boom') })).rejects.toThrow('boom')
    await expect(limit(async () => 'next')).resolves.toBe('next')
  })
})

describe('parseAccountTab', () => {
  it('accepts the known tabs', () => {
    expect(parseAccountTab('tokens')).toBe('tokens')
    expect(parseAccountTab('subscriptions')).toBe('subscriptions')
  })

  it('falls back to the overview', () => {
    expect(parseAccountTab('x')).toBe('overview')
    expect(parseAccountTab(undefined)).toBe('overview')
  })
})
