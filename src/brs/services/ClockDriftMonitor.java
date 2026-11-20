package brs.services;

import java.util.OptionalInt;

/**
 * Estimates how far the local clock is off from the network, from two independent sources: the
 * timestamps of new blocks (only blocks that passed validation count) and the clocks peers report
 * in the handshake. Purely diagnostic: the estimates never affect block or transaction validation
 * and never adjust the local time.
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

    /**
     * Records the clock offset measured against a peer, now. Replaces that peer's earlier sample.
     *
     * @param peerAddress  the peer's address, one sample per address
     * @param offsetMillis the local clock minus the peer's clock in milliseconds, corrected for the
     *                     round trip; positive when the local clock is ahead
     */
    void onPeerClockSample(String peerAddress, long offsetMillis);

    /**
     * @return the median offset of the local clock against peers in seconds (rounded toward zero),
     *         same sign as {@link #getEstimatedOffsetSeconds()}; empty until enough peers reported
     *         a recent clock
     */
    OptionalInt getPeerOffsetSeconds();

    /**
     * Forgets a peer's clock sample, e.g. when the peer was dropped or no longer reports its clock.
     *
     * @param peerAddress the address the sample was recorded under
     */
    void removePeerClockSample(String peerAddress);

    /** @return the number of peers with a recent clock sample */
    int getPeerSampleCount();

    /** @return true if the block-based or the peer-based offset is beyond the configured threshold */
    boolean isDrifting();

    /** @return the threshold in seconds above which the clock counts as drifting; 0 if disabled */
    int getThresholdSeconds();
}
