package brs.services.impl;

/** Whether a peer has a given block, as reported by {@code getBlockPeerStatus}. */
enum BlockPeerState {
    /** Has the block. */
    HAS_IT("hasIt"),
    /** Is at or above the block's height without it: on another chain there. */
    MISSING("missing"),
    /** Reported a height below the block: cannot have it yet. */
    NOT_YET("notYet"),
    /** Did not answer in time, or answered with an error. */
    NO_ANSWER("noAnswer");

    private final String apiValue;

    BlockPeerState(String apiValue) {
        this.apiValue = apiValue;
    }

    /** @return the key used in the API response */
    String apiValue() {
        return apiValue;
    }
}
