package brs.web.api.http.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.OptionalInt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.google.gson.JsonObject;

import brs.Block;
import brs.Blockchain;
import brs.BlockchainProcessor;
import brs.Signum;
import brs.props.PropertyService;
import brs.services.ClockDriftMonitor;
import brs.services.TimeService;
import jakarta.servlet.http.HttpServletRequest;

class GetBlockchainStatusTest {

    private MockedStatic<Signum> signumMock;
    private ClockDriftMonitor clockDriftMonitor;
    private GetBlockchainStatus handler;

    @BeforeEach
    void setUp() {
        signumMock = mockStatic(Signum.class);
        signumMock.when(Signum::getPropertyService).thenReturn(mock(PropertyService.class));

        Block lastBlock = mock(Block.class);
        when(lastBlock.getStringId()).thenReturn("123");
        when(lastBlock.getCumulativeDifficulty()).thenReturn(BigInteger.ONE);
        Blockchain blockchain = mock(Blockchain.class);
        when(blockchain.getLastBlock()).thenReturn(lastBlock);
        clockDriftMonitor = mock(ClockDriftMonitor.class);

        handler = new GetBlockchainStatus(mock(BlockchainProcessor.class), blockchain, mock(TimeService.class),
                clockDriftMonitor);
    }

    @AfterEach
    void tearDown() {
        signumMock.close();
    }

    @Test
    void processRequest_GivenDriftingClock_IncludesClockDriftFields() {
        when(clockDriftMonitor.getEstimatedOffsetSeconds()).thenReturn(OptionalInt.of(-30));
        when(clockDriftMonitor.isDrifting()).thenReturn(true);
        when(clockDriftMonitor.getThresholdSeconds()).thenReturn(10);

        JsonObject response = (JsonObject) handler.processRequest(mock(HttpServletRequest.class));

        assertEquals(-30, response.get(ClockDriftFields.OFFSET).getAsInt());
        assertTrue(response.get(ClockDriftFields.DRIFTING).getAsBoolean());
        assertEquals(10, response.get(ClockDriftFields.THRESHOLD).getAsInt());
    }
}
