package brs.listeners;

/**
 * Listener for performance statistics related to block processing.
 */
public interface PerformanceListener {
    /**
     * Called when new performance statistics are available after a block is pushed.
     *
     * @param totalTimeMs The total time taken to execute the pushBlock method in
     *                    milliseconds.
     * @param dbTimeMs    The time spent within the database transaction in
     *                    milliseconds.
     * @param atTimeMs    The time spent processing Automated Transactions in
     *                    milliseconds.
     * @param txCount     The number of transactions in the processed block.
     * @param blockHeight The height of the processed block.
     */
    void onPerformanceStatsUpdated(long totalTimeMs, long dbTimeMs, long atTimeMs, int txCount, int blockHeight);
}