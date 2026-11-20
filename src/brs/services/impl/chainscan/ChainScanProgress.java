package brs.services.impl.chainscan;

/**
 * A snapshot of a running chain scan.
 *
 * @param blocksDone      blocks checked so far
 * @param totalBlocks     blocks in the scanned range
 * @param maxHeight       highest height checked so far, -1 before the first block
 * @param blocksPerSecond average speed since the start
 * @param etaSeconds      estimated seconds left, -1 if unknown
 * @param errors          findings of severity ERROR so far
 * @param warnings        findings of severity WARN so far
 */
public record ChainScanProgress(long blocksDone, long totalBlocks, long maxHeight, double blocksPerSecond,
        long etaSeconds, long errors, long warnings) {

    /** Done share from 0 to 1. */
    public double fraction() {
        return totalBlocks == 0 ? 1 : Math.min(1, (double) blocksDone / totalBlocks);
    }
}
