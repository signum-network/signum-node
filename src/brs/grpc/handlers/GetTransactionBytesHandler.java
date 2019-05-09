package brs.grpc.handlers;

import brs.*;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.BrsApi;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

public class GetTransactionBytesHandler implements GrpcApiHandler<BrsApi.BasicTransaction, BrsApi.TransactionBytes> {

    private final Blockchain blockchain;

    public GetTransactionBytesHandler(Blockchain blockchain) {
        this.blockchain = blockchain;
    }

    @Override
    public BrsApi.TransactionBytes handleRequest(BrsApi.BasicTransaction basicTransaction) throws Exception {
        try {
            Transaction.Builder transactionBuilder = new Transaction.Builder(((byte) basicTransaction.getVersion()), basicTransaction.getSender().toByteArray(), basicTransaction.getAmount(), basicTransaction.getFee(), basicTransaction.getTimestamp(), ((short) basicTransaction.getDeadline()), Attachment.AbstractAttachment.parseProtobufMessage(basicTransaction.getAttachment()))
                    .recipientId(basicTransaction.getRecipient());

            if (basicTransaction.getReferencedTransactionFullHash().size() > 0) {
                transactionBuilder.referencedTransactionFullHash(basicTransaction.getReferencedTransactionFullHash().toByteArray());
            }

            int blockchainHeight = blockchain.getHeight();

            for (Any appendix : basicTransaction.getAppendagesList()) {
                try {
                    if (appendix.is(BrsApi.MessageAppendix.class)) {
                        transactionBuilder.message(new Appendix.Message(appendix.unpack(BrsApi.MessageAppendix.class), blockchainHeight));
                    } else if (appendix.is(BrsApi.EncryptedMessageAppendix.class)) {
                        BrsApi.EncryptedMessageAppendix encryptedMessageAppendix = appendix.unpack(BrsApi.EncryptedMessageAppendix.class);
                        switch (encryptedMessageAppendix.getType()) {
                            case TO_RECIPIENT:
                                transactionBuilder.encryptedMessage(new Appendix.EncryptedMessage(encryptedMessageAppendix, blockchainHeight));
                            case TO_SELF:
                                transactionBuilder.encryptToSelfMessage(new Appendix.EncryptToSelfMessage(encryptedMessageAppendix, blockchainHeight));
                        }
                    } else if (appendix.is(BrsApi.PublicKeyAnnouncementAppendix.class)) {
                        transactionBuilder.publicKeyAnnouncement(new Appendix.PublicKeyAnnouncement(appendix.unpack(BrsApi.PublicKeyAnnouncementAppendix.class), blockchainHeight));
                    }
                } catch (InvalidProtocolBufferException e) {
                    throw new ApiException("Failed to unpack Any: " + e.getMessage());
                }
            }

            return BrsApi.TransactionBytes.newBuilder()
                    .setTransactionBytes(ByteString.copyFrom(transactionBuilder.build().getBytes()))
                    .build();
        } catch (BurstException.NotValidException e) {
            throw new ApiException("Transaction not valid: " + e.getMessage());
        } catch (InvalidProtocolBufferException e) {
            throw new ApiException("Could not parse an Any: " + e.getMessage());
        }
    }
}
