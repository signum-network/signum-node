package brs.grpc.handlers;

import brs.TransactionProcessor;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.IndirectIncomingService;

import java.util.stream.Collectors;

public class GetUnconfirmedTransactionsHandler implements GrpcApiHandler<BrsApi.GetAccountRequest, BrsApi.UnconfirmedTransactions> {

    private final IndirectIncomingService indirectIncomingService;
    private final TransactionProcessor transactionProcessor;

    public GetUnconfirmedTransactionsHandler(IndirectIncomingService indirectIncomingService, TransactionProcessor transactionProcessor) {
        this.indirectIncomingService = indirectIncomingService;
        this.transactionProcessor = transactionProcessor;
    }

    @Override
    public BrsApi.UnconfirmedTransactions handleRequest(BrsApi.GetAccountRequest getAccountRequest) throws Exception {
        return BrsApi.UnconfirmedTransactions.newBuilder()
                .addAllUnconfirmedTransactions(transactionProcessor.getAllUnconfirmedTransactions()
                        .stream()
                        .filter(transaction -> getAccountRequest.getAccountId() == 0
                                || getAccountRequest.getAccountId() == transaction.getSenderId()
                                || getAccountRequest.getAccountId() == transaction.getRecipientId()
                                || indirectIncomingService.isIndirectlyReceiving(transaction, getAccountRequest.getAccountId()))
                        .map(ProtoBuilder::buildUnconfirmedTransaction)
                        .collect(Collectors.toList()))
                .build();
    }
}
