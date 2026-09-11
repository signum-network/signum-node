package brs.services.impl;

import brs.Blockchain;
import brs.Block;
import brs.db.cache.DBCacheManagerImpl;
import brs.db.sql.Db;
import brs.props.CaselessProperties;
import brs.props.PropertyService;
import brs.props.PropertyServiceImpl;
import brs.props.Props;
import brs.services.NetworkAnalysisService;
import brs.statistics.StatisticsManagerImpl;
import com.google.gson.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NetworkAnalysisServiceImplTransactionTest {

    private PropertyService propertyService;

    @Before
    public void setUp() {
        CaselessProperties properties = new CaselessProperties();
        properties.setProperty(Props.DB_URL.getName(), "jdbc:sqlite:file::memory:?cache=shared");
        propertyService = new PropertyServiceImpl(properties);

        DBCacheManagerImpl dbCacheManager = new DBCacheManagerImpl(new StatisticsManagerImpl(new TimeServiceImpl()));
        Db.init(propertyService, dbCacheManager);
    }

    @After
    public void tearDown() {
        Db.shutdown();
    }

    @Test
    public void recordFork_calledDuringActiveTransaction_doesNotCloseSharedConnection() {
        NetworkAnalysisService service = new NetworkAnalysisServiceImpl(mock(Blockchain.class), propertyService);

        Block poppedBlock = mock(Block.class);
        when(poppedBlock.getHeight()).thenReturn(123);
        when(poppedBlock.getStringId()).thenReturn("123456789");

        Db.beginTransaction();
        try {
            // Mirrors how BlockchainProcessorImpl.popOffTo() invokes this as a
            // BLOCK_AUTO_POPPED listener while its own transaction is still open.
            service.recordFork(poppedBlock);

            // The ambient transactional connection must still be usable afterwards.
            int result = Db.fetchWithDSLContext(ctx -> ctx.fetchOne("SELECT 1").get(0, Integer.class));
            assertEquals(1, result);

            Db.commitTransaction();
        } finally {
            Db.endTransaction();
        }

        List<JsonObject> history = service.getForkHistory(10);
        assertEquals(1, history.size());
        assertEquals(123, history.get(0).get("rollbackHeight").getAsInt());
    }
}
