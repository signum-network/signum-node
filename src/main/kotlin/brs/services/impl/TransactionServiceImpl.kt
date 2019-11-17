package brs.services.impl

import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.services.TransactionService
import brs.util.BurstException

class TransactionServiceImpl(private val dp: DependencyProvider) : TransactionService {

    override fun verifyPublicKey(transaction: Transaction): Boolean {
        val account = dp.accountService.getAccount(transaction.senderId) ?: return false
        return if (transaction.signature == null) {
            false
        } else account.setOrVerify(dp, transaction.senderPublicKey, transaction.height)
    }

    override fun validate(transaction: Transaction, preValidate: Boolean) {
        for (appendage in transaction.appendages) {
            appendage.validate(transaction)
        }
        if (preValidate) preValidate(transaction, dp.blockchainService.height)
    }

    override fun preValidate(transaction: Transaction, height: Int) {
        val minimumFeePlanck = transaction.type.minimumFeePlanck(height, transaction.appendagesSize)
        if (transaction.feePlanck < minimumFeePlanck) {
            throw BurstException.NotCurrentlyValidException("Transaction fee ${transaction.feePlanck} less than minimum fee $minimumFeePlanck at height ${dp.blockchainService.height}")
        }
        for (appendage in transaction.appendages) {
            appendage.preValidate(transaction, height)
        }
    }

    override fun applyUnconfirmed(transaction: Transaction): Boolean {
        val senderAccount = dp.accountService.getAccount(transaction.senderId)
        return senderAccount != null && transaction.type.applyUnconfirmed(transaction, senderAccount)
    }

    override fun apply(transaction: Transaction) {
        val senderAccount = dp.accountService.getAccount(transaction.senderId)!!
        senderAccount.apply(dp, transaction.senderPublicKey, transaction.height)
        val recipientAccount = dp.accountService.getOrAddAccount(transaction.recipientId)
        for (appendage in transaction.appendages) {
            appendage.apply(transaction, senderAccount, recipientAccount)
        }
    }

    override fun undoUnconfirmed(transaction: Transaction) {
        val senderAccount = dp.accountService.getAccount(transaction.senderId)!!
        transaction.type.undoUnconfirmed(transaction, senderAccount)
    }

}
