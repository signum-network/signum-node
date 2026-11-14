package brs.services.impl.chainscan;

/** How serious a chain scan finding is. */
public enum Severity {
    /** Violates a consensus rule: a current node would reject the block. */
    ERROR,
    /** Suspicious, but the scan cannot decide with the data it has. */
    WARN,
    /** Noteworthy, no rule violated. */
    INFO
}
