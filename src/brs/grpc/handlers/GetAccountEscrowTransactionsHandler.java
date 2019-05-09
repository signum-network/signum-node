package brs.grpc.handlers;

import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.EscrowService;

public class GetAccountEscrowTransactionsHandler implements GrpcApiHandler<BrsApi.GetAccountRequest, BrsApi.EscrowTransactions> {

    private final EscrowService escrowService;

    public GetAccountEscrowTransactionsHandler(EscrowService escrowService) {
        this.escrowService = escrowService;
    }

    @Override
    public BrsApi.EscrowTransactions handleRequest(BrsApi.GetAccountRequest request) throws Exception {
        long accountId = request.getAccountId();
        BrsApi.EscrowTransactions.Builder builder = BrsApi.EscrowTransactions.newBuilder();
        escrowService.getEscrowTransactionsByParticipant(accountId)
                .forEach(escrow -> builder.addEscrowTransactions(ProtoBuilder.buildEscrowTransaction(escrow)));
        return builder.build();
    }
}
