package brs.web.api.http.common;

import brs.Block;
import brs.Transaction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockPageTest {

    private static final int MAX_TRANSACTIONS = 100;

    @Test
    void testOf_GivenFullRangeWithinBudget_ReturnsAllBlocksAndNextIndexAfterRange() {
        List<Block> range = blocks(10, 5);

        BlockPage page = BlockPage.of(range, 0, 9, MAX_TRANSACTIONS);

        assertEquals(range, page.blocks());
        assertEquals(OptionalInt.of(10), page.nextIndex());
    }

    @Test
    void testOf_GivenBudgetReached_StopsAndPointsNextIndexAtFirstMissingBlock() {
        List<Block> range = blocks(10, 40);

        BlockPage page = BlockPage.of(range, 20, 29, MAX_TRANSACTIONS);

        // 40 + 40 + 40 = 120 >= 100 after the third block
        assertEquals(range.subList(0, 3), page.blocks());
        assertEquals(OptionalInt.of(23), page.nextIndex());
    }

    @Test
    void testOf_GivenFirstBlockAboveBudget_StillReturnsThatBlock() {
        List<Block> range = blocks(10, 5000);

        BlockPage page = BlockPage.of(range, 0, 9, MAX_TRANSACTIONS);

        assertEquals(range.subList(0, 1), page.blocks());
        assertEquals(OptionalInt.of(1), page.nextIndex());
    }

    @Test
    void testOf_GivenBudgetReachedExactlyByLastBlock_ReturnsWholeRange() {
        List<Block> range = blocks(4, 25);

        BlockPage page = BlockPage.of(range, 0, 3, MAX_TRANSACTIONS);

        assertEquals(range, page.blocks());
        assertEquals(OptionalInt.of(4), page.nextIndex());
    }

    @Test
    void testOf_GivenBudgetReachedBeforeLastBlock_StopsAtBudget() {
        List<Block> range = blocks(5, 25);

        BlockPage page = BlockPage.of(range, 0, 4, MAX_TRANSACTIONS);

        assertEquals(range.subList(0, 4), page.blocks());
        assertEquals(OptionalInt.of(4), page.nextIndex());
    }

    @Test
    void testOf_GivenEndOfChainWithinBudget_ReturnsNoNextIndex() {
        List<Block> range = blocks(4, 5);

        BlockPage page = BlockPage.of(range, 0, 9, MAX_TRANSACTIONS);

        assertEquals(range, page.blocks());
        assertEquals(OptionalInt.empty(), page.nextIndex());
    }

    @Test
    void testOf_GivenEndOfChainButBudgetReached_ReturnsNextIndex() {
        List<Block> range = blocks(4, 60);

        BlockPage page = BlockPage.of(range, 0, 9, MAX_TRANSACTIONS);

        assertEquals(range.subList(0, 2), page.blocks());
        assertEquals(OptionalInt.of(2), page.nextIndex());
    }

    @Test
    void testOf_GivenEmptyRange_ReturnsNoBlocksAndNoNextIndex() {
        BlockPage page = BlockPage.of(Collections.emptyList(), 0, 9, MAX_TRANSACTIONS);

        assertEquals(List.of(), page.blocks());
        assertEquals(OptionalInt.empty(), page.nextIndex());
    }

    @Test
    void testOf_GivenBlocksWithoutTransactions_ReturnsWholeRange() {
        List<Block> range = blocks(100, 0);

        BlockPage page = BlockPage.of(range, 0, 99, MAX_TRANSACTIONS);

        assertEquals(range, page.blocks());
        assertEquals(OptionalInt.of(100), page.nextIndex());
    }

    @Test
    void testOf_GivenZeroBudget_ReturnsOneBlockPerPage() {
        List<Block> range = blocks(3, 0);

        BlockPage page = BlockPage.of(range, 7, 9, 0);

        assertEquals(range.subList(0, 1), page.blocks());
        assertEquals(OptionalInt.of(8), page.nextIndex());
    }

    /** {@code count} blocks with {@code transactionsPerBlock} transactions each. */
    private static List<Block> blocks(int count, int transactionsPerBlock) {
        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Block block = mock(Block.class);
            List<Transaction> transactions = Collections.nCopies(transactionsPerBlock, mock(Transaction.class));
            when(block.getAllTransactions()).thenReturn(transactions);
            blocks.add(block);
        }
        return blocks;
    }
}
