package brs.transaction.appendix

import brs.api.grpc.proto.BrsApi
import brs.api.grpc.service.ProtoBuilder
import brs.api.grpc.service.toByteString
import brs.api.http.common.Parameters.ALIAS_PARAMETER
import brs.api.http.common.Parameters.AMOUNT_PLANCK_PARAMETER
import brs.api.http.common.Parameters.ASSET_PARAMETER
import brs.api.http.common.Parameters.COMMENT_PARAMETER
import brs.api.http.common.Parameters.CREATION_BYTES_PARAMETER
import brs.api.http.common.Parameters.DEADLINE_ACTION_PARAMETER
import brs.api.http.common.Parameters.DEADLINE_PARAMETER
import brs.api.http.common.Parameters.DECIMALS_PARAMETER
import brs.api.http.common.Parameters.DECISION_PARAMETER
import brs.api.http.common.Parameters.DELIVERY_DEADLINE_TIMESTAMP_PARAMETER
import brs.api.http.common.Parameters.DELTA_QUANTITY_PARAMETER
import brs.api.http.common.Parameters.DESCRIPTION_PARAMETER
import brs.api.http.common.Parameters.DISCOUNT_PLANCK_PARAMETER
import brs.api.http.common.Parameters.ESCROW_ID_PARAMETER
import brs.api.http.common.Parameters.FREQUENCY_PARAMETER
import brs.api.http.common.Parameters.GOODS_DATA_PARAMETER
import brs.api.http.common.Parameters.GOODS_IS_TEXT_PARAMETER
import brs.api.http.common.Parameters.GOODS_NONCE_PARAMETER
import brs.api.http.common.Parameters.GOODS_PARAMETER
import brs.api.http.common.Parameters.NAME_PARAMETER
import brs.api.http.common.Parameters.ORDER_PARAMETER
import brs.api.http.common.Parameters.PERIOD_PARAMETER
import brs.api.http.common.Parameters.PRICE_PLANCK_PARAMETER
import brs.api.http.common.Parameters.PURCHASE_PARAMETER
import brs.api.http.common.Parameters.QUANTITY_PARAMETER
import brs.api.http.common.Parameters.QUANTITY_QNT_PARAMETER
import brs.api.http.common.Parameters.RECIPIENTS_PARAMETER
import brs.api.http.common.Parameters.RECIPIENTS_RESPONSE
import brs.api.http.common.Parameters.REFUND_PLANCK_PARAMETER
import brs.api.http.common.Parameters.REQUIRED_SIGNERS_PARAMETER
import brs.api.http.common.Parameters.SIGNERS_PARAMETER
import brs.api.http.common.Parameters.SUBSCRIPTION_ID_PARAMETER
import brs.api.http.common.Parameters.URI_PARAMETER
import brs.api.http.common.ResultFields.ALIAS_RESPONSE
import brs.api.http.common.ResultFields.AMOUNT_PLANCK_RESPONSE
import brs.api.http.common.ResultFields.ASSET_RESPONSE
import brs.api.http.common.ResultFields.COMMENT_RESPONSE
import brs.api.http.common.ResultFields.CREATION_BYTES_RESPONSE
import brs.api.http.common.ResultFields.DEADLINE_ACTION_RESPONSE
import brs.api.http.common.ResultFields.DEADLINE_RESPONSE
import brs.api.http.common.ResultFields.DECIMALS_RESPONSE
import brs.api.http.common.ResultFields.DECISION_RESPONSE
import brs.api.http.common.ResultFields.DELIVERY_DEADLINE_TIMESTAMP_RESPONSE
import brs.api.http.common.ResultFields.DELTA_QUANTITY_RESPONSE
import brs.api.http.common.ResultFields.DESCRIPTION_RESPONSE
import brs.api.http.common.ResultFields.DISCOUNT_PLANCK_RESPONSE
import brs.api.http.common.ResultFields.ESCROW_ID_RESPONSE
import brs.api.http.common.ResultFields.FREQUENCY_RESPONSE
import brs.api.http.common.ResultFields.GOODS_DATA_RESPONSE
import brs.api.http.common.ResultFields.GOODS_IS_TEXT_RESPONSE
import brs.api.http.common.ResultFields.GOODS_NONCE_RESPONSE
import brs.api.http.common.ResultFields.GOODS_RESPONSE
import brs.api.http.common.ResultFields.NAME_RESPONSE
import brs.api.http.common.ResultFields.ORDER_RESPONSE
import brs.api.http.common.ResultFields.PERIOD_RESPONSE
import brs.api.http.common.ResultFields.PRICE_PLANCK_RESPONSE
import brs.api.http.common.ResultFields.PURCHASE_RESPONSE
import brs.api.http.common.ResultFields.QUANTITY_QNT_RESPONSE
import brs.api.http.common.ResultFields.QUANTITY_RESPONSE
import brs.api.http.common.ResultFields.REFUND_PLANCK_RESPONSE
import brs.api.http.common.ResultFields.REQUIRED_SIGNERS_RESPONSE
import brs.api.http.common.ResultFields.SIGNERS_RESPONSE
import brs.api.http.common.ResultFields.SUBSCRIPTION_ID_RESPONSE
import brs.api.http.common.ResultFields.TAGS_RESPONSE
import brs.api.http.common.ResultFields.URI_RESPONSE
import brs.entity.*
import brs.objects.Constants
import brs.transaction.type.TransactionType
import brs.util.BurstException
import brs.util.convert.*
import brs.util.json.*
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.protobuf.Any
import com.google.protobuf.InvalidProtocolBufferException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.*


interface Attachment : Appendix {

    val transactionType: TransactionType

    abstract class AbstractAttachment : Appendix.AbstractAppendix,
        Attachment {
        private val dp: DependencyProvider

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            buffer,
            transactionVersion
        ) {
            this.dp = dp
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(attachmentData) {
            this.dp = dp
        }

        internal constructor(dp: DependencyProvider, version: Byte) : super(version) {
            this.dp = dp
        }

        internal constructor(dp: DependencyProvider, blockchainHeight: Int) : super(dp, blockchainHeight) {
            this.dp = dp
        }

        override fun validate(transaction: Transaction) {
            transactionType.preValidateAttachment(transaction,)
        }

        override fun apply(transaction: Transaction, senderAccount: Account, recipientAccount: Account) {
            transactionType.apply(transaction, senderAccount, recipientAccount)
        }

        // TODO this is super inefficient. All of the validation etc. Functions should be moved into the attachment
        final override val transactionType: TransactionType
            get() {
                val type = transactionTypeAndSubtype
                return dp.transactionTypes[type.first]!![type.second]!!
            }

        abstract val transactionTypeAndSubtype: Pair<Byte, Byte>

        companion object {
            @Throws(InvalidProtocolBufferException::class, BurstException.NotValidException::class)
            fun parseProtobufMessage(dp: DependencyProvider, attachment: Any): AbstractAttachment {
                // Yes, this is fairly horrible. I wish there was a better way to do this but any does not let us switch on its contained class.
                return when {
                    attachment.`is`(BrsApi.OrdinaryPaymentAttachment::class.java) -> return OrdinaryPayment(
                        dp
                    )
                    attachment.`is`(BrsApi.ArbitraryMessageAttachment::class.java) -> return ArbitraryMessage(
                        dp
                    )
                    attachment.`is`(BrsApi.ATPaymentAttachment::class.java) -> return AtPayment(
                        dp
                    )
                    attachment.`is`(BrsApi.MultiOutAttachment::class.java) -> return PaymentMultiOutCreation(
                        dp,
                        attachment.unpack(BrsApi.MultiOutAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.MultiOutSameAttachment::class.java) -> return PaymentMultiSameOutCreation(
                        dp,
                        attachment.unpack(BrsApi.MultiOutSameAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.AliasAssignmentAttachment::class.java) -> return MessagingAliasAssignment(
                        dp,
                        attachment.unpack(BrsApi.AliasAssignmentAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.AliasSellAttachment::class.java) -> return MessagingAliasSell(
                        dp,
                        attachment.unpack(BrsApi.AliasSellAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.AliasBuyAttachment::class.java) -> return MessagingAliasBuy(
                        dp,
                        attachment.unpack(BrsApi.AliasBuyAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.AccountInfoAttachment::class.java) -> return MessagingAccountInfo(
                        dp,
                        attachment.unpack(BrsApi.AccountInfoAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.AssetIssuanceAttachment::class.java) -> return ColoredCoinsAssetIssuance(
                        dp,
                        attachment.unpack(BrsApi.AssetIssuanceAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.AssetTransferAttachment::class.java) -> return ColoredCoinsAssetTransfer(
                        dp,
                        attachment.unpack(BrsApi.AssetTransferAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.AssetOrderPlacementAttachment::class.java) -> {
                        val placementAttachment = attachment.unpack(BrsApi.AssetOrderPlacementAttachment::class.java)
                        return when (placementAttachment.type) {
                            BrsApi.OrderType.ASK -> ColoredCoinsAskOrderPlacement(
                                dp,
                                placementAttachment
                            )
                            BrsApi.OrderType.BID -> ColoredCoinsBidOrderPlacement(
                                dp,
                                placementAttachment
                            )
                            else -> throw IllegalArgumentException("Attachment type must be ASK or BID")
                        }
                    }
                    attachment.`is`(BrsApi.AssetOrderCancellationAttachment::class.java) -> {
                        val placementAttachment = attachment.unpack(BrsApi.AssetOrderCancellationAttachment::class.java)
                        return when (placementAttachment.type) {
                            BrsApi.OrderType.ASK -> ColoredCoinsAskOrderCancellation(
                                dp,
                                placementAttachment
                            )
                            BrsApi.OrderType.BID -> ColoredCoinsBidOrderCancellation(
                                dp,
                                placementAttachment
                            )
                            else -> throw IllegalArgumentException("Attachment type must be ASK or BID")
                        }
                    }
                    attachment.`is`(BrsApi.DigitalGoodsListingAttachment::class.java) -> return DigitalGoodsListing(
                        dp,
                        attachment.unpack(BrsApi.DigitalGoodsListingAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.DigitalGoodsDelistingAttachment::class.java) -> return DigitalGoodsDelisting(
                        dp,
                        attachment.unpack(BrsApi.DigitalGoodsDelistingAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.DigitalGoodsPriceChangeAttachment::class.java) -> return DigitalGoodsPriceChange(
                        dp,
                        attachment.unpack(BrsApi.DigitalGoodsPriceChangeAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.DigitalGoodsQuantityChangeAttachment::class.java) -> return DigitalGoodsQuantityChange(
                        dp,
                        attachment.unpack(BrsApi.DigitalGoodsQuantityChangeAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.DigitalGoodsPurchaseAttachment::class.java) -> return DigitalGoodsPurchase(
                        dp,
                        attachment.unpack(BrsApi.DigitalGoodsPurchaseAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.DigitalGoodsDeliveryAttachment::class.java) -> return DigitalGoodsDelivery(
                        dp,
                        attachment.unpack(BrsApi.DigitalGoodsDeliveryAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.DigitalGoodsFeedbackAttachment::class.java) -> return DigitalGoodsFeedback(
                        dp,
                        attachment.unpack(BrsApi.DigitalGoodsFeedbackAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.DigitalGoodsRefundAttachment::class.java) -> return DigitalGoodsRefund(
                        dp,
                        attachment.unpack(BrsApi.DigitalGoodsRefundAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.EffectiveBalanceLeasingAttachment::class.java) -> return AccountControlEffectiveBalanceLeasing(
                        dp,
                        attachment.unpack(BrsApi.EffectiveBalanceLeasingAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.RewardRecipientAssignmentAttachment::class.java) -> return BurstMiningRewardRecipientAssignment(
                        dp,
                        attachment.unpack(BrsApi.RewardRecipientAssignmentAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.EscrowCreationAttachment::class.java) -> return AdvancedPaymentEscrowCreation(
                        dp,
                        attachment.unpack(BrsApi.EscrowCreationAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.EscrowSignAttachment::class.java) -> return AdvancedPaymentEscrowSign(
                        dp,
                        attachment.unpack(BrsApi.EscrowSignAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.EscrowResultAttachment::class.java) -> return AdvancedPaymentEscrowResult(
                        dp,
                        attachment.unpack(BrsApi.EscrowResultAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.SubscriptionSubscribeAttachment::class.java) -> return AdvancedPaymentSubscriptionSubscribe(
                        dp,
                        attachment.unpack(BrsApi.SubscriptionSubscribeAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.SubscriptionCancelAttachment::class.java) -> return AdvancedPaymentSubscriptionCancel(
                        dp,
                        attachment.unpack(BrsApi.SubscriptionCancelAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.SubscriptionPaymentAttachment::class.java) -> return AdvancedPaymentSubscriptionPayment(
                        dp,
                        attachment.unpack(BrsApi.SubscriptionPaymentAttachment::class.java)
                    )
                    attachment.`is`(BrsApi.ATCreationAttachment::class.java) -> return AutomatedTransactionsCreation(
                        dp,
                        attachment.unpack(BrsApi.ATCreationAttachment::class.java)
                    )
                    else -> OrdinaryPayment(dp)
                }
            }
        }
    }

    abstract class EmptyAttachment internal constructor(dp: DependencyProvider) : AbstractAttachment(dp, 0.toByte()) {

        override val mySize: Int
            get() = 0

        override fun putMyBytes(buffer: ByteBuffer) = Unit

        override fun putMyJSON(attachment: JsonObject) = Unit

        override fun verifyVersion(transactionVersion: Byte): Boolean {
            return true
        }
    }

    class PaymentMultiOutCreation : AbstractAttachment {
        private val recipients = mutableListOf<List<Long>>()

        override fun getAppendixName(): String = "MultiOutCreation"

        override val mySize: Int
            get() = 1 + recipients.size * 16

        val amountPlanck: Long
            get() {
                var amountPlanck: Long = 0
                for (recipient in recipients) {
                    amountPlanck = amountPlanck.safeAdd(recipient[1])
                }
                return amountPlanck
            }

        override val protobufMessage: Any
            get() {
                val builder = BrsApi.MultiOutAttachment.newBuilder()
                    .setVersion(version.toInt())
                for (recipient in recipients) {
                    builder.addRecipients(
                        BrsApi.MultiOutAttachment.MultiOutRecipient.newBuilder()
                            .setRecipient(recipient[0])
                            .setAmount(recipient[1])
                    )
                }
                return Any.pack(builder.build())
            }

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {

            val numberOfRecipients = buffer.get().toUnsignedInt()
            val recipientOf = mutableMapOf<Long, Boolean>()

            for (i in 0 until numberOfRecipients) {
                val recipientId = buffer.long
                val amountPlanck = buffer.long

                if (recipientOf.containsKey(recipientId))
                    throw BurstException.NotValidException("Duplicate recipient on multi out transaction")

                if (amountPlanck <= 0)
                    throw BurstException.NotValidException("Insufficient amountPlanck on multi out transaction")

                recipientOf[recipientId] = true
                this.recipients.add(listOf(recipientId, amountPlanck))
            }
            if (recipients.size > Constants.MAX_MULTI_OUT_RECIPIENTS || recipients.size <= 1) {
                throw BurstException.NotValidException(
                    "Invalid number of recipients listed on multi out transaction"
                )
            }
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {

            val receipientsJson = attachmentData.get(RECIPIENTS_PARAMETER).mustGetAsJsonArray(RECIPIENTS_PARAMETER)
            val recipientOf = mutableMapOf<Long, Boolean>()

            for (recipientObject in receipientsJson) {
                val recipient = recipientObject.mustGetAsJsonArray("recipient")

                val recipientId = BigInteger(recipient.get(0).mustGetAsString("recipientId")).toLong()
                val amountPlanck = recipient.get(1).mustGetAsLong("amountPlanck")
                if (recipientOf.containsKey(recipientId))
                    throw BurstException.NotValidException("Duplicate recipient on multi out transaction")

                if (amountPlanck <= 0)
                    throw BurstException.NotValidException("Insufficient amountPlanck on multi out transaction")

                recipientOf[recipientId] = true
                this.recipients.add(listOf(recipientId, amountPlanck))
            }
            if (receipientsJson.size() > Constants.MAX_MULTI_OUT_RECIPIENTS || receipientsJson.size() <= 1) {
                throw BurstException.NotValidException("Invalid number of recipients listed on multi out transaction")
            }
        }

        constructor(dp: DependencyProvider, recipients: Map<Long, Long>, blockchainHeight: Int) : super(
            dp,
            blockchainHeight
        ) {
            val recipientOf = mutableMapOf<Long, Boolean>()
            for ((recipientId, amountPlanck) in recipients) {
                if (recipientOf.containsKey(recipientId))
                    throw BurstException.NotValidException("Duplicate recipient on multi out transaction")

                if (amountPlanck <= 0)
                    throw BurstException.NotValidException("Insufficient amountPlanck on multi out transaction")

                recipientOf[recipientId] = true
                this.recipients.add(listOf(recipientId, amountPlanck))
            }
            if (recipients.size > Constants.MAX_MULTI_OUT_RECIPIENTS || recipients.size <= 1) {
                throw BurstException.NotValidException("Invalid number of recipients listed on multi out transaction")
            }
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.MultiOutAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            val recipientOf = mutableMapOf<Long, Boolean>()
            for (recipient in attachment.recipientsList) {
                val recipientId = recipient.recipient
                val amountPlanck = recipient.amount
                if (recipientOf.containsKey(recipientId))
                    throw BurstException.NotValidException("Duplicate recipient on multi out transaction")

                if (amountPlanck <= 0)
                    throw BurstException.NotValidException("Insufficient amountPlanck on multi out transaction")

                recipientOf[recipientId] = true
                this.recipients.add(listOf(recipientId, amountPlanck))
            }
            if (recipients.size > Constants.MAX_MULTI_OUT_RECIPIENTS || recipients.size <= 1) {
                throw BurstException.NotValidException("Invalid number of recipients listed on multi out transaction")
            }
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            buffer.put(this.recipients.size.toByte())
            this.recipients.forEach { a ->
                buffer.putLong(a[0])
                buffer.putLong(a[1])
            }
        }

        override fun putMyJSON(attachment: JsonObject) {
            val recipientsJSON = JsonArray()

            this.recipients.map { recipient ->
                val recipientJSON = JsonArray()
                recipientJSON.add(recipient[0].toUnsignedString())
                recipientJSON.add(recipient[1].toString())
                recipientJSON
            }.forEach { recipientsJSON.add(it) }

            attachment.add(RECIPIENTS_RESPONSE, recipientsJSON)
        }

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_PAYMENT, TransactionType.SUBTYPE_PAYMENT_ORDINARY_PAYMENT_MULTI_OUT)

        fun getRecipients(): Collection<List<Long>> {
            return recipients
        }
    }

    open class PaymentMultiSameOutCreation : AbstractAttachment {

        private val recipients = mutableListOf<Long>()

        override fun getAppendixName(): String = "MultiSameOutCreation"

        override val mySize: Int
            get() = 1 + recipients.size * 8

        override val transactionTypeAndSubtype =
            Pair(TransactionType.TYPE_PAYMENT, TransactionType.SUBTYPE_PAYMENT_ORDINARY_PAYMENT_MULTI_SAME_OUT)

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.MultiOutSameAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .addAllRecipients(recipients)
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {

            val numberOfRecipients = buffer.get().toUnsignedInt()
            val recipientOf = mutableMapOf<Long, Boolean>()

            for (i in 0 until numberOfRecipients) {
                val recipientId = buffer.long

                if (recipientOf.containsKey(recipientId))
                    throw BurstException.NotValidException("Duplicate recipient on multi same out transaction")

                recipientOf[recipientId] = true
                this.recipients.add(recipientId)
            }
            if (recipients.size > Constants.MAX_MULTI_SAME_OUT_RECIPIENTS || recipients.size <= 1) {
                throw BurstException.NotValidException("Invalid number of recipients listed on multi same out transaction")
            }
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {

            val recipientsJson = attachmentData.get(RECIPIENTS_PARAMETER).mustGetAsJsonArray(RECIPIENTS_PARAMETER)
            val recipientOf = mutableMapOf<Long, Boolean>()

            for (recipient in recipientsJson) {
                val recipientId = recipient.mustGetAsString("recipient").parseUnsignedLong()
                if (recipientOf.containsKey(recipientId))
                    throw BurstException.NotValidException("Duplicate recipient on multi same out transaction")

                recipientOf[recipientId] = true
                this.recipients.add(recipientId)
            }
            if (recipientsJson.size() > Constants.MAX_MULTI_SAME_OUT_RECIPIENTS || recipientsJson.size() <= 1) {
                throw BurstException.NotValidException(
                    "Invalid number of recipients listed on multi same out transaction"
                )
            }
        }

        constructor(dp: DependencyProvider, recipients: Collection<Long>, blockchainHeight: Int) : super(
            dp,
            blockchainHeight
        ) {

            val recipientOf = mutableMapOf<Long, Boolean>()
            for (recipientId in recipients) {
                if (recipientOf.containsKey(recipientId))
                    throw BurstException.NotValidException("Duplicate recipient on multi same out transaction")

                recipientOf[recipientId] = true
                this.recipients.add(recipientId)
            }
            if (recipients.size > Constants.MAX_MULTI_SAME_OUT_RECIPIENTS || recipients.size <= 1) {
                throw BurstException.NotValidException(
                    "Invalid number of recipients listed on multi same out transaction"
                )
            }
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.MultiOutSameAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            val recipientOf = mutableMapOf<Long, Boolean>()
            for (recipientId in attachment.recipientsList) {
                if (recipientOf.containsKey(recipientId))
                    throw BurstException.NotValidException("Duplicate recipient on multi same out transaction")

                recipientOf[recipientId] = true
                this.recipients.add(recipientId)
            }
            if (recipients.size > Constants.MAX_MULTI_SAME_OUT_RECIPIENTS || recipients.size <= 1) {
                throw BurstException.NotValidException(
                    "Invalid number of recipients listed on multi same out transaction"
                )
            }
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            buffer.put(this.recipients.size.toByte())
            this.recipients.forEach { buffer.putLong(it) }
        }

        override fun putMyJSON(attachment: JsonObject) {
            val recipients = JsonArray()
            this.recipients.forEach { a -> recipients.add(a.toUnsignedString()) }
            attachment.add(RECIPIENTS_RESPONSE, recipients)
        }

        fun getRecipients(): Collection<Long> {
            return recipients
        }
    }

    class MessagingAliasAssignment : AbstractAttachment {

        val aliasName: String
        val aliasURI: String

        override fun getAppendixName(): String = "AliasAssignment"

        override val mySize: Int
            get() = 1 + aliasName.toBytes().size + 2 + aliasURI.toBytes().size

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_MESSAGING, TransactionType.SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT)

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.AliasAssignmentAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setName(aliasName)
                    .setUri(aliasURI)
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            aliasName = buffer.readString(buffer.get().toInt(), Constants.MAX_ALIAS_LENGTH).trim { it <= ' ' }
            aliasURI = buffer.readString(buffer.short.toInt(), Constants.MAX_ALIAS_URI_LENGTH).trim { it <= ' ' }
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            aliasName = attachmentData.get(ALIAS_PARAMETER).safeGetAsString().orEmpty().trim { it <= ' ' }
            aliasURI = attachmentData.get(URI_PARAMETER).safeGetAsString().orEmpty().trim { it <= ' ' }
        }

        constructor(dp: DependencyProvider, aliasName: String, aliasURI: String, blockchainHeight: Int) : super(
            dp,
            blockchainHeight
        ) {
            this.aliasName = aliasName.trim { it <= ' ' }
            this.aliasURI = aliasURI.trim { it <= ' ' }
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.AliasAssignmentAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.aliasName = attachment.name
            this.aliasURI = attachment.uri
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            val alias = this.aliasName.toBytes()
            val uri = this.aliasURI.toBytes()
            buffer.put(alias.size.toByte())
            buffer.put(alias)
            buffer.putShort(uri.size.toShort())
            buffer.put(uri)
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(ALIAS_RESPONSE, aliasName)
            attachment.addProperty(URI_RESPONSE, aliasURI)
        }
    }

    class MessagingAliasSell : AbstractAttachment {

        val aliasName: String
        val pricePlanck: Long

        override fun getAppendixName(): String = "AliasSell"

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_MESSAGING, TransactionType.SUBTYPE_MESSAGING_ALIAS_SELL)

        override val mySize: Int
            get() = 1 + aliasName.toBytes().size + 8

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.AliasSellAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setName(aliasName)
                    .setPrice(pricePlanck)
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.aliasName = buffer.readString(buffer.get().toInt(), Constants.MAX_ALIAS_LENGTH)
            this.pricePlanck = buffer.long
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.aliasName = attachmentData.get(ALIAS_PARAMETER).safeGetAsString().orEmpty()
            this.pricePlanck = attachmentData.get(PRICE_PLANCK_PARAMETER).mustGetAsLong(PRICE_PLANCK_PARAMETER)
        }

        constructor(dp: DependencyProvider, aliasName: String, pricePlanck: Long, blockchainHeight: Int) : super(
            dp,
            blockchainHeight
        ) {
            this.aliasName = aliasName
            this.pricePlanck = pricePlanck
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.AliasSellAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.aliasName = attachment.name
            this.pricePlanck = attachment.price
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            val aliasBytes = aliasName.toBytes()
            buffer.put(aliasBytes.size.toByte())
            buffer.put(aliasBytes)
            buffer.putLong(pricePlanck)
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(ALIAS_RESPONSE, aliasName)
            attachment.addProperty(PRICE_PLANCK_RESPONSE, pricePlanck)
        }
    }

    class MessagingAliasBuy : AbstractAttachment {

        val aliasName: String

        override fun getAppendixName(): String = "AliasBuy"

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_MESSAGING, TransactionType.SUBTYPE_MESSAGING_ALIAS_BUY)

        override val mySize: Int
            get() = 1 + aliasName.toBytes().size

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.AliasBuyAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setName(aliasName)
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.aliasName = buffer.readString(buffer.get().toInt(), Constants.MAX_ALIAS_LENGTH)
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.aliasName = attachmentData.get(ALIAS_PARAMETER).safeGetAsString().orEmpty()
        }

        constructor(dp: DependencyProvider, aliasName: String, blockchainHeight: Int) : super(dp, blockchainHeight) {
            this.aliasName = aliasName
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.AliasBuyAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.aliasName = attachment.name
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            val aliasBytes = aliasName.toBytes()
            buffer.put(aliasBytes.size.toByte())
            buffer.put(aliasBytes)
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(ALIAS_RESPONSE, aliasName)
        }
    }

    class MessagingAccountInfo : AbstractAttachment {

        val name: String
        val description: String

        override fun getAppendixName(): String = "AccountInfo"

        override val mySize: Int
            get() = 1 + name.toBytes().size + 2 + description.toBytes().size

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_MESSAGING, TransactionType.SUBTYPE_MESSAGING_ACCOUNT_INFO)

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.AccountInfoAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setName(name)
                    .setDescription(description)
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.name = buffer.readString(buffer.get().toInt(), Constants.MAX_ACCOUNT_NAME_LENGTH)
            this.description = buffer.readString(buffer.short.toInt(), Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH)
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.name = attachmentData.get(NAME_PARAMETER).safeGetAsString().orEmpty()
            this.description = attachmentData.get(DESCRIPTION_PARAMETER).safeGetAsString().orEmpty()
        }

        constructor(dp: DependencyProvider, name: String, description: String, blockchainHeight: Int) : super(
            dp,
            blockchainHeight
        ) {
            this.name = name
            this.description = description
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.AccountInfoAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.name = attachment.name
            this.description = attachment.description
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            val putName = this.name.toBytes()
            val putDescription = this.description.toBytes()
            buffer.put(putName.size.toByte())
            buffer.put(putName)
            buffer.putShort(putDescription.size.toShort())
            buffer.put(putDescription)
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(NAME_RESPONSE, name)
            attachment.addProperty(DESCRIPTION_RESPONSE, description)
        }
    }

    class ColoredCoinsAssetIssuance : AbstractAttachment {
        val name: String
        val description: String
        val quantity: Long
        val decimals: Byte

        override fun getAppendixName(): String = "AssetIssuance"

        override val mySize: Int
            get() = 1 + name.toBytes().size + 2 + description.toBytes().size + 8 + 1

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_COLORED_COINS, TransactionType.SUBTYPE_COLORED_COINS_ASSET_ISSUANCE)

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.AssetIssuanceAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setName(name)
                    .setDescription(description)
                    .setQuantity(quantity)
                    .setDecimals(decimals.toInt())
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.name = buffer.readString(buffer.get().toInt(), Constants.MAX_ASSET_NAME_LENGTH)
            this.description = buffer.readString(buffer.short.toInt(), Constants.MAX_ASSET_DESCRIPTION_LENGTH)
            this.quantity = buffer.long
            this.decimals = buffer.get()
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.name = attachmentData.get(NAME_PARAMETER).mustGetAsString(NAME_PARAMETER)
            this.description = attachmentData.get(DESCRIPTION_PARAMETER).safeGetAsString().orEmpty()
            this.quantity = attachmentData.get(QUANTITY_QNT_PARAMETER).mustGetAsLong(QUANTITY_QNT_PARAMETER)
            this.decimals = attachmentData.get(DECIMALS_PARAMETER).mustGetAsByte(DECIMALS_PARAMETER)
        }

        constructor(
            dp: DependencyProvider,
            name: String,
            description: String,
            quantity: Long,
            decimals: Byte,
            blockchainHeight: Int
        ) : super(dp, blockchainHeight) {
            this.name = name
            this.description = description
            this.quantity = quantity
            this.decimals = decimals
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.AssetIssuanceAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.name = attachment.name
            this.description = attachment.description
            this.quantity = attachment.quantity
            this.decimals = attachment.decimals.toByte()
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            val name = this.name.toBytes()
            val description = this.description.toBytes()
            buffer.put(name.size.toByte())
            buffer.put(name)
            buffer.putShort(description.size.toShort())
            buffer.put(description)
            buffer.putLong(quantity)
            buffer.put(decimals)
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(NAME_RESPONSE, name)
            attachment.addProperty(DESCRIPTION_RESPONSE, description)
            attachment.addProperty(QUANTITY_QNT_RESPONSE, quantity)
            attachment.addProperty(DECIMALS_RESPONSE, decimals)
        }
    }

    class ColoredCoinsAssetTransfer : AbstractAttachment {

        val assetId: Long
        val quantity: Long
        val comment: String?

        override fun getAppendixName(): String = "AssetTransfer"

        override val mySize: Int
            get() = 8 + 8 + if (version.toInt() == 0) 2 + comment.toBytes().size else 0

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_COLORED_COINS, TransactionType.SUBTYPE_COLORED_COINS_ASSET_TRANSFER)

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.AssetTransferAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setAsset(assetId)
                    .setQuantity(quantity)
                    .setComment(comment)
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.assetId = buffer.long
            this.quantity = buffer.long
            this.comment = if (version.toInt() == 0) buffer.readString(
                buffer.short.toInt(),
                Constants.MAX_ASSET_TRANSFER_COMMENT_LENGTH
            ) else null
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.assetId = attachmentData.get(ASSET_PARAMETER).safeGetAsString().parseUnsignedLong()
            this.quantity = attachmentData.get(QUANTITY_QNT_PARAMETER).mustGetAsLong(QUANTITY_QNT_PARAMETER)
            this.comment =
                if (version.toInt() == 0) attachmentData.get(COMMENT_PARAMETER).safeGetAsString().orEmpty() else null
        }

        constructor(dp: DependencyProvider, assetId: Long, quantity: Long, blockchainHeight: Int) : super(
            dp,
            blockchainHeight
        ) {
            this.assetId = assetId
            this.quantity = quantity
            this.comment = null
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.AssetTransferAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.assetId = attachment.asset
            this.quantity = attachment.quantity
            this.comment = attachment.comment
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            buffer.putLong(assetId)
            buffer.putLong(quantity)
            if (version.toInt() == 0 && comment != null) {
                val commentBytes = this.comment.toBytes()
                buffer.putShort(commentBytes.size.toShort())
                buffer.put(commentBytes)
            }
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(ASSET_RESPONSE, assetId.toUnsignedString())
            attachment.addProperty(QUANTITY_QNT_RESPONSE, quantity)
            if (version.toInt() == 0) {
                attachment.addProperty(COMMENT_RESPONSE, comment)
            }
        }
    }

    abstract class ColoredCoinsOrderPlacement : AbstractAttachment {

        val assetId: Long
        val quantity: Long
        val pricePlanck: Long

        override val mySize: Int
            get() = 8 + 8 + 8

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.AssetOrderPlacementAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setAsset(assetId)
                    .setQuantity(quantity)
                    .setPrice(pricePlanck)
                    .setType(type)
                    .build()
            )

        protected abstract val type: BrsApi.OrderType

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.assetId = buffer.long
            this.quantity = buffer.long
            this.pricePlanck = buffer.long
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.assetId = attachmentData.get(ASSET_PARAMETER).safeGetAsString().parseUnsignedLong()
            this.quantity = attachmentData.get(QUANTITY_QNT_PARAMETER).mustGetAsLong(QUANTITY_QNT_PARAMETER)
            this.pricePlanck = attachmentData.get(PRICE_PLANCK_PARAMETER).mustGetAsLong(PRICE_PLANCK_PARAMETER)
        }

        constructor(
            dp: DependencyProvider,
            assetId: Long,
            quantity: Long,
            pricePlanck: Long,
            blockchainHeight: Int
        ) : super(dp, blockchainHeight) {
            this.assetId = assetId
            this.quantity = quantity
            this.pricePlanck = pricePlanck
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.AssetOrderPlacementAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.assetId = attachment.asset
            this.quantity = attachment.quantity
            this.pricePlanck = attachment.price
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            buffer.putLong(assetId)
            buffer.putLong(quantity)
            buffer.putLong(pricePlanck)
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(ASSET_RESPONSE, assetId.toUnsignedString())
            attachment.addProperty(QUANTITY_QNT_RESPONSE, quantity)
            attachment.addProperty(PRICE_PLANCK_RESPONSE, pricePlanck)
        }
    }

    class ColoredCoinsAskOrderPlacement : ColoredCoinsOrderPlacement {

        override val type: BrsApi.OrderType
            get() = BrsApi.OrderType.ASK

        override fun getAppendixName(): String = "AskOrderPlacement"

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_COLORED_COINS, TransactionType.SUBTYPE_COLORED_COINS_ASK_ORDER_PLACEMENT)

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        )

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData)

        constructor(
            dp: DependencyProvider,
            assetId: Long,
            quantity: Long,
            pricePlanck: Long,
            blockchainHeight: Int
        ) : super(dp, assetId, quantity, pricePlanck, blockchainHeight)

        internal constructor(dp: DependencyProvider, attachment: BrsApi.AssetOrderPlacementAttachment) : super(
            dp,
            attachment
        ) {
            require(attachment.type == type) { "Type does not match" }
        }
    }

    class ColoredCoinsBidOrderPlacement : ColoredCoinsOrderPlacement {

        override val type: BrsApi.OrderType
            get() = BrsApi.OrderType.BID

        override fun getAppendixName(): String = "BidOrderPlacement"

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_COLORED_COINS, TransactionType.SUBTYPE_COLORED_COINS_BID_ORDER_PLACEMENT)

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        )

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData)

        constructor(
            dp: DependencyProvider,
            assetId: Long,
            quantity: Long,
            pricePlanck: Long,
            blockchainHeight: Int
        ) : super(dp, assetId, quantity, pricePlanck, blockchainHeight)

        internal constructor(dp: DependencyProvider, attachment: BrsApi.AssetOrderPlacementAttachment) : super(
            dp,
            attachment
        ) {
            require(attachment.type == type) { "Type does not match" }
        }

    }

    abstract class ColoredCoinsOrderCancellation : AbstractAttachment {

        val orderId: Long

        override val mySize: Int
            get() = 8

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.AssetOrderCancellationAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setOrder(orderId)
                    .setType(type)
                    .build()
            )

        protected abstract val type: BrsApi.OrderType

        constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.orderId = buffer.long
        }

        constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.orderId = attachmentData.get(ORDER_PARAMETER).safeGetAsString().parseUnsignedLong()
        }

        constructor(dp: DependencyProvider, orderId: Long, blockchainHeight: Int) : super(dp, blockchainHeight) {
            this.orderId = orderId
        }

        constructor(dp: DependencyProvider, attachment: BrsApi.AssetOrderCancellationAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.orderId = attachment.order
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            buffer.putLong(orderId)
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(ORDER_RESPONSE, orderId.toUnsignedString())
        }
    }

    class ColoredCoinsAskOrderCancellation : ColoredCoinsOrderCancellation {

        override val type: BrsApi.OrderType
            get() = BrsApi.OrderType.ASK

        override fun getAppendixName(): String = "AskOrderCancellation"

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(
                TransactionType.TYPE_COLORED_COINS,
                TransactionType.SUBTYPE_COLORED_COINS_ASK_ORDER_CANCELLATION
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        )

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData)

        constructor(dp: DependencyProvider, orderId: Long, blockchainHeight: Int) : super(dp, orderId, blockchainHeight)

        internal constructor(dp: DependencyProvider, attachment: BrsApi.AssetOrderCancellationAttachment) : super(
            dp,
            attachment
        ) {
            require(attachment.type == type) { "Type does not match" }
        }

    }

    class ColoredCoinsBidOrderCancellation : ColoredCoinsOrderCancellation {

        override val type: BrsApi.OrderType
            get() = BrsApi.OrderType.BID

        override fun getAppendixName(): String = "BidOrderCancellation"

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(
                TransactionType.TYPE_COLORED_COINS,
                TransactionType.SUBTYPE_COLORED_COINS_BID_ORDER_CANCELLATION
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        )

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData)

        constructor(dp: DependencyProvider, orderId: Long, blockchainHeight: Int) : super(dp, orderId, blockchainHeight)

        internal constructor(dp: DependencyProvider, attachment: BrsApi.AssetOrderCancellationAttachment) : super(
            dp,
            attachment
        ) {
            require(attachment.type == type) { "Type does not match" }
        }

    }

    class DigitalGoodsListing : AbstractAttachment {

        val name: String?
        val description: String?
        val tags: String?
        val quantity: Int
        val pricePlanck: Long

        override fun getAppendixName(): String = "DigitalGoodsListing"

        override val mySize: Int
            get() = (2 + name.toBytes().size + 2 + description.toBytes().size + 2
                    + tags.toBytes().size + 4 + 8)

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_DIGITAL_GOODS, TransactionType.SUBTYPE_DIGITAL_GOODS_LISTING)

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.DigitalGoodsListingAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setName(name)
                    .setDescription(description)
                    .setTags(tags)
                    .setQuantity(quantity)
                    .setPrice(pricePlanck)
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.name = buffer.readString(buffer.short.toInt(), Constants.MAX_DGS_LISTING_NAME_LENGTH)
            this.description = buffer.readString(buffer.short.toInt(), Constants.MAX_DGS_LISTING_DESCRIPTION_LENGTH)
            this.tags = buffer.readString(buffer.short.toInt(), Constants.MAX_DGS_LISTING_TAGS_LENGTH)
            this.quantity = buffer.int
            this.pricePlanck = buffer.long
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.name = attachmentData.get(NAME_RESPONSE).safeGetAsString()
            this.description = attachmentData.get(DESCRIPTION_RESPONSE).safeGetAsString()
            this.tags = attachmentData.get(TAGS_RESPONSE).safeGetAsString()
            this.quantity = attachmentData.get(QUANTITY_RESPONSE).mustGetAsInt(QUANTITY_RESPONSE)
            this.pricePlanck = attachmentData.get(PRICE_PLANCK_PARAMETER).mustGetAsLong(PRICE_PLANCK_PARAMETER)
        }

        constructor(
            dp: DependencyProvider,
            name: String,
            description: String,
            tags: String,
            quantity: Int,
            pricePlanck: Long,
            blockchainHeight: Int
        ) : super(dp, blockchainHeight) {
            this.name = name
            this.description = description
            this.tags = tags
            this.quantity = quantity
            this.pricePlanck = pricePlanck
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.DigitalGoodsListingAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.name = attachment.name
            this.description = attachment.description
            this.tags = attachment.tags
            this.quantity = attachment.quantity
            this.pricePlanck = attachment.price
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            val nameBytes = name.toBytes()
            buffer.putShort(nameBytes.size.toShort())
            buffer.put(nameBytes)
            val descriptionBytes = description.toBytes()
            buffer.putShort(descriptionBytes.size.toShort())
            buffer.put(descriptionBytes)
            val tagsBytes = tags.toBytes()
            buffer.putShort(tagsBytes.size.toShort())
            buffer.put(tagsBytes)
            buffer.putInt(quantity)
            buffer.putLong(pricePlanck)
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(NAME_RESPONSE, name)
            attachment.addProperty(DESCRIPTION_RESPONSE, description)
            attachment.addProperty(TAGS_RESPONSE, tags)
            attachment.addProperty(QUANTITY_RESPONSE, quantity)
            attachment.addProperty(PRICE_PLANCK_RESPONSE, pricePlanck)
        }
    }

    class DigitalGoodsDelisting : AbstractAttachment {

        val goodsId: Long

        override fun getAppendixName(): String = "DigitalGoodsDelisting"

        override val mySize: Int
            get() = 8

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_DIGITAL_GOODS, TransactionType.SUBTYPE_DIGITAL_GOODS_DELISTING)

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.DigitalGoodsDelistingAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setGoods(goodsId)
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.goodsId = buffer.long
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.goodsId = attachmentData.get(GOODS_PARAMETER).safeGetAsString().parseUnsignedLong()
        }

        constructor(dp: DependencyProvider, goodsId: Long, blockchainHeight: Int) : super(dp, blockchainHeight) {
            this.goodsId = goodsId
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.DigitalGoodsDelistingAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.goodsId = attachment.goods
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            buffer.putLong(goodsId)
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(GOODS_RESPONSE, goodsId.toUnsignedString())
        }
    }

    class DigitalGoodsPriceChange : AbstractAttachment {

        val goodsId: Long
        val pricePlanck: Long

        override fun getAppendixName(): String = "DigitalGoodsPriceChange"

        override val mySize: Int
            get() = 8 + 8

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_DIGITAL_GOODS, TransactionType.SUBTYPE_DIGITAL_GOODS_PRICE_CHANGE)

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.DigitalGoodsPriceChangeAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setGoods(goodsId)
                    .setPrice(pricePlanck)
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.goodsId = buffer.long
            this.pricePlanck = buffer.long
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.goodsId = attachmentData.get(GOODS_PARAMETER).safeGetAsString().parseUnsignedLong()
            this.pricePlanck = attachmentData.get(PRICE_PLANCK_PARAMETER).mustGetAsLong(PRICE_PLANCK_PARAMETER)
        }

        constructor(dp: DependencyProvider, goodsId: Long, pricePlanck: Long, blockchainHeight: Int) : super(
            dp,
            blockchainHeight
        ) {
            this.goodsId = goodsId
            this.pricePlanck = pricePlanck
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.DigitalGoodsPriceChangeAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.goodsId = attachment.goods
            this.pricePlanck = attachment.price
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            buffer.putLong(goodsId)
            buffer.putLong(pricePlanck)
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(GOODS_RESPONSE, goodsId.toUnsignedString())
            attachment.addProperty(PRICE_PLANCK_RESPONSE, pricePlanck)
        }
    }

    class DigitalGoodsQuantityChange : AbstractAttachment {

        val goodsId: Long
        val deltaQuantity: Int

        override fun getAppendixName(): String = "DigitalGoodsQuantityChange"

        override val mySize: Int
            get() = 8 + 4

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_DIGITAL_GOODS, TransactionType.SUBTYPE_DIGITAL_GOODS_QUANTITY_CHANGE)

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.DigitalGoodsQuantityChangeAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setGoods(goodsId)
                    .setDeltaQuantity(deltaQuantity)
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.goodsId = buffer.long
            this.deltaQuantity = buffer.int
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.goodsId = attachmentData.get(GOODS_PARAMETER).safeGetAsString().parseUnsignedLong()
            this.deltaQuantity = attachmentData.get(DELTA_QUANTITY_PARAMETER).mustGetAsInt(DELTA_QUANTITY_PARAMETER)
        }

        constructor(dp: DependencyProvider, goodsId: Long, deltaQuantity: Int, blockchainHeight: Int) : super(
            dp,
            blockchainHeight
        ) {
            this.goodsId = goodsId
            this.deltaQuantity = deltaQuantity
        }

        internal constructor(
            dp: DependencyProvider,
            attachment: BrsApi.DigitalGoodsQuantityChangeAttachment
        ) : super(dp, attachment.version.toByte()) {
            this.goodsId = attachment.goods
            this.deltaQuantity = attachment.deltaQuantity
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            buffer.putLong(goodsId)
            buffer.putInt(deltaQuantity)
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(GOODS_RESPONSE, goodsId.toUnsignedString())
            attachment.addProperty(DELTA_QUANTITY_RESPONSE, deltaQuantity)
        }
    }

    class DigitalGoodsPurchase : AbstractAttachment {

        val goodsId: Long
        val quantity: Int
        val pricePlanck: Long
        val deliveryDeadlineTimestamp: Int

        override fun getAppendixName(): String = "DigitalGoodsPurchase"

        override val mySize: Int
            get() = 8 + 4 + 8 + 4

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_DIGITAL_GOODS, TransactionType.SUBTYPE_DIGITAL_GOODS_PURCHASE)

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.DigitalGoodsPurchaseAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setGoods(goodsId)
                    .setQuantity(quantity)
                    .setPrice(pricePlanck)
                    .setDeliveryDeadlineTimestmap(deliveryDeadlineTimestamp)
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.goodsId = buffer.long
            this.quantity = buffer.int
            this.pricePlanck = buffer.long
            this.deliveryDeadlineTimestamp = buffer.int
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.goodsId = attachmentData.get(GOODS_PARAMETER).safeGetAsString().parseUnsignedLong()
            this.quantity = attachmentData.get(QUANTITY_PARAMETER).mustGetAsInt(QUANTITY_PARAMETER)
            this.pricePlanck = attachmentData.get(PRICE_PLANCK_PARAMETER).mustGetAsLong(PRICE_PLANCK_PARAMETER)
            this.deliveryDeadlineTimestamp = attachmentData.get(DELIVERY_DEADLINE_TIMESTAMP_PARAMETER)
                .mustGetAsInt(DELIVERY_DEADLINE_TIMESTAMP_PARAMETER)
        }

        constructor(
            dp: DependencyProvider,
            goodsId: Long,
            quantity: Int,
            pricePlanck: Long,
            deliveryDeadlineTimestamp: Int,
            blockchainHeight: Int
        ) : super(dp, blockchainHeight) {
            this.goodsId = goodsId
            this.quantity = quantity
            this.pricePlanck = pricePlanck
            this.deliveryDeadlineTimestamp = deliveryDeadlineTimestamp
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.DigitalGoodsPurchaseAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.goodsId = attachment.goods
            this.quantity = attachment.quantity
            this.pricePlanck = attachment.price
            this.deliveryDeadlineTimestamp = attachment.deliveryDeadlineTimestmap
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            buffer.putLong(goodsId)
            buffer.putInt(quantity)
            buffer.putLong(pricePlanck)
            buffer.putInt(deliveryDeadlineTimestamp)
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(GOODS_RESPONSE, goodsId.toUnsignedString())
            attachment.addProperty(QUANTITY_RESPONSE, quantity)
            attachment.addProperty(PRICE_PLANCK_RESPONSE, pricePlanck)
            attachment.addProperty(DELIVERY_DEADLINE_TIMESTAMP_RESPONSE, deliveryDeadlineTimestamp)
        }
    }

    class DigitalGoodsDelivery : AbstractAttachment {

        val purchaseId: Long
        val goods: EncryptedData
        val discountPlanck: Long
        private val goodsIsText: Boolean

        override fun getAppendixName(): String = "DigitalGoodsDelivery"

        override val mySize: Int
            get() = 8 + 4 + goods.size + 8

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_DIGITAL_GOODS, TransactionType.SUBTYPE_DIGITAL_GOODS_DELIVERY)

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.DigitalGoodsDeliveryAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setPurchase(purchaseId)
                    .setDiscount(discountPlanck)
                    .setGoods(ProtoBuilder.buildEncryptedData(goods))
                    .setIsText(goodsIsText)
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.purchaseId = buffer.long
            var length = buffer.int
            goodsIsText = length < 0
            if (length < 0) {
                length = length and Integer.MAX_VALUE
            }
            this.goods = EncryptedData.readEncryptedData(buffer, length, Constants.MAX_DGS_GOODS_LENGTH)
            this.discountPlanck = buffer.long
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.purchaseId = attachmentData.get(PURCHASE_PARAMETER).safeGetAsString().parseUnsignedLong()
            this.goods = EncryptedData(
                attachmentData.get(GOODS_DATA_PARAMETER).mustGetAsString(GOODS_DATA_PARAMETER).parseHexString(),
                attachmentData.get(GOODS_NONCE_PARAMETER).mustGetAsString(GOODS_NONCE_PARAMETER).parseHexString()
            )
            this.discountPlanck = attachmentData.get(DISCOUNT_PLANCK_PARAMETER).mustGetAsLong(DISCOUNT_PLANCK_PARAMETER)
            this.goodsIsText = attachmentData.get(GOODS_IS_TEXT_PARAMETER).mustGetAsBoolean(GOODS_IS_TEXT_PARAMETER)
        }

        constructor(
            dp: DependencyProvider,
            purchaseId: Long,
            goods: EncryptedData,
            goodsIsText: Boolean,
            discountPlanck: Long,
            blockchainHeight: Int
        ) : super(dp, blockchainHeight) {
            this.purchaseId = purchaseId
            this.goods = goods
            this.discountPlanck = discountPlanck
            this.goodsIsText = goodsIsText
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.DigitalGoodsDeliveryAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.purchaseId = attachment.purchase
            this.goods = ProtoBuilder.parseEncryptedData(attachment.goods)
            this.goodsIsText = attachment.isText
            this.discountPlanck = attachment.discount
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            buffer.putLong(purchaseId)
            buffer.putInt(if (goodsIsText) goods.data.size or Integer.MIN_VALUE else goods.data.size)
            buffer.put(goods.data)
            buffer.put(goods.nonce)
            buffer.putLong(discountPlanck)
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(PURCHASE_RESPONSE, purchaseId.toUnsignedString())
            attachment.addProperty(GOODS_DATA_RESPONSE, goods.data.toHexString())
            attachment.addProperty(GOODS_NONCE_RESPONSE, goods.nonce.toHexString())
            attachment.addProperty(DISCOUNT_PLANCK_RESPONSE, discountPlanck)
            attachment.addProperty(GOODS_IS_TEXT_RESPONSE, goodsIsText)
        }

        fun goodsIsText(): Boolean {
            return goodsIsText
        }
    }

    class DigitalGoodsFeedback : AbstractAttachment {

        val purchaseId: Long

        override fun getAppendixName(): String = "DigitalGoodsFeedback"

        override val mySize: Int
            get() = 8

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_DIGITAL_GOODS, TransactionType.SUBTYPE_DIGITAL_GOODS_FEEDBACK)

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.DigitalGoodsFeedbackAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setPurchase(purchaseId)
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.purchaseId = buffer.long
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.purchaseId = attachmentData.get(PURCHASE_PARAMETER).safeGetAsString().parseUnsignedLong()
        }

        constructor(dp: DependencyProvider, purchaseId: Long, blockchainHeight: Int) : super(dp, blockchainHeight) {
            this.purchaseId = purchaseId
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.DigitalGoodsFeedbackAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.purchaseId = attachment.purchase
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            buffer.putLong(purchaseId)
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(PURCHASE_RESPONSE, purchaseId.toUnsignedString())
        }
    }

    class DigitalGoodsRefund : AbstractAttachment {

        val purchaseId: Long
        val refundPlanck: Long

        override fun getAppendixName(): String = "DigitalGoodsRefund"

        override val mySize: Int
            get() = 8 + 8

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_DIGITAL_GOODS, TransactionType.SUBTYPE_DIGITAL_GOODS_REFUND)

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.DigitalGoodsRefundAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setPurchase(purchaseId)
                    .setRefund(refundPlanck)
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.purchaseId = buffer.long
            this.refundPlanck = buffer.long
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.purchaseId = attachmentData.get(PURCHASE_PARAMETER).safeGetAsString().parseUnsignedLong()
            this.refundPlanck = attachmentData.get(REFUND_PLANCK_PARAMETER).mustGetAsLong(REFUND_PLANCK_PARAMETER)
        }

        constructor(dp: DependencyProvider, purchaseId: Long, refundPlanck: Long, blockchainHeight: Int) : super(
            dp,
            blockchainHeight
        ) {
            this.purchaseId = purchaseId
            this.refundPlanck = refundPlanck
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.DigitalGoodsRefundAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.purchaseId = attachment.purchase
            this.refundPlanck = attachment.refund
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            buffer.putLong(purchaseId)
            buffer.putLong(refundPlanck)
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(PURCHASE_RESPONSE, purchaseId.toUnsignedString())
            attachment.addProperty(REFUND_PLANCK_RESPONSE, refundPlanck)
        }
    }

    class AccountControlEffectiveBalanceLeasing : AbstractAttachment {
        override val transactionTypeAndSubtype = Pair(
            TransactionType.TYPE_ACCOUNT_CONTROL,
            TransactionType.SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING
        )

        val period: Short

        override fun getAppendixName() = "EffectiveBalanceLeasing"

        override val mySize = 2

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.EffectiveBalanceLeasingAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setPeriod(period.toInt())
                    .build()
            )

        constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.period = buffer.short
        }

        constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.period = attachmentData.get(PERIOD_PARAMETER).mustGetAsShort(PERIOD_PARAMETER)
        }

        constructor(dp: DependencyProvider, attachment: BrsApi.EffectiveBalanceLeasingAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.period = attachment.period.toShort()
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            buffer.putShort(period)
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(PERIOD_RESPONSE, period)
        }
    }

    class BurstMiningRewardRecipientAssignment : AbstractAttachment {

        override fun getAppendixName(): String = "RewardRecipientAssignment"

        override val mySize: Int
            get() = 0

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(
                TransactionType.TYPE_BURST_MINING,
                TransactionType.SUBTYPE_BURST_MINING_REWARD_RECIPIENT_ASSIGNMENT
            )

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.RewardRecipientAssignmentAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        )

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData)

        constructor(dp: DependencyProvider, blockchainHeight: Int) : super(dp, blockchainHeight)

        internal constructor(dp: DependencyProvider, attachment: BrsApi.RewardRecipientAssignmentAttachment) : super(
            dp,
            attachment.version.toByte()
        )

        override fun putMyBytes(buffer: ByteBuffer) {
            // Reward recipient does not have additional data.
        }

        override fun putMyJSON(attachment: JsonObject) {
            // Reward recipient does not have additional data.
        }
    }

    class AdvancedPaymentEscrowCreation : AbstractAttachment {

        val amountPlanck: Long
        private val requiredSigners: Byte
        private val signers = TreeSet<Long>()
        val deadline: Int
        val deadlineAction: Escrow.DecisionType

        override fun getAppendixName(): String = "EscrowCreation"

        override val mySize: Int
            get() {
                var size = 8 + 4 + 1 + 1 + 1
                size += signers.size * 8
                return size
            }

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(
                TransactionType.TYPE_ADVANCED_PAYMENT,
                TransactionType.SUBTYPE_ADVANCED_PAYMENT_ESCROW_CREATION
            )

        val totalSigners: Int
            get() = signers.size

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.EscrowCreationAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setAmount(amountPlanck)
                    .setRequiredSigners(requiredSigners.toInt())
                    .addAllSigners(signers)
                    .setDeadline(deadline)
                    .setDeadlineAction(Escrow.decisionToProtobuf(deadlineAction!!))
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.amountPlanck = buffer.long
            this.deadline = buffer.int
            this.deadlineAction = Escrow.byteToDecision(buffer.get()) ?: error("Escrow Decision Type Not Recognized")
            this.requiredSigners = buffer.get()
            val totalSigners = buffer.get()
            if (totalSigners > 10 || totalSigners <= 0) {
                throw BurstException.NotValidException("Invalid number of signers listed on create escrow transaction")
            }
            for (i in 0 until totalSigners) {
                if (!this.signers.add(buffer.long)) {
                    throw BurstException.NotValidException("Duplicate signer on escrow creation")
                }
            }
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.amountPlanck = attachmentData.get(AMOUNT_PLANCK_PARAMETER).safeGetAsString().parseUnsignedLong()
            this.deadline = attachmentData.get(DEADLINE_PARAMETER).mustGetAsInt(DEADLINE_PARAMETER)
            this.deadlineAction = Escrow.stringToDecision(attachmentData.get(DEADLINE_ACTION_PARAMETER).mustGetAsString(DEADLINE_ACTION_PARAMETER)) ?: error("Escrow Decision Type not recognized: $attachmentData")
            this.requiredSigners =
                attachmentData.get(REQUIRED_SIGNERS_PARAMETER).mustGetAsByte(REQUIRED_SIGNERS_PARAMETER)
            val totalSigners = attachmentData.get(SIGNERS_PARAMETER).mustGetAsJsonArray(SIGNERS_PARAMETER).size()
            if (totalSigners > 10 || totalSigners <= 0) {
                throw BurstException.NotValidException("Invalid number of signers listed on create escrow transaction")
            }
            val signersJson = attachmentData.get(SIGNERS_PARAMETER).mustGetAsJsonArray(SIGNERS_PARAMETER)
            for (aSignersJson in signersJson) {
                this.signers.add(aSignersJson.mustGetAsString("signer").parseUnsignedLong())
            }
            if (this.signers.size != attachmentData.get(SIGNERS_PARAMETER).mustGetAsJsonArray(SIGNERS_PARAMETER).size()) {
                throw BurstException.NotValidException("Duplicate signer on escrow creation")
            }
        }

        constructor(
            dp: DependencyProvider,
            amountPlanck: Long,
            deadline: Int,
            deadlineAction: Escrow.DecisionType,
            requiredSigners: Int,
            signers: Collection<Long>,
            blockchainHeight: Int
        ) : super(dp, blockchainHeight) {
            this.amountPlanck = amountPlanck
            this.deadline = deadline
            this.deadlineAction = deadlineAction
            this.requiredSigners = requiredSigners.toByte()
            if (signers.size > 10 || signers.isEmpty()) {
                throw BurstException.NotValidException("Invalid number of signers listed on create escrow transaction")
            }
            this.signers.addAll(signers)
            if (this.signers.size != signers.size) {
                throw BurstException.NotValidException("Duplicate signer on escrow creation")
            }
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.EscrowCreationAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.amountPlanck = attachment.amount
            this.requiredSigners = attachment.requiredSigners.toByte()
            this.deadline = attachment.deadline
            this.deadlineAction = Escrow.protoBufToDecision(attachment.deadlineAction) ?: error("Escrow Decision Type Not Recognized: ${attachment.deadlineAction}")
            this.signers.addAll(attachment.signersList)
            if (signers.size > 10 || signers.isEmpty()) {
                throw BurstException.NotValidException("Invalid number of signers listed on create escrow transaction")
            }
            if (this.signers.size != attachment.signersList.size) {
                throw BurstException.NotValidException("Duplicate signer on escrow creation")
            }
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            buffer.putLong(this.amountPlanck)
            buffer.putInt(this.deadline)
            buffer.put(Escrow.decisionToByte(this.deadlineAction!!))
            buffer.put(this.requiredSigners)
            val totalSigners = this.signers.size.toByte()
            buffer.put(totalSigners)
            this.signers.forEach { buffer.putLong(it) }
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(AMOUNT_PLANCK_RESPONSE, this.amountPlanck.toUnsignedString())
            attachment.addProperty(DEADLINE_RESPONSE, this.deadline)
            attachment.addProperty(
                DEADLINE_ACTION_RESPONSE,
                Escrow.decisionToString(this.deadlineAction!!)
            )
            attachment.addProperty(REQUIRED_SIGNERS_RESPONSE, this.requiredSigners.toInt())
            val ids = JsonArray()
            for (signer in this.signers) {
                ids.add(signer.toUnsignedString())
            }
            attachment.add(SIGNERS_RESPONSE, ids)
        }

        fun getRequiredSigners(): Int {
            return requiredSigners.toInt()
        }

        fun getSigners(): Collection<Long> {
            return signers
        }
    }

    class AdvancedPaymentEscrowSign : AbstractAttachment {

        val escrowId: Long?
        val decision: Escrow.DecisionType?

        override fun getAppendixName(): String = "EscrowSign"

        override val mySize: Int
            get() = 8 + 1

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_ADVANCED_PAYMENT, TransactionType.SUBTYPE_ADVANCED_PAYMENT_ESCROW_SIGN)

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.EscrowSignAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setEscrow(escrowId!!)
                    .setDecision(Escrow.decisionToProtobuf(decision!!))
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.escrowId = buffer.long
            this.decision = Escrow.byteToDecision(buffer.get())
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.escrowId = attachmentData.get(ESCROW_ID_PARAMETER).safeGetAsString().parseUnsignedLong()
            this.decision = Escrow.stringToDecision(
                attachmentData.get(DECISION_PARAMETER).mustGetAsString(DECISION_PARAMETER)
            )
        }

        constructor(
            dp: DependencyProvider,
            escrowId: Long?,
            decision: Escrow.DecisionType,
            blockchainHeight: Int
        ) : super(dp, blockchainHeight) {
            this.escrowId = escrowId
            this.decision = decision
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.EscrowSignAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.escrowId = attachment.escrow
            this.decision = Escrow.protoBufToDecision(attachment.decision)
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            buffer.putLong(this.escrowId!!)
            buffer.put(Escrow.decisionToByte(this.decision!!))
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(ESCROW_ID_RESPONSE, this.escrowId!!.toUnsignedString())
            attachment.addProperty(DECISION_RESPONSE, Escrow.decisionToString(this.decision!!))
        }
    }

    class AdvancedPaymentEscrowResult : AbstractAttachment {

        private val escrowId: Long
        private val decision: Escrow.DecisionType?

        override fun getAppendixName(): String = "EscrowResult"

        override val mySize: Int
            get() = 8 + 1

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_ADVANCED_PAYMENT, TransactionType.SUBTYPE_ADVANCED_PAYMENT_ESCROW_RESULT)

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.EscrowResultAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setEscrow(escrowId)
                    .setDecision(Escrow.decisionToProtobuf(decision!!))
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.escrowId = buffer.long
            this.decision = Escrow.byteToDecision(buffer.get())
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.escrowId = attachmentData.get(ESCROW_ID_PARAMETER).safeGetAsString().parseUnsignedLong()
            this.decision = Escrow.stringToDecision(
                attachmentData.get(DECISION_PARAMETER).mustGetAsString(DECISION_PARAMETER)
            )
        }

        constructor(
            dp: DependencyProvider,
            escrowId: Long,
            decision: Escrow.DecisionType,
            blockchainHeight: Int
        ) : super(dp, blockchainHeight) {
            this.escrowId = escrowId
            this.decision = decision
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.EscrowResultAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.escrowId = attachment.escrow
            this.decision = Escrow.protoBufToDecision(attachment.decision)
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            buffer.putLong(this.escrowId)
            buffer.put(Escrow.decisionToByte(this.decision!!))
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(ESCROW_ID_RESPONSE, this.escrowId.toUnsignedString())
            attachment.addProperty(DECISION_RESPONSE, Escrow.decisionToString(this.decision!!))
        }
    }

    class AdvancedPaymentSubscriptionSubscribe : AbstractAttachment {

        val frequency: Int?

        override fun getAppendixName(): String = "SubscriptionSubscribe"

        override val mySize: Int
            get() = 4

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(
                TransactionType.TYPE_ADVANCED_PAYMENT,
                TransactionType.SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_SUBSCRIBE
            )

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.SubscriptionSubscribeAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setFrequency(frequency!!)
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.frequency = buffer.int
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.frequency = attachmentData.get(FREQUENCY_PARAMETER).safeGetAsInt()
        }

        constructor(dp: DependencyProvider, frequency: Int, blockchainHeight: Int) : super(dp, blockchainHeight) {
            this.frequency = frequency
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.SubscriptionSubscribeAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.frequency = attachment.frequency
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            buffer.putInt(this.frequency!!)
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(FREQUENCY_RESPONSE, this.frequency)
        }
    }

    class AdvancedPaymentSubscriptionCancel : AbstractAttachment {

        val subscriptionId: Long

        override fun getAppendixName(): String = "SubscriptionCancel"

        override val mySize: Int
            get() = 8

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(
                TransactionType.TYPE_ADVANCED_PAYMENT,
                TransactionType.SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_CANCEL
            )

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.SubscriptionCancelAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setSubscription(subscriptionId)
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.subscriptionId = buffer.long
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.subscriptionId = attachmentData.get(SUBSCRIPTION_ID_PARAMETER).safeGetAsString().parseUnsignedLong()
        }

        constructor(dp: DependencyProvider, subscriptionId: Long, blockchainHeight: Int) : super(dp, blockchainHeight) {
            this.subscriptionId = subscriptionId
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.SubscriptionCancelAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.subscriptionId = attachment.subscription
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            buffer.putLong(subscriptionId)
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(SUBSCRIPTION_ID_RESPONSE, this.subscriptionId.toUnsignedString())
        }
    }

    class AdvancedPaymentSubscriptionPayment : AbstractAttachment {

        private val subscriptionId: Long?

        override fun getAppendixName(): String = "SubscriptionPayment"

        override val mySize: Int
            get() = 8

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(
                TransactionType.TYPE_ADVANCED_PAYMENT,
                TransactionType.SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_PAYMENT
            )

        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.SubscriptionPaymentAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setSubscription(subscriptionId!!)
                    .build()
            )

        internal constructor(dp: DependencyProvider, buffer: ByteBuffer, transactionVersion: Byte) : super(
            dp,
            buffer,
            transactionVersion
        ) {
            this.subscriptionId = buffer.long
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {
            this.subscriptionId = attachmentData.get(SUBSCRIPTION_ID_PARAMETER).safeGetAsString().parseUnsignedLong()
        }

        constructor(dp: DependencyProvider, subscriptionId: Long?, blockchainHeight: Int) : super(
            dp,
            blockchainHeight
        ) {
            this.subscriptionId = subscriptionId
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.SubscriptionPaymentAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.subscriptionId = attachment.subscription
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            buffer.putLong(this.subscriptionId!!)
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(SUBSCRIPTION_ID_RESPONSE, this.subscriptionId!!.toUnsignedString())
        }
    }

    class AutomatedTransactionsCreation : AbstractAttachment {
        val name: String?
        val description: String?
        val creationBytes: ByteArray

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_AUTOMATED_TRANSACTIONS, TransactionType.SUBTYPE_AT_CREATION)

        override fun getAppendixName(): String = "AutomatedTransactionsCreation"
        override val mySize: Int
            get() = 1 + name.toBytes().size + 2 + description.toBytes().size + creationBytes.size


        override val protobufMessage: Any
            get() = Any.pack(
                BrsApi.ATCreationAttachment.newBuilder()
                    .setVersion(version.toInt())
                    .setName(name)
                    .setDescription(description)
                    .setCreationBytes(creationBytes.toByteString())
                    .build()
            )

        internal constructor(
            dp: DependencyProvider, buffer: ByteBuffer,
            transactionVersion: Byte
        ) : super(dp, buffer, transactionVersion) {

            this.name = buffer.readString(buffer.get().toInt(), Constants.MAX_AUTOMATED_TRANSACTION_NAME_LENGTH)
            this.description = buffer.readString(
                buffer.short.toInt(),
                Constants.MAX_AUTOMATED_TRANSACTION_DESCRIPTION_LENGTH
            )

            // rest of the parsing is at related; code comes from
            // public AtMachineState( byte[] atId, byte[] creator, byte[] creationBytes, int height ) {
            val startPosition = buffer.position()
            buffer.short

            buffer.short //future: reserved for future needs

            val pageSize = dp.atConstants.pageSize(dp.blockchainService.height).toInt()
            val codePages = buffer.short
            val dataPages = buffer.short
            buffer.short
            buffer.short

            buffer.long

            var codeLen: Int
            if (codePages * pageSize < pageSize + 1) {
                codeLen = buffer.get().toInt()
                if (codeLen < 0)
                    codeLen += (Byte.MAX_VALUE + 1) * 2
            } else if (codePages * pageSize < Short.MAX_VALUE + 1) {
                codeLen = buffer.short.toInt()
                if (codeLen < 0)
                    codeLen += (Short.MAX_VALUE + 1) * 2
            } else {
                codeLen = buffer.int
            }
            val code = ByteArray(codeLen)
            buffer.get(code, 0, codeLen)

            var dataLen: Int
            if (dataPages * pageSize < 257) {
                dataLen = buffer.get().toInt()
                if (dataLen < 0)
                    dataLen += (Byte.MAX_VALUE + 1) * 2
            } else if (dataPages * pageSize < Short.MAX_VALUE + 1) {
                dataLen = buffer.short.toInt()
                if (dataLen < 0)
                    dataLen += (Short.MAX_VALUE + 1) * 2
            } else {
                dataLen = buffer.int
            }
            val data = ByteArray(dataLen)
            buffer.get(data, 0, dataLen)

            val endPosition = buffer.position()
            buffer.position(startPosition)
            val dst = ByteArray(endPosition - startPosition)
            buffer.get(dst, 0, endPosition - startPosition)
            this.creationBytes = dst
        }

        internal constructor(dp: DependencyProvider, attachmentData: JsonObject) : super(dp, attachmentData) {

            this.name = attachmentData.get(NAME_PARAMETER).safeGetAsString()
            this.description = attachmentData.get(DESCRIPTION_PARAMETER).safeGetAsString()

            this.creationBytes =
                attachmentData.get(CREATION_BYTES_PARAMETER).mustGetAsString(CREATION_BYTES_PARAMETER).parseHexString()

        }

        constructor(
            dp: DependencyProvider,
            name: String,
            description: String,
            creationBytes: ByteArray,
            blockchainHeight: Int
        ) : super(dp, blockchainHeight) {
            this.name = name
            this.description = description
            this.creationBytes = creationBytes
        }

        internal constructor(dp: DependencyProvider, attachment: BrsApi.ATCreationAttachment) : super(
            dp,
            attachment.version.toByte()
        ) {
            this.name = attachment.name
            this.description = attachment.description
            this.creationBytes = attachment.creationBytes.toByteArray()
        }

        override fun putMyBytes(buffer: ByteBuffer) {
            val nameBytes = name.toBytes()
            buffer.put(nameBytes.size.toByte())
            buffer.put(nameBytes)
            val descriptionBytes = description.toBytes()
            buffer.putShort(descriptionBytes.size.toShort())
            buffer.put(descriptionBytes)

            buffer.put(creationBytes)
        }

        override fun putMyJSON(attachment: JsonObject) {
            attachment.addProperty(NAME_RESPONSE, name)
            attachment.addProperty(DESCRIPTION_RESPONSE, description)
            attachment.addProperty(CREATION_BYTES_RESPONSE, creationBytes.toHexString())
        }
    }

    class OrdinaryPayment(dp: DependencyProvider) : EmptyAttachment(dp) {
        override val protobufMessage: Any
            get() = Any.pack(BrsApi.OrdinaryPaymentAttachment.getDefaultInstance())

        override fun getAppendixName(): String = "OrdinaryPayment"

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_PAYMENT, TransactionType.SUBTYPE_PAYMENT_ORDINARY_PAYMENT)
    }

    // the message payload is in the Appendix
    class ArbitraryMessage(dp: DependencyProvider) : EmptyAttachment(dp) {

        override val protobufMessage: Any = Any.pack(BrsApi.ArbitraryMessageAttachment.getDefaultInstance())

        override fun getAppendixName() = "ArbitraryMessage"

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_MESSAGING, TransactionType.SUBTYPE_MESSAGING_ARBITRARY_MESSAGE)
    }

    class AtPayment(dp: DependencyProvider) : EmptyAttachment(dp) {
        override val protobufMessage: Any
            get() = Any.pack(BrsApi.ATPaymentAttachment.getDefaultInstance())

        override val transactionTypeAndSubtype: Pair<Byte, Byte>
            get() = Pair(TransactionType.TYPE_AUTOMATED_TRANSACTIONS, TransactionType.SUBTYPE_AT_PAYMENT)

        override fun getAppendixName(): String = "AT Payment"
    }
}
