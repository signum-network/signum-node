package brs.grpc.handlers;

import brs.TransactionProcessor;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;

import java.util.stream.Collectors;

public class GetUnconfirmedTransactionsHandler implements GrpcApiHandler<BrsApi.GetAccountRequest, BrsApi.UnconfirmedTransactions> {

    private final TransactionProcessor transactionProcessor;

    public GetUnconfirmedTransactionsHandler(TransactionProcessor transactionProcessor) {
        this.transactionProcessor = transactionProcessor;
    }

    @Override
    public BrsApi.UnconfirmedTransactions handleRequest(BrsApi.GetAccountRequest getAccountRequest) throws Exception {
        return BrsApi.UnconfirmedTransactions.newBuilder()
                .addAllUnconfirmedTransactions(transactionProcessor.getAllUnconfirmedTransactions()
                        .stream()
                        .filter(transaction -> getAccountRequest.getId() == 0 || getAccountRequest.getId() == transaction.getSenderId() || getAccountRequest.getId() == transaction.getRecipientId())
                        .map(ProtoBuilder::buildUnconfirmedTransaction)
                        .collect(Collectors.toList()))
                .build();
    }
}
