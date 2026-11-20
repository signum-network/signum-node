package brs;

import brs.peer.Peer;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BetterPeerSearchTest {

    private static final BigInteger OURS = BigInteger.valueOf(1_000);
    private static final JsonObject REQUEST = new JsonObject();
    private static final int MAX_POLLS = 5;

    private Supplier<Peer> connectedPeers;
    private Predicate<Peer> usable;
    private BetterPeerSearch.FeederListener feederListener;
    private BooleanSupplier mayContinue;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        connectedPeers = mock(Supplier.class);
        usable = peer -> true;
        feederListener = mock(BetterPeerSearch.FeederListener.class);
        mayContinue = () -> true;
    }

    private BetterPeerSearch search() {
        return new BetterPeerSearch(connectedPeers, usable, REQUEST, feederListener, mayContinue, MAX_POLLS);
    }

    private static Peer peerReporting(int height, long cumulativeDifficulty) {
        JsonObject response = new JsonObject();
        response.addProperty("blockchainHeight", height);
        response.addProperty("cumulativeDifficulty", Long.toString(cumulativeDifficulty));
        Peer peer = mock(Peer.class);
        when(peer.send(REQUEST)).thenReturn(response);
        return peer;
    }

    @Test
    void find_GivenPeerWithHigherDifficulty_ReturnsItWithHeightAndDifficulty() {
        // Arrange
        Peer better = peerReporting(42, 1_001);
        when(connectedPeers.get()).thenReturn(better);

        // Act
        BetterPeerSearch.Feeder feeder = search().find(OURS);

        // Assert
        assertSame(better, feeder.peer());
        assertEquals(42, feeder.height());
        assertEquals(BigInteger.valueOf(1_001), feeder.cumulativeDifficulty());
        verify(feederListener).reportedHeight(better, 42);
    }

    @Test
    void find_GivenWorsePeersFirst_KeepsAskingUntilABetterOne() {
        // Arrange
        Peer worse = peerReporting(40, 999);
        Peer equal = peerReporting(41, 1_000);
        Peer better = peerReporting(42, 1_001);
        when(connectedPeers.get()).thenReturn(worse, equal, better);

        // Act
        BetterPeerSearch.Feeder feeder = search().find(OURS);

        // Assert
        assertSame(better, feeder.peer());
    }

    @Test
    void find_GivenOnlyPeersWithoutABetterChain_StopsAfterMaxPolls() {
        // Arrange: every better peer is blacklisted, the remaining one is behind
        Peer behind = peerReporting(933_651, 500);
        when(connectedPeers.get()).thenReturn(behind);

        // Act
        BetterPeerSearch.Feeder feeder = search().find(OURS);

        // Assert
        assertNull(feeder);
        verify(behind, times(MAX_POLLS)).send(REQUEST);
    }

    @Test
    void find_GivenNoConnectedPeer_ReturnsNull() {
        // Arrange
        when(connectedPeers.get()).thenReturn(null);

        // Act
        BetterPeerSearch.Feeder feeder = search().find(OURS);

        // Assert
        assertNull(feeder);
        verify(connectedPeers, times(1)).get();
    }

    @Test
    void find_GivenSearchMayNotContinueAfterTheFirstPoll_StopsBeforeTheNextOne() {
        // Arrange: e.g. a pop-off or the shutdown pauses the download thread
        Peer behind = peerReporting(40, 999);
        when(connectedPeers.get()).thenReturn(behind);
        AtomicInteger checks = new AtomicInteger();
        mayContinue = () -> checks.getAndIncrement() == 0;

        // Act
        BetterPeerSearch.Feeder feeder = search().find(OURS);

        // Assert
        assertNull(feeder);
        verify(behind, times(1)).send(REQUEST);
    }

    @Test
    void find_GivenSearchMayNotContinue_AsksNoPeer() {
        // Arrange
        mayContinue = () -> false;

        // Act
        BetterPeerSearch.Feeder feeder = search().find(OURS);

        // Assert
        assertNull(feeder);
        verify(connectedPeers, never()).get();
    }

    @Test
    void find_GivenUnusablePeer_DoesNotAskIt() {
        // Arrange
        Peer otherNetwork = peerReporting(42, 1_001);
        Peer better = peerReporting(43, 1_002);
        when(connectedPeers.get()).thenReturn(otherNetwork, better);
        usable = peer -> peer != otherNetwork;

        // Act
        BetterPeerSearch.Feeder feeder = search().find(OURS);

        // Assert
        assertSame(better, feeder.peer());
        verify(otherNetwork, never()).send(any());
    }

    @Test
    void find_GivenNoResponseOrIncompleteResponses_SkipsThesePeers() {
        // Arrange
        Peer silent = mock(Peer.class);
        when(silent.send(REQUEST)).thenReturn(null);
        Peer withoutHeight = mock(Peer.class);
        JsonObject noHeight = new JsonObject();
        noHeight.addProperty("cumulativeDifficulty", "5000");
        when(withoutHeight.send(REQUEST)).thenReturn(noHeight);
        Peer withoutDifficulty = mock(Peer.class);
        JsonObject noDifficulty = new JsonObject();
        noDifficulty.addProperty("blockchainHeight", 41);
        when(withoutDifficulty.send(REQUEST)).thenReturn(noDifficulty);
        Peer better = peerReporting(42, 1_001);
        when(connectedPeers.get()).thenReturn(silent, withoutHeight, withoutDifficulty, better);

        // Act
        BetterPeerSearch.Feeder feeder = search().find(OURS);

        // Assert
        assertSame(better, feeder.peer());
        verify(feederListener, never()).reportedHeight(eq(withoutHeight), anyInt());
        verify(feederListener).reportedHeight(withoutDifficulty, 41);
        verify(feederListener).reportedHeight(better, 42);
    }

    @Test
    void find_GivenPeersReportingHeightAndDifficulty_RecordsChainStateOfEveryPeer() {
        // Arrange: the network analysis needs the chain state of worse peers, too
        Peer worse = peerReporting(40, 999);
        Peer better = peerReporting(42, 1_001);
        when(connectedPeers.get()).thenReturn(worse, better);

        // Act
        search().find(OURS);

        // Assert
        verify(worse).updateChainState(40, "999");
        verify(better).updateChainState(42, "1001");
    }

    @Test
    void find_GivenPeerWithoutDifficulty_DoesNotRecordChainState() {
        // Arrange
        Peer withoutDifficulty = mock(Peer.class);
        JsonObject noDifficulty = new JsonObject();
        noDifficulty.addProperty("blockchainHeight", 41);
        when(withoutDifficulty.send(REQUEST)).thenReturn(noDifficulty);
        Peer better = peerReporting(42, 1_001);
        when(connectedPeers.get()).thenReturn(withoutDifficulty, better);

        // Act
        search().find(OURS);

        // Assert
        verify(withoutDifficulty, never()).updateChainState(anyInt(), any());
    }

    @Test
    void find_GivenNonPositiveMaxPolls_StillAsksOnePeer() {
        // Arrange: e.g. P2P.MaxConnections set to 0
        Peer better = peerReporting(42, 1_001);
        when(connectedPeers.get()).thenReturn(better);
        BetterPeerSearch search = new BetterPeerSearch(connectedPeers, usable, REQUEST, feederListener, mayContinue, 0);

        // Act
        BetterPeerSearch.Feeder feeder = search.find(OURS);

        // Assert
        assertSame(better, feeder.peer());
        verify(better, times(1)).send(REQUEST);
    }
}
