package brs.grpc.handlers;

import brs.Attachment;
import brs.BurstException;
import brs.Transaction;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.BrsApi;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

public class GetTransactionBytesHandler implements GrpcApiHandler<BrsApi.BasicTransaction, BrsApi.TransactionBytes> {

    @Override
    public BrsApi.TransactionBytes handleRequest(BrsApi.BasicTransaction basicTransaction) throws Exception {
        try {
            Transaction transaction = new Transaction.Builder(((byte) basicTransaction.getVersion()), basicTransaction.getSender().toByteArray(), basicTransaction.getAmount(), basicTransaction.getFee(), basicTransaction.getTimestamp(), ((short) basicTransaction.getDeadline()), Attachment.AbstractAttachment.parseProtobufMessage(basicTransaction.getAttachment()))
                    // TODO other fields such as appendixes
                    .build();

            return BrsApi.TransactionBytes.newBuilder()
                    .setTransactionBytes(ByteString.copyFrom(transaction.getBytes()))
                    .build();
        } catch (BurstException.NotValidException e) {
            throw new ApiException("Transaction not valid: " + e.getMessage());
        } catch (InvalidProtocolBufferException e) {
            throw new ApiException("Could not parse an Any: " + e.getMessage());
        }
    }
}
