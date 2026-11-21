package brs.services.impl;

/** Where a peer stands relative to our chain, as reported by {@code getNetworkStatus}. */
enum PeerChainStatus {
    /** Has our tip, or is at most {@code STALE_GAP - 1} blocks behind it on our chain. */
    ON_CHAIN("on-chain", true),
    /** Its newest block is on our chain, at least {@code STALE_GAP} blocks below our tip. */
    STALE("stale", true),
    /** Has our tip and is higher: same chain, ahead. */
    AHEAD("ahead", true),
    /** Lacks our block at the height checked: a different chain. */
    FORKING("forking", false),
    /** Did not answer, or answered with an error. */
    UNKNOWN("unknown", false);

    private final String apiValue;
    private final boolean onOurChain;

    PeerChainStatus(String apiValue, boolean onOurChain) {
        this.apiValue = apiValue;
        this.onOurChain = onOurChain;
    }

    /** @return the status string used in the API response */
    String apiValue() {
        return apiValue;
    }

    /** @return true if the peer is on our chain (at, behind or ahead of our height) */
    boolean isOnOurChain() {
        return onOurChain;
    }
}
