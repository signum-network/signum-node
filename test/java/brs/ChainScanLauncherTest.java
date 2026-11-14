package brs;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChainScanLauncherTest {

    @Test
    void parseChecks_GivenGroups_ReturnsThem() {
        assertEquals(Set.of("tx"), ChainScanLauncher.parseChecks("tx"));
        assertEquals(Set.of("tx", "block"), ChainScanLauncher.parseChecks("TX, block"));
        assertEquals(Set.of("tx", "block"), ChainScanLauncher.parseChecks("all"));
    }

    @Test
    void parseChecks_GivenNotYetImplementedGroup_Throws() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ChainScanLauncher.parseChecks("tx,poc"));
        assertEquals("check group 'poc' is not implemented yet", e.getMessage());
    }

    @Test
    void parseChecks_GivenUnknownGroup_Throws() {
        assertThrows(IllegalArgumentException.class, () -> ChainScanLauncher.parseChecks("signatures"));
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
