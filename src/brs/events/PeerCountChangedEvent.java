package brs.events;

public class PeerCountChangedEvent {
    private final int newCount;
    private final int newConnectedCount;

    /*
     * PeerCountChangedEvent is an event that carries information about the change
     * in the number of peers.
     * It contains the new total count of peers and the count of connected peers.
     * This event can be used to notify subscribers about changes in the peer
     * network.
     * * @param newCount The new total count of peers.
     * * @param newConnectedCount The new count of connected peers.
     */
    public PeerCountChangedEvent(int newCount, int newConnectedCount) {
        this.newCount = newCount;
        this.newConnectedCount = newConnectedCount;
    }

    public int getNewCount() {
        return newCount;
    }

    public int getNewConnectedCount() {
        return newConnectedCount;
    }
}