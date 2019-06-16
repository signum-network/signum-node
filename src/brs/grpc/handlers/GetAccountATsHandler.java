package brs.grpc.handlers;

import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.ATService;
import brs.services.AccountService;

import java.util.stream.Collectors;

public class GetAccountATsHandler implements GrpcApiHandler<BrsApi.GetAccountRequest, BrsApi.AccountATs> {

    private final ATService atService;
    private final AccountService accountService;

    public GetAccountATsHandler(ATService atService, AccountService accountService) {
        this.atService = atService;
        this.accountService = accountService;
    }

    @Override
    public BrsApi.AccountATs handleRequest(BrsApi.GetAccountRequest getAccountRequest) throws Exception {
        return BrsApi.AccountATs.newBuilder()
                .addAllAts(atService.getATsIssuedBy(getAccountRequest.getAccountId())
                        .stream()
                        .map(atService::getAT)
                        .map(at -> ProtoBuilder.buildAT(accountService, at))
                        .collect(Collectors.toList()))
                .build();
    }
}
