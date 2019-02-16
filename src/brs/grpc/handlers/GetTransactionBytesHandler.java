package brs.grpc.handlers;

import brs.Blockchain;
import brs.TransactionProcessor;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import com.google.protobuf.ByteString;

public class GetTransactionBytesHandler implements GrpcApiHandler<BrsApi.GetTransactionRequest, BrsApi.TransactionBytes> {

    private final Blockchain blockchain;
    private final TransactionProcessor transactionProcessor;

    public GetTransactionBytesHandler(Blockchain blockchain, TransactionProcessor transactionProcessor) {
        this.blockchain = blockchain;
        this.transactionProcessor = transactionProcessor;
    }

    @Override
    public BrsApi.TransactionBytes handleRequest(BrsApi.GetTransactionRequest request) throws Exception {
        return BrsApi.TransactionBytes.newBuilder()
                .setTransactionBytes(ByteString.copyFrom(GetTransactionHandler.getTransaction(blockchain, transactionProcessor, request).getBytes()))
                .build();
    }
}
