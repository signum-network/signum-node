package brs.grpc.handlers;

import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.services.ATService;
import com.google.protobuf.Empty;

public class GetATIdsHandler implements GrpcApiHandler<Empty, BrsApi.ATIds> {

    private final ATService atService;

    public GetATIdsHandler(ATService atService) {
        this.atService = atService;
    }

    @Override
    public BrsApi.ATIds handleRequest(Empty empty) throws Exception {
        return BrsApi.ATIds.newBuilder()
                .addAllIds(atService.getAllATIds())
                .build();
    }
}
