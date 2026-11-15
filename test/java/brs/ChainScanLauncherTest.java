package brs;

import brs.db.store.ChainScanStore;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChainScanLauncherTest {

    @Test
    void parseChecks_GivenGroups_ReturnsThem() {
        assertEquals(Set.of("tx"), ChainScanLauncher.parseChecks("tx"));
        assertEquals(Set.of("tx", "block"), ChainScanLauncher.parseChecks("TX, block"));
        assertEquals(Set.of("tx", "poc"), ChainScanLauncher.parseChecks("tx,poc"));
        assertEquals(Set.of("tx", "block", "poc"), ChainScanLauncher.parseChecks("all"));
    }

    @Test
    void parseChecks_GivenNotYetImplementedGroup_Throws() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ChainScanLauncher.parseChecks("tx,basetarget"));
        assertEquals("check group 'basetarget' is not implemented yet", e.getMessage());
    }

    @Test
    void parseChecks_GivenUnknownGroup_Throws() {
        assertThrows(IllegalArgumentException.class, () -> ChainScanLauncher.parseChecks("signatures"));
    }

    @Test
    void isRequested_GivenTheOptionOrAPrefix_ReturnsTrue() {
        assertTrue(ChainScanLauncher.isRequested(new String[] {"--verify-chain"}));
        assertTrue(ChainScanLauncher.isRequested(new String[] {"-c", "conf", "--verify-chain", "--threads"}));
        assertTrue(ChainScanLauncher.isRequested(new String[] {"--verify"}));
    }

    @Test
    void isRequested_GivenOtherOptions_ReturnsFalse() {
        assertFalse(ChainScanLauncher.isRequested(new String[] {}));
        assertFalse(ChainScanLauncher.isRequested(new String[] {"--headless", "-c", "conf"}));
        assertFalse(ChainScanLauncher.isRequested(new String[] {"--"}));
        assertFalse(ChainScanLauncher.isRequested(new String[] {"--verify-chains"}));
    }

    @Test
    void parseThreads_GivenValidCount_ReturnsIt() {
        assertEquals(1, ChainScanLauncher.parseThreads("1"));
        assertEquals(ChainScanLauncher.MAX_THREADS, ChainScanLauncher.parseThreads(" 256 "));
    }

    @Test
    void parseThreads_GivenInvalidCount_ThrowsWithClearMessage() {
        assertEquals("--threads must be between 1 and 256: 0",
                assertThrows(IllegalArgumentException.class, () -> ChainScanLauncher.parseThreads("0")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> ChainScanLauncher.parseThreads("-3"));
        assertThrows(IllegalArgumentException.class, () -> ChainScanLauncher.parseThreads("2147483647"));
        assertEquals("--threads must be a number: many",
                assertThrows(IllegalArgumentException.class, () -> ChainScanLauncher.parseThreads("many")).getMessage());
    }

    @Test
    void verifyChain_GivenOptionWithoutValue_FailsBeforeStartingAnything() {
        // Arrange: --threads needs a value; this must not fall through to the node
        PrintStream originalErr = System.err;
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            // Act
            int exitCode = Signum.verifyChain(new String[] {"--verify-chain", "--threads"});

            // Assert
            assertEquals(ChainScanLauncher.EXIT_FAILED, exitCode);
            assertTrue(err.toString(StandardCharsets.UTF_8).contains("Chain scan: Missing argument for option: threads"),
                    err.toString(StandardCharsets.UTF_8));
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    void storedChainUpTo_AnswersHeightAndCommitmentQueriesFromTheStore() {
        // Arrange
        ChainScanStore store = mock(ChainScanStore.class);
        when(store.getBlocksCount(5L, 10, 20)).thenReturn(3);
        when(store.getCommittedAmount(5L, 21, 20)).thenReturn(700L);
        Blockchain blockchain = ChainScanLauncher.storedChainUpTo(store, 20);

        // Act + Assert
        assertEquals(20, blockchain.getHeight());
        assertEquals(3, blockchain.getBlocksCount(5L, 10, 20));
        assertEquals(700L, blockchain.getCommittedAmount(5L, 21, 20, null));
        assertThrows(UnsupportedOperationException.class,
                () -> blockchain.getCommittedAmount(5L, 21, 20, mock(Transaction.class)));
        assertThrows(UnsupportedOperationException.class, blockchain::getLastBlock);
    }

    @Test
    void tipOnlyBlockchain_AnswersOnlyTheHeight() {
        // Arrange
        Blockchain blockchain = ChainScanLauncher.tipOnlyBlockchain(1234);

        // Act + Assert
        assertEquals(1234, blockchain.getHeight());
        assertThrows(UnsupportedOperationException.class, blockchain::getLastBlock);
    }
}
