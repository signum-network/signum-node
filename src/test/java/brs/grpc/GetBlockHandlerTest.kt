package brs.grpc

import brs.grpc.proto.BrsApi
import io.grpc.StatusRuntimeException
import org.junit.Before
import org.junit.Test

import java.io.IOException

class GetBlockHandlerTest : AbstractGrpcTest() {
    @Before
    @Throws(IOException::class)
    fun setupGetBlockHandlerTest() {
        defaultBrsService()
    }

    @Test(expected = StatusRuntimeException::class)
    fun testGetBlockWithNoBlockSelected() {
        brsService!!.getBlock(BrsApi.GetBlockRequest.newBuilder()
                .setHeight(Integer.MAX_VALUE)
                .build())
    }
}