package brs.services;

/**
 * Which connected peers have one block, from {@link NetworkAnalysisService#checkBlockPeers(long)}.
 * The counts are only set for {@link Outcome#OK}.
 *
 * @param checkedAt when the round started (epoch ms)
 */
public record BlockPeerCheck(Outcome outcome, long blockId, int height, int hasIt, int missing, int notYet,
        int noAnswer, long checkedAt) {

    public enum Outcome {
        /** The peers were asked; the counts are set. */
        OK,
        /** The block is older than the window peers are asked for. */
        TOO_OLD,
        /** A round for another block is running. */
        BUSY,
        /** We do not have the block. */
        UNKNOWN_BLOCK
    }

    /** A result without counts, for every outcome but {@link Outcome#OK}. */
    public static BlockPeerCheck without(Outcome outcome, long blockId, int height) {
        return new BlockPeerCheck(outcome, blockId, height, 0, 0, 0, 0, 0L);
    }
}
