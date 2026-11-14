package brs.services;

import brs.services.impl.chainscan.ChainScanSummary;
import brs.services.impl.chainscan.Finding;

import java.util.function.Consumer;

/** Re-checks the stored chain against the consensus rules that need no replay of account state. */
public interface ChainScanService {

    /**
     * Scans the blocks from {@code fromHeight} to {@code toHeight}.
     *
     * @param findingSink receives every finding, from several threads, in no particular order
     * @return counts of what was scanned and found
     * @throws InterruptedException if interrupted while waiting for the workers
     */
    ChainScanSummary scan(int fromHeight, int toHeight, Consumer<Finding> findingSink) throws InterruptedException;
}
