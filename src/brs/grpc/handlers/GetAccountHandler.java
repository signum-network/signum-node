package brs.grpc.handlers;

import brs.Account;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.AccountService;

public class GetAccountHandler implements GrpcApiHandler<BrsApi.GetAccountRequest, BrsApi.Account> {
    private final AccountService accountService;

    public GetAccountHandler(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public BrsApi.Account handleRequest(BrsApi.GetAccountRequest request) throws Exception {
        Account account;
        try {
            account = accountService.getAccount(request.getAccountId());
            if (account == null) throw new NullPointerException();
        } catch (RuntimeException e) {
            throw new ApiException("Could not find account");
        }
        return ProtoBuilder.buildAccount(account, accountService);
    }
}
