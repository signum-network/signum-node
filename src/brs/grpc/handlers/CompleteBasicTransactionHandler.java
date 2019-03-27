package brs.grpc.handlers;

import brs.Attachment;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.BrsApi;
import brs.services.TimeService;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;

public class CompleteBasicTransactionHandler implements GrpcApiHandler<BrsApi.BasicTransaction, BrsApi.BasicTransaction> {

    private final TimeService timeService;

    public CompleteBasicTransactionHandler(TimeService timeService) {
        this.timeService = timeService;
    }

    @Override
    public BrsApi.BasicTransaction handleRequest(BrsApi.BasicTransaction basicTransaction) throws Exception {
        try {
            BrsApi.BasicTransaction.Builder builder = basicTransaction.toBuilder();
            Attachment.AbstractAttachment attachment = Attachment.AbstractAttachment.parseProtobufMessage(basicTransaction.getAttachment());
            builder.setVersion(1); // TODO
            builder.setType(attachment.getTransactionType().getType());
            builder.setSubtype(attachment.getTransactionType().getSubtype());
            builder.setTimestamp(timeService.getEpochTime());
            if (builder.getAttachment().equals(Any.getDefaultInstance())) {
                builder.setAttachment(Attachment.ORDINARY_PAYMENT.getProtobufMessage());
            }
            return builder.build();
        } catch (InvalidProtocolBufferException e) {
            throw new ApiException("Could not parse an Any: " + e.getMessage());
        }
    }
}
