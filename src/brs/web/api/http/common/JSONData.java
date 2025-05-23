package brs.web.api.http.common;

import brs.*;
import brs.Alias.Offer;
import brs.at.AT;
import brs.at.AtApiHelper;
import brs.at.AtMachineState;
import brs.crypto.Crypto;
import brs.crypto.EncryptedData;
import brs.db.sql.SqlTransactionDb;
import brs.peer.Peer;
import brs.props.Props;
import brs.util.Convert;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import static brs.web.api.http.common.ResultFields.*;

import java.util.List;

public final class JSONData {

  static final long[] PRICE_MULTIPLIER = {1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000};

  public static JsonObject alias(Alias alias, Alias tld, Offer offer, int numbefOfAliases) {
    JsonObject json = new JsonObject();
    putAccount(json, ACCOUNT_RESPONSE, alias.getAccountId());
    json.addProperty(ALIAS_NAME_RESPONSE, alias.getAliasName());
    json.addProperty(TIMESTAMP_RESPONSE, alias.getTimestamp());
    json.addProperty(ALIAS_RESPONSE, Convert.toUnsignedLong(alias.getId()));
    if(tld != null) {
      json.addProperty(ALIAS_URI_RESPONSE, alias.getAliasUri());
      json.addProperty(TLD_RESPONSE, Convert.toUnsignedLong(tld.getId()));
      json.addProperty(TLD_NAME_RESPONSE, tld.getAliasName());
    }
    else {
      json.addProperty(NUMBER_OF_ALIASES, numbefOfAliases);
    }

    if (offer != null) {
      json.addProperty(PRICE_NQT_RESPONSE, String.valueOf(offer.getPriceNqt()));
      if (offer.getBuyerId() != 0) {
        json.addProperty(BUYER_RESPONSE, Convert.toUnsignedLong(offer.getBuyerId()));
      }
    }
    return json;
  }

  public static JsonObject accountBalance(Account account) {
    JsonObject json = new JsonObject();
    if (account == null) {
      json.addProperty(BALANCE_NQT_RESPONSE,             "0");
      json.addProperty(UNCONFIRMED_BALANCE_NQT_RESPONSE, "0");
      json.addProperty(FORGED_BALANCE_NQT_RESPONSE,      "0");
      json.addProperty(GUARANTEED_BALANCE_NQT_RESPONSE,  "0");
    }
    else {
      json.addProperty(BALANCE_NQT_RESPONSE, String.valueOf(account.getBalanceNqt()));
      json.addProperty(UNCONFIRMED_BALANCE_NQT_RESPONSE, String.valueOf(account.getUnconfirmedBalanceNqt()));
      json.addProperty(FORGED_BALANCE_NQT_RESPONSE, String.valueOf(account.getForgedBalanceNqt()));
      json.addProperty(GUARANTEED_BALANCE_NQT_RESPONSE, String.valueOf(account.getBalanceNqt()));
    }
    return json;
  }

  public static JsonObject asset(Asset asset, Account issuerAccount, long quantityBurnt, int tradeCount, int transferCount, int assetAccountsCount, long circulatingSupply,
      long tradeVolume, long highPrice, long lowPrice, long openPrice, long closePrice) {
    JsonObject json = new JsonObject();
    putAccount(json, ACCOUNT_RESPONSE, asset.getAccountId());
    putAccount(json, ISSUER_RESPONSE, asset.getIssuerId());
    if(issuerAccount != null) {
      json.addProperty(PUBLIC_KEY_RESPONSE, Convert.toHexString(issuerAccount.getPublicKey()));
    }
    json.addProperty(NAME_RESPONSE, asset.getName());
    json.addProperty(DESCRIPTION_RESPONSE, asset.getDescription());
    json.addProperty(DECIMALS_RESPONSE, asset.getDecimals());
    json.addProperty(MINTABLE_RESPONSE, asset.getMintable());
    json.addProperty(QUANTITY_QNT_RESPONSE, String.valueOf(asset.getQuantityQnt()));
    if(quantityBurnt >= 0) {
      json.addProperty(QUANTITY_BURNT_QNT_RESPONSE, String.valueOf(quantityBurnt));
    }
    json.addProperty(ASSET_RESPONSE, Convert.toUnsignedLong(asset.getId()));
    if(assetAccountsCount >= 0){
      json.addProperty(QUANTITY_CIRCULATING_QNT_RESPONSE, String.valueOf(circulatingSupply));
      json.addProperty(NUMBER_OF_TRADES_RESPONSE, tradeCount);
      json.addProperty(NUMBER_OF_TRANSFERS_RESPONSE, transferCount);
      json.addProperty(NUMBER_OF_ACCOUNTS_RESPONSE, assetAccountsCount);
    }
    if(tradeVolume >=0) {
      long multiplier = PRICE_MULTIPLIER[asset.getDecimals()];
      json.addProperty(VOLUME_QNT_RESPONSE, String.valueOf(tradeVolume));
      json.addProperty(PRICE_HIGH_RESPONSE, String.valueOf(highPrice*multiplier));
      json.addProperty(PRICE_LOW_RESPONSE, String.valueOf(lowPrice*multiplier));
      json.addProperty(PRICE_OPEN_RESPONSE, String.valueOf(openPrice*multiplier));
      json.addProperty(PRICE_CLOSE_RESPONSE, String.valueOf(closePrice*multiplier));
    }
    return json;
  }

  public static JsonObject accountAsset(Account.AccountAsset accountAsset) {
    JsonObject json = new JsonObject();
    putAccount(json, ACCOUNT_RESPONSE, accountAsset.getAccountId());
    json.addProperty(ASSET_RESPONSE, Convert.toUnsignedLong(accountAsset.getAssetId()));
    json.addProperty(QUANTITY_QNT_RESPONSE, String.valueOf(accountAsset.getQuantityQnt()));
    json.addProperty(UNCONFIRMED_QUANTITY_QNT_RESPONSE, String.valueOf(accountAsset.getUnconfirmedQuantityQnt()));
    json.addProperty(IS_TREASURY_RESPONSE, accountAsset.isTreasury());
    return json;
  }

  public static JsonObject askOrder(Order.Ask order, Asset asset) {
    JsonObject json = order(order, asset);
    json.addProperty(TYPE_RESPONSE, "ask");
    return json;
  }

  public static JsonObject bidOrder(Order.Bid order, Asset asset) {
    JsonObject json = order(order, asset);
    json.addProperty(TYPE_RESPONSE, "bid");
    return json;
  }

  public static JsonObject order(Order order, Asset asset) {
    JsonObject json = new JsonObject();
    json.addProperty(ORDER_RESPONSE, Convert.toUnsignedLong(order.getId()));
    json.addProperty(ASSET_RESPONSE, Convert.toUnsignedLong(order.getAssetId()));
    putAccount(json, ACCOUNT_RESPONSE, order.getAccountId());
    json.addProperty(QUANTITY_QNT_RESPONSE, String.valueOf(order.getQuantityQNT()));
    json.addProperty(PRICE_NQT_RESPONSE, String.valueOf(order.getPriceNQT()));
    json.addProperty(HEIGHT_RESPONSE, order.getHeight());
    if(asset != null) {
      json.addProperty(NAME_RESPONSE, asset.getName());
      json.addProperty(DECIMALS_RESPONSE, asset.getDecimals());
      json.addProperty(PRICE_RESPONSE, String.valueOf(order.getPriceNQT() * PRICE_MULTIPLIER[asset.getDecimals()]));
    }
    return json;
  }

  public static JsonObject block(Block block, boolean includeTransactions, int currentBlockchainHeight, long blockReward, int scoopNum) {
    JsonObject json = new JsonObject();
    List<Transaction> allBlockTransactions = block.getAllTransactions();
    json.addProperty(BLOCK_RESPONSE, block.getStringId());
    json.addProperty(HEIGHT_RESPONSE, block.getHeight());
    putAccount(json, GENERATOR_RESPONSE, block.getGeneratorId());
    json.addProperty(GENERATOR_PUBLIC_KEY_RESPONSE, Convert.toHexString(block.getGeneratorPublicKey()));
    json.addProperty(NONCE_RESPONSE, Convert.toUnsignedLong(block.getNonce()));
    json.addProperty(SCOOP_NUM_RESPONSE, scoopNum);
    json.addProperty(TIMESTAMP_RESPONSE, block.getTimestamp());
    json.addProperty(NUMBER_OF_TRANSACTIONS_RESPONSE, allBlockTransactions.size());
    json.addProperty(TOTAL_AMOUNT_NQT_RESPONSE, String.valueOf(block.getTotalAmountNqt()));
    json.addProperty(TOTAL_FEE_NQT_RESPONSE, String.valueOf(block.getTotalFeeNqt()));
    json.addProperty(TOTAL_FEE_CASH_BACK_NQT_RESPONSE, String.valueOf(block.getTotalFeeCashBackNqt()));
    json.addProperty(TOTAL_FEE_BURNT_NQT_RESPONSE, String.valueOf(block.getTotalFeeBurntNqt()));
    json.addProperty(BLOCK_REWARD_NQT_RESPONSE, Convert.toUnsignedLong(blockReward));
    json.addProperty(BLOCK_REWARD_RESPONSE, Convert.toUnsignedLong(blockReward / Signum.getPropertyService().getInt(Props.ONE_COIN_NQT)));
    json.addProperty(PAYLOAD_LENGTH_RESPONSE, block.getPayloadLength());
    json.addProperty(VERSION_RESPONSE, block.getVersion());
    json.addProperty(BASE_TARGET_RESPONSE, Convert.toUnsignedLong(block.getCapacityBaseTarget()));
    json.addProperty(AVERAGE_COMMITMENT_NQT_RESPONSE, Convert.toUnsignedLong(block.getAverageCommitment()));
    json.addProperty(CUMULATIVE_DIFFICULTY_RESPONSE, block.getCumulativeDifficulty().toString());


    if (block.getPreviousBlockId() != 0) {
      json.addProperty(PREVIOUS_BLOCK_RESPONSE, Convert.toUnsignedLong(block.getPreviousBlockId()));
    }

    if (block.getNextBlockId() != 0) {
      json.addProperty(NEXT_BLOCK_RESPONSE, Convert.toUnsignedLong(block.getNextBlockId()));
    }

    json.addProperty(PAYLOAD_HASH_RESPONSE, Convert.toHexString(block.getPayloadHash()));
    json.addProperty(GENERATION_SIGNATURE_RESPONSE, Convert.toHexString(block.getGenerationSignature()));

    if (block.getVersion() > 1) {
      json.addProperty(PREVIOUS_BLOCK_HASH_RESPONSE, Convert.toHexString(block.getPreviousBlockHash()));
    }

    json.addProperty(BLOCK_SIGNATURE_RESPONSE, Convert.toHexString(block.getBlockSignature()));

    JsonArray transactions = new JsonArray();
    for (Transaction transaction : allBlockTransactions) {
      if (includeTransactions) {
        transactions.add(transaction(transaction, currentBlockchainHeight));
      } else {
        transactions.add(Convert.toUnsignedLong(transaction.getId()));
      }
    }
    json.add(TRANSACTIONS_RESPONSE, transactions);
    return json;
  }

  public static JsonObject encryptedData(EncryptedData encryptedData) {
    JsonObject json = new JsonObject();
    json.addProperty(DATA_RESPONSE, Convert.toHexString(encryptedData.getData()));
    json.addProperty(NONCE_RESPONSE, Convert.toHexString(encryptedData.getNonce()));
    return json;
  }

  public static JsonObject escrowTransaction(Escrow escrow) {
    JsonObject json = new JsonObject();
    json.addProperty(ID_RESPONSE, Convert.toUnsignedLong(escrow.getId()));
    json.addProperty(SENDER_RESPONSE, Convert.toUnsignedLong(escrow.getSenderId()));
    json.addProperty(SENDER_RS_RESPONSE, Convert.rsAccount(escrow.getSenderId()));
    json.addProperty(RECIPIENT_RESPONSE, Convert.toUnsignedLong(escrow.getRecipientId()));
    json.addProperty(RECIPIENT_RS_RESPONSE, Convert.rsAccount(escrow.getRecipientId()));
    json.addProperty(AMOUNT_NQT_RESPONSE, Convert.toUnsignedLong(escrow.getAmountNQT()));
    json.addProperty(REQUIRED_SIGNERS_RESPONSE, escrow.getRequiredSigners());
    json.addProperty(DEADLINE_RESPONSE, escrow.getDeadline());
    json.addProperty(DEADLINE_ACTION_RESPONSE, Escrow.decisionToString(escrow.getDeadlineAction()));

    JsonArray signers = new JsonArray();
    for (Escrow.Decision decision : escrow.getDecisions()) {
      if(decision.getAccountId().equals(escrow.getSenderId()) ||
              decision.getAccountId().equals(escrow.getRecipientId())) {
        continue;
      }
      JsonObject signerDetails = new JsonObject();
      signerDetails.addProperty(ID_RESPONSE, Convert.toUnsignedLong(decision.getAccountId()));
      signerDetails.addProperty(ID_RS_RESPONSE, Convert.rsAccount(decision.getAccountId()));
      signerDetails.addProperty(DECISION_RESPONSE, Escrow.decisionToString(decision.getDecision()));
      signers.add(signerDetails);
    }
    json.add(SIGNERS_RESPONSE, signers);
    return json;
  }

  public static JsonObject goods(DigitalGoodsStore.Goods goods) {
    JsonObject json = new JsonObject();
    json.addProperty(GOODS_RESPONSE, Convert.toUnsignedLong(goods.getId()));
    json.addProperty(NAME_RESPONSE, goods.getName());
    json.addProperty(DESCRIPTION_RESPONSE, goods.getDescription());
    json.addProperty(QUANTITY_RESPONSE, goods.getQuantity());
    json.addProperty(PRICE_NQT_RESPONSE, String.valueOf(goods.getPriceNQT()));
    putAccount(json, SELLER_RESPONSE, goods.getSellerId());
    json.addProperty(TAGS_RESPONSE, goods.getTags());
    json.addProperty(DELISTED_RESPONSE, goods.isDelisted());
    json.addProperty(TIMESTAMP_RESPONSE, goods.getTimestamp());
    return json;
  }

  public static JsonObject token(Token token) {
    JsonObject json = new JsonObject();
    putAccount(json, "account", Account.getId(token.getPublicKey()));
    json.addProperty("timestamp", token.getTimestamp());
    json.addProperty("valid", token.isValid());
    return json;
  }

  public static JsonObject peer(Peer peer) {
    JsonObject json = new JsonObject();
    json.addProperty("state", peer.getState().ordinal());
    json.addProperty("announcedAddress", peer.getAnnouncedAddress());
    json.addProperty("shareAddress", peer.shareAddress());
    json.addProperty("downloadedVolume", peer.getDownloadedVolume());
    json.addProperty("uploadedVolume", peer.getUploadedVolume());
    json.addProperty("application", peer.getApplication());
    json.addProperty("version", peer.getVersion().toStringIfNotEmpty());
    json.addProperty("platform", peer.getPlatform());
    json.addProperty("networkName", peer.getNetworkName());
    json.addProperty("blacklisted", peer.isBlacklisted());
    json.addProperty("lastUpdated", peer.getLastUpdated());
    return json;
  }

  public static JsonObject purchase(DigitalGoodsStore.Purchase purchase) {
    JsonObject json = new JsonObject();
    json.addProperty(PURCHASE_RESPONSE, Convert.toUnsignedLong(purchase.getId()));
    json.addProperty(GOODS_RESPONSE, Convert.toUnsignedLong(purchase.getGoodsId()));
    json.addProperty(NAME_RESPONSE, purchase.getName());
    putAccount(json, SELLER_RESPONSE, purchase.getSellerId());
    json.addProperty(PRICE_NQT_RESPONSE, String.valueOf(purchase.getPriceNQT()));
    json.addProperty(QUANTITY_RESPONSE, purchase.getQuantity());
    putAccount(json, BUYER_RESPONSE, purchase.getBuyerId());
    json.addProperty(TIMESTAMP_RESPONSE, purchase.getTimestamp());
    json.addProperty(DELIVERY_DEADLINE_TIMESTAMP_RESPONSE, purchase.getDeliveryDeadlineTimestamp());
    if (purchase.getNote() != null) {
      json.add(NOTE_RESPONSE, encryptedData(purchase.getNote()));
    }
    json.addProperty(PENDING_RESPONSE, purchase.isPending());
    if (purchase.getEncryptedGoods() != null) {
      json.add(GOODS_DATA_RESPONSE, encryptedData(purchase.getEncryptedGoods()));
      json.addProperty(GOODS_IS_TEXT_RESPONSE, purchase.goodsIsText());
    }
    if (purchase.getFeedbackNotes() != null) {
      JsonArray feedbacks = new JsonArray();
      for (EncryptedData encryptedData : purchase.getFeedbackNotes()) {
        feedbacks.add(encryptedData(encryptedData));
      }
      json.add(FEEDBACK_NOTES_RESPONSE, feedbacks);
    }
    if (!purchase.getPublicFeedback().isEmpty()) {
      JsonArray publicFeedbacks = new JsonArray();
      for (String string : purchase.getPublicFeedback()) {
        publicFeedbacks.add(string);
      }
      json.add(PUBLIC_FEEDBACKS_RESPONSE, publicFeedbacks);
    }
    if (purchase.getRefundNote() != null) {
      json.add(REFUND_NOTE_RESPONSE, encryptedData(purchase.getRefundNote()));
    }
    if (purchase.getDiscountNQT() > 0) {
      json.addProperty(DISCOUNT_NQT_RESPONSE, String.valueOf(purchase.getDiscountNQT()));
    }
    if (purchase.getRefundNQT() > 0) {
      json.addProperty(REFUND_NQT_RESPONSE, String.valueOf(purchase.getRefundNQT()));
    }
    return json;
  }

  public static JsonObject subscription(Subscription subscription, Alias alias, Alias tld, Transaction transaction) {
    JsonObject json = new JsonObject();
    json.addProperty(ID_RESPONSE, Convert.toUnsignedLong(subscription.getId()));
    putAccount(json, SENDER_RESPONSE, subscription.getSenderId());
    if(alias == null) {
      putAccount(json, RECIPIENT_RESPONSE, subscription.getRecipientId());
    }
    else {
      putAccount(json, RECIPIENT_RESPONSE, tld.getAccountId());
      json.addProperty(ALIAS_RESPONSE, Convert.toUnsignedLong(alias.getId()));
      json.addProperty(ALIAS_NAME_RESPONSE, alias.getAliasName());
      json.addProperty(TLD_RESPONSE, Convert.toUnsignedLong(tld.getId()));
      json.addProperty(TLD_NAME_RESPONSE, tld.getAliasName());
    }
    json.addProperty(AMOUNT_NQT_RESPONSE, Convert.toUnsignedLong(subscription.getAmountNQT()));
    json.addProperty(FREQUENCY_RESPONSE, subscription.getFrequency());
    json.addProperty(TIME_NEXT_RESPONSE, subscription.getTimeNext());

    if(transaction != null) {
      json.addProperty(TIMESTAMP_RESPONSE, transaction.getTimestamp());
      Appendix.Message message = transaction.getMessage();
      if(message != null) {
        json.add(ATTACHMENT_RESPONSE, message.getJsonObject());
      }
    }
    return json;
  }

  public static JsonObject trade(Trade trade, Asset asset) {
    JsonObject json = new JsonObject();
    json.addProperty(TIMESTAMP_RESPONSE, trade.getTimestamp());
    json.addProperty(QUANTITY_QNT_RESPONSE, String.valueOf(trade.getQuantityQNT()));
    json.addProperty(PRICE_NQT_RESPONSE, String.valueOf(trade.getPriceNQT()));
    json.addProperty(ASSET_RESPONSE, Convert.toUnsignedLong(trade.getAssetId()));
    json.addProperty(ASK_ORDER_RESPONSE, Convert.toUnsignedLong(trade.getAskOrderId()));
    json.addProperty(BID_ORDER_RESPONSE, Convert.toUnsignedLong(trade.getBidOrderId()));
    json.addProperty(ASK_ORDER_HEIGHT_RESPONSE, trade.getAskOrderHeight());
    json.addProperty(BID_ORDER_HEIGHT_RESPONSE, trade.getBidOrderHeight());
    putAccount(json, SELLER_RESPONSE, trade.getSellerId());
    putAccount(json, BUYER_RESPONSE, trade.getBuyerId());
    json.addProperty(BLOCK_RESPONSE, Convert.toUnsignedLong(trade.getBlockId()));
    json.addProperty(HEIGHT_RESPONSE, trade.getHeight());
    json.addProperty(TRADE_TYPE_RESPONSE, trade.isBuy() ? "buy" : "sell");
    if (asset != null) {
      json.addProperty(NAME_RESPONSE, asset.getName());
      json.addProperty(DECIMALS_RESPONSE, asset.getDecimals());
      json.addProperty(PRICE_RESPONSE, String.valueOf(trade.getPriceNQT() * PRICE_MULTIPLIER[asset.getDecimals()]));
    }
    return json;
  }

  public static JsonObject assetTransfer(AssetTransfer assetTransfer, Asset asset) {
    JsonObject json = new JsonObject();
    json.addProperty(ASSET_TRANSFER_RESPONSE, Convert.toUnsignedLong(assetTransfer.getId()));
    json.addProperty(ASSET_RESPONSE, Convert.toUnsignedLong(assetTransfer.getAssetId()));
    putAccount(json, SENDER_RESPONSE, assetTransfer.getSenderId());
    putAccount(json, RECIPIENT_RESPONSE, assetTransfer.getRecipientId());
    json.addProperty(QUANTITY_QNT_RESPONSE, String.valueOf(assetTransfer.getQuantityQnt()));
    json.addProperty(HEIGHT_RESPONSE, assetTransfer.getHeight());
    json.addProperty(TIMESTAMP_RESPONSE, assetTransfer.getTimestamp());
    if (asset != null) {
      json.addProperty(NAME_RESPONSE, asset.getName());
      json.addProperty(DECIMALS_RESPONSE, asset.getDecimals());
    }

    return json;
  }

  public static JsonObject unconfirmedTransaction(Transaction transaction) {
    JsonObject json = new JsonObject();
    json.addProperty(TYPE_RESPONSE, transaction.getType().getType());
    json.addProperty(SUBTYPE_RESPONSE, transaction.getType().getSubtype());
    json.addProperty(TIMESTAMP_RESPONSE, transaction.getTimestamp());
    json.addProperty(DEADLINE_RESPONSE, transaction.getDeadline());
    json.addProperty(SENDER_PUBLIC_KEY_RESPONSE, Convert.toHexString(transaction.getSenderPublicKey()));
    if (transaction.getRecipientId() != 0) {
      putAccount(json, RECIPIENT_RESPONSE, transaction.getRecipientId());
    }
    json.addProperty(AMOUNT_NQT_RESPONSE, String.valueOf(transaction.getAmountNqt()));
    json.addProperty(FEE_NQT_RESPONSE, String.valueOf(transaction.getFeeNqt()));
    if (transaction.getReferencedTransactionFullHash() != null) {
      json.addProperty(REFERENCED_TRANSACTION_FULL_HASH_RESPONSE, transaction.getReferencedTransactionFullHash());
    }
    byte[] signature = Convert.emptyToNull(transaction.getSignature());
    if (signature != null) {
      json.addProperty(SIGNATURE_RESPONSE, Convert.toHexString(signature));
      json.addProperty(SIGNATURE_HASH_RESPONSE, Convert.toHexString(Crypto.sha256().digest(signature)));
      json.addProperty(FULL_HASH_RESPONSE, transaction.getFullHash());
      json.addProperty(TRANSACTION_RESPONSE, transaction.getStringId());
    }
    else if (!transaction.getType().isSigned()) {
      json.addProperty(FULL_HASH_RESPONSE, transaction.getFullHash());
      json.addProperty(TRANSACTION_RESPONSE, transaction.getStringId());
    }
    else if(transaction.hasId()){
      json.addProperty(TRANSACTION_RESPONSE, transaction.getStringId());
    }
    JsonObject attachmentJSON = new JsonObject();
    for (Appendix appendage : transaction.getAppendages()) {
      JSON.addAll(attachmentJSON, appendage.getJsonObject());
    }
    if (attachmentJSON.size() > 0) {
      normalizeAttachmentValues(attachmentJSON);
      json.add(ATTACHMENT_RESPONSE, attachmentJSON);
    }
    byte[] attachmentBytes = SqlTransactionDb.getAttachmentBytes(transaction);
    if (attachmentBytes != null) {
      json.addProperty(ATTACHMENT_BYTES_RESPONSE, Convert.toHexString(attachmentBytes));
    }
    putAccount(json, SENDER_RESPONSE, transaction.getSenderId());
    json.addProperty(HEIGHT_RESPONSE, transaction.getHeight());
    json.addProperty(VERSION_RESPONSE, transaction.getVersion());
    if (transaction.getVersion() > 0) {
      json.addProperty(EC_BLOCK_ID_RESPONSE, Convert.toUnsignedLong(transaction.getEcBlockId()));
      json.addProperty(EC_BLOCK_HEIGHT_RESPONSE, transaction.getEcBlockHeight());
    }
    json.addProperty(CASH_BACK_ID_RESPONSE, Convert.toUnsignedLong(transaction.getCashBackId()));

    return json;
  }

  public static JsonObject transaction(Transaction transaction, int currentBlockchainHeight) {
    JsonObject json = unconfirmedTransaction(transaction);
    json.addProperty(BLOCK_RESPONSE, Convert.toUnsignedLong(transaction.getBlockId()));
    json.addProperty(CONFIRMATIONS_RESPONSE, currentBlockchainHeight - transaction.getHeight());
    json.addProperty(BLOCK_TIMESTAMP_RESPONSE, transaction.getBlockTimestamp());
    return json;
  }

  public static JsonObject indirect(IndirectIncoming indirectIncoming, int currentBlockchainHeight) {
    JsonObject json = new JsonObject();
    json.addProperty(AMOUNT_NQT_RESPONSE, String.valueOf(indirectIncoming.getAmount()));
    json.addProperty(QUANTITY_QNT_RESPONSE, String.valueOf(indirectIncoming.getQuantity()));
    json.addProperty(HEIGHT_RESPONSE, indirectIncoming.getHeight());
    json.addProperty(CONFIRMATIONS_RESPONSE, currentBlockchainHeight - indirectIncoming.getHeight());
    return json;
  }

  /**
   * Normalize attachment values so numeric fields are serialized as strings.
   * This preserves formatting for clients expecting string-based numbers.
   */
  private static void normalizeAttachmentValues(JsonObject json) {
    convertNumbersToStrings(json,
        QUANTITY_QNT_RESPONSE,
        PRICE_NQT_RESPONSE,
        DISCOUNT_NQT_RESPONSE,
        REFUND_NQT_RESPONSE);
  }

  private static void convertNumbersToStrings(JsonObject json, String... keys) {
    for (String key : keys) {
      JsonElement element = json.get(key);
      if (element != null && element.isJsonPrimitive()) {
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isNumber()) {
          json.addProperty(key, primitive.getAsString());
        }
      }
    }
  }

  public static void putAccount(JsonObject json, String name, long accountId) {
    json.addProperty(name, Convert.toUnsignedLong(accountId));
    json.addProperty(name + "RS", Convert.rsAccount(accountId));
  }

  public static JsonObject at(AT at) {
    return at(at, null, true);
  }

  public static JsonObject at(AT at, AtMachineState atCreation, boolean includeDetails) {
    JsonObject json = new JsonObject();

    long id = AtApiHelper.getLong(at.getId());

    json.addProperty("at", Convert.toUnsignedLong( id ));
    json.addProperty("machineData", Convert.toHexString(at.getApDataBytes()));
    json.addProperty("balanceNQT", Convert.toUnsignedLong(at.getgBalance()));
    json.addProperty("prevBalanceNQT", Convert.toUnsignedLong(at.getpBalance()));
    json.addProperty("nextBlock", at.nextHeight());
    json.addProperty("frozen", at.freezeOnSameBalance());
    json.addProperty("running", at.getMachineState().isRunning());
    json.addProperty("stopped", at.getMachineState().isStopped());
    json.addProperty("finished", at.getMachineState().isFinished());
    json.addProperty("dead", at.getMachineState().isDead());
    json.addProperty("machineCodeHashId", Convert.toUnsignedLong(at.getApCodeHashId()) );

    // some immutable data, but still take little bytes
    json.addProperty("atVersion", at.getVersion());
    json.addProperty("atRS", Convert.rsAccount(id));
    json.addProperty("name", at.getName());
    json.addProperty("description", at.getDescription());
    json.addProperty("creator", Convert.toUnsignedLong(AtApiHelper.getLong(at.getCreator())));
    json.addProperty("creatorRS", Convert.rsAccount(AtApiHelper.getLong(at.getCreator())));
    json.addProperty("minActivation", Convert.toUnsignedLong(at.minActivationAmount()));
    json.addProperty("creationBlock", at.getCreationBlockHeight());

    if(includeDetails) {
      // These take more bytes and can be skipped
      json.addProperty("machineCode", Convert.toHexString(at.getApCodeBytes()));
      if(atCreation != null) {
        json.addProperty("creationMachineData", Convert.toHexString(atCreation.getApDataBytes()));
      }
    }
    return json;
  }

  public static JsonObject hex2long(String longString){
    JsonObject json = new JsonObject();
    json.addProperty("hex2long", longString);
    return json;
  }

  private JSONData() {} // never

}
