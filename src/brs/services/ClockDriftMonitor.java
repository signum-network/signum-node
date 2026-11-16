package brs.services;

import java.util.OptionalInt;

/**
 * Estimates how far the local clock is off from the network, from the timestamps of new blocks.
 * Purely diagnostic: the estimate never affects block or transaction validation.
 */
public interface ClockDriftMonitor {

    /**
     * Records that a block extending the known chain was seen for the first time, now.
     *
     * @param height         the block's height
     * @param blockTimestamp the block's timestamp (epoch seconds)
     */
    void onBlockReceived(int height, int blockTimestamp);

    /**
     * @return the estimated offset of the local clock in seconds, positive when the local clock is
     *         ahead and negative when it is behind; empty until enough blocks were seen. The value
     *         includes the block propagation delay, so a correct clock shows a small positive offset.
     */
    OptionalInt getEstimatedOffsetSeconds();

    /** @return true if the estimated offset is beyond the configured threshold */
    boolean isDrifting();

    /** @return the threshold in seconds above which the clock counts as drifting; 0 if disabled */
    int getThresholdSeconds();
}
