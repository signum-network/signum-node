package brs.grpc.handlers;

import brs.Blockchain;
import brs.Transaction;
import brs.TransactionProcessor;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.util.Convert;

public class GetTransactionHandler implements GrpcApiHandler<BrsApi.GetTransactionRequest, BrsApi.Transaction> {

    private final Blockchain blockchain;
    private final TransactionProcessor transactionProcessor;

    public GetTransactionHandler(Blockchain blockchain, TransactionProcessor transactionProcessor) {
        this.blockchain = blockchain;
        this.transactionProcessor = transactionProcessor;
    }

    @Override
    public BrsApi.Transaction handleRequest(BrsApi.GetTransactionRequest request) throws Exception {
        return ProtoBuilder.buildTransaction(getTransaction(blockchain, transactionProcessor, request), blockchain.getHeight());
    }

    public static Transaction getTransaction(Blockchain blockchain, TransactionProcessor transactionProcessor, BrsApi.GetTransactionRequest request) throws ApiException {
        long id = request.getTransactionId();
        byte[] fullHash = request.getFullHash().toByteArray();
        Transaction transaction;
        if (fullHash.length > 0) {
            transaction = blockchain.getTransactionByFullHash(Convert.toHexString(fullHash));
        } else if (id != 0) {
            transaction = blockchain.getTransaction(id);
            if (transaction == null) transaction = transactionProcessor.getUnconfirmedTransaction(id);
        } else {
            throw new ApiException("Could not find transaction");
        }
        if (transaction == null) {
            throw new ApiException("Could not find transaction");
        }
        return transaction;
    }
}
