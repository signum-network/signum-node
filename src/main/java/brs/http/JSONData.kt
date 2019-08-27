package brs.http

import brs.*
import brs.Alias.Offer
import brs.at.AT
import brs.at.AtApiHelper
import brs.crypto.Crypto
import brs.crypto.EncryptedData
import brs.peer.Peer
import brs.services.AccountService
import brs.util.Convert
import brs.util.JSON
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

import java.nio.ByteBuffer
import java.nio.ByteOrder

import brs.http.common.ResultFields.*

object JSONData {

    internal fun alias(alias: Alias, offer: Offer?): JsonObject {
        val json = JsonObject()
        putAccount(json, ACCOUNT_RESPONSE, alias.accountId)
        json.addProperty(ALIAS_NAME_RESPONSE, alias.aliasName)
        json.addProperty(ALIAS_URI_RESPONSE, alias.aliasURI)
        json.addProperty(TIMESTAMP_RESPONSE, alias.timestamp)
        json.addProperty(ALIAS_RESPONSE, Convert.toUnsignedLong(alias.id))

        if (offer != null) {
            json.addProperty(PRICE_NQT_RESPONSE, offer.priceNQT.toString())
            if (offer.buyerId != 0L) {
                json.addProperty(BUYER_RESPONSE, Convert.toUnsignedLong(offer.buyerId))
            }
        }
        return json
    }

    internal fun accountBalance(account: Account?): JsonObject {
        val json = JsonObject()
        if (account == null) {
            json.addProperty(BALANCE_NQT_RESPONSE, "0")
            json.addProperty(UNCONFIRMED_BALANCE_NQT_RESPONSE, "0")
            json.addProperty(EFFECTIVE_BALANCE_NQT_RESPONSE, "0")
            json.addProperty(FORGED_BALANCE_NQT_RESPONSE, "0")
            json.addProperty(GUARANTEED_BALANCE_NQT_RESPONSE, "0")
        } else {
            json.addProperty(BALANCE_NQT_RESPONSE, account.balanceNQT.toString())
            json.addProperty(UNCONFIRMED_BALANCE_NQT_RESPONSE, account.unconfirmedBalanceNQT.toString())
            json.addProperty(EFFECTIVE_BALANCE_NQT_RESPONSE, account.balanceNQT.toString())
            json.addProperty(FORGED_BALANCE_NQT_RESPONSE, account.forgedBalanceNQT.toString())
            json.addProperty(GUARANTEED_BALANCE_NQT_RESPONSE, account.balanceNQT.toString())
        }
        return json
    }

    internal fun asset(asset: Asset, tradeCount: Int, transferCount: Int, assetAccountsCount: Int): JsonObject {
        val json = JsonObject()
        putAccount(json, ACCOUNT_RESPONSE, asset.accountId)
        json.addProperty(NAME_RESPONSE, asset.name)
        json.addProperty(DESCRIPTION_RESPONSE, asset.description)
        json.addProperty(DECIMALS_RESPONSE, asset.decimals)
        json.addProperty(QUANTITY_QNT_RESPONSE, asset.quantityQNT.toString())
        json.addProperty(ASSET_RESPONSE, Convert.toUnsignedLong(asset.id))
        json.addProperty(NUMBER_OF_TRADES_RESPONSE, tradeCount)
        json.addProperty(NUMBER_OF_TRANSFERS_RESPONSE, transferCount)
        json.addProperty(NUMBER_OF_ACCOUNTS_RESPONSE, assetAccountsCount)
        return json
    }

    internal fun accountAsset(accountAsset: Account.AccountAsset): JsonObject {
        val json = JsonObject()
        putAccount(json, ACCOUNT_RESPONSE, accountAsset.getAccountId())
        json.addProperty(ASSET_RESPONSE, Convert.toUnsignedLong(accountAsset.getAssetId()))
        json.addProperty(QUANTITY_QNT_RESPONSE, accountAsset.quantityQNT.toString())
        json.addProperty(UNCONFIRMED_QUANTITY_QNT_RESPONSE, accountAsset.unconfirmedQuantityQNT.toString())
        return json
    }

    internal fun askOrder(order: Order.Ask): JsonObject {
        val json = order(order)
        json.addProperty(TYPE_RESPONSE, "ask")
        return json
    }

    internal fun bidOrder(order: Order.Bid): JsonObject {
        val json = order(order)
        json.addProperty(TYPE_RESPONSE, "bid")
        return json
    }

    private fun order(order: Order): JsonObject {
        val json = JsonObject()
        json.addProperty(ORDER_RESPONSE, Convert.toUnsignedLong(order.id))
        json.addProperty(ASSET_RESPONSE, Convert.toUnsignedLong(order.assetId))
        putAccount(json, ACCOUNT_RESPONSE, order.accountId)
        json.addProperty(QUANTITY_QNT_RESPONSE, order.quantityQNT.toString())
        json.addProperty(PRICE_NQT_RESPONSE, order.priceNQT.toString())
        json.addProperty(HEIGHT_RESPONSE, order.height)
        return json
    }

    internal fun block(block: Block, includeTransactions: Boolean, currentBlockchainHeight: Int, blockReward: Long, scoopNum: Int): JsonObject {
        val json = JsonObject()
        json.addProperty(BLOCK_RESPONSE, block.stringId)
        json.addProperty(HEIGHT_RESPONSE, block.height)
        putAccount(json, GENERATOR_RESPONSE, block.generatorId)
        json.addProperty(GENERATOR_PUBLIC_KEY_RESPONSE, Convert.toHexString(block.generatorPublicKey))
        json.addProperty(NONCE_RESPONSE, Convert.toUnsignedLong(block.nonce!!))
        json.addProperty(SCOOP_NUM_RESPONSE, scoopNum)
        json.addProperty(TIMESTAMP_RESPONSE, block.timestamp)
        json.addProperty(NUMBER_OF_TRANSACTIONS_RESPONSE, block.transactions.size)
        json.addProperty(TOTAL_AMOUNT_NQT_RESPONSE, block.totalAmountNQT.toString())
        json.addProperty(TOTAL_FEE_NQT_RESPONSE, block.totalFeeNQT.toString())
        json.addProperty(BLOCK_REWARD_RESPONSE, Convert.toUnsignedLong(blockReward / Constants.ONE_BURST))
        json.addProperty(PAYLOAD_LENGTH_RESPONSE, block.payloadLength)
        json.addProperty(VERSION_RESPONSE, block.version)
        json.addProperty(BASE_TARGET_RESPONSE, Convert.toUnsignedLong(block.baseTarget))

        if (block.previousBlockId != 0L) {
            json.addProperty(PREVIOUS_BLOCK_RESPONSE, Convert.toUnsignedLong(block.previousBlockId))
        }

        if (block.nextBlockId != 0L) {
            json.addProperty(NEXT_BLOCK_RESPONSE, Convert.toUnsignedLong(block.nextBlockId))
        }

        json.addProperty(PAYLOAD_HASH_RESPONSE, Convert.toHexString(block.payloadHash))
        json.addProperty(GENERATION_SIGNATURE_RESPONSE, Convert.toHexString(block.generationSignature))

        if (block.version > 1) {
            json.addProperty(PREVIOUS_BLOCK_HASH_RESPONSE, Convert.toHexString(block.previousBlockHash))
        }

        json.addProperty(BLOCK_SIGNATURE_RESPONSE, Convert.toHexString(block.blockSignature))

        val transactions = JsonArray()
        for (transaction in block.transactions) {
            if (includeTransactions) {
                transactions.add(transaction(transaction, currentBlockchainHeight))
            } else {
                transactions.add(Convert.toUnsignedLong(transaction.id))
            }
        }
        json.add(TRANSACTIONS_RESPONSE, transactions)
        return json
    }

    internal fun encryptedData(encryptedData: EncryptedData): JsonObject {
        val json = JsonObject()
        json.addProperty(DATA_RESPONSE, Convert.toHexString(encryptedData.data))
        json.addProperty(NONCE_RESPONSE, Convert.toHexString(encryptedData.nonce))
        return json
    }

    internal fun escrowTransaction(escrow: Escrow): JsonObject {
        val json = JsonObject()
        json.addProperty(ID_RESPONSE, Convert.toUnsignedLong(escrow.getId()!!))
        json.addProperty(SENDER_RESPONSE, Convert.toUnsignedLong(escrow.getSenderId()!!))
        json.addProperty(SENDER_RS_RESPONSE, Convert.rsAccount(escrow.getSenderId()!!))
        json.addProperty(RECIPIENT_RESPONSE, Convert.toUnsignedLong(escrow.getRecipientId()!!))
        json.addProperty(RECIPIENT_RS_RESPONSE, Convert.rsAccount(escrow.getRecipientId()!!))
        json.addProperty(AMOUNT_NQT_RESPONSE, Convert.toUnsignedLong(escrow.getAmountNQT()!!))
        json.addProperty(REQUIRED_SIGNERS_RESPONSE, escrow.getRequiredSigners())
        json.addProperty(DEADLINE_RESPONSE, escrow.getDeadline())
        json.addProperty(DEADLINE_ACTION_RESPONSE, Escrow.decisionToString(escrow.getDeadlineAction()))

        val signers = JsonArray()
        for (decision in escrow.decisions) {
            if (decision.getAccountId() == escrow.getSenderId() || decision.getAccountId() == escrow.getRecipientId()) {
                continue
            }
            val signerDetails = JsonObject()
            signerDetails.addProperty(ID_RESPONSE, Convert.toUnsignedLong(decision.getAccountId()!!))
            signerDetails.addProperty(ID_RS_RESPONSE, Convert.rsAccount(decision.getAccountId()!!))
            signerDetails.addProperty(DECISION_RESPONSE, Escrow.decisionToString(decision.decision))
            signers.add(signerDetails)
        }
        json.add(SIGNERS_RESPONSE, signers)
        return json
    }

    internal fun goods(goods: DigitalGoodsStore.Goods): JsonObject {
        val json = JsonObject()
        json.addProperty(GOODS_RESPONSE, Convert.toUnsignedLong(goods.id))
        json.addProperty(NAME_RESPONSE, goods.name)
        json.addProperty(DESCRIPTION_RESPONSE, goods.description)
        json.addProperty(QUANTITY_RESPONSE, goods.quantity)
        json.addProperty(PRICE_NQT_RESPONSE, goods.priceNQT.toString())
        putAccount(json, SELLER_RESPONSE, goods.sellerId)
        json.addProperty(TAGS_RESPONSE, goods.tags)
        json.addProperty(DELISTED_RESPONSE, goods.isDelisted)
        json.addProperty(TIMESTAMP_RESPONSE, goods.timestamp)
        return json
    }

    internal fun token(token: Token): JsonObject {
        val json = JsonObject()
        putAccount(json, "account", Account.getId(token.publicKey))
        json.addProperty("timestamp", token.timestamp)
        json.addProperty("valid", token.isValid)
        return json
    }

    internal fun peer(peer: Peer): JsonObject {
        val json = JsonObject()
        json.addProperty("state", peer.state.ordinal)
        json.addProperty("announcedAddress", peer.announcedAddress)
        json.addProperty("shareAddress", peer.shareAddress())
        json.addProperty("downloadedVolume", peer.downloadedVolume)
        json.addProperty("uploadedVolume", peer.uploadedVolume)
        json.addProperty("application", peer.application)
        json.addProperty("version", peer.version.toStringIfNotEmpty())
        json.addProperty("platform", peer.platform)
        json.addProperty("blacklisted", peer.isBlacklisted)
        json.addProperty("lastUpdated", peer.lastUpdated)
        return json
    }

    internal fun purchase(purchase: DigitalGoodsStore.Purchase): JsonObject {
        val json = JsonObject()
        json.addProperty(PURCHASE_RESPONSE, Convert.toUnsignedLong(purchase.id))
        json.addProperty(GOODS_RESPONSE, Convert.toUnsignedLong(purchase.goodsId))
        json.addProperty(NAME_RESPONSE, purchase.name)
        putAccount(json, SELLER_RESPONSE, purchase.sellerId)
        json.addProperty(PRICE_NQT_RESPONSE, purchase.priceNQT.toString())
        json.addProperty(QUANTITY_RESPONSE, purchase.quantity)
        putAccount(json, BUYER_RESPONSE, purchase.buyerId)
        json.addProperty(TIMESTAMP_RESPONSE, purchase.timestamp)
        json.addProperty(DELIVERY_DEADLINE_TIMESTAMP_RESPONSE, purchase.deliveryDeadlineTimestamp)
        if (purchase.note != null) {
            json.add(NOTE_RESPONSE, encryptedData(purchase.note))
        }
        json.addProperty(PENDING_RESPONSE, purchase.isPending)
        if (purchase.encryptedGoods != null) {
            json.add(GOODS_DATA_RESPONSE, encryptedData(purchase.encryptedGoods))
            json.addProperty(GOODS_IS_TEXT_RESPONSE, purchase.goodsIsText())
        }
        if (purchase.feedbackNotes != null) {
            val feedbacks = JsonArray()
            for (encryptedData in purchase.feedbackNotes!!) {
                feedbacks.add(encryptedData(encryptedData))
            }
            json.add(FEEDBACK_NOTES_RESPONSE, feedbacks)
        }
        if (!purchase.publicFeedback.isEmpty()) {
            val publicFeedbacks = JsonArray()
            for (string in purchase.publicFeedback) {
                publicFeedbacks.add(string)
            }
            json.add(PUBLIC_FEEDBACKS_RESPONSE, publicFeedbacks)
        }
        if (purchase.refundNote != null) {
            json.add(REFUND_NOTE_RESPONSE, encryptedData(purchase.refundNote))
        }
        if (purchase.discountNQT > 0) {
            json.addProperty(DISCOUNT_NQT_RESPONSE, purchase.discountNQT.toString())
        }
        if (purchase.refundNQT > 0) {
            json.addProperty(REFUND_NQT_RESPONSE, purchase.refundNQT.toString())
        }
        return json
    }

    internal fun subscription(subscription: Subscription): JsonObject {
        val json = JsonObject()
        json.addProperty(ID_RESPONSE, Convert.toUnsignedLong(subscription.getId()!!))
        putAccount(json, SENDER_RESPONSE, subscription.getSenderId()!!)
        putAccount(json, RECIPIENT_RESPONSE, subscription.getRecipientId()!!)
        json.addProperty(AMOUNT_NQT_RESPONSE, Convert.toUnsignedLong(subscription.getAmountNQT()!!))
        json.addProperty(FREQUENCY_RESPONSE, subscription.getFrequency())
        json.addProperty(TIME_NEXT_RESPONSE, subscription.timeNext)
        return json
    }

    internal fun trade(trade: Trade, asset: Asset?): JsonObject {
        val json = JsonObject()
        json.addProperty(TIMESTAMP_RESPONSE, trade.timestamp)
        json.addProperty(QUANTITY_QNT_RESPONSE, trade.quantityQNT.toString())
        json.addProperty(PRICE_NQT_RESPONSE, trade.priceNQT.toString())
        json.addProperty(ASSET_RESPONSE, Convert.toUnsignedLong(trade.assetId))
        json.addProperty(ASK_ORDER_RESPONSE, Convert.toUnsignedLong(trade.askOrderId))
        json.addProperty(BID_ORDER_RESPONSE, Convert.toUnsignedLong(trade.bidOrderId))
        json.addProperty(ASK_ORDER_HEIGHT_RESPONSE, trade.askOrderHeight)
        json.addProperty(BID_ORDER_HEIGHT_RESPONSE, trade.bidOrderHeight)
        putAccount(json, SELLER_RESPONSE, trade.sellerId)
        putAccount(json, BUYER_RESPONSE, trade.buyerId)
        json.addProperty(BLOCK_RESPONSE, Convert.toUnsignedLong(trade.blockId))
        json.addProperty(HEIGHT_RESPONSE, trade.height)
        json.addProperty(TRADE_TYPE_RESPONSE, if (trade.isBuy) "buy" else "sell")
        if (asset != null) {
            json.addProperty(NAME_RESPONSE, asset.name)
            json.addProperty(DECIMALS_RESPONSE, asset.decimals)
        }
        return json
    }

    internal fun assetTransfer(assetTransfer: AssetTransfer, asset: Asset?): JsonObject {
        val json = JsonObject()
        json.addProperty(ASSET_TRANSFER_RESPONSE, Convert.toUnsignedLong(assetTransfer.id))
        json.addProperty(ASSET_RESPONSE, Convert.toUnsignedLong(assetTransfer.assetId))
        putAccount(json, SENDER_RESPONSE, assetTransfer.senderId)
        putAccount(json, RECIPIENT_RESPONSE, assetTransfer.recipientId)
        json.addProperty(QUANTITY_QNT_RESPONSE, assetTransfer.quantityQNT.toString())
        json.addProperty(HEIGHT_RESPONSE, assetTransfer.height)
        json.addProperty(TIMESTAMP_RESPONSE, assetTransfer.timestamp)
        if (asset != null) {
            json.addProperty(NAME_RESPONSE, asset.name)
            json.addProperty(DECIMALS_RESPONSE, asset.decimals)
        }

        return json
    }

    internal fun unconfirmedTransaction(transaction: Transaction): JsonObject {
        val json = JsonObject()
        json.addProperty(TYPE_RESPONSE, transaction.type.type)
        json.addProperty(SUBTYPE_RESPONSE, transaction.type.subtype)
        json.addProperty(TIMESTAMP_RESPONSE, transaction.timestamp)
        json.addProperty(DEADLINE_RESPONSE, transaction.deadline)
        json.addProperty(SENDER_PUBLIC_KEY_RESPONSE, Convert.toHexString(transaction.senderPublicKey))
        if (transaction.recipientId != 0L) {
            putAccount(json, RECIPIENT_RESPONSE, transaction.recipientId)
        }
        json.addProperty(AMOUNT_NQT_RESPONSE, transaction.amountNQT.toString())
        json.addProperty(FEE_NQT_RESPONSE, transaction.feeNQT.toString())
        if (transaction.referencedTransactionFullHash != null) {
            json.addProperty(REFERENCED_TRANSACTION_FULL_HASH_RESPONSE, transaction.referencedTransactionFullHash)
        }
        val signature = Convert.emptyToNull(transaction.signature)
        if (signature != null) {
            json.addProperty(SIGNATURE_RESPONSE, Convert.toHexString(signature))
            json.addProperty(SIGNATURE_HASH_RESPONSE, Convert.toHexString(Crypto.sha256().digest(signature)))
            json.addProperty(FULL_HASH_RESPONSE, transaction.fullHash)
            json.addProperty(TRANSACTION_RESPONSE, transaction.stringId)
        } else if (!transaction.type.isSigned) {
            json.addProperty(FULL_HASH_RESPONSE, transaction.fullHash)
            json.addProperty(TRANSACTION_RESPONSE, transaction.stringId)
        }
        val attachmentJSON = JsonObject()
        for (appendage in transaction.appendages) {
            JSON.addAll(attachmentJSON, appendage.jsonObject)
        }
        if (attachmentJSON.size() > 0) {
            modifyAttachmentJSON(attachmentJSON)
            json.add(ATTACHMENT_RESPONSE, attachmentJSON)
        }
        putAccount(json, SENDER_RESPONSE, transaction.senderId)
        json.addProperty(HEIGHT_RESPONSE, transaction.height)
        json.addProperty(VERSION_RESPONSE, transaction.version)
        if (transaction.version > 0) {
            json.addProperty(EC_BLOCK_ID_RESPONSE, Convert.toUnsignedLong(transaction.ecBlockId))
            json.addProperty(EC_BLOCK_HEIGHT_RESPONSE, transaction.ecBlockHeight)
        }

        return json
    }

    fun transaction(transaction: Transaction, currentBlockchainHeight: Int): JsonObject {
        val json = unconfirmedTransaction(transaction)
        json.addProperty(BLOCK_RESPONSE, Convert.toUnsignedLong(transaction.blockId))
        json.addProperty(CONFIRMATIONS_RESPONSE, currentBlockchainHeight - transaction.height)
        json.addProperty(BLOCK_TIMESTAMP_RESPONSE, transaction.blockTimestamp)
        return json
    }

    // ugly, hopefully temporary
    private fun modifyAttachmentJSON(json: JsonObject) {
        val quantityQNT = json.remove(QUANTITY_QNT_RESPONSE)
        if (quantityQNT != null && quantityQNT.isJsonPrimitive) {
            json.addProperty(QUANTITY_QNT_RESPONSE, quantityQNT.asString)
        }
        val priceNQT = json.remove(PRICE_NQT_RESPONSE)
        if (priceNQT != null && priceNQT.isJsonPrimitive) {
            json.addProperty(PRICE_NQT_RESPONSE, priceNQT.asString)
        }
        val discountNQT = json.remove(DISCOUNT_NQT_RESPONSE)
        if (discountNQT != null && discountNQT.isJsonPrimitive) {
            json.addProperty(DISCOUNT_NQT_RESPONSE, discountNQT.asString)
        }
        val refundNQT = json.remove(REFUND_NQT_RESPONSE)
        if (refundNQT != null && refundNQT.isJsonPrimitive) {
            json.addProperty(REFUND_NQT_RESPONSE, refundNQT.asString)
        }
    }

    internal fun putAccount(json: JsonObject, name: String, accountId: Long) {
        json.addProperty(name, Convert.toUnsignedLong(accountId))
        json.addProperty(name + "RS", Convert.rsAccount(accountId))
    }

    //TODO refactor the accountservice out of this :-)
    internal fun at(at: AT, accountService: AccountService): JsonObject {
        val json = JsonObject()
        val bf = ByteBuffer.allocate(8)
        bf.order(ByteOrder.LITTLE_ENDIAN)

        bf.put(at.creator)
        bf.clear()
        putAccount(json, "creator", bf.long) // TODO is this redundant or does this bring LE byte order?
        bf.clear()
        bf.put(at.id, 0, 8)
        val id = bf.getLong(0)
        json.addProperty("at", Convert.toUnsignedLong(id))
        json.addProperty("atRS", Convert.rsAccount(id))
        json.addProperty("atVersion", at.version)
        json.addProperty("name", at.name)
        json.addProperty("description", at.description)
        json.addProperty("creator", Convert.toUnsignedLong(AtApiHelper.getLong(at.creator)))
        json.addProperty("creatorRS", Convert.rsAccount(AtApiHelper.getLong(at.creator)))
        json.addProperty("machineCode", Convert.toHexString(at.apCodeBytes))
        json.addProperty("machineData", Convert.toHexString(at.apDataBytes))
        json.addProperty("balanceNQT", Convert.toUnsignedLong(accountService.getAccount(id).balanceNQT))
        json.addProperty("prevBalanceNQT", Convert.toUnsignedLong(at.getpBalance()!!))
        json.addProperty("nextBlock", at.nextHeight())
        json.addProperty("frozen", at.freezeOnSameBalance())
        json.addProperty("running", at.machineState.isRunning)
        json.addProperty("stopped", at.machineState.isStopped)
        json.addProperty("finished", at.machineState.isFinished)
        json.addProperty("dead", at.machineState.isDead)
        json.addProperty("minActivation", Convert.toUnsignedLong(at.minActivationAmount()))
        json.addProperty("creationBlock", at.creationBlockHeight)
        return json
    }

    internal fun hex2long(longString: String): JsonObject {
        val json = JsonObject()
        json.addProperty("hex2long", longString)
        return json
    }

}// never