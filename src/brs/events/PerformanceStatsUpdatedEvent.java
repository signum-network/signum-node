package brs.events;

public class PerformanceStatsUpdatedEvent {
    private final long totalTimeMs;
    private final long dbTimeMs;
    private final long atTimeMs;
    private final int txCount;
    private final int blockHeight;

    /*
     * PerformanceStatsUpdatedEvent is an event that carries performance statistics.
     * It contains the total time taken for processing, database time, AT (Account
     * Transaction)
     * time, transaction count, and block height.
     * * @param totalTimeMs The total time taken for processing in milliseconds.
     * * @param dbTimeMs The time taken for database operations in milliseconds.
     * * @param atTimeMs The time taken for AT operations in milliseconds.
     * * @param txCount The number of transactions processed.
     * * @param blockHeight The current block height.
     */
    public PerformanceStatsUpdatedEvent(long totalTimeMs, long dbTimeMs, long atTimeMs, int txCount, int blockHeight) {
        this.totalTimeMs = totalTimeMs;
        this.dbTimeMs = dbTimeMs;
        this.atTimeMs = atTimeMs;
        this.txCount = txCount;
        this.blockHeight = blockHeight;
    }

    public long getTotalTimeMs() {
        return totalTimeMs;
    }

    public long getDbTimeMs() {
        return dbTimeMs;
    }

    public long getAtTimeMs() {
        return atTimeMs;
    }

    public int getTxCount() {
        return txCount;
    }

    public int getBlockHeight() {
        return blockHeight;
    }
}