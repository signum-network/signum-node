package brs.transaction.type

import brs.entity.Account
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.entity.TransactionDuplicationKey
import brs.objects.Constants
import brs.objects.Constants.FEE_QUANT
import brs.objects.Constants.ONE_BURST
import brs.objects.FluxValues
import brs.transaction.appendix.Appendix
import brs.transaction.appendix.Attachment.AbstractAttachment
import brs.transaction.type.accountControl.EffectiveBalanceLeasing
import brs.transaction.type.advancedPayment.*
import brs.transaction.type.automatedTransactions.AtPayment
import brs.transaction.type.automatedTransactions.AutomatedTransactionCreation
import brs.transaction.type.burstMining.RewardRecipientAssignment
import brs.transaction.type.coloredCoins.*
import brs.transaction.type.digitalGoods.*
import brs.transaction.type.messaging.*
import brs.transaction.type.payment.MultiOutPayment
import brs.transaction.type.payment.MultiOutSamePayment
import brs.transaction.type.payment.OrdinaryPayment
import brs.util.convert.safeAdd
import brs.util.convert.safeMultiply
import brs.util.logging.safeTrace
import com.google.gson.JsonObject
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

abstract class TransactionType constructor(internal val dp: DependencyProvider) {
    abstract val type: Byte
    abstract val subtype: Byte
    abstract val description: String
    open val isSigned = true
    abstract fun parseAttachment(buffer: ByteBuffer, transactionVersion: Byte): AbstractAttachment
    internal abstract fun parseAttachment(attachmentData: JsonObject): AbstractAttachment

    /**
     * Validate that the attachment is valid at the height specified.
     * May be called from different threads - must not modify any transaction data
     * and must not rely on current blockchain state.
     */
    internal abstract fun preValidateAttachment(transaction: Transaction, height: Int)

    /**
     * Validate that the attachment is valid at the height specified.
     * Called from the block importer thread before pushing so may
     * rely on current blockchain state.
     */
    internal abstract fun validateAttachment(transaction: Transaction)

    /**
     * @return false if double spending
     */
    fun applyUnconfirmed(transaction: Transaction, senderAccount: Account): Boolean {
        val totalAmountPlanck = calculateTransactionAmountPlanck(transaction)
        logger.safeTrace { "applyUnconfirmed: ${senderAccount.unconfirmedBalancePlanck} < totalamount: $totalAmountPlanck = false" }
        if (senderAccount.unconfirmedBalancePlanck < totalAmountPlanck) {
            return false
        }
        dp.accountService.addToUnconfirmedBalancePlanck(senderAccount, -totalAmountPlanck)
        if (!applyAttachmentUnconfirmed(transaction, senderAccount)) {
            logger.safeTrace { "!applyAttachmentUnconfirmed($transaction, ${senderAccount.id})" }
            dp.accountService.addToUnconfirmedBalancePlanck(senderAccount, totalAmountPlanck)
            return false
        }
        return true
    }

    fun calculateTotalAmountPlanck(transaction: Transaction): Long {
        return calculateTransactionAmountPlanck(transaction).safeAdd(calculateAttachmentTotalAmountPlanck(transaction)!!)
    }

    private fun calculateTransactionAmountPlanck(transaction: Transaction): Long {
        var totalAmountPlanck = transaction.amountPlanck.safeAdd(transaction.feePlanck)
        if (transaction.referencedTransactionFullHash != null) {
            totalAmountPlanck = totalAmountPlanck.safeAdd(Constants.UNCONFIRMED_POOL_DEPOSIT_PLANCK)
        }
        return totalAmountPlanck
    }

    protected open fun calculateAttachmentTotalAmountPlanck(transaction: Transaction): Long? {
        return 0L
    }

    internal abstract fun applyAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account): Boolean

    internal fun apply(transaction: Transaction, senderAccount: Account, recipientAccount: Account) {
        dp.accountService.addToBalancePlanck(senderAccount, -transaction.amountPlanck.safeAdd(transaction.feePlanck))
        if (transaction.referencedTransactionFullHash != null) {
            dp.accountService.addToUnconfirmedBalancePlanck(senderAccount, Constants.UNCONFIRMED_POOL_DEPOSIT_PLANCK)
        }
        dp.accountService.addToBalanceAndUnconfirmedBalancePlanck(recipientAccount, transaction.amountPlanck)
        logger.safeTrace { "applying transaction - id: ${transaction.id}, type: ${transaction.type}" }
        applyAttachment(transaction, senderAccount, recipientAccount)
    }

    internal abstract fun applyAttachment(transaction: Transaction, senderAccount: Account, recipientAccount: Account)

    open fun parseAppendices(builder: Transaction.Builder, attachmentData: JsonObject) {
        builder.message(Appendix.Message.parse(attachmentData))
        builder.encryptedMessage(Appendix.EncryptedMessage.parse(attachmentData))
        builder.publicKeyAnnouncement(Appendix.PublicKeyAnnouncement.parse(dp, attachmentData))
        builder.encryptToSelfMessage(Appendix.EncryptToSelfMessage.parse(attachmentData))
    }

    open fun parseAppendices(builder: Transaction.Builder, flags: Int, version: Byte, buffer: ByteBuffer) {
        var position = 1
        if (flags and position != 0) {
            builder.message(Appendix.Message(buffer, version))
        }
        position = position shl 1
        if (flags and position != 0) {
            builder.encryptedMessage(Appendix.EncryptedMessage(buffer, version))
        }
        position = position shl 1
        if (flags and position != 0) {
            builder.publicKeyAnnouncement(Appendix.PublicKeyAnnouncement(dp, buffer, version))
        }
        position = position shl 1
        if (flags and position != 0) {
            builder.encryptToSelfMessage(Appendix.EncryptToSelfMessage(buffer, version))
        }
    }

    fun undoUnconfirmed(transaction: Transaction, senderAccount: Account) {
        undoAttachmentUnconfirmed(transaction, senderAccount)
        dp.accountService.addToUnconfirmedBalancePlanck(
            senderAccount,
            transaction.amountPlanck.safeAdd(transaction.feePlanck)
        )
        if (transaction.referencedTransactionFullHash != null) {
            dp.accountService.addToUnconfirmedBalancePlanck(senderAccount, Constants.UNCONFIRMED_POOL_DEPOSIT_PLANCK)
        }
    }

    internal abstract fun undoAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account)

    open fun getDuplicationKey(transaction: Transaction): TransactionDuplicationKey {
        return TransactionDuplicationKey.IS_NEVER_DUPLICATE
    }

    abstract fun hasRecipient(): Boolean

    override fun toString(): String {
        return "type: $type, subtype: $subtype"
    }

    fun minimumFeePlanck(height: Int, appendagesSize: Int): Long {
        if (height < BASELINE_FEE_HEIGHT) {
            return 0 // No need to validate fees before baseline block
        }
        val fee = getBaselineFee(height)
        return fee.constantFee.safeAdd(fee.appendagesFee.safeMultiply(appendagesSize.toLong()))
    }

    protected open fun getBaselineFee(height: Int): Fee {
        return Fee(
            if (dp.fluxCapacitorService.getValue(
                    FluxValues.PRE_DYMAXION,
                    height
                )
            ) FEE_QUANT else ONE_BURST, 0
        )
    }

    class Fee internal constructor(internal val constantFee: Long, internal val appendagesFee: Long) {
        override fun toString(): String {
            return "Fee{" +
                    "constantFee=" + constantFee +
                    ", appendagesFee=" + appendagesFee +
                    '}'.toString()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TransactionType::class.java)

        const val TYPE_PAYMENT: Byte = 0
        const val TYPE_MESSAGING: Byte = 1
        const val TYPE_COLORED_COINS: Byte = 2
        const val TYPE_DIGITAL_GOODS: Byte = 3
        const val TYPE_ACCOUNT_CONTROL: Byte = 4
        const val TYPE_BURST_MINING: Byte = 20 // jump some for easier nxt updating
        const val TYPE_ADVANCED_PAYMENT: Byte = 21
        const val TYPE_AUTOMATED_TRANSACTIONS: Byte = 22

        const val SUBTYPE_PAYMENT_ORDINARY_PAYMENT: Byte = 0
        const val SUBTYPE_PAYMENT_ORDINARY_PAYMENT_MULTI_OUT: Byte = 1
        const val SUBTYPE_PAYMENT_ORDINARY_PAYMENT_MULTI_SAME_OUT: Byte = 2

        const val SUBTYPE_MESSAGING_ARBITRARY_MESSAGE: Byte = 0
        const val SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT: Byte = 1
        const val SUBTYPE_MESSAGING_ACCOUNT_INFO: Byte = 5
        const val SUBTYPE_MESSAGING_ALIAS_SELL: Byte = 6
        const val SUBTYPE_MESSAGING_ALIAS_BUY: Byte = 7

        const val SUBTYPE_COLORED_COINS_ASSET_ISSUANCE: Byte = 0
        const val SUBTYPE_COLORED_COINS_ASSET_TRANSFER: Byte = 1
        const val SUBTYPE_COLORED_COINS_ASK_ORDER_PLACEMENT: Byte = 2
        const val SUBTYPE_COLORED_COINS_BID_ORDER_PLACEMENT: Byte = 3
        const val SUBTYPE_COLORED_COINS_ASK_ORDER_CANCELLATION: Byte = 4
        const val SUBTYPE_COLORED_COINS_BID_ORDER_CANCELLATION: Byte = 5

        const val SUBTYPE_DIGITAL_GOODS_LISTING: Byte = 0
        const val SUBTYPE_DIGITAL_GOODS_DELISTING: Byte = 1
        const val SUBTYPE_DIGITAL_GOODS_PRICE_CHANGE: Byte = 2
        const val SUBTYPE_DIGITAL_GOODS_QUANTITY_CHANGE: Byte = 3
        const val SUBTYPE_DIGITAL_GOODS_PURCHASE: Byte = 4
        const val SUBTYPE_DIGITAL_GOODS_DELIVERY: Byte = 5
        const val SUBTYPE_DIGITAL_GOODS_FEEDBACK: Byte = 6
        const val SUBTYPE_DIGITAL_GOODS_REFUND: Byte = 7

        const val SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING: Byte = 0

        const val SUBTYPE_AT_CREATION: Byte = 0
        const val SUBTYPE_AT_PAYMENT: Byte = 1

        const val SUBTYPE_BURST_MINING_REWARD_RECIPIENT_ASSIGNMENT: Byte = 0

        const val SUBTYPE_ADVANCED_PAYMENT_ESCROW_CREATION: Byte = 0
        const val SUBTYPE_ADVANCED_PAYMENT_ESCROW_SIGN: Byte = 1
        const val SUBTYPE_ADVANCED_PAYMENT_ESCROW_RESULT: Byte = 2
        const val SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_SUBSCRIBE: Byte = 3
        const val SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_CANCEL: Byte = 4
        const val SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_PAYMENT: Byte = 5

        const val BASELINE_FEE_HEIGHT = 1 // At release time must be less than current block - 1440
        val BASELINE_ASSET_ISSUANCE_FEE =
            Fee(Constants.ASSET_ISSUANCE_FEE_PLANCK, 0)

        fun getTransactionTypes(dp: DependencyProvider): Map<Byte, Map<Byte, TransactionType>> {
            val paymentTypes = mutableMapOf<Byte, TransactionType>()
            paymentTypes[SUBTYPE_PAYMENT_ORDINARY_PAYMENT] = OrdinaryPayment(dp)
            paymentTypes[SUBTYPE_PAYMENT_ORDINARY_PAYMENT_MULTI_OUT] = MultiOutPayment(dp)
            paymentTypes[SUBTYPE_PAYMENT_ORDINARY_PAYMENT_MULTI_SAME_OUT] = MultiOutSamePayment(dp)

            val messagingTypes = mutableMapOf<Byte, TransactionType>()
            messagingTypes[SUBTYPE_MESSAGING_ARBITRARY_MESSAGE] = ArbitraryMessage(dp)
            messagingTypes[SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT] = AliasAssignment(dp)
            messagingTypes[SUBTYPE_MESSAGING_ACCOUNT_INFO] = AccountInfo(dp)
            messagingTypes[SUBTYPE_MESSAGING_ALIAS_BUY] = AliasBuy(dp)
            messagingTypes[SUBTYPE_MESSAGING_ALIAS_SELL] = AliasSell(dp)

            val coloredCoinsTypes = mutableMapOf<Byte, TransactionType>()
            coloredCoinsTypes[SUBTYPE_COLORED_COINS_ASSET_ISSUANCE] = AssetIssuance(dp)
            coloredCoinsTypes[SUBTYPE_COLORED_COINS_ASSET_TRANSFER] = AssetTransfer(dp)
            coloredCoinsTypes[SUBTYPE_COLORED_COINS_ASK_ORDER_PLACEMENT] = AskOrderPlacement(dp)
            coloredCoinsTypes[SUBTYPE_COLORED_COINS_BID_ORDER_PLACEMENT] = BidOrderPlacement(dp)
            coloredCoinsTypes[SUBTYPE_COLORED_COINS_ASK_ORDER_CANCELLATION] = AskOrderCancellation(dp)
            coloredCoinsTypes[SUBTYPE_COLORED_COINS_BID_ORDER_CANCELLATION] = BidOrderCancellation(dp)

            val digitalGoodsTypes = mutableMapOf<Byte, TransactionType>()
            digitalGoodsTypes[SUBTYPE_DIGITAL_GOODS_LISTING] = DigitalGoodsListing(dp)
            digitalGoodsTypes[SUBTYPE_DIGITAL_GOODS_DELISTING] = DigitalGoodsDelisting(dp)
            digitalGoodsTypes[SUBTYPE_DIGITAL_GOODS_PRICE_CHANGE] = DigitalGoodsPriceChange(dp)
            digitalGoodsTypes[SUBTYPE_DIGITAL_GOODS_QUANTITY_CHANGE] = DigitalGoodsQuantityChange(dp)
            digitalGoodsTypes[SUBTYPE_DIGITAL_GOODS_PURCHASE] = DigitalGoodsPurchase(dp)
            digitalGoodsTypes[SUBTYPE_DIGITAL_GOODS_DELIVERY] = DigitalGoodsDelivery(dp)
            digitalGoodsTypes[SUBTYPE_DIGITAL_GOODS_FEEDBACK] = DigitalGoodsFeedback(dp)
            digitalGoodsTypes[SUBTYPE_DIGITAL_GOODS_REFUND] = DigitalGoodsRefund(dp)

            val accountControlTypes = mutableMapOf<Byte, TransactionType>()
            accountControlTypes[SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING] = EffectiveBalanceLeasing(dp)

            val atTypes = mutableMapOf<Byte, TransactionType>()
            atTypes[SUBTYPE_AT_CREATION] = AutomatedTransactionCreation(dp)
            atTypes[SUBTYPE_AT_PAYMENT] = AtPayment(dp)

            val burstMiningTypes = mutableMapOf<Byte, TransactionType>()
            burstMiningTypes[SUBTYPE_BURST_MINING_REWARD_RECIPIENT_ASSIGNMENT] = RewardRecipientAssignment(dp)

            val advancedPaymentTypes = mutableMapOf<Byte, TransactionType>()
            advancedPaymentTypes[SUBTYPE_ADVANCED_PAYMENT_ESCROW_CREATION] = EscrowCreation(dp)
            advancedPaymentTypes[SUBTYPE_ADVANCED_PAYMENT_ESCROW_SIGN] = EscrowSign(dp)
            advancedPaymentTypes[SUBTYPE_ADVANCED_PAYMENT_ESCROW_RESULT] = EscrowResult(dp)
            advancedPaymentTypes[SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_SUBSCRIBE] = SubscriptionSubscribe(dp)
            advancedPaymentTypes[SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_CANCEL] = SubscriptionCancel(dp)
            advancedPaymentTypes[SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_PAYMENT] = SubscriptionPayment(dp)

            val transactionTypes = mutableMapOf<Byte, Map<Byte, TransactionType>>()
            transactionTypes[TYPE_PAYMENT] = paymentTypes
            transactionTypes[TYPE_MESSAGING] = messagingTypes
            transactionTypes[TYPE_COLORED_COINS] = coloredCoinsTypes
            transactionTypes[TYPE_DIGITAL_GOODS] = digitalGoodsTypes
            transactionTypes[TYPE_ACCOUNT_CONTROL] = accountControlTypes
            transactionTypes[TYPE_BURST_MINING] = burstMiningTypes
            transactionTypes[TYPE_ADVANCED_PAYMENT] = advancedPaymentTypes
            transactionTypes[TYPE_AUTOMATED_TRANSACTIONS] = atTypes

            return transactionTypes
        }

        fun findTransactionType(dp: DependencyProvider, type: Byte, subtype: Byte): TransactionType? {
            val subtypes = dp.transactionTypes[type]
            return if (subtypes == null) null else subtypes[subtype]
        }

        fun getTypeDescription(type: Byte): String {
            return when (type) {
                TYPE_PAYMENT -> "Payment"
                TYPE_MESSAGING -> "Messaging"
                TYPE_COLORED_COINS -> "Colored coins"
                TYPE_DIGITAL_GOODS -> "Digital Goods"
                TYPE_BURST_MINING -> "Burst Mining"
                TYPE_ADVANCED_PAYMENT -> "Advanced Payment"
                TYPE_AUTOMATED_TRANSACTIONS -> "Automated Transactions"
                else -> "Unknown"
            }
        }
    }
}
