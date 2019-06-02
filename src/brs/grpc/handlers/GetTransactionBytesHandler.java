package brs.grpc.handlers;

import brs.Blockchain;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import com.google.protobuf.ByteString;

public class GetTransactionBytesHandler implements GrpcApiHandler<BrsApi.BasicTransaction, BrsApi.TransactionBytes> {

    private final Blockchain blockchain;

    public GetTransactionBytesHandler(Blockchain blockchain) {
        this.blockchain = blockchain;
    }

    @Override
    public BrsApi.TransactionBytes handleRequest(BrsApi.BasicTransaction basicTransaction) throws Exception {
        return BrsApi.TransactionBytes.newBuilder()
                .setTransactionBytes(ByteString.copyFrom(ProtoBuilder.parseBasicTransaction(blockchain, basicTransaction).getBytes()))
                .build();
    }
}
