package brs.services.impl;

import brs.Blockchain;
import brs.RollbackEvent;
import brs.db.cache.DBCacheManagerImpl;
import brs.db.sql.Db;
import brs.props.CaselessProperties;
import brs.props.PropertyService;
import brs.props.PropertyServiceImpl;
import brs.props.Props;
import brs.statistics.StatisticsManagerImpl;
import com.google.gson.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class NetworkAnalysisServiceImplTransactionTest {

    private PropertyService propertyService;

    @Before
    public void setUp() {
        CaselessProperties properties = new CaselessProperties();
        properties.setProperty(Props.DB_URL.getName(), "jdbc:sqlite:file::memory:?cache=shared");
        propertyService = new PropertyServiceImpl(properties);

        DBCacheManagerImpl dbCacheManager = new DBCacheManagerImpl(new StatisticsManagerImpl(new TimeServiceImpl()));
        Db.init(propertyService, dbCacheManager);
        // the shared in-memory database outlives a single test
        Db.useDSLContext(ctx -> ctx.execute("DELETE FROM fork_history"));
    }

    @After
    public void tearDown() {
        Db.shutdown();
    }

    @Test
    public void storeRollback_GivenActiveTransaction_KeepsSharedConnectionUsable() {
        NetworkAnalysisServiceImpl service = service(mock(ScheduledExecutorService.class));

        Db.beginTransaction();
        try {
            // pop-offs run inside their own transaction; the write must not close that connection
            service.storeRollback(new RollbackEvent(120, 3, 999L, 1000L, "1.2.3.4"));

            int result = Db.fetchWithDSLContext(ctx -> ctx.fetchOne("SELECT 1").get(0, Integer.class));
            assertEquals(1, result);

            Db.commitTransaction();
        } finally {
            Db.endTransaction();
        }

        List<JsonObject> history = service.getForkHistory(10);
        assertEquals(1, history.size());
        JsonObject entry = history.get(0);
        assertEquals(120, entry.get("rollbackHeight").getAsInt());
        assertEquals(3, entry.get("rollbackDepth").getAsInt());
        assertEquals("999", entry.get("oldTopBlockId").getAsString());
        assertEquals("1000", entry.get("newTopBlockId").getAsString());
        assertEquals("1.2.3.4", entry.get("peerSource").getAsString());
    }

    @Test
    public void storeRollback_GivenManualPopOff_StoresNullTopAndPeer() {
        NetworkAnalysisServiceImpl service = service(mock(ScheduledExecutorService.class));

        service.storeRollback(new RollbackEvent(50, 2, 7L, null, null));

        JsonObject entry = service.getForkHistory(10).get(0);
        assertTrue(entry.get("newTopBlockId").isJsonNull());
        assertTrue(entry.get("peerSource").isJsonNull());
    }

    @Test
    public void storeRollback_GivenNegativeBlockIds_StoresThemUnsigned() {
        NetworkAnalysisServiceImpl service = service(mock(ScheduledExecutorService.class));

        service.storeRollback(new RollbackEvent(50, 1, -5L, -6L, "1.2.3.4"));

        JsonObject entry = service.getForkHistory(10).get(0);
        assertEquals("18446744073709551611", entry.get("oldTopBlockId").getAsString());
        assertEquals("18446744073709551610", entry.get("newTopBlockId").getAsString());
    }

    @Test
    public void recordRollback_SubmitsTheWriteToTheScheduler() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        NetworkAnalysisServiceImpl service = service(scheduler);

        service.recordRollback(new RollbackEvent(80, 4, 11L, 12L, "1.2.3.4"));

        assertTrue(service.getForkHistory(10).isEmpty());
        ArgumentCaptor<Runnable> write = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).execute(write.capture());
        write.getValue().run();
        assertEquals(4, service.getForkHistory(10).get(0).get("rollbackDepth").getAsInt());
    }

    private NetworkAnalysisServiceImpl service(ScheduledExecutorService scheduler) {
        return new NetworkAnalysisServiceImpl(mock(Blockchain.class), propertyService, List::of,
                mock(PeerBlockProbe.class), scheduler, mock(ExecutorService.class), 1_000L);
    }
}
