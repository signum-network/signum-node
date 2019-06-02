package brs.grpc.handlers;

import brs.Account;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.AccountService;

import java.util.Collection;
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
            Collection<Account> accounts = accountService.getAccountsWithName(request.getName());
            accounts.forEach(account -> builder.addIds(account.getId()));
            if (request.getIncludeAccounts()) {
                accounts.forEach(account -> builder.addAccounts(ProtoBuilder.buildAccount(account, accountService)));
            }
        }
        if (request.getRewardRecipient() != 0) {
            Collection<Account.RewardRecipientAssignment> accounts = accountService.getAccountsWithRewardRecipient(request.getRewardRecipient());
            accounts.forEach(assignment -> builder.addIds(assignment.getAccountId()));
            if (request.getIncludeAccounts()) {
                accounts.forEach(assignment -> builder.addAccounts(ProtoBuilder.buildAccount(accountService.getAccount(assignment.getAccountId()), accountService)));
            }
        }
        return builder.build();
    }
}
