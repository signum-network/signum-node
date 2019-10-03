package brs.grpc.handlers

import brs.Attachment
import brs.DependencyProvider
import brs.grpc.GrpcApiHandler
import brs.grpc.proto.ApiException
import brs.grpc.proto.BrsApi
import burst.kit.crypto.BurstCrypto
import com.google.protobuf.Any
import com.google.protobuf.InvalidProtocolBufferException

class CompleteBasicTransactionHandler(private val dp: DependencyProvider) : GrpcApiHandler<BrsApi.BasicTransaction, BrsApi.BasicTransaction> {
    override suspend fun handleRequest(request: BrsApi.BasicTransaction): BrsApi.BasicTransaction {
        try {
            val builder = request.toBuilder()
            val attachment = Attachment.AbstractAttachment.parseProtobufMessage(dp, request.attachment)
            if (builder.deadline == 0) {
                builder.deadline = 1440
            }
            if (builder.senderId == 0L) {
                builder.senderId = BurstCrypto.getInstance().getBurstAddressFromPublic(builder.senderPublicKey.toByteArray()).burstID.signedLongId
            }
            builder.version = dp.transactionProcessor.getTransactionVersion(dp.blockchain.height)
            builder.type = attachment.transactionType.type.toInt()
            builder.subtype = attachment.transactionType.subtype.toInt()
            builder.timestamp = dp.timeService.epochTime
            if (builder.attachment == Any.getDefaultInstance()) {
                builder.attachment = Attachment.OrdinaryPayment(dp).protobufMessage
            }
            return builder.build()
        } catch (e: InvalidProtocolBufferException) {
            throw ApiException("Could not parse an Any: " + e.message)
        }
    }
}