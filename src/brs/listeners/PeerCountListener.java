package brs.listeners;

/**
 * Listener interface for changes in the peer count.
 * This interface allows components to be notified when the number of peers
 * changes.
 */
public interface PeerCountListener {
    void onPeerCountChanged(int newCount, int newConnectedCount);
}