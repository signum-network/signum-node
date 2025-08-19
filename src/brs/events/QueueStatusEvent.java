package brs.events;

public class QueueStatusEvent {
    private final int unverifiedSize;
    private final int verifiedSize;
    private final int totalSize;

    /*
     * QueueStatusEvent is an event that carries information about the status of the
     * transaction queue.
     * It contains the sizes of unverified, verified, and total transactions in the
     * queue.
     * This event can be used to notify subscribers about changes in the queue
     * status.
     * * @param unverifiedSize The size of the unverified transactions in the queue.
     * * @param verifiedSize The size of the verified transactions in the queue.
     * * @param totalSize The total size of transactions in the queue (unverified +
     * verified).
     */
    public QueueStatusEvent(int unverifiedSize, int verifiedSize, int totalSize) {
        this.unverifiedSize = unverifiedSize;
        this.verifiedSize = verifiedSize;
        this.totalSize = totalSize;
    }

    public int getUnverifiedSize() {
        return unverifiedSize;
    }

    public int getVerifiedSize() {
        return verifiedSize;
    }

    public int getTotalSize() {
        return totalSize;
    }
}