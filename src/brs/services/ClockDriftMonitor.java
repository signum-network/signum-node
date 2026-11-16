package brs.services;

import java.util.OptionalInt;

/**
 * Estimates how far the local clock is off from the network, from the timestamps of new blocks.
 * Only blocks that passed validation count, so peers cannot feed it made-up timestamps.
 * Purely diagnostic: the estimate never affects block or transaction validation.
 */
public interface ClockDriftMonitor {

    /**
     * Records that a block was received from a peer, now. Only the first arrival of a block counts.
     * The block is not validated yet, so this only notes the arrival time.
     *
     * @param blockId the block's id
     */
    void onBlockReceived(long blockId);

    /**
     * Takes a sample once a block passed validation and became the new tip, using the time it
     * first arrived.
     *
     * @param blockId        the block's id
     * @param height         the block's height
     * @param blockTimestamp the block's timestamp (epoch seconds)
     */
    void onBlockPushed(long blockId, int height, int blockTimestamp);

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
