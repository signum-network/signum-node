package brs;

import java.util.function.LongSupplier;

/**
 * Tracks consecutive failures to persist a block and derives when the importer may retry
 * and when it should escalate to popping off blocks.
 * <p>
 * The retry delay doubles with every consecutive failure, starting at {@code baseDelayMs}
 * and capped at {@code maxDelayMs}, so a failure that keeps repeating neither floods the logs
 * nor re-downloads the same blocks from peers in a tight loop. A successful push resets the
 * streak. Thread-safe: block pushes from the importer, generator and fork processing share one
 * instance.
 */
final class PersistenceFailureBackoff {

    private final long baseDelayMs;
    private final long maxDelayMs;
    private final int popOffThreshold;
    private final LongSupplier clockMs;

    private int consecutiveFailures;
    private long nextRetryAtMs;

    PersistenceFailureBackoff(long baseDelayMs, long maxDelayMs, int popOffThreshold, LongSupplier clockMs) {
        if (baseDelayMs <= 0) {
            throw new IllegalArgumentException("Base delay must be positive: " + baseDelayMs);
        }
        if (maxDelayMs < baseDelayMs) {
            throw new IllegalArgumentException(
                    "Max delay " + maxDelayMs + " must not be less than base delay " + baseDelayMs);
        }
        if (popOffThreshold <= 0) {
            throw new IllegalArgumentException("Pop off threshold must be positive: " + popOffThreshold);
        }
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.popOffThreshold = popOffThreshold;
        this.clockMs = clockMs;
    }

    /**
     * Records a failure and schedules the next retry.
     *
     * @return the number of consecutive failures including this one
     */
    synchronized int recordFailure() {
        consecutiveFailures++;
        nextRetryAtMs = clockMs.getAsLong() + retryDelayMs();
        return consecutiveFailures;
    }

    synchronized void recordSuccess() {
        consecutiveFailures = 0;
        nextRetryAtMs = 0;
    }

    synchronized boolean isRetryDue() {
        return consecutiveFailures == 0 || clockMs.getAsLong() >= nextRetryAtMs;
    }

    synchronized boolean isPopOffDue() {
        return consecutiveFailures >= popOffThreshold;
    }

    /** The delay before the next retry, or 0 if there is no failure streak. */
    synchronized long retryDelayMs() {
        if (consecutiveFailures == 0) {
            return 0;
        }
        // cap the shift so the delay cannot overflow; maxDelayMs caps it anyway
        int doublings = Math.min(consecutiveFailures - 1, 30);
        return Math.min(baseDelayMs << doublings, maxDelayMs);
    }
}
