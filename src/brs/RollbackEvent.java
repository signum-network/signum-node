package brs;

/**
 * One rollback of the chain: blocks were popped off down to a common block, either to switch to a
 * peer's fork or by a pop-off (API, console, GUI or after repeated push failures).
 *
 * @param rollbackHeight height of the common block that stays; the chain was cut above it
 * @param depth          number of blocks popped off
 * @param oldTopBlockId  the tip before the rollback
 * @param newTopBlockId  the tip after a fork switch, {@code null} for a pop-off
 * @param peerAddress    the peer whose fork we switched to, {@code null} for a pop-off
 */
public record RollbackEvent(int rollbackHeight, int depth, long oldTopBlockId, Long newTopBlockId,
        String peerAddress) {
}
