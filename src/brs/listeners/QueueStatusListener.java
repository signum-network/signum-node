package brs.listeners;

/**
 * Listener interface for changes in the verified queue size.
 * This interface allows components to be notified when the size of the
 * verified (but not yet pushed) queue changes.
 */
public interface QueueStatusListener {
    void onQueueStatusChanged(int downloadCacheUnverifiedSize,
            int downloadCacheVerifiedSize,
            int downloadCacheTotalSize);
}