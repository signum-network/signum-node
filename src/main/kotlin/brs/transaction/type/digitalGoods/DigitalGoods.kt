package brs.transaction.type.digitalGoods

import brs.entity.Account
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.objects.FluxValues
import brs.transaction.type.TransactionType
import brs.util.BurstException

abstract class DigitalGoods(dp: DependencyProvider) : TransactionType(dp) {
    override val type = TYPE_DIGITAL_GOODS
    override fun applyAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account) = true
    override fun undoAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account) = Unit
    internal abstract fun doPreValidateAttachment(transaction: Transaction, height: Int)
    override fun preValidateAttachment(transaction: Transaction, height: Int) {
        if (!dp.fluxCapacitorService.getValue(FluxValues.DIGITAL_GOODS_STORE, height)) {
            throw BurstException.NotYetEnabledException("Digital goods listing not yet enabled at height $height")
        }
        if (transaction.amountPlanck != 0L) {
            throw BurstException.NotValidException("Invalid digital goods transaction")
        }
        doPreValidateAttachment(transaction, height)
    }
}
