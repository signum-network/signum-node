import type { BlockDetail, Constants, TransactionDetail } from '@/lib/nodeApi'

export const PAGE_SIZE = 25

/** API indices of a page; page 0 holds the newest blocks. */
export function pageRange(page: number): { firstIndex: number; lastIndex: number } {
  const firstIndex = page * PAGE_SIZE
  return { firstIndex, lastIndex: firstIndex + PAGE_SIZE - 1 }
}

/** Extra blocks fetched below an anchored page, so blocks pushed meanwhile cannot shorten it. */
const ANCHOR_MARGIN = 10

/**
 * API indices for the page whose newest block is at height `from`. Indices count from the tip, which
 * may grow between reading it and fetching; `blocksFrom` cuts the result back to `from`.
 */
export function anchoredRange(from: number, tipHeight: number): { firstIndex: number; lastIndex: number } {
  const firstIndex = Math.max(0, tipHeight - from)
  return { firstIndex, lastIndex: firstIndex + PAGE_SIZE - 1 + ANCHOR_MARGIN }
}

/** The page starting at height `from`, out of a window fetched with `anchoredRange`. */
export function blocksFrom(blocks: BlockDetail[], from: number): BlockDetail[] {
  return blocks.filter((block) => block.height <= from).slice(0, PAGE_SIZE)
}

/** The subtype's name from the node constants, e.g. "Ordinary Payment"; `type/subtype` if unknown. */
export function txTypeName(constants: Constants | undefined, type: number, subtype: number): string {
  const name = constants?.transactionTypes
    .find((t) => t.value === type)
    ?.subtypes.find((s) => s.value === subtype)?.description
  return name ?? `${type}/${subtype}`
}

const COMMITMENT_TYPE = 20

const ADD_COMMITMENT = 1

const REMOVE_COMMITMENT = 2

/** The amount to show: for adding or removing a commitment the node carries it in the attachment. */
export function txAmountNqt(tx: TransactionDetail): string {
  const isCommitment = tx.type === COMMITMENT_TYPE && (tx.subtype === ADD_COMMITMENT || tx.subtype === REMOVE_COMMITMENT)
  const committed = tx.attachment?.amountNQT
  if (!isCommitment) return tx.amountNQT
  if (typeof committed === 'string') return committed
  // the node sends this one as a JSON number; exact up to 2^53 NQT (about 90 million SIGNA)
  if (typeof committed === 'number' && Number.isSafeInteger(committed)) return String(committed)
  return tx.amountNQT
}
