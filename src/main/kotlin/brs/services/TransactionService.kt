package brs.services

import brs.entity.Transaction

interface TransactionService {
    /**
     * TODO
     */
    fun verifyPublicKey(transaction: Transaction): Boolean

    /**
     * TODO
     */
    fun validate(transaction: Transaction, preValidate: Boolean = true)

    fun preValidate(transaction: Transaction, height: Int)

    /**
     * TODO
     */
    fun applyUnconfirmed(transaction: Transaction): Boolean

    /**
     * TODO
     */
    fun apply(transaction: Transaction)

    /**
     * TODO
     */
    fun undoUnconfirmed(transaction: Transaction)
}
