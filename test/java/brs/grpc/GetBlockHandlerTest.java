package brs.grpc;

import brs.grpc.proto.BrsApi;
import io.grpc.StatusRuntimeException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class GetBlockHandlerTest extends AbstractGrpcTest {
    @Before
    public void setupGetBlockHandlerTest() throws IOException {
        defaultBrsService();
    }

    @Test(expected = StatusRuntimeException.class)
    public void testGetBlockWithNoBlockSelected() {
        brsService.getBlock(BrsApi.GetBlockRequest.newBuilder()
                .setHeight(Integer.MAX_VALUE)
                .build());
    }
}
