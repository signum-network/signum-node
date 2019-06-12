package brs.grpc;

import brs.grpc.proto.BrsApi;
import com.google.protobuf.Empty;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.Iterator;

import static org.junit.Assert.*;

@RunWith(JUnit4.class)
public class GetMiningInfoHandlerTest extends AbstractGrpcTest {
    @Before
    public void setUpGetMiningInfoHandlerTest() throws IOException {
        defaultBrsService();
    }

    @Test
    public void testGetMiningInfo() {
        runAndCancel(() -> {
            Iterator<BrsApi.MiningInfo> miningInfoIterator = brsService.getMiningInfo(Empty.getDefaultInstance());
            assertTrue("Mining info is not available", miningInfoIterator.hasNext());
            BrsApi.MiningInfo miningInfo = miningInfoIterator.next();
            assertNotNull("Mining info is null", miningInfo);
            assertEquals(1, miningInfo.getHeight());
            assertArrayEquals(new byte[32], miningInfo.getGenerationSignature().toByteArray());
            assertEquals(0, miningInfo.getBaseTarget());
        });
    }
}
