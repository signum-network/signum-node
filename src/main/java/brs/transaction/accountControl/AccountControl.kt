package brs.transaction.accountControl

import brs.Account
import brs.DependencyProvider
import brs.Transaction
import brs.transaction.TransactionType

abstract class AccountControl(dp: DependencyProvider) : TransactionType(dp) {
    override val type = TYPE_ACCOUNT_CONTROL
    override fun applyAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account) = true
    override fun undoAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account) = Unit
}