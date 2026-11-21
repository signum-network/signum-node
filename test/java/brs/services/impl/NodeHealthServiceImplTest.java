package brs.services.impl;

import brs.db.sql.dialects.DatabaseInstance;
import brs.services.PeerServerFacts;
import com.google.gson.JsonObject;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NodeHealthServiceImplTest {

    private static final long STARTED_AT = 1_000L;
    private static final Path DB_FILE = Path.of("/data/db/signum.sqlite.db");

    private DatabaseInstance database;
    private PeerServerFacts peers;
    private final AtomicLong now = new AtomicLong(10_000L);
    private final List<Path> askedVolumes = new ArrayList<>();
    private long usableSpace = 50_000L;

    @BeforeEach
    void setUp() {
        database = mock(DatabaseInstance.class);
        when(database.getDialect()).thenReturn(SQLDialect.SQLITE);
        when(database.getDatabaseSizeBytes()).thenReturn(OptionalLong.of(1_234L));
        when(database.getDatabaseFile()).thenReturn(Optional.of(DB_FILE));
        peers = mock(PeerServerFacts.class);
        when(peers.peerServerRunning()).thenReturn(true);
        when(peers.announcesAddress()).thenReturn(true);
        when(peers.lastPublicInboundMillis()).thenReturn(OptionalLong.empty());
        when(peers.lastLocalInboundMillis()).thenReturn(OptionalLong.empty());
    }

    private NodeHealthServiceImpl service() {
        return new NodeHealthServiceImpl(database, peers, now::get, STARTED_AT, path -> {
            askedVolumes.add(path);
            return usableSpace;
        });
    }

    @Test
    void getNodeHealth_GivenSqlite_ReportsFileVolumeAndDatabaseScope() {
        JsonObject health = service().getNodeHealth();

        assertEquals(STARTED_AT, health.get("startedAt").getAsLong());
        assertEquals("sqlite", health.get("databaseType").getAsString());
        assertEquals(1_234L, health.get("databaseSizeBytes").getAsLong());
        assertEquals(50_000L, health.get("freeDiskBytes").getAsLong());
        assertEquals("database", health.get("freeDiskScope").getAsString());
        assertEquals(List.of(DB_FILE.getParent()), askedVolumes);
        assertTrue(health.get("peerServerRunning").getAsBoolean());
        assertTrue(health.get("announcesAddress").getAsBoolean());
    }

    @Test
    void getNodeHealth_GivenPostgres_ReportsWorkingDirectoryAndNodeScope() {
        when(database.getDialect()).thenReturn(SQLDialect.POSTGRES);
        when(database.getDatabaseFile()).thenReturn(Optional.empty());

        JsonObject health = service().getNodeHealth();

        assertEquals("postgres", health.get("databaseType").getAsString());
        assertEquals("node", health.get("freeDiskScope").getAsString());
        assertEquals(List.of(Path.of("").toAbsolutePath()), askedVolumes);
    }

    @Test
    void getNodeHealth_GivenMariaDb_ReportsMariadb() {
        when(database.getDialect()).thenReturn(SQLDialect.MARIADB);

        assertEquals("mariadb", service().getNodeHealth().get("databaseType").getAsString());
    }

    @Test
    void getNodeHealth_GivenSizeUnavailable_ReportsNullAndKeepsOtherFields() {
        when(database.getDatabaseSizeBytes()).thenReturn(OptionalLong.empty());

        JsonObject health = service().getNodeHealth();

        assertTrue(health.get("databaseSizeBytes").isJsonNull());
        assertEquals("sqlite", health.get("databaseType").getAsString());
        assertEquals(50_000L, health.get("freeDiskBytes").getAsLong());
    }

    @Test
    void getNodeHealth_GivenUsableSpaceThrows_ReportsNullFreeDisk() {
        NodeHealthServiceImpl service = new NodeHealthServiceImpl(database, peers, now::get, STARTED_AT, path -> {
            throw new SecurityException("no access");
        });

        JsonObject health = service.getNodeHealth();

        assertTrue(health.get("freeDiskBytes").isJsonNull());
        assertEquals(1_234L, health.get("databaseSizeBytes").getAsLong());
    }

    @Test
    void getNodeHealth_GivenCallsWithin60s_ComputesOnce() {
        NodeHealthServiceImpl service = service();

        service.getNodeHealth();
        now.addAndGet(59_999L);
        service.getNodeHealth();
        verify(database, times(1)).getDatabaseSizeBytes();

        now.addAndGet(1L);
        service.getNodeHealth();
        verify(database, times(2)).getDatabaseSizeBytes();
    }

    @Test
    void getNodeHealth_GivenConcurrentFirstCalls_ComputesOnce() throws InterruptedException {
        // many visitors at once must not run the size query many times
        NodeHealthServiceImpl service = service();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                service.getNodeHealth();
            });
            thread.start();
            threads.add(thread);
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }

        verify(database, times(1)).getDatabaseSizeBytes();
    }

    @Test
    void getNodeHealth_GivenInboundTimes_ReportsThemOrNull() {
        when(peers.lastPublicInboundMillis()).thenReturn(OptionalLong.of(9_000L));
        when(peers.peerServerRunning()).thenReturn(false);
        when(peers.announcesAddress()).thenReturn(false);

        JsonObject health = service().getNodeHealth();

        assertEquals(9_000L, health.get("lastInboundPeerAt").getAsLong());
        assertTrue(health.get("lastLocalInboundPeerAt").isJsonNull());
        assertFalse(health.get("peerServerRunning").getAsBoolean());
        assertFalse(health.get("announcesAddress").getAsBoolean());
    }
}
