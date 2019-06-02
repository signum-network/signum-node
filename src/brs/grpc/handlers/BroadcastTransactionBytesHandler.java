package brs.grpc.handlers;

import brs.Transaction;
import brs.TransactionProcessor;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;

public class BroadcastTransactionBytesHandler implements GrpcApiHandler<BrsApi.TransactionBytes, BrsApi.TransactionBroadcastResult> {

    private final TransactionProcessor transactionProcessor;

    public BroadcastTransactionBytesHandler(TransactionProcessor transactionProcessor) {
        this.transactionProcessor = transactionProcessor;
    }

    @Override
    public BrsApi.TransactionBroadcastResult handleRequest(BrsApi.TransactionBytes transactionBytes) throws Exception {
        return BrsApi.TransactionBroadcastResult.newBuilder()
                .setNumberOfPeersSentTo(transactionProcessor.broadcast(Transaction.parseTransaction(transactionBytes.getTransactionBytes().toByteArray())))
                .build();
    }
}
