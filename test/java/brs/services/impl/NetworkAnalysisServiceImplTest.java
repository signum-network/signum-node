package brs.services.impl;

import brs.Block;
import brs.Blockchain;
import brs.peer.Peer;
import brs.props.PropertyService;
import brs.props.Props;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

public class NetworkAnalysisServiceImplTest {

    private Blockchain mockBlockchain;
    private PropertyService mockPropertyService;
    private Peer mockPeer;

    @Before
    public void setUp() {
        mockBlockchain = mock(Blockchain.class);
        mockPropertyService = mock(PropertyService.class);
        mockPeer = mock(Peer.class);

        when(mockPropertyService.getInt(Props.WEB_UI_NETWORK_STATUS_INTERVAL_SECONDS)).thenReturn(120);
        when(mockPropertyService.getInt(Props.WEB_UI_FORK_POINT_MAX_LOOKBACK)).thenReturn(10000);
        when(mockPropertyService.getInt(Props.WEB_UI_FORK_HISTORY_TTL_DAYS)).thenReturn(30);
    }

    /** Makes a service whose fetchPeerBlockId is controlled by a height→blockId map. */
    private NetworkAnalysisServiceImpl makeService(Peer peer, Map<Integer, String> peerBlocks) {
        return new NetworkAnalysisServiceImpl(mockBlockchain, mockPropertyService) {
            @Override protected Peer lookupPeer(String addr) { return peer; }
            @Override protected String fetchPeerBlockId(Peer p, int height) { return peerBlocks.get(height); }
        };
    }

    private void setupPeerHeight(int height) {
        JsonObject cdResponse = new JsonObject();
        cdResponse.addProperty("blockchainHeight", height);
        when(mockPeer.send(any())).thenReturn(cdResponse);
    }

    // ─── peer availability ───────────────────────────────────────────────────

    @Test
    public void findForkPoint_whenPeerNotFound_returnsError() {
        JsonObject result = makeService(null, Map.of()).findForkPoint("1.2.3.4:8123");
        assertTrue(result.has("error"));
    }

    @Test
    public void findForkPoint_whenPeerUnreachable_returnsError() {
        when(mockPeer.send(any())).thenReturn(null);
        JsonObject result = makeService(mockPeer, Map.of()).findForkPoint("1.2.3.4:8123");
        assertTrue(result.has("error"));
    }

    @Test
    public void findForkPoint_whenPeerFetchFails_returnsError() {
        when(mockBlockchain.getHeight()).thenReturn(1000);
        setupPeerHeight(1000);
        when(mockBlockchain.getBlockIdAtHeight(anyInt())).thenReturn(100L);

        JsonObject result = makeService(mockPeer, Map.of()).findForkPoint("1.2.3.4:8123");
        assertTrue(result.has("error"));
    }

    // ─── fast-fail for old forks ─────────────────────────────────────────────

    @Test
    public void findForkPoint_whenForkOlderThanMaxLookback_returnsForkTooOld() {
        when(mockBlockchain.getHeight()).thenReturn(1_000_000);
        setupPeerHeight(1_000_000);
        when(mockBlockchain.getBlockIdAtHeight(anyInt())).thenReturn(111L);

        Map<Integer, String> peerBlocks = new HashMap<>();
        peerBlocks.put(990_000, "999"); // differs from our "111"

        JsonObject result = makeService(mockPeer, peerBlocks).findForkPoint("1.2.3.4:8123");

        assertTrue(result.has("error"));
        assertTrue(result.has("forkTooOld"));
        assertTrue(result.get("forkTooOld").getAsBoolean());
    }

    @Test
    public void findForkPoint_whenNodesAgreeBelowLookbackWindow_proceedsToSearch() {
        int forkAt = 995_000;
        when(mockBlockchain.getHeight()).thenReturn(1_000_000);
        setupPeerHeight(1_000_000);

        when(mockBlockchain.getBlockIdAtHeight(anyInt())).thenAnswer(inv -> {
            int h = (int) inv.getArgument(0);
            return h <= forkAt ? 100L + h : 200L + h;
        });

        Map<Integer, String> peerBlocks = new HashMap<>();
        for (int h = 989_999; h <= 1_000_000; h++) {
            peerBlocks.put(h, h <= forkAt ? String.valueOf(100L + h) : String.valueOf(300L + h));
        }

        Block forkBlock = mock(Block.class);
        when(forkBlock.getStringId()).thenReturn(String.valueOf(100L + forkAt));
        when(mockBlockchain.getBlockAtHeight(forkAt)).thenReturn(forkBlock);

        JsonObject result = makeService(mockPeer, peerBlocks).findForkPoint("1.2.3.4:8123");

        assertFalse("unexpected error: " + result, result.has("error"));
        assertEquals(forkAt, result.get("forkAtHeight").getAsInt());
    }

    // ─── correct fork height ─────────────────────────────────────────────────

    @Test
    public void findForkPoint_findsCorrectForkHeight() {
        int forkAt = 800;
        when(mockBlockchain.getHeight()).thenReturn(1000);
        setupPeerHeight(1000);

        when(mockBlockchain.getBlockIdAtHeight(anyInt())).thenAnswer(inv -> {
            int h = (int) inv.getArgument(0);
            return h <= forkAt ? 100L + h : 200L + h;
        });

        Map<Integer, String> peerBlocks = new HashMap<>();
        for (int h = 1; h <= 1000; h++) {
            peerBlocks.put(h, h <= forkAt ? String.valueOf(100L + h) : String.valueOf(300L + h));
        }

        Block forkBlock = mock(Block.class);
        when(forkBlock.getStringId()).thenReturn(String.valueOf(100L + forkAt));
        when(mockBlockchain.getBlockAtHeight(forkAt)).thenReturn(forkBlock);

        JsonObject result = makeService(mockPeer, peerBlocks).findForkPoint("1.2.3.4:8123");

        assertFalse("unexpected error: " + result, result.has("error"));
        assertEquals(forkAt, result.get("forkAtHeight").getAsInt());
    }

    @Test
    public void findForkPoint_whenForkIsAtVeryRecentBlock_findsIt() {
        int forkAt = 998;
        when(mockBlockchain.getHeight()).thenReturn(1000);
        setupPeerHeight(1000);

        when(mockBlockchain.getBlockIdAtHeight(anyInt())).thenAnswer(inv -> {
            int h = (int) inv.getArgument(0);
            return h <= forkAt ? 100L + h : 200L + h;
        });

        Map<Integer, String> peerBlocks = new HashMap<>();
        for (int h = 1; h <= 1000; h++) {
            peerBlocks.put(h, h <= forkAt ? String.valueOf(100L + h) : String.valueOf(300L + h));
        }

        Block forkBlock = mock(Block.class);
        when(forkBlock.getStringId()).thenReturn(String.valueOf(100L + forkAt));
        when(mockBlockchain.getBlockAtHeight(forkAt)).thenReturn(forkBlock);

        JsonObject result = makeService(mockPeer, peerBlocks).findForkPoint("1.2.3.4:8123");

        assertFalse(result.has("error"));
        assertEquals(forkAt, result.get("forkAtHeight").getAsInt());
    }

    // ─── logarithmic step count ──────────────────────────────────────────────

    @Test
    public void findForkPoint_usesAtMostLogNPlusOneFetchCalls() {
        int forkAt = 500;
        when(mockBlockchain.getHeight()).thenReturn(1000);
        setupPeerHeight(1000);

        when(mockBlockchain.getBlockIdAtHeight(anyInt())).thenAnswer(inv -> {
            int h = (int) inv.getArgument(0);
            return h <= forkAt ? 100L + h : 200L + h;
        });

        Map<Integer, String> allPeerBlocks = new HashMap<>();
        for (int h = 1; h <= 1000; h++) {
            allPeerBlocks.put(h, h <= forkAt ? String.valueOf(100L + h) : String.valueOf(300L + h));
        }

        Block forkBlock = mock(Block.class);
        when(forkBlock.getStringId()).thenReturn("anything");
        when(mockBlockchain.getBlockAtHeight(anyInt())).thenReturn(forkBlock);

        int[] fetchCount = {0};
        NetworkAnalysisServiceImpl service = new NetworkAnalysisServiceImpl(mockBlockchain, mockPropertyService) {
            @Override protected Peer lookupPeer(String addr) { return mockPeer; }
            @Override protected String fetchPeerBlockId(Peer p, int height) {
                fetchCount[0]++;
                return allPeerBlocks.get(height);
            }
        };

        JsonObject result = service.findForkPoint("1.2.3.4");

        assertFalse(result.has("error"));
        assertEquals(forkAt, result.get("forkAtHeight").getAsInt());
        // log2(999) ≈ 10 binary steps + 1 fast-fail = 11; allow 12 for rounding
        assertTrue("Expected ≤12 fetch calls, got " + fetchCount[0], fetchCount[0] <= 12);
    }

    @Test
    public void findForkPoint_whenPeerIsAhead_searchesUpToOurTip() {
        // Peer at 1200, us at 1000 — fork at 800 (within our chain)
        int forkAt = 800;
        when(mockBlockchain.getHeight()).thenReturn(1000);
        setupPeerHeight(1200);

        when(mockBlockchain.getBlockIdAtHeight(anyInt())).thenAnswer(inv -> {
            int h = (int) inv.getArgument(0);
            return h <= forkAt ? 100L + h : 200L + h;
        });

        Map<Integer, String> peerBlocks = new HashMap<>();
        for (int h = 1; h <= 1000; h++) {
            peerBlocks.put(h, h <= forkAt ? String.valueOf(100L + h) : String.valueOf(300L + h));
        }

        Block forkBlock = mock(Block.class);
        when(forkBlock.getStringId()).thenReturn(String.valueOf(100L + forkAt));
        when(mockBlockchain.getBlockAtHeight(forkAt)).thenReturn(forkBlock);

        JsonObject result = makeService(mockPeer, peerBlocks).findForkPoint("1.2.3.4:8123");

        assertFalse("unexpected error: " + result, result.has("error"));
        assertEquals(forkAt, result.get("forkAtHeight").getAsInt());
    }

    @Test
    public void findForkPoint_whenPeerIsAheadAndForkIsAtOurTip_findsIt() {
        // Fork right at our tip: peer has a different block at height 1000
        int forkAt = 999; // last common block
        when(mockBlockchain.getHeight()).thenReturn(1000);
        setupPeerHeight(1200);

        when(mockBlockchain.getBlockIdAtHeight(anyInt())).thenAnswer(inv -> {
            int h = (int) inv.getArgument(0);
            return h <= forkAt ? 100L + h : 200L + h;
        });

        Map<Integer, String> peerBlocks = new HashMap<>();
        for (int h = 1; h <= 1000; h++) {
            peerBlocks.put(h, h <= forkAt ? String.valueOf(100L + h) : String.valueOf(300L + h));
        }

        Block forkBlock = mock(Block.class);
        when(forkBlock.getStringId()).thenReturn(String.valueOf(100L + forkAt));
        when(mockBlockchain.getBlockAtHeight(forkAt)).thenReturn(forkBlock);

        JsonObject result = makeService(mockPeer, peerBlocks).findForkPoint("1.2.3.4:8123");

        assertFalse("unexpected error: " + result, result.has("error"));
        assertEquals(forkAt, result.get("forkAtHeight").getAsInt());
    }

    // ─── fetchPeerBlockId ────────────────────────────────────────────────────

    // The primary path calls getBlocksFromHeight(height=100) which returns the block at 101.
    // That block's previousBlock field is the peer's block ID at height 100.
    @Test
    public void fetchPeerBlockId_returnsBlockIdFromPreviousBlockField() {
        NetworkAnalysisServiceImpl service = new NetworkAnalysisServiceImpl(mockBlockchain, mockPropertyService) {
            @Override protected Peer lookupPeer(String addr) { return null; }
        };

        com.google.gson.JsonArray nextBlocks = new com.google.gson.JsonArray();
        JsonObject blockAt101 = new JsonObject();
        blockAt101.addProperty("previousBlock", "12345678"); // peer's block ID at height 100
        nextBlocks.add(blockAt101);
        JsonObject resp = new JsonObject();
        resp.add("nextBlocks", nextBlocks);

        when(mockPeer.send(any())).thenReturn(resp);

        assertEquals("12345678", service.fetchPeerBlockId(mockPeer, 100));
    }

    @Test
    public void fetchPeerBlockId_whenPeerAtTipReturnsEmptyNextBlocks_returnsNull() {
        NetworkAnalysisServiceImpl service = new NetworkAnalysisServiceImpl(mockBlockchain, mockPropertyService) {
            @Override protected Peer lookupPeer(String addr) { return null; }
        };

        // First call (height=100): empty nextBlocks (peer is at its tip)
        // Second call (height=99, fallback): also empty — simulate unreachable
        JsonObject emptyResp = new JsonObject();
        emptyResp.add("nextBlocks", new com.google.gson.JsonArray());
        when(mockPeer.send(any())).thenReturn(emptyResp);

        assertNull(service.fetchPeerBlockId(mockPeer, 100));
    }
}
