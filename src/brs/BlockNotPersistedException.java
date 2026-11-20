package brs;

/**
 * Thrown by a block push that failed before its transaction was committed. The in-memory
 * chain state has already been restored when this is thrown, so callers must not restore it again.
 */
public final class BlockNotPersistedException extends RuntimeException {

    private final BlockPushRecovery.Failure failure;
    private final int height;

    BlockNotPersistedException(int height, BlockPushRecovery.Failure failure, RuntimeException cause) {
        super("Block at height " + height + " was not persisted (" + failure + "): " + cause, cause);
        this.failure = failure;
        this.height = height;
    }

    BlockPushRecovery.Failure getFailure() {
        return failure;
    }

    public int getHeight() {
        return height;
    }

    public boolean isTransient() {
        return failure == BlockPushRecovery.Failure.TRANSIENT;
    }
}
