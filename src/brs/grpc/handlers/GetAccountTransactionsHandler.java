package brs.grpc.handlers;

import brs.Account;
import brs.Blockchain;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.AccountService;

public class GetAccountTransactionsHandler implements GrpcApiHandler<BrsApi.GetAccountTransactionsRequest, BrsApi.Transactions> {

    private final Blockchain blockchain;
    private final AccountService accountService;

    public GetAccountTransactionsHandler(Blockchain blockchain, AccountService accountService) {
        this.blockchain = blockchain;
        this.accountService = accountService;
    }

    @Override
    public BrsApi.Transactions handleRequest(BrsApi.GetAccountTransactionsRequest request) throws Exception {
        long accountId = request.getAccountId();
        int timestamp = request.getTimestamp();
        BrsApi.IndexRange indexRange = ProtoBuilder.sanitizeIndexRange(request.getIndexRange());
        int firstIndex = indexRange.getFirstIndex();
        int lastIndex = indexRange.getLastIndex();
        int numberOfConfirmations = request.getConfirmations();
        byte type = (byte) (request.getFilterByType() ? request.getType() : -1);
        byte subtype = (byte) (request.getFilterByType() ? request.getSubtype() : -1);

        Account account = accountService.getAccount(accountId);
        if (account == null) throw new ApiException("Could not find account");

        BrsApi.Transactions.Builder builder = BrsApi.Transactions.newBuilder();

        int currentHeight = blockchain.getHeight();
        blockchain.getTransactions(account, numberOfConfirmations, type, subtype, timestamp, firstIndex, lastIndex, true)
                .forEach(transaction -> builder.addTransactions(ProtoBuilder.buildTransaction(transaction, currentHeight)));

        return builder.build();
    }
}
