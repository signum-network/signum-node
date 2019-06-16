package brs;

import brs.TransactionType.Payment;
import brs.at.AtConstants;
import brs.crypto.EncryptedData;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.util.Convert;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.Map.Entry;

import static brs.http.common.Parameters.*;
import static brs.http.common.ResultFields.*;

public interface Attachment extends Appendix {

  TransactionType getTransactionType();

  abstract class AbstractAttachment extends AbstractAppendix implements Attachment {

    private AbstractAttachment(ByteBuffer buffer, byte transactionVersion) {
      super(buffer, transactionVersion);
    }

    private AbstractAttachment(JsonObject attachmentData) {
      super(attachmentData);
    }

    private AbstractAttachment(byte version) {
      super(version);
    }

    private AbstractAttachment(int blockchainHeight) {
      super(blockchainHeight);
    }

    @Override
    public final void validate(Transaction transaction) throws BurstException.ValidationException {
      getTransactionType().validateAttachment(transaction);
    }

    @Override
    public final void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
      getTransactionType().apply(transaction, senderAccount, recipientAccount);
    }

    public static AbstractAttachment parseProtobufMessage(Any attachment) throws InvalidProtocolBufferException, BurstException.NotValidException {
      // Yes, this is fairly horrible. I wish there was a better way to do this but any does not let us switch on its contained class.
      if (attachment.is(BrsApi.OrdinaryPaymentAttachment.class)) {
        return ORDINARY_PAYMENT;
      } else if (attachment.is(BrsApi.ArbitraryMessageAttachment.class)) {
        return ARBITRARY_MESSAGE;
      } else if (attachment.is(BrsApi.ATPaymentAttachment.class)) {
        return AT_PAYMENT;
      } else if (attachment.is(BrsApi.MultiOutAttachment.class)) {
        return new PaymentMultiOutCreation(attachment.unpack(BrsApi.MultiOutAttachment.class));
      } else if (attachment.is(BrsApi.MultiOutSameAttachment.class)) {
        return new PaymentMultiSameOutCreation(attachment.unpack(BrsApi.MultiOutSameAttachment.class));
      } else if (attachment.is(BrsApi.AliasAssignmentAttachment.class)) {
        return new MessagingAliasAssignment(attachment.unpack(BrsApi.AliasAssignmentAttachment.class));
      } else if (attachment.is(BrsApi.AliasSellAttachment.class)) {
        return new MessagingAliasSell(attachment.unpack(BrsApi.AliasSellAttachment.class));
      } else if (attachment.is(BrsApi.AliasBuyAttachment.class)) {
        return new MessagingAliasBuy(attachment.unpack(BrsApi.AliasBuyAttachment.class));
      } else if (attachment.is(BrsApi.AccountInfoAttachment.class)) {
        return new MessagingAccountInfo(attachment.unpack(BrsApi.AccountInfoAttachment.class));
      } else if (attachment.is(BrsApi.AssetIssuanceAttachment.class)) {
        return new ColoredCoinsAssetIssuance(attachment.unpack(BrsApi.AssetIssuanceAttachment.class));
      } else if (attachment.is(BrsApi.AssetTransferAttachment.class)) {
        return new ColoredCoinsAssetTransfer(attachment.unpack(BrsApi.AssetTransferAttachment.class));
      } else if (attachment.is(BrsApi.AssetOrderPlacementAttachment.class)) {
        BrsApi.AssetOrderPlacementAttachment placementAttachment = attachment.unpack(BrsApi.AssetOrderPlacementAttachment.class);
        if (placementAttachment.getType() == BrsApi.OrderType.ASK) {
          return new ColoredCoinsAskOrderPlacement(placementAttachment);
        } else if (placementAttachment.getType() == BrsApi.OrderType.BID) {
          return new ColoredCoinsBidOrderPlacement(placementAttachment);
        }
      } else if (attachment.is(BrsApi.AssetOrderCancellationAttachment.class)) {
        BrsApi.AssetOrderCancellationAttachment placementAttachment = attachment.unpack(BrsApi.AssetOrderCancellationAttachment.class);
        if (placementAttachment.getType() == BrsApi.OrderType.ASK) {
          return new ColoredCoinsAskOrderCancellation(placementAttachment);
        } else if (placementAttachment.getType() == BrsApi.OrderType.BID) {
          return new ColoredCoinsBidOrderCancellation(placementAttachment);
        }
      } else if (attachment.is(BrsApi.DigitalGoodsListingAttachment.class)) {
        return new DigitalGoodsListing(attachment.unpack(BrsApi.DigitalGoodsListingAttachment.class));
      } else if (attachment.is(BrsApi.DigitalGoodsDelistingAttachment.class)) {
        return new DigitalGoodsDelisting(attachment.unpack(BrsApi.DigitalGoodsDelistingAttachment.class));
      } else if (attachment.is(BrsApi.DigitalGoodsPriceChangeAttachment.class)) {
        return new DigitalGoodsPriceChange(attachment.unpack(BrsApi.DigitalGoodsPriceChangeAttachment.class));
      } else if (attachment.is(BrsApi.DigitalGoodsQuantityChangeAttachment.class)) {
        return new DigitalGoodsQuantityChange(attachment.unpack(BrsApi.DigitalGoodsQuantityChangeAttachment.class));
      } else if (attachment.is(BrsApi.DigitalGoodsPurchaseAttachment.class)) {
        return new DigitalGoodsPurchase(attachment.unpack(BrsApi.DigitalGoodsPurchaseAttachment.class));
      } else if (attachment.is(BrsApi.DigitalGoodsDeliveryAttachment.class)) {
        return new DigitalGoodsDelivery(attachment.unpack(BrsApi.DigitalGoodsDeliveryAttachment.class));
      } else if (attachment.is(BrsApi.DigitalGoodsFeedbackAttachment.class)) {
        return new DigitalGoodsFeedback(attachment.unpack(BrsApi.DigitalGoodsFeedbackAttachment.class));
      } else if (attachment.is(BrsApi.DigitalGoodsRefundAttachment.class)) {
        return new DigitalGoodsRefund(attachment.unpack(BrsApi.DigitalGoodsRefundAttachment.class));
      } else if (attachment.is(BrsApi.EffectiveBalanceLeasingAttachment.class)) {
        return new AccountControlEffectiveBalanceLeasing(attachment.unpack(BrsApi.EffectiveBalanceLeasingAttachment.class));
      } else if (attachment.is(BrsApi.RewardRecipientAssignmentAttachment.class)) {
        return new BurstMiningRewardRecipientAssignment(attachment.unpack(BrsApi.RewardRecipientAssignmentAttachment.class));
      } else if (attachment.is(BrsApi.EscrowCreationAttachment.class)) {
        return new AdvancedPaymentEscrowCreation(attachment.unpack(BrsApi.EscrowCreationAttachment.class));
      } else if (attachment.is(BrsApi.EscrowSignAttachment.class)) {
        return new AdvancedPaymentEscrowSign(attachment.unpack(BrsApi.EscrowSignAttachment.class));
      } else if (attachment.is(BrsApi.EscrowResultAttachment.class)) {
        return new AdvancedPaymentEscrowResult(attachment.unpack(BrsApi.EscrowResultAttachment.class));
      } else if (attachment.is(BrsApi.SubscriptionSubscribeAttachment.class)) {
        return new AdvancedPaymentSubscriptionSubscribe(attachment.unpack(BrsApi.SubscriptionSubscribeAttachment.class));
      } else if (attachment.is(BrsApi.SubscriptionCancelAttachment.class)) {
        return new AdvancedPaymentSubscriptionCancel(attachment.unpack(BrsApi.SubscriptionCancelAttachment.class));
      } else if (attachment.is(BrsApi.SubscriptionPaymentAttachment.class)) {
        return new AdvancedPaymentSubscriptionPayment(attachment.unpack(BrsApi.SubscriptionPaymentAttachment.class));
      } else if (attachment.is(BrsApi.ATCreationAttachment.class)) {
        return new AutomatedTransactionsCreation(attachment.unpack(BrsApi.ATCreationAttachment.class));
      }
      // Default to ordinary payment
      return ORDINARY_PAYMENT;
    }
  }

  abstract class EmptyAttachment extends AbstractAttachment {

    private EmptyAttachment() {
      super((byte) 0);
    }

    @Override
    final int getMySize() {
      return 0;
    }

    @Override
    final void putMyBytes(ByteBuffer buffer) {
    }

    @Override
    final void putMyJSON(JsonObject json) {
    }

    @Override
    final boolean verifyVersion(byte transactionVersion) {
      return true;
    }

  }

  EmptyAttachment ORDINARY_PAYMENT = new EmptyAttachment() {

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.OrdinaryPaymentAttachment.getDefaultInstance());
    }

    @Override
    String getAppendixName() {
      return "OrdinaryPayment";
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.Payment.ORDINARY;
    }

  };

  class PaymentMultiOutCreation extends AbstractAttachment {

    private final ArrayList<ArrayList<Long>> recipients = new ArrayList<>();

    PaymentMultiOutCreation(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
      super(buffer, transactionVersion);

      int numberOfRecipients = Byte.toUnsignedInt(buffer.get());
      HashMap<Long,Boolean> recipientOf = new HashMap<>(numberOfRecipients);

      for (int i = 0; i < numberOfRecipients; ++i) {
        long recipientId = buffer.getLong();
        long amountNQT = buffer.getLong();

        if (recipientOf.containsKey(recipientId))
          throw new BurstException.NotValidException("Duplicate recipient on multi out transaction");

        if (amountNQT <= 0)
          throw new BurstException.NotValidException("Insufficient amountNQT on multi out transaction");

        recipientOf.put(recipientId, true);
        this.recipients.add(new ArrayList<>(Arrays.asList(recipientId, amountNQT)));
      }
      if (recipients.size() > Constants.MAX_MULTI_OUT_RECIPIENTS || recipients.size() <= 1) {
        throw new BurstException.NotValidException(
            "Invalid number of recipients listed on multi out transaction");
      }
    }

    PaymentMultiOutCreation(JsonObject attachmentData) throws BurstException.NotValidException {
      super(attachmentData);

      JsonArray receipientsJson = JSON.getAsJsonArray(attachmentData.get(RECIPIENTS_PARAMETER));
      HashMap<Long,Boolean> recipientOf = new HashMap<>();

      for (JsonElement recipientObject : receipientsJson) {
        JsonArray recipient = JSON.getAsJsonArray(recipientObject);

        long recipientId = new BigInteger(JSON.getAsString(recipient.get(0))).longValue();
        long amountNQT = JSON.getAsLong(recipient.get(1));
        if (recipientOf.containsKey(recipientId))
          throw new BurstException.NotValidException("Duplicate recipient on multi out transaction");

        if (amountNQT  <=0)
          throw new BurstException.NotValidException("Insufficient amountNQT on multi out transaction");

        recipientOf.put(recipientId, true);
        this.recipients.add(new ArrayList<>(Arrays.asList(recipientId, amountNQT)));
      }
      if (receipientsJson.size() > Constants.MAX_MULTI_OUT_RECIPIENTS || receipientsJson.size() <= 1) {
        throw new BurstException.NotValidException("Invalid number of recipients listed on multi out transaction");
      }
    }

    public PaymentMultiOutCreation(Collection<Entry<String, Long>> recipients, int blockchainHeight) throws BurstException.NotValidException {
      super(blockchainHeight);

      HashMap<Long,Boolean> recipientOf = new HashMap<>();
      for(Entry<String, Long> recipient : recipients ) {
        long recipientId = (new BigInteger(recipient.getKey())).longValue();
        long amountNQT   = recipient.getValue();
        if (recipientOf.containsKey(recipientId))
          throw new BurstException.NotValidException("Duplicate recipient on multi out transaction");

        if (amountNQT <= 0)
          throw new BurstException.NotValidException("Insufficient amountNQT on multi out transaction");

        recipientOf.put(recipientId, true);
        this.recipients.add(new ArrayList<>(Arrays.asList(recipientId, amountNQT)));
      }
      if (recipients.size() > Constants.MAX_MULTI_OUT_RECIPIENTS || recipients.size() <= 1) {
        throw new BurstException.NotValidException("Invalid number of recipients listed on multi out transaction");
      }
    }

    PaymentMultiOutCreation(BrsApi.MultiOutAttachment attachment) throws BurstException.NotValidException {
      super(((byte) attachment.getVersion()));
      HashMap<Long,Boolean> recipientOf = new HashMap<>();
      for (BrsApi.MultiOutAttachment.MultiOutRecipient recipient : attachment.getRecipientsList()) {
        long recipientId = recipient.getRecipient();
        long amountNQT = recipient.getAmount();
        if (recipientOf.containsKey(recipientId))
          throw new BurstException.NotValidException("Duplicate recipient on multi out transaction");

        if (amountNQT  <=0)
          throw new BurstException.NotValidException("Insufficient amountNQT on multi out transaction");

        recipientOf.put(recipientId, true);
        this.recipients.add(new ArrayList<>(Arrays.asList(recipientId, amountNQT)));
      }
      if (recipients.size() > Constants.MAX_MULTI_OUT_RECIPIENTS || recipients.size() <= 1) {
        throw new BurstException.NotValidException("Invalid number of recipients listed on multi out transaction");
      }
    }

    @Override
    String getAppendixName() {
      return "MultiOutCreation";
    }

    @Override
    int getMySize() {
      return 1 + recipients.size() * 16;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.put((byte) this.recipients.size());
      this.recipients.forEach((a) -> { buffer.putLong(a.get(0)); buffer.putLong(a.get(1)); });
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      final JsonArray recipientsJSON = new JsonArray();

      this.recipients.stream()
        .map(recipient -> {
          final JsonArray recipientJSON = new JsonArray();
          recipientJSON.add(Convert.toUnsignedLong(recipient.get(0)));
          recipientJSON.add(recipient.get(1).toString());
          return recipientJSON;
        }).forEach(recipientsJSON::add);

      attachment.add(RECIPIENTS_RESPONSE, recipientsJSON);
    }

    @Override
    public TransactionType getTransactionType() {
      return Payment.MULTI_OUT;
    }

    public Long getAmountNQT() {
      long amountNQT = 0;
      for ( ArrayList<Long> recipient : recipients ) {
        amountNQT = Convert.safeAdd(amountNQT, recipient.get(1));
      }
      return amountNQT;
    }

    public Collection<List<Long>> getRecipients() {
      return Collections.unmodifiableCollection(recipients);
    }

    @Override
    public Any getProtobufMessage() {
      BrsApi.MultiOutAttachment.Builder builder =  BrsApi.MultiOutAttachment.newBuilder()
              .setVersion(getVersion());
      for (ArrayList<Long> recipient : recipients) {
        builder.addRecipients(BrsApi.MultiOutAttachment.MultiOutRecipient.newBuilder()
                .setRecipient(recipient.get(0))
                .setAmount(recipient.get(1)));
      }
      return Any.pack(builder.build());
    }
  }

  class PaymentMultiSameOutCreation extends AbstractAttachment {

    private final ArrayList<Long> recipients = new ArrayList<>();

    PaymentMultiSameOutCreation(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
      super(buffer, transactionVersion);

      int numberOfRecipients = Byte.toUnsignedInt(buffer.get());
      HashMap<Long,Boolean> recipientOf = new HashMap<>(numberOfRecipients);

      for (int i = 0; i < numberOfRecipients; ++i) {
        long recipientId = buffer.getLong();

        if (recipientOf.containsKey(recipientId))
          throw new BurstException.NotValidException("Duplicate recipient on multi same out transaction");

        recipientOf.put(recipientId, true);
        this.recipients.add(recipientId);
      }
      if (recipients.size() > Constants.MAX_MULTI_SAME_OUT_RECIPIENTS || recipients.size() <= 1) {
        throw new BurstException.NotValidException(
            "Invalid number of recipients listed on multi same out transaction");
      }
    }

    PaymentMultiSameOutCreation(JsonObject attachmentData) throws BurstException.NotValidException {
      super(attachmentData);

      JsonArray recipientsJson = JSON.getAsJsonArray(attachmentData.get(RECIPIENTS_PARAMETER));
      HashMap<Long,Boolean> recipientOf = new HashMap<>();

      for (JsonElement recipient : recipientsJson) {
        long recipientId = new BigInteger(JSON.getAsString(recipient)).longValue();
        if (recipientOf.containsKey(recipientId))
          throw new BurstException.NotValidException("Duplicate recipient on multi same out transaction");

        recipientOf.put(recipientId, true);
        this.recipients.add(recipientId);
      }
      if (recipientsJson.size() > Constants.MAX_MULTI_SAME_OUT_RECIPIENTS || recipientsJson.size() <= 1) {
        throw new BurstException.NotValidException(
            "Invalid number of recipients listed on multi same out transaction");
      }
    }

    public PaymentMultiSameOutCreation(Collection<Long> recipients, int blockchainHeight) throws BurstException.NotValidException {
      super(blockchainHeight);

      HashMap<Long,Boolean> recipientOf = new HashMap<>();
      for(Long recipientId : recipients ) {
        if (recipientOf.containsKey(recipientId))
          throw new BurstException.NotValidException("Duplicate recipient on multi same out transaction");

        recipientOf.put(recipientId, true);
        this.recipients.add(recipientId);
      }
      if (recipients.size() > Constants.MAX_MULTI_SAME_OUT_RECIPIENTS || recipients.size() <= 1) {
        throw new BurstException.NotValidException(
            "Invalid number of recipients listed on multi same out transaction");
      }
    }

    PaymentMultiSameOutCreation(BrsApi.MultiOutSameAttachment attachment) throws BurstException.NotValidException {
      super(((byte) attachment.getVersion()));
      HashMap<Long,Boolean> recipientOf = new HashMap<>();
      for(Long recipientId : attachment.getRecipientsList()) {
        if (recipientOf.containsKey(recipientId))
          throw new BurstException.NotValidException("Duplicate recipient on multi same out transaction");

        recipientOf.put(recipientId, true);
        this.recipients.add(recipientId);
      }
      if (recipients.size() > Constants.MAX_MULTI_SAME_OUT_RECIPIENTS || recipients.size() <= 1) {
        throw new BurstException.NotValidException(
                "Invalid number of recipients listed on multi same out transaction");
      }
    }

    @Override
    String getAppendixName() {
      return "MultiSameOutCreation";
    }

    @Override
    int getMySize() {
      return 1 + recipients.size() * 8;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.put((byte) this.recipients.size());
      this.recipients.forEach(buffer::putLong);
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      JsonArray recipients = new JsonArray();
      this.recipients.forEach(a -> recipients.add(Convert.toUnsignedLong(a)));
      attachment.add(RECIPIENTS_RESPONSE, recipients);
    }

    @Override
    public TransactionType getTransactionType() {
      return Payment.MULTI_SAME_OUT;
    }

    public Collection<Long> getRecipients() {
      return Collections.unmodifiableCollection(recipients);
    }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.MultiOutSameAttachment.newBuilder()
              .setVersion(getVersion())
              .addAllRecipients(recipients)
              .build());
    }
  }

  // the message payload is in the Appendix
  EmptyAttachment ARBITRARY_MESSAGE = new EmptyAttachment() {

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.ArbitraryMessageAttachment.getDefaultInstance());
    }

    @Override
      String getAppendixName() {
        return "ArbitraryMessage";
      }

      @Override
      public TransactionType getTransactionType() {
        return TransactionType.Messaging.ARBITRARY_MESSAGE;
      }

    };

  EmptyAttachment AT_PAYMENT = new EmptyAttachment() {

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.ATPaymentAttachment.getDefaultInstance());
    }

    @Override
      public TransactionType getTransactionType() {
        return TransactionType.AutomatedTransactions.AT_PAYMENT;
      }

      @Override
      String getAppendixName() {
        return "AT Payment";
      }


    };

  class MessagingAliasAssignment extends AbstractAttachment {

    private final String aliasName;
    private final String aliasURI;

    MessagingAliasAssignment(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
      super(buffer, transactionVersion);
      aliasName = Convert.readString(buffer, buffer.get(), Constants.MAX_ALIAS_LENGTH).trim();
      aliasURI = Convert.readString(buffer, buffer.getShort(), Constants.MAX_ALIAS_URI_LENGTH).trim();
    }

    MessagingAliasAssignment(JsonObject attachmentData) {
      super(attachmentData);
      aliasName = (Convert.nullToEmpty(JSON.getAsString(attachmentData.get(ALIAS_PARAMETER)))).trim();
      aliasURI = (Convert.nullToEmpty(JSON.getAsString(attachmentData.get(URI_PARAMETER)))).trim();
    }

    public MessagingAliasAssignment(String aliasName, String aliasURI, int blockchainHeight) {
      super(blockchainHeight);
      this.aliasName = aliasName.trim();
      this.aliasURI = aliasURI.trim();
    }

    MessagingAliasAssignment(BrsApi.AliasAssignmentAttachment attachment) {
      super((byte) attachment.getVersion());
      this.aliasName = attachment.getName();
      this.aliasURI = attachment.getUri();
    }

    @Override
    String getAppendixName() {
      return "AliasAssignment";
    }

    @Override
    int getMySize() {
      return 1 + Convert.toBytes(aliasName).length + 2 + Convert.toBytes(aliasURI).length;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      byte[] alias = Convert.toBytes(this.aliasName);
      byte[] uri = Convert.toBytes(this.aliasURI);
      buffer.put((byte)alias.length);
      buffer.put(alias);
      buffer.putShort((short) uri.length);
      buffer.put(uri);
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(ALIAS_RESPONSE, aliasName);
      attachment.addProperty(URI_RESPONSE, aliasURI);
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.Messaging.ALIAS_ASSIGNMENT;
    }

    public String getAliasName() {
      return aliasName;
    }

    public String getAliasURI() {
      return aliasURI;
    }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.AliasAssignmentAttachment.newBuilder()
              .setVersion(getVersion())
              .setName(aliasName)
              .setUri(aliasURI)
              .build());
    }
  }

  class MessagingAliasSell extends AbstractAttachment {

    private final String aliasName;
    private final long priceNQT;

    MessagingAliasSell(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
      super(buffer, transactionVersion);
      this.aliasName = Convert.readString(buffer, buffer.get(), Constants.MAX_ALIAS_LENGTH);
      this.priceNQT = buffer.getLong();
    }

    MessagingAliasSell(JsonObject attachmentData) {
      super(attachmentData);
      this.aliasName = Convert.nullToEmpty(JSON.getAsString(attachmentData.get(ALIAS_PARAMETER)));
      this.priceNQT = JSON.getAsLong(attachmentData.get(PRICE_NQT_PARAMETER));
    }

    public MessagingAliasSell(String aliasName, long priceNQT, int blockchainHeight) {
      super(blockchainHeight);
      this.aliasName = aliasName;
      this.priceNQT = priceNQT;
    }

    MessagingAliasSell(BrsApi.AliasSellAttachment attachment) {
      super((byte) attachment.getVersion());
      this.aliasName = attachment.getName();
      this.priceNQT = attachment.getPrice();
    }

    @Override
    String getAppendixName() {
      return "AliasSell";
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.Messaging.ALIAS_SELL;
    }

    @Override
    int getMySize() {
      return 1 + Convert.toBytes(aliasName).length + 8;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      byte[] aliasBytes = Convert.toBytes(aliasName);
      buffer.put((byte)aliasBytes.length);
      buffer.put(aliasBytes);
      buffer.putLong(priceNQT);
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(ALIAS_RESPONSE, aliasName);
      attachment.addProperty(PRICE_NQT_RESPONSE, priceNQT);
    }

    public String getAliasName(){
      return aliasName;
    }

    public long getPriceNQT(){
      return priceNQT;
    }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.AliasSellAttachment.newBuilder()
              .setVersion(getVersion())
              .setName(aliasName)
              .setPrice(priceNQT)
              .build());
    }
  }

  final class MessagingAliasBuy extends AbstractAttachment {

    private final String aliasName;

    MessagingAliasBuy(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
      super(buffer, transactionVersion);
      this.aliasName = Convert.readString(buffer, buffer.get(), Constants.MAX_ALIAS_LENGTH);
    }

    MessagingAliasBuy(JsonObject attachmentData) {
      super(attachmentData);
      this.aliasName = Convert.nullToEmpty(JSON.getAsString(attachmentData.get(ALIAS_PARAMETER)));
    }

    public MessagingAliasBuy(String aliasName, int blockchainHeight) {
      super(blockchainHeight);
      this.aliasName = aliasName;
    }

    MessagingAliasBuy(BrsApi.AliasBuyAttachment attachment) {
      super((byte) attachment.getVersion());
      this.aliasName = attachment.getName();
    }

    @Override
    String getAppendixName() {
      return "AliasBuy";
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.Messaging.ALIAS_BUY;
    }

    @Override
    int getMySize() {
      return 1 + Convert.toBytes(aliasName).length;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      byte[] aliasBytes = Convert.toBytes(aliasName);
      buffer.put((byte)aliasBytes.length);
      buffer.put(aliasBytes);
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(ALIAS_RESPONSE, aliasName);
    }

    public String getAliasName(){
      return aliasName;
    }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.AliasBuyAttachment.newBuilder()
              .setVersion(getVersion())
              .setName(aliasName)
              .build());
    }
  }

  final class MessagingAccountInfo extends AbstractAttachment {

    private final String name;
    private final String description;

    MessagingAccountInfo(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
      super(buffer, transactionVersion);
      this.name = Convert.readString(buffer, buffer.get(), Constants.MAX_ACCOUNT_NAME_LENGTH);
      this.description = Convert.readString(buffer, buffer.getShort(), Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH);
    }

    MessagingAccountInfo(JsonObject attachmentData) {
      super(attachmentData);
      this.name = Convert.nullToEmpty(JSON.getAsString(attachmentData.get(NAME_PARAMETER)));
      this.description = Convert.nullToEmpty(JSON.getAsString(attachmentData.get(DESCRIPTION_PARAMETER)));
    }

    public MessagingAccountInfo(String name, String description, int blockchainHeight) {
      super(blockchainHeight);
      this.name = name;
      this.description = description;
    }

    MessagingAccountInfo(BrsApi.AccountInfoAttachment attachment) {
      super((byte) attachment.getVersion());
      this.name = attachment.getName();
      this.description = attachment.getDescription();
    }

    @Override
    String getAppendixName() {
      return "AccountInfo";
    }

    @Override
    int getMySize() {
      return 1 + Convert.toBytes(name).length + 2 + Convert.toBytes(description).length;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      byte[] putName = Convert.toBytes(this.name);
      byte[] putDescription = Convert.toBytes(this.description);
      buffer.put((byte)putName.length);
      buffer.put(putName);
      buffer.putShort((short) putDescription.length);
      buffer.put(putDescription);
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(NAME_RESPONSE, name);
      attachment.addProperty(DESCRIPTION_RESPONSE, description);
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.Messaging.ACCOUNT_INFO;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.AccountInfoAttachment.newBuilder()
              .setVersion(getVersion())
              .setName(name)
              .setDescription(description)
              .build());
    }
  }

  class ColoredCoinsAssetIssuance extends AbstractAttachment {

    private final String name;
    private final String description;
    private final long quantityQNT;
    private final byte decimals;

    ColoredCoinsAssetIssuance(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
      super(buffer, transactionVersion);
      this.name = Convert.readString(buffer, buffer.get(), Constants.MAX_ASSET_NAME_LENGTH);
      this.description = Convert.readString(buffer, buffer.getShort(), Constants.MAX_ASSET_DESCRIPTION_LENGTH);
      this.quantityQNT = buffer.getLong();
      this.decimals = buffer.get();
    }

    ColoredCoinsAssetIssuance(JsonObject attachmentData) {
      super(attachmentData);
      this.name = JSON.getAsString(attachmentData.get(NAME_PARAMETER));
      this.description = Convert.nullToEmpty(JSON.getAsString(attachmentData.get(DESCRIPTION_PARAMETER)));
      this.quantityQNT = JSON.getAsLong(attachmentData.get(QUANTITY_QNT_PARAMETER));
      this.decimals = JSON.getAsByte(attachmentData.get(DECIMALS_PARAMETER));
    }

    public ColoredCoinsAssetIssuance(String name, String description, long quantityQNT, byte decimals, int blockchainHeight) {
      super(blockchainHeight);
      this.name = name;
      this.description = Convert.nullToEmpty(description);
      this.quantityQNT = quantityQNT;
      this.decimals = decimals;
    }

    ColoredCoinsAssetIssuance(BrsApi.AssetIssuanceAttachment attachment) {
        super((byte) attachment.getVersion());
        this.name = attachment.getName();
        this.description = attachment.getDescription();
        this.quantityQNT = attachment.getQuantity();
        this.decimals = (byte) attachment.getDecimals();
    }

    @Override
    String getAppendixName() {
      return "AssetIssuance";
    }

    @Override
    int getMySize() {
      return 1 + Convert.toBytes(name).length + 2 + Convert.toBytes(description).length + 8 + 1;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      byte[] name = Convert.toBytes(this.name);
      byte[] description = Convert.toBytes(this.description);
      buffer.put((byte)name.length);
      buffer.put(name);
      buffer.putShort((short) description.length);
      buffer.put(description);
      buffer.putLong(quantityQNT);
      buffer.put(decimals);
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(NAME_RESPONSE, name);
      attachment.addProperty(DESCRIPTION_RESPONSE, description);
      attachment.addProperty(QUANTITY_QNT_RESPONSE, quantityQNT);
      attachment.addProperty(DECIMALS_RESPONSE, decimals);
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.ColoredCoins.ASSET_ISSUANCE;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    public long getQuantityQNT() {
      return quantityQNT;
    }

    public byte getDecimals() {
      return decimals;
    }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.AssetIssuanceAttachment.newBuilder()
              .setVersion(getVersion())
              .setName(name)
              .setDescription(description)
              .setQuantity(quantityQNT)
              .setDecimals(decimals)
              .build());
    }
  }

  final class ColoredCoinsAssetTransfer extends AbstractAttachment {

    private final long assetId;
    private final long quantityQNT;
    private final String comment;

    ColoredCoinsAssetTransfer(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
      super(buffer, transactionVersion);
      this.assetId = buffer.getLong();
      this.quantityQNT = buffer.getLong();
      this.comment = getVersion() == 0 ? Convert.readString(buffer, buffer.getShort(), Constants.MAX_ASSET_TRANSFER_COMMENT_LENGTH) : null;
    }

    ColoredCoinsAssetTransfer(JsonObject attachmentData) {
      super(attachmentData);
      this.assetId = Convert.parseUnsignedLong(JSON.getAsString(attachmentData.get(ASSET_PARAMETER)));
      this.quantityQNT = JSON.getAsLong(attachmentData.get(QUANTITY_QNT_PARAMETER));
      this.comment = getVersion() == 0 ? Convert.nullToEmpty(JSON.getAsString(attachmentData.get(COMMENT_PARAMETER))) : null;
    }

    public ColoredCoinsAssetTransfer(long assetId, long quantityQNT, int blockchainHeight) {
      super(blockchainHeight);
      this.assetId = assetId;
      this.quantityQNT = quantityQNT;
      this.comment = null;
    }

    ColoredCoinsAssetTransfer(BrsApi.AssetTransferAttachment attachment) {
        super((byte) attachment.getVersion());
        this.assetId = attachment.getAsset();
        this.quantityQNT = attachment.getQuantity();
        this.comment = attachment.getComment();
    }

    @Override
    String getAppendixName() {
      return "AssetTransfer";
    }

    @Override
    int getMySize() {
      return 8 + 8 + (getVersion() == 0 ? (2 + Convert.toBytes(comment).length) : 0);
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.putLong(assetId);
      buffer.putLong(quantityQNT);
      if (getVersion() == 0 && comment != null) {
        byte[] commentBytes = Convert.toBytes(this.comment);
        buffer.putShort((short) commentBytes.length);
        buffer.put(commentBytes);
      }
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(ASSET_RESPONSE, Convert.toUnsignedLong(assetId));
      attachment.addProperty(QUANTITY_QNT_RESPONSE, quantityQNT);
      if (getVersion() == 0) {
        attachment.addProperty(COMMENT_RESPONSE, comment);
      }
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.ColoredCoins.ASSET_TRANSFER;
    }

    public long getAssetId() {
      return assetId;
    }

    public long getQuantityQNT() {
      return quantityQNT;
    }

    public String getComment() {
      return comment;
    }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.AssetTransferAttachment.newBuilder()
              .setVersion(getVersion())
              .setAsset(assetId)
              .setQuantity(quantityQNT)
              .setComment(comment)
              .build());
    }
  }

  abstract class ColoredCoinsOrderPlacement extends AbstractAttachment {

    private final long assetId;
    private final long quantityQNT;
    private final long priceNQT;

    private ColoredCoinsOrderPlacement(ByteBuffer buffer, byte transactionVersion) {
      super(buffer, transactionVersion);
      this.assetId = buffer.getLong();
      this.quantityQNT = buffer.getLong();
      this.priceNQT = buffer.getLong();
    }

    private ColoredCoinsOrderPlacement(JsonObject attachmentData) {
      super(attachmentData);
      this.assetId = Convert.parseUnsignedLong(JSON.getAsString(attachmentData.get(ASSET_PARAMETER)));
      this.quantityQNT = JSON.getAsLong(attachmentData.get(QUANTITY_QNT_PARAMETER));
      this.priceNQT = JSON.getAsLong(attachmentData.get(PRICE_NQT_PARAMETER));
    }

    private ColoredCoinsOrderPlacement(long assetId, long quantityQNT, long priceNQT, int blockchainHeight) {
      super(blockchainHeight);
      this.assetId = assetId;
      this.quantityQNT = quantityQNT;
      this.priceNQT = priceNQT;
    }

    ColoredCoinsOrderPlacement(BrsApi.AssetOrderPlacementAttachment attachment) {
      super((byte) attachment.getVersion());
      this.assetId = attachment.getAsset();
      this.quantityQNT = attachment.getQuantity();
      this.priceNQT = attachment.getPrice();
    }

    @Override
    int getMySize() {
      return 8 + 8 + 8;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.putLong(assetId);
      buffer.putLong(quantityQNT);
      buffer.putLong(priceNQT);
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(ASSET_RESPONSE, Convert.toUnsignedLong(assetId));
      attachment.addProperty(QUANTITY_QNT_RESPONSE, quantityQNT);
      attachment.addProperty(PRICE_NQT_RESPONSE, priceNQT);
    }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.AssetOrderPlacementAttachment.newBuilder()
              .setVersion(getVersion())
              .setAsset(assetId)
              .setQuantity(quantityQNT)
              .setPrice(priceNQT)
              .setType(getType())
              .build());
    }

    public long getAssetId() {
      return assetId;
    }

    public long getQuantityQNT() {
      return quantityQNT;
    }

    public long getPriceNQT() {
      return priceNQT;
    }

    protected abstract BrsApi.OrderType getType();
  }

  final class ColoredCoinsAskOrderPlacement extends ColoredCoinsOrderPlacement {

    ColoredCoinsAskOrderPlacement(ByteBuffer buffer, byte transactionVersion) {
      super(buffer, transactionVersion);
    }

    ColoredCoinsAskOrderPlacement(JsonObject attachmentData) {
      super(attachmentData);
    }

    public ColoredCoinsAskOrderPlacement(long assetId, long quantityQNT, long priceNQT, int blockchainHeight) {
      super(assetId, quantityQNT, priceNQT, blockchainHeight);
    }

    ColoredCoinsAskOrderPlacement(BrsApi.AssetOrderPlacementAttachment attachment) {
      super(attachment);
      if (attachment.getType() != getType()) throw new IllegalArgumentException("Type does not match");
    }

    @Override
    protected BrsApi.OrderType getType() {
      return BrsApi.OrderType.ASK;
    }

    @Override
    String getAppendixName() {
      return "AskOrderPlacement";
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.ColoredCoins.ASK_ORDER_PLACEMENT;
    }

  }

  final class ColoredCoinsBidOrderPlacement extends ColoredCoinsOrderPlacement {

    ColoredCoinsBidOrderPlacement(ByteBuffer buffer, byte transactionVersion) {
      super(buffer, transactionVersion);
    }

    ColoredCoinsBidOrderPlacement(JsonObject attachmentData) {
      super(attachmentData);
    }

    public ColoredCoinsBidOrderPlacement(long assetId, long quantityQNT, long priceNQT, int blockchainHeight) {
      super(assetId, quantityQNT, priceNQT, blockchainHeight);
    }

    ColoredCoinsBidOrderPlacement(BrsApi.AssetOrderPlacementAttachment attachment) {
      super(attachment);
      if (attachment.getType() != getType()) throw new IllegalArgumentException("Type does not match");
    }

    @Override
    protected BrsApi.OrderType getType() {
      return BrsApi.OrderType.BID;
    }

    @Override
    String getAppendixName() {
      return "BidOrderPlacement";
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.ColoredCoins.BID_ORDER_PLACEMENT;
    }

  }

  abstract class ColoredCoinsOrderCancellation extends AbstractAttachment {

    private final long orderId;

    private ColoredCoinsOrderCancellation(ByteBuffer buffer, byte transactionVersion) {
      super(buffer, transactionVersion);
      this.orderId = buffer.getLong();
    }

    private ColoredCoinsOrderCancellation(JsonObject attachmentData) {
      super(attachmentData);
      this.orderId = Convert.parseUnsignedLong(JSON.getAsString(attachmentData.get(ORDER_PARAMETER)));
    }

    private ColoredCoinsOrderCancellation(long orderId, int blockchainHeight) {
      super(blockchainHeight);
      this.orderId = orderId;
    }

    ColoredCoinsOrderCancellation(BrsApi.AssetOrderCancellationAttachment attachment) {
      super((byte) attachment.getVersion());
      this.orderId = attachment.getOrder();
    }

    @Override
    int getMySize() {
      return 8;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.putLong(orderId);
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(ORDER_RESPONSE, Convert.toUnsignedLong(orderId));
    }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.AssetOrderCancellationAttachment.newBuilder()
              .setVersion(getVersion())
              .setOrder(orderId)
              .setType(getType())
              .build());
    }

    public long getOrderId() {
      return orderId;
    }

    protected abstract BrsApi.OrderType getType();
  }

  final class ColoredCoinsAskOrderCancellation extends ColoredCoinsOrderCancellation {

    ColoredCoinsAskOrderCancellation(ByteBuffer buffer, byte transactionVersion) {
      super(buffer, transactionVersion);
    }

    ColoredCoinsAskOrderCancellation(JsonObject attachmentData) {
      super(attachmentData);
    }

    public ColoredCoinsAskOrderCancellation(long orderId, int blockchainHeight) {
      super(orderId, blockchainHeight);
    }

    ColoredCoinsAskOrderCancellation(BrsApi.AssetOrderCancellationAttachment attachment) {
      super(attachment);
      if (attachment.getType() != getType()) throw new IllegalArgumentException("Type does not match");
    }

    @Override
    protected BrsApi.OrderType getType() {
      return BrsApi.OrderType.ASK;
    }

    @Override
    String getAppendixName() {
      return "AskOrderCancellation";
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.ColoredCoins.ASK_ORDER_CANCELLATION;
    }

  }

  final class ColoredCoinsBidOrderCancellation extends ColoredCoinsOrderCancellation {

    ColoredCoinsBidOrderCancellation(ByteBuffer buffer, byte transactionVersion) {
      super(buffer, transactionVersion);
    }

    ColoredCoinsBidOrderCancellation(JsonObject attachmentData) {
      super(attachmentData);
    }

    public ColoredCoinsBidOrderCancellation(long orderId, int blockchainHeight) {
      super(orderId, blockchainHeight);
    }

    ColoredCoinsBidOrderCancellation(BrsApi.AssetOrderCancellationAttachment attachment) {
      super(attachment);
      if (attachment.getType() != getType()) throw new IllegalArgumentException("Type does not match");
    }

    @Override
    protected BrsApi.OrderType getType() {
      return BrsApi.OrderType.BID;
    }

    @Override
    String getAppendixName() {
      return "BidOrderCancellation";
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.ColoredCoins.BID_ORDER_CANCELLATION;
    }

  }

  final class DigitalGoodsListing extends AbstractAttachment {

    private final String name;
    private final String description;
    private final String tags;
    private final int quantity;
    private final long priceNQT;

    DigitalGoodsListing(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
      super(buffer, transactionVersion);
      this.name = Convert.readString(buffer, buffer.getShort(), Constants.MAX_DGS_LISTING_NAME_LENGTH);
      this.description = Convert.readString(buffer, buffer.getShort(), Constants.MAX_DGS_LISTING_DESCRIPTION_LENGTH);
      this.tags = Convert.readString(buffer, buffer.getShort(), Constants.MAX_DGS_LISTING_TAGS_LENGTH);
      this.quantity = buffer.getInt();
      this.priceNQT = buffer.getLong();
    }

    DigitalGoodsListing(JsonObject attachmentData) {
      super(attachmentData);
      this.name = JSON.getAsString(attachmentData.get(NAME_RESPONSE));
      this.description = JSON.getAsString(attachmentData.get(DESCRIPTION_RESPONSE));
      this.tags = JSON.getAsString(attachmentData.get(TAGS_RESPONSE));
      this.quantity = JSON.getAsInt(attachmentData.get(QUANTITY_RESPONSE));
      this.priceNQT = JSON.getAsLong(attachmentData.get(PRICE_NQT_PARAMETER));
    }

    public DigitalGoodsListing(String name, String description, String tags, int quantity, long priceNQT, int blockchainHeight) {
      super(blockchainHeight);
      this.name = name;
      this.description = description;
      this.tags = tags;
      this.quantity = quantity;
      this.priceNQT = priceNQT;
    }

    DigitalGoodsListing(BrsApi.DigitalGoodsListingAttachment attachment) {
      super((byte) attachment.getVersion());
      this.name = attachment.getName();
      this.description = attachment.getDescription();
      this.tags = attachment.getTags();
      this.quantity = attachment.getQuantity();
      this.priceNQT = attachment.getPrice();
    }

    @Override
    String getAppendixName() {
      return "DigitalGoodsListing";
    }

    @Override
    int getMySize() {
      return 2 + Convert.toBytes(name).length + 2 + Convert.toBytes(description).length + 2
          + Convert.toBytes(tags).length + 4 + 8;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      byte[] nameBytes = Convert.toBytes(name);
      buffer.putShort((short) nameBytes.length);
      buffer.put(nameBytes);
      byte[] descriptionBytes = Convert.toBytes(description);
      buffer.putShort((short) descriptionBytes.length);
      buffer.put(descriptionBytes);
      byte[] tagsBytes = Convert.toBytes(tags);
      buffer.putShort((short) tagsBytes.length);
      buffer.put(tagsBytes);
      buffer.putInt(quantity);
      buffer.putLong(priceNQT);
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(NAME_RESPONSE, name);
      attachment.addProperty(DESCRIPTION_RESPONSE, description);
      attachment.addProperty(TAGS_RESPONSE, tags);
      attachment.addProperty(QUANTITY_RESPONSE, quantity);
      attachment.addProperty(PRICE_NQT_RESPONSE, priceNQT);
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.DigitalGoods.LISTING;
    }

    public String getName() { return name; }

    public String getDescription() { return description; }

    public String getTags() { return tags; }

    public int getQuantity() { return quantity; }

    public long getPriceNQT() { return priceNQT; }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.DigitalGoodsListingAttachment.newBuilder()
              .setVersion(getVersion())
              .setName(name)
              .setDescription(description)
              .setTags(tags)
              .setQuantity(quantity)
              .setPrice(priceNQT)
              .build());
    }
  }

  final class DigitalGoodsDelisting extends AbstractAttachment {

    private final long goodsId;

    DigitalGoodsDelisting(ByteBuffer buffer, byte transactionVersion) {
      super(buffer, transactionVersion);
      this.goodsId = buffer.getLong();
    }

    DigitalGoodsDelisting(JsonObject attachmentData) {
      super(attachmentData);
      this.goodsId = Convert.parseUnsignedLong(JSON.getAsString(attachmentData.get(GOODS_PARAMETER)));
    }

    public DigitalGoodsDelisting(long goodsId, int blockchainHeight) {
      super(blockchainHeight);
      this.goodsId = goodsId;
    }

    DigitalGoodsDelisting(BrsApi.DigitalGoodsDelistingAttachment attachment) {
      super((byte) attachment.getVersion());
      this.goodsId = attachment.getGoods();
    }

    @Override
    String getAppendixName() {
      return "DigitalGoodsDelisting";
    }

    @Override
    int getMySize() {
      return 8;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.putLong(goodsId);
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(GOODS_RESPONSE, Convert.toUnsignedLong(goodsId));
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.DigitalGoods.DELISTING;
    }

    public long getGoodsId() { return goodsId; }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.DigitalGoodsDelistingAttachment.newBuilder()
              .setVersion(getVersion())
              .setGoods(goodsId)
              .build());
    }
  }

  final class DigitalGoodsPriceChange extends AbstractAttachment {

    private final long goodsId;
    private final long priceNQT;

    DigitalGoodsPriceChange(ByteBuffer buffer, byte transactionVersion) {
      super(buffer, transactionVersion);
      this.goodsId = buffer.getLong();
      this.priceNQT = buffer.getLong();
    }

    DigitalGoodsPriceChange(JsonObject attachmentData) {
      super(attachmentData);
      this.goodsId = Convert.parseUnsignedLong(JSON.getAsString(attachmentData.get(GOODS_PARAMETER)));
      this.priceNQT = JSON.getAsLong(attachmentData.get(PRICE_NQT_PARAMETER));
    }

    public DigitalGoodsPriceChange(long goodsId, long priceNQT, int blockchainHeight) {
      super(blockchainHeight);
      this.goodsId = goodsId;
      this.priceNQT = priceNQT;
    }

    DigitalGoodsPriceChange(BrsApi.DigitalGoodsPriceChangeAttachment attachment) {
      super((byte) attachment.getVersion());
      this.goodsId = attachment.getGoods();
      this.priceNQT = attachment.getPrice();
    }

    @Override
    String getAppendixName() {
      return "DigitalGoodsPriceChange";
    }

    @Override
    int getMySize() {
      return 8 + 8;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.putLong(goodsId);
      buffer.putLong(priceNQT);
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(GOODS_RESPONSE, Convert.toUnsignedLong(goodsId));
      attachment.addProperty(PRICE_NQT_RESPONSE, priceNQT);
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.DigitalGoods.PRICE_CHANGE;
    }

    public long getGoodsId() { return goodsId; }

    public long getPriceNQT() { return priceNQT; }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.DigitalGoodsPriceChangeAttachment.newBuilder()
              .setVersion(getVersion())
              .setGoods(goodsId)
              .setPrice(priceNQT)
              .build());
    }
  }

  final class DigitalGoodsQuantityChange extends AbstractAttachment {

    private final long goodsId;
    private final int deltaQuantity;

    DigitalGoodsQuantityChange(ByteBuffer buffer, byte transactionVersion) {
      super(buffer, transactionVersion);
      this.goodsId = buffer.getLong();
      this.deltaQuantity = buffer.getInt();
    }

    DigitalGoodsQuantityChange(JsonObject attachmentData) {
      super(attachmentData);
      this.goodsId = Convert.parseUnsignedLong(JSON.getAsString(attachmentData.get(GOODS_PARAMETER)));
      this.deltaQuantity = JSON.getAsInt(attachmentData.get(DELTA_QUANTITY_PARAMETER));
    }

    public DigitalGoodsQuantityChange(long goodsId, int deltaQuantity, int blockchainHeight) {
      super(blockchainHeight);
      this.goodsId = goodsId;
      this.deltaQuantity = deltaQuantity;
    }

    DigitalGoodsQuantityChange(BrsApi.DigitalGoodsQuantityChangeAttachment attachment) {
      super((byte) attachment.getVersion());
      this.goodsId = attachment.getGoods();
      this.deltaQuantity = attachment.getDeltaQuantity();
    }

    @Override
    String getAppendixName() {
      return "DigitalGoodsQuantityChange";
    }

    @Override
    int getMySize() {
      return 8 + 4;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.putLong(goodsId);
      buffer.putInt(deltaQuantity);
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(GOODS_RESPONSE, Convert.toUnsignedLong(goodsId));
      attachment.addProperty(DELTA_QUANTITY_RESPONSE, deltaQuantity);
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.DigitalGoods.QUANTITY_CHANGE;
    }

    public long getGoodsId() { return goodsId; }

    public int getDeltaQuantity() { return deltaQuantity; }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.DigitalGoodsQuantityChangeAttachment.newBuilder()
              .setVersion(getVersion())
              .setGoods(goodsId)
              .setDeltaQuantity(deltaQuantity)
              .build());
    }
  }

  final class DigitalGoodsPurchase extends AbstractAttachment {

    private final long goodsId;
    private final int quantity;
    private final long priceNQT;
    private final int deliveryDeadlineTimestamp;

    DigitalGoodsPurchase(ByteBuffer buffer, byte transactionVersion) {
      super(buffer, transactionVersion);
      this.goodsId = buffer.getLong();
      this.quantity = buffer.getInt();
      this.priceNQT = buffer.getLong();
      this.deliveryDeadlineTimestamp = buffer.getInt();
    }

    DigitalGoodsPurchase(JsonObject attachmentData) {
      super(attachmentData);
      this.goodsId = Convert.parseUnsignedLong(JSON.getAsString(attachmentData.get(GOODS_PARAMETER)));
      this.quantity = JSON.getAsInt(attachmentData.get(QUANTITY_PARAMETER));
      this.priceNQT = JSON.getAsLong(attachmentData.get(PRICE_NQT_PARAMETER));
      this.deliveryDeadlineTimestamp = JSON.getAsInt(attachmentData.get(DELIVERY_DEADLINE_TIMESTAMP_PARAMETER));
    }

    public DigitalGoodsPurchase(long goodsId, int quantity, long priceNQT, int deliveryDeadlineTimestamp, int blockchainHeight) {
      super(blockchainHeight);
      this.goodsId = goodsId;
      this.quantity = quantity;
      this.priceNQT = priceNQT;
      this.deliveryDeadlineTimestamp = deliveryDeadlineTimestamp;
    }

    DigitalGoodsPurchase(BrsApi.DigitalGoodsPurchaseAttachment attachment) {
      super((byte) attachment.getVersion());
      this.goodsId = attachment.getGoods();
      this.quantity = attachment.getQuantity();
      this.priceNQT = attachment.getPrice();
      this.deliveryDeadlineTimestamp = attachment.getDeliveryDeadlineTimestmap();
    }

    @Override
    String getAppendixName() {
      return "DigitalGoodsPurchase";
    }

    @Override
    int getMySize() {
      return 8 + 4 + 8 + 4;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.putLong(goodsId);
      buffer.putInt(quantity);
      buffer.putLong(priceNQT);
      buffer.putInt(deliveryDeadlineTimestamp);
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(GOODS_RESPONSE, Convert.toUnsignedLong(goodsId));
      attachment.addProperty(QUANTITY_RESPONSE, quantity);
      attachment.addProperty(PRICE_NQT_RESPONSE, priceNQT);
      attachment.addProperty(DELIVERY_DEADLINE_TIMESTAMP_RESPONSE, deliveryDeadlineTimestamp);
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.DigitalGoods.PURCHASE;
    }

    public long getGoodsId() { return goodsId; }

    public int getQuantity() { return quantity; }

    public long getPriceNQT() { return priceNQT; }

    public int getDeliveryDeadlineTimestamp() { return deliveryDeadlineTimestamp; }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.DigitalGoodsPurchaseAttachment.newBuilder()
              .setVersion(getVersion())
              .setGoods(goodsId)
              .setQuantity(quantity)
              .setPrice(priceNQT)
              .setDeliveryDeadlineTimestmap(deliveryDeadlineTimestamp)
              .build());
    }
  }

  final class DigitalGoodsDelivery extends AbstractAttachment {

    private final long purchaseId;
    private final EncryptedData goods;
    private final long discountNQT;
    private final boolean goodsIsText;

    DigitalGoodsDelivery(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
      super(buffer, transactionVersion);
      this.purchaseId = buffer.getLong();
      int length = buffer.getInt();
      goodsIsText = length < 0;
      if (length < 0) {
        length &= Integer.MAX_VALUE;
      }
      this.goods = EncryptedData.readEncryptedData(buffer, length, Constants.MAX_DGS_GOODS_LENGTH);
      this.discountNQT = buffer.getLong();
    }

    DigitalGoodsDelivery(JsonObject attachmentData) {
      super(attachmentData);
      this.purchaseId = Convert.parseUnsignedLong(JSON.getAsString(attachmentData.get(PURCHASE_PARAMETER)));
      this.goods = new EncryptedData(Convert.parseHexString(JSON.getAsString(attachmentData.get(GOODS_DATA_PARAMETER))),
                                     Convert.parseHexString(JSON.getAsString(attachmentData.get(GOODS_NONCE_PARAMETER))));
      this.discountNQT = JSON.getAsLong(attachmentData.get(DISCOUNT_NQT_PARAMETER));
      this.goodsIsText = Boolean.TRUE.equals(JSON.getAsBoolean(attachmentData.get(GOODS_IS_TEXT_PARAMETER)));
    }

    public DigitalGoodsDelivery(long purchaseId, EncryptedData goods, boolean goodsIsText, long discountNQT, int blockchainHeight) {
      super(blockchainHeight);
      this.purchaseId = purchaseId;
      this.goods = goods;
      this.discountNQT = discountNQT;
      this.goodsIsText = goodsIsText;
    }

    DigitalGoodsDelivery(BrsApi.DigitalGoodsDeliveryAttachment attachment) {
      super((byte) attachment.getVersion());
      this.purchaseId = attachment.getPurchase();
      this.goods = ProtoBuilder.parseEncryptedData(attachment.getGoods());
      this.goodsIsText = attachment.getIsText();
      this.discountNQT = attachment.getDiscount();
    }

    @Override
    String getAppendixName() {
      return "DigitalGoodsDelivery";
    }

    @Override
    int getMySize() {
      return 8 + 4 + goods.getSize() + 8;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.putLong(purchaseId);
      buffer.putInt(goodsIsText ? goods.getData().length | Integer.MIN_VALUE : goods.getData().length);
      buffer.put(goods.getData());
      buffer.put(goods.getNonce());
      buffer.putLong(discountNQT);
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(PURCHASE_RESPONSE, Convert.toUnsignedLong(purchaseId));
      attachment.addProperty(GOODS_DATA_RESPONSE, Convert.toHexString(goods.getData()));
      attachment.addProperty(GOODS_NONCE_RESPONSE, Convert.toHexString(goods.getNonce()));
      attachment.addProperty(DISCOUNT_NQT_RESPONSE, discountNQT);
      attachment.addProperty(GOODS_IS_TEXT_RESPONSE, goodsIsText);
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.DigitalGoods.DELIVERY;
    }

    public long getPurchaseId() { return purchaseId; }

    public EncryptedData getGoods() { return goods; }

    public long getDiscountNQT() { return discountNQT; }

    public boolean goodsIsText() {
      return goodsIsText;
    }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.DigitalGoodsDeliveryAttachment.newBuilder()
              .setVersion(getVersion())
              .setPurchase(purchaseId)
              .setDiscount(discountNQT)
              .setGoods(ProtoBuilder.buildEncryptedData(goods))
              .setIsText(goodsIsText)
              .build());
    }
  }

  final class DigitalGoodsFeedback extends AbstractAttachment {

    private final long purchaseId;

    DigitalGoodsFeedback(ByteBuffer buffer, byte transactionVersion) {
      super(buffer, transactionVersion);
      this.purchaseId = buffer.getLong();
    }

    DigitalGoodsFeedback(JsonObject attachmentData) {
      super(attachmentData);
      this.purchaseId = Convert.parseUnsignedLong(JSON.getAsString(attachmentData.get(PURCHASE_PARAMETER)));
    }

    public DigitalGoodsFeedback(long purchaseId, int blockchainHeight) {
      super(blockchainHeight);
      this.purchaseId = purchaseId;
    }

    DigitalGoodsFeedback(BrsApi.DigitalGoodsFeedbackAttachment attachment) {
      super((byte) attachment.getVersion());
      this.purchaseId = attachment.getPurchase();
    }

    @Override
    String getAppendixName() {
      return "DigitalGoodsFeedback";
    }

    @Override
    int getMySize() {
      return 8;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.putLong(purchaseId);
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(PURCHASE_RESPONSE, Convert.toUnsignedLong(purchaseId));
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.DigitalGoods.FEEDBACK;
    }

    public long getPurchaseId() { return purchaseId; }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.DigitalGoodsFeedbackAttachment.newBuilder()
              .setVersion(getVersion())
              .setPurchase(purchaseId)
              .build());
    }
  }

  final class DigitalGoodsRefund extends AbstractAttachment {

    private final long purchaseId;
    private final long refundNQT;

    DigitalGoodsRefund(ByteBuffer buffer, byte transactionVersion) {
      super(buffer, transactionVersion);
      this.purchaseId = buffer.getLong();
      this.refundNQT = buffer.getLong();
    }

    DigitalGoodsRefund(JsonObject attachmentData) {
      super(attachmentData);
      this.purchaseId = Convert.parseUnsignedLong(JSON.getAsString(attachmentData.get(PURCHASE_PARAMETER)));
      this.refundNQT = JSON.getAsLong(attachmentData.get(REFUND_NQT_PARAMETER));
    }

    public DigitalGoodsRefund(long purchaseId, long refundNQT, int blockchainHeight) {
      super(blockchainHeight);
      this.purchaseId = purchaseId;
      this.refundNQT = refundNQT;
    }

    DigitalGoodsRefund(BrsApi.DigitalGoodsRefundAttachment attachment) {
      super((byte) attachment.getVersion());
      this.purchaseId = attachment.getPurchase();
      this.refundNQT = attachment.getRefund();
    }

    @Override
    String getAppendixName() {
      return "DigitalGoodsRefund";
    }

    @Override
    int getMySize() {
      return 8 + 8;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.putLong(purchaseId);
      buffer.putLong(refundNQT);
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(PURCHASE_RESPONSE, Convert.toUnsignedLong(purchaseId));
      attachment.addProperty(REFUND_NQT_RESPONSE, refundNQT);
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.DigitalGoods.REFUND;
    }

    public long getPurchaseId() { return purchaseId; }

    public long getRefundNQT() { return refundNQT; }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.DigitalGoodsRefundAttachment.newBuilder()
              .setVersion(getVersion())
              .setPurchase(purchaseId)
              .setRefund(refundNQT)
              .build());
    }
  }

  final class AccountControlEffectiveBalanceLeasing extends AbstractAttachment {

    private final short period;

    AccountControlEffectiveBalanceLeasing(ByteBuffer buffer, byte transactionVersion) {
      super(buffer, transactionVersion);
      this.period = buffer.getShort();
    }

    AccountControlEffectiveBalanceLeasing(JsonObject attachmentData) {
      super(attachmentData);
      this.period = JSON.getAsShort(attachmentData.get(PERIOD_PARAMETER));
    }

    public AccountControlEffectiveBalanceLeasing(short period, int blockchainHeight) {
      super(blockchainHeight);
      this.period = period;
    }

    AccountControlEffectiveBalanceLeasing(BrsApi.EffectiveBalanceLeasingAttachment attachment) {
      super((byte) attachment.getVersion());
      this.period = (short) attachment.getPeriod();
    }

    @Override
    String getAppendixName() {
      return "EffectiveBalanceLeasing";
    }

    @Override
    int getMySize() {
      return 2;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.putShort(period);
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(PERIOD_RESPONSE, period);
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.AccountControl.EFFECTIVE_BALANCE_LEASING;
    }

    public short getPeriod() {
      return period;
    }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.EffectiveBalanceLeasingAttachment.newBuilder()
              .setVersion(getVersion())
              .setPeriod(period)
              .build());
    }
  }

  final class BurstMiningRewardRecipientAssignment extends AbstractAttachment {

    BurstMiningRewardRecipientAssignment(ByteBuffer buffer, byte transactionVersion) {
      super(buffer, transactionVersion);
    }

    BurstMiningRewardRecipientAssignment(JsonObject attachmentData) {
      super(attachmentData);
    }

    public BurstMiningRewardRecipientAssignment(int blockchainHeight) {
      super(blockchainHeight);
    }

    BurstMiningRewardRecipientAssignment(BrsApi.RewardRecipientAssignmentAttachment attachment) {
      super((byte) attachment.getVersion());
    }

    @Override
    String getAppendixName() {
      return "RewardRecipientAssignment";
    }

    @Override
    int getMySize() {
      return 0;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      // Reward recipient does not have additional data.
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      // Reward recipient does not have additional data.
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.BurstMining.REWARD_RECIPIENT_ASSIGNMENT;
    }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.RewardRecipientAssignmentAttachment.newBuilder()
              .setVersion(getVersion())
              .build());
    }
  }

  final class AdvancedPaymentEscrowCreation extends AbstractAttachment {

    private final Long amountNQT;
    private final byte requiredSigners;
    private final SortedSet<Long> signers = new TreeSet<>();
    private final int deadline;
    private final Escrow.DecisionType deadlineAction;

    AdvancedPaymentEscrowCreation(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
      super(buffer, transactionVersion);
      this.amountNQT = buffer.getLong();
      this.deadline = buffer.getInt();
      this.deadlineAction = Escrow.byteToDecision(buffer.get());
      this.requiredSigners = buffer.get();
      byte totalSigners = buffer.get();
      if(totalSigners > 10 || totalSigners <= 0) {
        throw new BurstException.NotValidException("Invalid number of signers listed on create escrow transaction");
      }
      for(int i = 0; i < totalSigners; i++) {
        if(!this.signers.add(buffer.getLong())) {
          throw new BurstException.NotValidException("Duplicate signer on escrow creation");
        }
      }
    }

    AdvancedPaymentEscrowCreation(JsonObject attachmentData) throws BurstException.NotValidException {
      super(attachmentData);
      this.amountNQT = Convert.parseUnsignedLong(JSON.getAsString(attachmentData.get(AMOUNT_NQT_PARAMETER)));
      this.deadline = JSON.getAsInt(attachmentData.get(DEADLINE_PARAMETER));
      this.deadlineAction = Escrow.stringToDecision(JSON.getAsString(attachmentData.get(DEADLINE_ACTION_PARAMETER)));
      this.requiredSigners = JSON.getAsByte(attachmentData.get(REQUIRED_SIGNERS_PARAMETER));
      int totalSigners = (JSON.getAsJsonArray(attachmentData.get(SIGNERS_PARAMETER))).size();
      if(totalSigners > 10 || totalSigners <= 0) {
        throw new BurstException.NotValidException("Invalid number of signers listed on create escrow transaction");
      }
      JsonArray signersJson = JSON.getAsJsonArray(attachmentData.get(SIGNERS_PARAMETER));
      for (JsonElement aSignersJson : signersJson) {
        this.signers.add(Convert.parseUnsignedLong(JSON.getAsString(aSignersJson)));
      }
      if(this.signers.size() != (JSON.getAsJsonArray(attachmentData.get(SIGNERS_PARAMETER))).size()) {
        throw new BurstException.NotValidException("Duplicate signer on escrow creation");
      }
    }

    public AdvancedPaymentEscrowCreation(Long amountNQT, int deadline, Escrow.DecisionType deadlineAction,
                                         int requiredSigners, Collection<Long> signers, int blockchainHeight) throws BurstException.NotValidException {
      super(blockchainHeight);
      this.amountNQT = amountNQT;
      this.deadline = deadline;
      this.deadlineAction = deadlineAction;
      this.requiredSigners = (byte)requiredSigners;
      if(signers.size() > 10 || signers.isEmpty()) {
        throw new BurstException.NotValidException("Invalid number of signers listed on create escrow transaction");
      }
      this.signers.addAll(signers);
      if(this.signers.size() != signers.size()) {
        throw new BurstException.NotValidException("Duplicate signer on escrow creation");
      }
    }

    AdvancedPaymentEscrowCreation(BrsApi.EscrowCreationAttachment attachment) throws BurstException.NotValidException {
      super((byte) attachment.getVersion());
      this.amountNQT = attachment.getAmount();
      this.requiredSigners = (byte) attachment.getRequiredSigners();
      this.deadline = attachment.getDeadline();
      this.deadlineAction = Escrow.protoBufToDecision(attachment.getDeadlineAction());
      this.signers.addAll(attachment.getSignersList());
      if(signers.size() > 10 || signers.isEmpty()) {
        throw new BurstException.NotValidException("Invalid number of signers listed on create escrow transaction");
      }
      if(this.signers.size() != attachment.getSignersList().size()) {
        throw new BurstException.NotValidException("Duplicate signer on escrow creation");
      }
    }

    @Override
    String getAppendixName() {
      return "EscrowCreation";
    }

    @Override
    int getMySize() {
      int size = 8 + 4 + 1 + 1 + 1;
      size += (signers.size() * 8);
      return size;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.putLong(this.amountNQT);
      buffer.putInt(this.deadline);
      buffer.put(Escrow.decisionToByte(this.deadlineAction));
      buffer.put(this.requiredSigners);
      byte totalSigners = (byte) this.signers.size();
      buffer.put(totalSigners);
      this.signers.forEach(buffer::putLong);
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(AMOUNT_NQT_RESPONSE, Convert.toUnsignedLong(this.amountNQT));
      attachment.addProperty(DEADLINE_RESPONSE, this.deadline);
      attachment.addProperty(DEADLINE_ACTION_RESPONSE, Escrow.decisionToString(this.deadlineAction));
      attachment.addProperty(REQUIRED_SIGNERS_RESPONSE, (int)this.requiredSigners);
      JsonArray ids = new JsonArray();
      for(Long signer : this.signers) {
        ids.add(Convert.toUnsignedLong(signer));
      }
      attachment.add(SIGNERS_RESPONSE, ids);
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.AdvancedPayment.ESCROW_CREATION;
    }

    public Long getAmountNQT() { return amountNQT; }

    public int getDeadline() { return deadline; }

    public Escrow.DecisionType getDeadlineAction() { return deadlineAction; }

    public int getRequiredSigners() { return (int)requiredSigners; }

    public Collection<Long> getSigners() { return Collections.unmodifiableCollection(signers); }

    public int getTotalSigners() { return signers.size(); }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.EscrowCreationAttachment.newBuilder()
              .setVersion(getVersion())
              .setAmount(amountNQT)
              .setRequiredSigners(requiredSigners)
              .addAllSigners(signers)
              .setDeadline(deadline)
              .setDeadlineAction(Escrow.decisionToProtobuf(deadlineAction))
              .build());
    }
  }

  final class AdvancedPaymentEscrowSign extends AbstractAttachment {

    private final Long escrowId;
    private final Escrow.DecisionType decision;

    AdvancedPaymentEscrowSign(ByteBuffer buffer, byte transactionVersion) {
      super(buffer, transactionVersion);
      this.escrowId = buffer.getLong();
      this.decision = Escrow.byteToDecision(buffer.get());
    }

    AdvancedPaymentEscrowSign(JsonObject attachmentData) {
      super(attachmentData);
      this.escrowId = Convert.parseUnsignedLong(JSON.getAsString(attachmentData.get(ESCROW_ID_PARAMETER)));
      this.decision = Escrow.stringToDecision(JSON.getAsString(attachmentData.get(DECISION_PARAMETER)));
    }

    public AdvancedPaymentEscrowSign(Long escrowId, Escrow.DecisionType decision, int blockchainHeight) {
      super(blockchainHeight);
      this.escrowId = escrowId;
      this.decision = decision;
    }

    AdvancedPaymentEscrowSign(BrsApi.EscrowSignAttachment attachment) {
      super((byte) attachment.getVersion());
      this.escrowId = attachment.getEscrow();
      this.decision = Escrow.protoBufToDecision(attachment.getDecision());
    }

    @Override
    String getAppendixName() {
      return "EscrowSign";
    }

    @Override
    int getMySize() {
      return 8 + 1;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.putLong(this.escrowId);
      buffer.put(Escrow.decisionToByte(this.decision));
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(ESCROW_ID_RESPONSE, Convert.toUnsignedLong(this.escrowId));
      attachment.addProperty(DECISION_RESPONSE, Escrow.decisionToString(this.decision));
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.AdvancedPayment.ESCROW_SIGN;
    }

    public Long getEscrowId() { return this.escrowId; }

    public Escrow.DecisionType getDecision() { return this.decision; }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.EscrowSignAttachment.newBuilder()
              .setVersion(getVersion())
              .setEscrow(escrowId)
              .setDecision(Escrow.decisionToProtobuf(decision))
              .build());
    }
  }

  final class AdvancedPaymentEscrowResult extends AbstractAttachment {

    private final Long escrowId;
    private final Escrow.DecisionType decision;

    AdvancedPaymentEscrowResult(ByteBuffer buffer, byte transactionVersion) {
      super(buffer, transactionVersion);
      this.escrowId = buffer.getLong();
      this.decision = Escrow.byteToDecision(buffer.get());
    }

    AdvancedPaymentEscrowResult(JsonObject attachmentData) {
      super(attachmentData);
      this.escrowId = Convert.parseUnsignedLong(JSON.getAsString(attachmentData.get(ESCROW_ID_PARAMETER)));
      this.decision = Escrow.stringToDecision(JSON.getAsString(attachmentData.get(DECISION_PARAMETER)));
    }

    public AdvancedPaymentEscrowResult(Long escrowId, Escrow.DecisionType decision, int blockchainHeight) {
      super(blockchainHeight);
      this.escrowId = escrowId;
      this.decision = decision;
    }

    AdvancedPaymentEscrowResult(BrsApi.EscrowResultAttachment attachment) {
      super((byte) attachment.getVersion());
      this.escrowId = attachment.getEscrow();
      this.decision = Escrow.protoBufToDecision(attachment.getDecision());
    }

    @Override
    String getAppendixName() {
      return "EscrowResult";
    }

    @Override
    int getMySize() {
      return 8 + 1;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.putLong(this.escrowId);
      buffer.put(Escrow.decisionToByte(this.decision));
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(ESCROW_ID_RESPONSE, Convert.toUnsignedLong(this.escrowId));
      attachment.addProperty(DECISION_RESPONSE, Escrow.decisionToString(this.decision));
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.AdvancedPayment.ESCROW_RESULT;
    }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.EscrowResultAttachment.newBuilder()
              .setVersion(getVersion())
              .setEscrow(2)
              .setDecision(Escrow.decisionToProtobuf(decision))
              .build());
    }
  }

  final class AdvancedPaymentSubscriptionSubscribe extends AbstractAttachment {

    private final Integer frequency;

    AdvancedPaymentSubscriptionSubscribe(ByteBuffer buffer, byte transactionVersion) {
      super(buffer, transactionVersion);
      this.frequency = buffer.getInt();
    }

    AdvancedPaymentSubscriptionSubscribe(JsonObject attachmentData) {
      super(attachmentData);
      this.frequency = JSON.getAsInt(attachmentData.get(FREQUENCY_PARAMETER));
    }

    public AdvancedPaymentSubscriptionSubscribe(int frequency, int blockchainHeight) {
      super(blockchainHeight);
      this.frequency = frequency;
    }

    AdvancedPaymentSubscriptionSubscribe(BrsApi.SubscriptionSubscribeAttachment attachment) {
      super((byte) attachment.getVersion());
      this.frequency = attachment.getFrequency();
    }

    @Override
    String getAppendixName() {
      return "SubscriptionSubscribe";
    }

    @Override
    int getMySize() {
      return 4;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.putInt(this.frequency);
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(FREQUENCY_RESPONSE, this.frequency);
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.AdvancedPayment.SUBSCRIPTION_SUBSCRIBE;
    }

    public Integer getFrequency() { return this.frequency; }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.SubscriptionSubscribeAttachment.newBuilder()
              .setVersion(getVersion())
              .setFrequency(frequency)
              .build());
    }
  }

  final class AdvancedPaymentSubscriptionCancel extends AbstractAttachment {

    private final Long subscriptionId;

    AdvancedPaymentSubscriptionCancel(ByteBuffer buffer, byte transactionVersion) {
      super(buffer, transactionVersion);
      this.subscriptionId = buffer.getLong();
    }

    AdvancedPaymentSubscriptionCancel(JsonObject attachmentData) {
      super(attachmentData);
      this.subscriptionId = Convert.parseUnsignedLong(JSON.getAsString(attachmentData.get(SUBSCRIPTION_ID_PARAMETER)));
    }

    public AdvancedPaymentSubscriptionCancel(Long subscriptionId, int blockchainHeight) {
      super(blockchainHeight);
      this.subscriptionId = subscriptionId;
    }

    AdvancedPaymentSubscriptionCancel(BrsApi.SubscriptionCancelAttachment attachment) {
      super((byte) attachment.getVersion());
      this.subscriptionId = attachment.getSubscription();
    }

    @Override
    String getAppendixName() {
      return "SubscriptionCancel";
    }

    @Override
    int getMySize() {
      return 8;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.putLong(subscriptionId);
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(SUBSCRIPTION_ID_RESPONSE, Convert.toUnsignedLong(this.subscriptionId));
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.AdvancedPayment.SUBSCRIPTION_CANCEL;
    }

    public Long getSubscriptionId() { return this.subscriptionId; }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.SubscriptionCancelAttachment.newBuilder()
              .setVersion(getVersion())
              .setSubscription(subscriptionId)
              .build());
    }
  }

  final class AdvancedPaymentSubscriptionPayment extends AbstractAttachment {

    private final Long subscriptionId;

    AdvancedPaymentSubscriptionPayment(ByteBuffer buffer, byte transactionVersion) {
      super(buffer, transactionVersion);
      this.subscriptionId = buffer.getLong();
    }

    AdvancedPaymentSubscriptionPayment(JsonObject attachmentData) {
      super(attachmentData);
      this.subscriptionId = Convert.parseUnsignedLong(JSON.getAsString(attachmentData.get(SUBSCRIPTION_ID_PARAMETER)));
    }

    public AdvancedPaymentSubscriptionPayment(Long subscriptionId, int blockchainHeight) {
      super(blockchainHeight);
      this.subscriptionId = subscriptionId;
    }

    AdvancedPaymentSubscriptionPayment(BrsApi.SubscriptionPaymentAttachment attachment) {
      super((byte) attachment.getVersion());
      this.subscriptionId = attachment.getSubscription();
    }

    @Override
    String getAppendixName() {
      return "SubscriptionPayment";
    }

    @Override
    int getMySize() {
      return 8;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.putLong(this.subscriptionId);
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(SUBSCRIPTION_ID_RESPONSE, Convert.toUnsignedLong(this.subscriptionId));
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.AdvancedPayment.SUBSCRIPTION_PAYMENT;
    }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.SubscriptionPaymentAttachment.newBuilder()
              .setVersion(getVersion())
              .setSubscription(subscriptionId)
              .build());
    }
  }

  final class AutomatedTransactionsCreation extends AbstractAttachment{

    private final String name;
    private final String description;
    private final byte[] creationBytes;

    AutomatedTransactionsCreation(ByteBuffer buffer,
                                  byte transactionVersion) throws BurstException.NotValidException {
      super(buffer, transactionVersion);

      this.name = Convert.readString( buffer , buffer.get() , Constants.MAX_AUTOMATED_TRANSACTION_NAME_LENGTH );
      this.description = Convert.readString( buffer , buffer.getShort() , Constants.MAX_AUTOMATED_TRANSACTION_DESCRIPTION_LENGTH );

      // rest of the parsing is at related; code comes from
      // public AtMachineState( byte[] atId, byte[] creator, byte[] creationBytes, int height ) {
      int startPosition = buffer.position();
      buffer.getShort();

      buffer.getShort(); //future: reserved for future needs

      int pageSize = ( int ) AtConstants.getInstance().pageSize( Burst.getBlockchain().getHeight() );
      short codePages = buffer.getShort();
      short dataPages = buffer.getShort();
      buffer.getShort();
      buffer.getShort();

      buffer.getLong();

      int codeLen;
      if ( codePages * pageSize < pageSize + 1 ) {
	      codeLen = buffer.get();
	      if ( codeLen < 0 )
	        codeLen += (Byte.MAX_VALUE + 1) * 2;
      }
      else if ( codePages * pageSize < Short.MAX_VALUE + 1 ) {
	    codeLen = buffer.getShort();
	    if( codeLen < 0 )
	      codeLen += (Short.MAX_VALUE + 1) * 2;
      }
      else {
	      codeLen = buffer.getInt();
      }
      byte[] code = new byte[ codeLen ];
      buffer.get( code, 0, codeLen );

      int dataLen;
      if ( dataPages * pageSize < 257 ) {
	      dataLen = buffer.get();
	      if ( dataLen < 0 )
	        dataLen += (Byte.MAX_VALUE + 1) * 2;
      }
      else if ( dataPages * pageSize < Short.MAX_VALUE + 1 ) {
	      dataLen = buffer.getShort();
	      if ( dataLen < 0 )
	        dataLen += (Short.MAX_VALUE + 1) * 2;
      }
      else {
	      dataLen = buffer.getInt();
      }
      byte[] data = new byte[ dataLen ];
      buffer.get( data, 0, dataLen );

      int endPosition = buffer.position();
      buffer.position(startPosition);
      byte[] dst = new byte[ endPosition - startPosition ];
      buffer.get( dst , 0 , endPosition - startPosition );
      this.creationBytes = dst;
    }

    AutomatedTransactionsCreation(JsonObject attachmentData) {
      super(attachmentData);

      this.name = JSON.getAsString(attachmentData.get(NAME_PARAMETER));
      this.description = JSON.getAsString(attachmentData.get(DESCRIPTION_PARAMETER));

      this.creationBytes = Convert.parseHexString(JSON.getAsString(attachmentData.get(CREATION_BYTES_PARAMETER)));

    }

    public AutomatedTransactionsCreation( String name, String description , byte[] creationBytes, int blockchainHeight) {
      super(blockchainHeight);
      this.name = name;
      this.description = description;
      this.creationBytes = creationBytes;
    }

    AutomatedTransactionsCreation(BrsApi.ATCreationAttachment attachment) {
      super((byte) attachment.getVersion());
      this.name = attachment.getName();
      this.description = attachment.getDescription();
      this.creationBytes = attachment.getCreationBytes().toByteArray();
    }

    @Override
    public TransactionType getTransactionType() {
      return TransactionType.AutomatedTransactions.AUTOMATED_TRANSACTION_CREATION;
    }

    @Override
    String getAppendixName() {
      return "AutomatedTransactionsCreation";
    }
    @Override
    int getMySize() {
      return 1 + Convert.toBytes( name ).length + 2 + Convert.toBytes( description ).length + creationBytes.length;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      byte[] nameBytes = Convert.toBytes( name );
      buffer.put( ( byte ) nameBytes.length );
      buffer.put( nameBytes );
      byte[] descriptionBytes = Convert.toBytes( description );
      buffer.putShort( ( short ) descriptionBytes.length );
      buffer.put( descriptionBytes );

      buffer.put( creationBytes );
    }

    @Override
    void putMyJSON(JsonObject attachment) {
      attachment.addProperty(NAME_RESPONSE, name);
      attachment.addProperty(DESCRIPTION_RESPONSE, description);
      attachment.addProperty(CREATION_BYTES_RESPONSE, Convert.toHexString( creationBytes ) );
    }

    public String getName() { return name; }

    public String getDescription() { return description; }

    public byte[] getCreationBytes() {
      return creationBytes;
    }


    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.ATCreationAttachment.newBuilder()
              .setVersion(getVersion())
              .setName(name)
              .setDescription(description)
              .setCreationBytes(ByteString.copyFrom(creationBytes))
              .build());
    }
  }

}
