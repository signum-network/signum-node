package brs.transaction.type.automatedTransactions

import brs.entity.Account
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.transaction.type.TransactionType

abstract class AutomatedTransactions(dp: DependencyProvider) : TransactionType(dp) {
    override val type = TYPE_AUTOMATED_TRANSACTIONS
    override fun applyAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account) = true
    override fun undoAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account) = Unit
}
