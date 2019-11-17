package brs.entity

import brs.db.BurstKey
import brs.db.VersionedBatchEntityTable
import brs.util.convert.fullHashToId
import brs.util.convert.toUnsignedString
import brs.util.crypto.Crypto
import brs.util.crypto.rsVerify
import java.util.logging.Level
import java.util.logging.Logger

open class Account {
    val id: Long
    val nxtKey: BurstKey
    val creationHeight: Int
    private var publicKeyInternal: ByteArray? = null
    var publicKey: ByteArray?
        get() = if (this.keyHeight == -1) {
            null
        } else publicKeyInternal
        set(v) {
            publicKeyInternal = v
        }
    var keyHeight: Int = 0
    var balancePlanck: Long = 0
    var unconfirmedBalancePlanck: Long = 0
    var forgedBalancePlanck: Long = 0

    var name: String? = null
    var description: String? = null

    enum class Event {
        BALANCE, UNCONFIRMED_BALANCE, ASSET_BALANCE, UNCONFIRMED_ASSET_BALANCE
    }

    open class AccountAsset {
        val accountId: Long
        val assetId: Long
        val burstKey: BurstKey
        var quantity: Long = 0
        var unconfirmedQuantity: Long = 0

        protected constructor(
            accountId: Long,
            assetId: Long,
            quantity: Long,
            unconfirmedQuantity: Long,
            burstKey: BurstKey
        ) {
            this.accountId = accountId
            this.assetId = assetId
            this.quantity = quantity
            this.unconfirmedQuantity = unconfirmedQuantity
            this.burstKey = burstKey
        }

        constructor(burstKey: BurstKey, accountId: Long, assetId: Long, quantity: Long, unconfirmedQuantity: Long) {
            this.accountId = accountId
            this.assetId = assetId
            this.burstKey = burstKey
            this.quantity = quantity
            this.unconfirmedQuantity = unconfirmedQuantity
        }

        fun checkBalance() {
            checkBalance(this.accountId, this.quantity, this.unconfirmedQuantity)
        }

        override fun toString(): String {
            return ("AccountAsset account_id: "
                    + accountId.toUnsignedString()
                    + " asset_id: "
                    + assetId.toUnsignedString()
                    + " quantity: "
                    + quantity
                    + " unconfirmedQuantity: "
                    + unconfirmedQuantity)
        }
    }

    open class RewardRecipientAssignment(
        val accountId: Long,
        var prevRecipientId: Long,
        var recipientId: Long,
        fromHeight: Int,
        val burstKey: BurstKey
    ) {
        var fromHeight: Int = 0
            private set

        init {
            this.fromHeight = fromHeight
        }

        fun setRecipient(newRecipientId: Long, fromHeight: Int) {
            prevRecipientId = recipientId
            recipientId = newRecipientId
            this.fromHeight = fromHeight
        }
    }

    internal class DoubleSpendingException(message: String) : RuntimeException(message)

    constructor(dp: DependencyProvider, id: Long) {
        if (!id.rsVerify()) {
            logger.log(Level.INFO, "CRITICAL ERROR: Reed-Solomon encoding fails for {0}", id)
        }
        this.id = id
        this.nxtKey = accountBurstKeyFactory(dp).newKey(this.id)
        this.creationHeight = dp.blockchainService.height
    }

    protected constructor(id: Long, burstKey: BurstKey, creationHeight: Int) {
        if (!id.rsVerify()) {
            logger.log(Level.INFO, "CRITICAL ERROR: Reed-Solomon encoding fails for {0}", id)
        }
        this.id = id
        this.nxtKey = burstKey
        this.creationHeight = creationHeight
    }

    fun encryptTo(data: ByteArray, senderSecretPhrase: String): EncryptedData {
        requireNotNull(publicKey) { "Recipient account doesn't have a public key set" }
        return EncryptedData.encrypt(data, Crypto.getPrivateKey(senderSecretPhrase), publicKey!!)
    }


    fun decryptFrom(encryptedData: EncryptedData, recipientSecretPhrase: String): ByteArray {
        requireNotNull(publicKey) { "Sender account doesn't have a public key set" }
        return encryptedData.decrypt(Crypto.getPrivateKey(recipientSecretPhrase), publicKey!!)
    }

    // returns true if:
    // this.publicKey is set to null (in which case this.publicKey also gets set to key)
    // or
    // this.publicKey is already set to an array equal to key
    fun setOrVerify(dp: DependencyProvider, key: ByteArray, height: Int): Boolean { // TODO should we deprecate?
        return dp.accountStore.setOrVerify(this, key, height)
    }

    fun apply(dp: DependencyProvider, key: ByteArray, height: Int) {
        check(setOrVerify(dp, key, this.creationHeight)) { "Public key mismatch" }
        checkNotNull(this.publicKeyInternal) {
            ("Public key has not been set for account " + id.toUnsignedString()
                    + " at height " + height + ", key height is " + keyHeight)
        }
        if (this.keyHeight == -1 || this.keyHeight > height) {
            this.keyHeight = height
            accountTable(dp).insert(this)
        }
    }

    fun checkBalance() {
        checkBalance(this.id, this.balancePlanck, this.unconfirmedBalancePlanck)
    }

    companion object {

        private val logger = Logger.getLogger(Account::class.java.simpleName)

        // TODO refactor methods that take dp
        private fun accountBurstKeyFactory(dp: DependencyProvider): BurstKey.LongKeyFactory<Account> {
            return dp.accountStore.accountKeyFactory
        }

        private fun accountTable(dp: DependencyProvider): VersionedBatchEntityTable<Account> {
            return dp.accountStore.accountTable
        }

        @Deprecated("Use dp.accountService.getAccount()")
        fun getAccount(dp: DependencyProvider, id: Long): Account? {
            return if (id == 0L) null else accountTable(dp)[accountBurstKeyFactory(
                dp
            ).newKey(id)]
        }

        @Deprecated("Just use Crypto/Convert class instead")
        fun getId(publicKey: ByteArray): Long {
            val publicKeyHash = Crypto.sha256().digest(publicKey)
            return publicKeyHash.fullHashToId()
        }

        fun getOrAddAccount(dp: DependencyProvider, id: Long): Account {
            var account = getAccount(dp, id)
            if (account == null) {
                account = Account(dp, id)
                accountTable(dp).insert(account)
            }
            return account
        }

        fun encryptTo(data: ByteArray, senderSecretPhrase: String, publicKey: ByteArray?): EncryptedData {
            requireNotNull(publicKey) { "public key required" }
            return EncryptedData.encrypt(data, Crypto.getPrivateKey(senderSecretPhrase), publicKey)
        }

        private fun checkBalance(accountId: Long, confirmed: Long, unconfirmed: Long) {
            if (confirmed < 0) {
                throw DoubleSpendingException(
                    "Negative balance or quantity ("
                            + confirmed
                            + ") for account "
                            + accountId.toUnsignedString()
                )
            }
            if (unconfirmed < 0) {
                throw DoubleSpendingException(
                    "Negative unconfirmed balance or quantity ("
                            + unconfirmed
                            + ") for account "
                            + accountId.toUnsignedString()
                )
            }
            if (unconfirmed > confirmed) {
                throw DoubleSpendingException(
                    "Unconfirmed ("
                            + unconfirmed
                            + ") exceeds confirmed ("
                            + confirmed
                            + ") balance or quantity for account "
                            + accountId.toUnsignedString()
                )
            }
        }
    }
}
