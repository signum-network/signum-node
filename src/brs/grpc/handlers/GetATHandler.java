package brs.grpc.handlers;

import brs.AT;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.ATService;
import brs.services.AccountService;

public class GetATHandler implements GrpcApiHandler<BrsApi.GetATRequest, BrsApi.AT> {

    private final ATService atService;
    private final AccountService accountService;

    public GetATHandler(ATService atService, AccountService accountService) {
        this.atService = atService;
        this.accountService = accountService;
    }

    @Override
    public BrsApi.AT handleRequest(BrsApi.GetATRequest getATRequest) throws Exception {
        AT at = atService.getAT(getATRequest.getId());
        if (at == null) throw new ApiException("AT not found");
        return ProtoBuilder.buildAT(accountService, at);
    }
}
