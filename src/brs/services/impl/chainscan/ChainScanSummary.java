package brs.services.impl.chainscan;

import java.util.Map;

/**
 * What a chain scan covered and found.
 *
 * @param fromHeight     first height scanned
 * @param toHeight       last height scanned
 * @param blocks         number of blocks scanned
 * @param transactions   number of transactions scanned
 * @param findingCounts  number of findings per {@code CHECK_ID/SEVERITY}, sorted
 * @param durationMillis run time
 */
public record ChainScanSummary(int fromHeight, int toHeight, long blocks, long transactions,
        Map<String, Long> findingCounts, long durationMillis) {

    public ChainScanSummary {
        findingCounts = Map.copyOf(findingCounts);
    }

    public long count(Severity severity) {
        return findingCounts.entrySet().stream()
                .filter(e -> e.getKey().endsWith("/" + severity.name()))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }
}
