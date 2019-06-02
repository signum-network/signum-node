package brs.grpc.handlers;

import brs.Blockchain;
import brs.TransactionProcessor;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;

public class BroadcastTransactionHandler implements GrpcApiHandler<BrsApi.BasicTransaction, BrsApi.TransactionBroadcastResult> {

    private final TransactionProcessor transactionProcessor;
    private final Blockchain blockchain;

    public BroadcastTransactionHandler(TransactionProcessor transactionProcessor, Blockchain blockchain) {
        this.transactionProcessor = transactionProcessor;
        this.blockchain = blockchain;
    }

    @Override
    public BrsApi.TransactionBroadcastResult handleRequest(BrsApi.BasicTransaction basicTransaction) throws Exception {
        return BrsApi.TransactionBroadcastResult.newBuilder()
                .setNumberOfPeersSentTo(transactionProcessor.broadcast(ProtoBuilder.parseBasicTransaction(blockchain, basicTransaction)))
                .build();
    }
}
