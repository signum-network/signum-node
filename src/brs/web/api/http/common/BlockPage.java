package brs.web.api.http.common;

import brs.Block;
import brs.props.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;

/**
 * One page of a block range for the API, bounded by a transaction budget.
 * <p>
 * Blocks are taken in order until the transactions of the blocks taken so far reach
 * {@code maxTransactions}. At least one block is always taken, so paging always makes progress;
 * a page may therefore exceed the budget by the transactions of its last block. Without such a
 * bound, a range of blocks full of (subscription payment) transactions could cost several GB of
 * heap in a single request (#1084).
 *
 * @param blocks    the blocks of this page, in the order of the range
 * @param nextIndex where the next page starts, empty if the range reached the end of the chain
 */
public record BlockPage(List<Block> blocks, OptionalInt nextIndex) {

    private static final Logger logger = LoggerFactory.getLogger(BlockPage.class);

    /**
     * The transaction budget to use for a configured {@code API.BlocksMaxTransactions}: a value
     * {@code <= 0} would turn every page into a single block, so it falls back to the default.
     */
    public static int budget(int configured) {
        if (configured > 0) {
            return configured;
        }
        int fallback = Props.API_BLOCKS_MAX_TRANSACTIONS.getDefaultValue();
        logger.warn("{} = {} is not allowed, using {}", Props.API_BLOCKS_MAX_TRANSACTIONS.getName(), configured,
                fallback);
        return fallback;
    }

    /**
     * @param range      the blocks of the requested range {@code [firstIndex, lastIndex]}, may be
     *                   shorter than the range at the end of the chain
     * @param firstIndex the index of the first block of the range
     * @param lastIndex  the index of the last block of the range
     */
    public static BlockPage of(Collection<Block> range, int firstIndex, int lastIndex, int maxTransactions) {
        List<Block> blocks = new ArrayList<>();
        int transactions = 0;
        for (Block block : range) {
            if (!blocks.isEmpty() && transactions >= maxTransactions) {
                break;
            }
            blocks.add(block);
            transactions += block.getAllTransactions().size();
        }
        boolean stoppedByBudget = blocks.size() < range.size();
        boolean fullRange = range.size() == lastIndex - firstIndex + 1;
        OptionalInt nextIndex = stoppedByBudget || fullRange
                ? OptionalInt.of(firstIndex + blocks.size())
                : OptionalInt.empty();
        return new BlockPage(List.copyOf(blocks), nextIndex);
    }
}
