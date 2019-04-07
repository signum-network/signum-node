package brs.grpc.handlers;

import brs.Transaction;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;

public class ParseTransactionHandler implements GrpcApiHandler<BrsApi.TransactionBytes, BrsApi.BasicTransaction> {
    @Override
    public BrsApi.BasicTransaction handleRequest(BrsApi.TransactionBytes transactionBytes) throws Exception {
        return ProtoBuilder.buildBasicTransaction(Transaction.parseTransaction(transactionBytes.getTransactionBytes().toByteArray()));
    }
}
