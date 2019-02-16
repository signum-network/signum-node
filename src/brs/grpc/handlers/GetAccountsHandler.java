package brs.grpc.handlers;

import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.AccountService;

import java.util.Objects;

public class GetAccountsHandler implements GrpcApiHandler<BrsApi.GetAccountsRequest, BrsApi.Accounts> {

    private final AccountService accountService;

    public GetAccountsHandler(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public BrsApi.Accounts handleRequest(BrsApi.GetAccountsRequest request) throws Exception {
        BrsApi.Accounts.Builder builder = BrsApi.Accounts.newBuilder();
        if (!Objects.equals(request.getName(), "")) {
            if (request.getIncludeAccounts()) {
                accountService.getAccountsWithName(request.getName()).forEachRemaining(account -> builder.addAccounts(ProtoBuilder.buildAccount(account, accountService)));
            } else {
                accountService.getAccountsWithName(request.getName()).forEachRemaining(account -> builder.addAccountIDs(account.getId()));
            }
        }
        if (request.getRewardRecipient() != 0) {
            if (request.getIncludeAccounts()) {
                accountService.getAccountsWithRewardRecipient(request.getRewardRecipient()).forEachRemaining(assignment -> builder.addAccounts(ProtoBuilder.buildAccount(accountService.getAccount(assignment.getAccountId()), accountService)));
            } else {
                accountService.getAccountsWithRewardRecipient(request.getRewardRecipient()).forEachRemaining(assignment -> builder.addAccountIDs(assignment.getAccountId()));
            }
        }
        return builder.build();
    }
}
