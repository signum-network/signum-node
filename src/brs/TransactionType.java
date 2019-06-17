package brs;

import brs.Attachment.AbstractAttachment;
import brs.Attachment.AutomatedTransactionsCreation;
import brs.BurstException.NotValidException;
import brs.BurstException.ValidationException;
import brs.assetexchange.AssetExchange;
import brs.at.AT;
import brs.at.AtConstants;
import brs.at.AtController;
import brs.at.AtException;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.services.*;
import brs.transactionduplicates.TransactionDuplicationKey;
import brs.util.Convert;
import brs.util.JSON;
import brs.util.TextUtils;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.*;

import static brs.Constants.FEE_QUANT;
import static brs.Constants.ONE_BURST;

public abstract class TransactionType {

  private static final Logger logger = LoggerFactory.getLogger(TransactionType.class);

  private static final Map<Byte, Map<Byte, TransactionType>> TRANSACTION_TYPES = new HashMap<>();

  private static final byte TYPE_PAYMENT = 0;
  private static final byte TYPE_MESSAGING = 1;
  private static final byte TYPE_COLORED_COINS = 2;
  private static final byte TYPE_DIGITAL_GOODS = 3;
  private static final byte TYPE_ACCOUNT_CONTROL = 4;
  private static final byte TYPE_BURST_MINING = 20; // jump some for easier nxt updating
  private static final byte TYPE_ADVANCED_PAYMENT = 21;
  private static final byte TYPE_AUTOMATED_TRANSACTIONS = 22;

  private static final byte SUBTYPE_PAYMENT_ORDINARY_PAYMENT = 0;
  private static final byte SUBTYPE_PAYMENT_ORDINARY_PAYMENT_MULTI_OUT = 1;
  private static final byte SUBTYPE_PAYMENT_ORDINARY_PAYMENT_MULTI_SAME_OUT = 2;

  private static final byte SUBTYPE_MESSAGING_ARBITRARY_MESSAGE = 0;
  private static final byte SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT = 1;
  private static final byte SUBTYPE_MESSAGING_ACCOUNT_INFO = 5;
  private static final byte SUBTYPE_MESSAGING_ALIAS_SELL = 6;
  private static final byte SUBTYPE_MESSAGING_ALIAS_BUY = 7;

  private static final byte SUBTYPE_COLORED_COINS_ASSET_ISSUANCE = 0;
  private static final byte SUBTYPE_COLORED_COINS_ASSET_TRANSFER = 1;
  private static final byte SUBTYPE_COLORED_COINS_ASK_ORDER_PLACEMENT = 2;
  private static final byte SUBTYPE_COLORED_COINS_BID_ORDER_PLACEMENT = 3;
  private static final byte SUBTYPE_COLORED_COINS_ASK_ORDER_CANCELLATION = 4;
  private static final byte SUBTYPE_COLORED_COINS_BID_ORDER_CANCELLATION = 5;

  private static final byte SUBTYPE_DIGITAL_GOODS_LISTING = 0;
  private static final byte SUBTYPE_DIGITAL_GOODS_DELISTING = 1;
  private static final byte SUBTYPE_DIGITAL_GOODS_PRICE_CHANGE = 2;
  private static final byte SUBTYPE_DIGITAL_GOODS_QUANTITY_CHANGE = 3;
  private static final byte SUBTYPE_DIGITAL_GOODS_PURCHASE = 4;
  private static final byte SUBTYPE_DIGITAL_GOODS_DELIVERY = 5;
  private static final byte SUBTYPE_DIGITAL_GOODS_FEEDBACK = 6;
  private static final byte SUBTYPE_DIGITAL_GOODS_REFUND = 7;

  private static final byte SUBTYPE_AT_CREATION = 0;
  private static final byte SUBTYPE_AT_NXT_PAYMENT = 1;

  private static final byte SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING = 0;

  private static final byte SUBTYPE_BURST_MINING_REWARD_RECIPIENT_ASSIGNMENT = 0;

  private static final byte SUBTYPE_ADVANCED_PAYMENT_ESCROW_CREATION = 0;
  private static final byte SUBTYPE_ADVANCED_PAYMENT_ESCROW_SIGN = 1;
  private static final byte SUBTYPE_ADVANCED_PAYMENT_ESCROW_RESULT = 2;
  private static final byte SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_SUBSCRIBE = 3;
  private static final byte SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_CANCEL = 4;
  private static final byte SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_PAYMENT = 5;

  private static final int BASELINE_FEE_HEIGHT = 1; // At release time must be less than current block - 1440
  private static final Fee BASELINE_ASSET_ISSUANCE_FEE = new Fee(Constants.ASSET_ISSUANCE_FEE_NQT, 0);

  private static Blockchain blockchain;
  private static FluxCapacitor fluxCapacitor;
  private static AccountService accountService;
  private static DGSGoodsStoreService dgsGoodsStoreService;
  private static AliasService aliasService;
  private static AssetExchange assetExchange;
  private static SubscriptionService subscriptionService;
  private static EscrowService escrowService;

  // TODO Temporary...
  public static void init(Blockchain blockchain, FluxCapacitor fluxCapacitor,
                          AccountService accountService, DGSGoodsStoreService dgsGoodsStoreService,
                          AliasService aliasService, AssetExchange assetExchange,
                          SubscriptionService subscriptionService, EscrowService escrowService) {
    TransactionType.blockchain = blockchain;
    TransactionType.fluxCapacitor = fluxCapacitor;
    TransactionType.accountService = accountService;
    TransactionType.dgsGoodsStoreService = dgsGoodsStoreService;
    TransactionType.aliasService = aliasService;
    TransactionType.assetExchange = assetExchange;
    TransactionType.subscriptionService = subscriptionService;
    TransactionType.escrowService = escrowService;

    Map<Byte, TransactionType> paymentTypes = new HashMap<>();
    paymentTypes.put(SUBTYPE_PAYMENT_ORDINARY_PAYMENT, Payment.ORDINARY);
    paymentTypes.put(SUBTYPE_PAYMENT_ORDINARY_PAYMENT_MULTI_OUT, Payment.MULTI_OUT);
    paymentTypes.put(SUBTYPE_PAYMENT_ORDINARY_PAYMENT_MULTI_SAME_OUT, Payment.MULTI_SAME_OUT);

    Map<Byte, TransactionType> messagingTypes = new HashMap<>();
    messagingTypes.put(SUBTYPE_MESSAGING_ARBITRARY_MESSAGE, Messaging.ARBITRARY_MESSAGE);
    messagingTypes.put(SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT, Messaging.ALIAS_ASSIGNMENT);
    messagingTypes.put(SUBTYPE_MESSAGING_ACCOUNT_INFO, Messaging.ACCOUNT_INFO);
    messagingTypes.put(SUBTYPE_MESSAGING_ALIAS_BUY, Messaging.ALIAS_BUY);
    messagingTypes.put(SUBTYPE_MESSAGING_ALIAS_SELL, Messaging.ALIAS_SELL);

    Map<Byte, TransactionType> coloredCoinsTypes = new HashMap<>();
    coloredCoinsTypes.put(SUBTYPE_COLORED_COINS_ASSET_ISSUANCE, ColoredCoins.ASSET_ISSUANCE);
    coloredCoinsTypes.put(SUBTYPE_COLORED_COINS_ASSET_TRANSFER, ColoredCoins.ASSET_TRANSFER);
    coloredCoinsTypes.put(SUBTYPE_COLORED_COINS_ASK_ORDER_PLACEMENT, ColoredCoins.ASK_ORDER_PLACEMENT);
    coloredCoinsTypes.put(SUBTYPE_COLORED_COINS_BID_ORDER_PLACEMENT, ColoredCoins.BID_ORDER_PLACEMENT);
    coloredCoinsTypes.put(SUBTYPE_COLORED_COINS_ASK_ORDER_CANCELLATION, ColoredCoins.ASK_ORDER_CANCELLATION);
    coloredCoinsTypes.put(SUBTYPE_COLORED_COINS_BID_ORDER_CANCELLATION, ColoredCoins.BID_ORDER_CANCELLATION);

    Map<Byte, TransactionType> digitalGoodsTypes = new HashMap<>();
    digitalGoodsTypes.put(SUBTYPE_DIGITAL_GOODS_LISTING, DigitalGoods.LISTING);
    digitalGoodsTypes.put(SUBTYPE_DIGITAL_GOODS_DELISTING, DigitalGoods.DELISTING);
    digitalGoodsTypes.put(SUBTYPE_DIGITAL_GOODS_PRICE_CHANGE, DigitalGoods.PRICE_CHANGE);
    digitalGoodsTypes.put(SUBTYPE_DIGITAL_GOODS_QUANTITY_CHANGE, DigitalGoods.QUANTITY_CHANGE);
    digitalGoodsTypes.put(SUBTYPE_DIGITAL_GOODS_PURCHASE, DigitalGoods.PURCHASE);
    digitalGoodsTypes.put(SUBTYPE_DIGITAL_GOODS_DELIVERY, DigitalGoods.DELIVERY);
    digitalGoodsTypes.put(SUBTYPE_DIGITAL_GOODS_FEEDBACK, DigitalGoods.FEEDBACK);
    digitalGoodsTypes.put(SUBTYPE_DIGITAL_GOODS_REFUND, DigitalGoods.REFUND);

    Map<Byte, TransactionType> atTypes = new HashMap<>();
    atTypes.put(SUBTYPE_AT_CREATION, AutomatedTransactions.AUTOMATED_TRANSACTION_CREATION);
    atTypes.put(SUBTYPE_AT_NXT_PAYMENT, AutomatedTransactions.AT_PAYMENT);

    Map<Byte, TransactionType> accountControlTypes = new HashMap<>();
    accountControlTypes.put(SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING, AccountControl.EFFECTIVE_BALANCE_LEASING);

    Map<Byte, TransactionType> burstMiningTypes = new HashMap<>();
    burstMiningTypes.put(SUBTYPE_BURST_MINING_REWARD_RECIPIENT_ASSIGNMENT, BurstMining.REWARD_RECIPIENT_ASSIGNMENT);

    Map<Byte, TransactionType> advancedPaymentTypes = new HashMap<>();
    advancedPaymentTypes.put(SUBTYPE_ADVANCED_PAYMENT_ESCROW_CREATION, AdvancedPayment.ESCROW_CREATION);
    advancedPaymentTypes.put(SUBTYPE_ADVANCED_PAYMENT_ESCROW_SIGN, AdvancedPayment.ESCROW_SIGN);
    advancedPaymentTypes.put(SUBTYPE_ADVANCED_PAYMENT_ESCROW_RESULT, AdvancedPayment.ESCROW_RESULT);
    advancedPaymentTypes.put(SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_SUBSCRIBE, AdvancedPayment.SUBSCRIPTION_SUBSCRIBE);
    advancedPaymentTypes.put(SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_CANCEL, AdvancedPayment.SUBSCRIPTION_CANCEL);
    advancedPaymentTypes.put(SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_PAYMENT, AdvancedPayment.SUBSCRIPTION_PAYMENT);

    TRANSACTION_TYPES.put(TYPE_PAYMENT, Collections.unmodifiableMap(paymentTypes));
    TRANSACTION_TYPES.put(TYPE_MESSAGING, Collections.unmodifiableMap(messagingTypes));
    TRANSACTION_TYPES.put(TYPE_COLORED_COINS, Collections.unmodifiableMap(coloredCoinsTypes));
    TRANSACTION_TYPES.put(TYPE_DIGITAL_GOODS, Collections.unmodifiableMap(digitalGoodsTypes));
    TRANSACTION_TYPES.put(TYPE_ACCOUNT_CONTROL, Collections.unmodifiableMap(accountControlTypes));
    TRANSACTION_TYPES.put(TYPE_BURST_MINING, Collections.unmodifiableMap(burstMiningTypes));
    TRANSACTION_TYPES.put(TYPE_ADVANCED_PAYMENT, Collections.unmodifiableMap(advancedPaymentTypes));
    TRANSACTION_TYPES.put(TYPE_AUTOMATED_TRANSACTIONS, Collections.unmodifiableMap(atTypes));
  }

  public static TransactionType findTransactionType(byte type, byte subtype) {
    Map<Byte, TransactionType> subtypes = TRANSACTION_TYPES.get(type);
    return subtypes == null ? null : subtypes.get(subtype);
  }

  public static String getTypeDescription(byte type) {
    switch (type) {
      case TYPE_PAYMENT:
        return "Payment";
      case TYPE_MESSAGING:
        return "Messaging";
      case TYPE_COLORED_COINS:
        return "Colored coins";
      case TYPE_DIGITAL_GOODS:
        return "Digital Goods";
      case TYPE_ACCOUNT_CONTROL:
        return "Account Control";
      case TYPE_BURST_MINING:
        return "Burst Mining";
      case TYPE_ADVANCED_PAYMENT:
        return "Advanced Payment";
      case TYPE_AUTOMATED_TRANSACTIONS:
        return "Automated Transactions";
      default:
        return "Unknown";
    }
  }

  public static Map<Byte, Map<Byte, TransactionType>> getTransactionTypes() {
    return Collections.unmodifiableMap(TRANSACTION_TYPES);
  }

  private TransactionType() {
  }

  public abstract byte getType();

  public abstract byte getSubtype();

  public abstract String getDescription();

  public abstract Attachment.AbstractAttachment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException;

  abstract Attachment.AbstractAttachment parseAttachment(JsonObject attachmentData) throws BurstException.NotValidException;

  abstract void validateAttachment(Transaction transaction) throws BurstException.ValidationException;

  // return false if double spending
  public final boolean applyUnconfirmed(Transaction transaction, Account senderAccount) {
    long totalAmountNQT = calculateTransactionAmountNQT(transaction);
    if (logger.isTraceEnabled()) {
      logger.trace("applyUnconfirmed: {} < totalamount: {} = false", senderAccount.getUnconfirmedBalanceNQT(), totalAmountNQT);
    }
    if (senderAccount.getUnconfirmedBalanceNQT() < totalAmountNQT) {
      return false;
    }
    accountService.addToUnconfirmedBalanceNQT(senderAccount, -totalAmountNQT);
    if (!applyAttachmentUnconfirmed(transaction, senderAccount)) {
      if (logger.isTraceEnabled()) {
        logger.trace("!applyAttachmentUnconfirmed({}, {})", transaction, senderAccount.getId());
      }
      accountService.addToUnconfirmedBalanceNQT(senderAccount, totalAmountNQT);
      return false;
    }
    return true;
  }

  public Long calculateTotalAmountNQT(Transaction transaction) {
    return Convert.safeAdd(calculateTransactionAmountNQT(transaction), calculateAttachmentTotalAmountNQT(transaction));
  }

  private Long calculateTransactionAmountNQT(Transaction transaction) {
    long totalAmountNQT = Convert.safeAdd(transaction.getAmountNQT(), transaction.getFeeNQT());
    if (transaction.getReferencedTransactionFullHash() != null
            && transaction.getTimestamp() > Constants.REFERENCED_TRANSACTION_FULL_HASH_BLOCK_TIMESTAMP) {
      totalAmountNQT = Convert.safeAdd(totalAmountNQT, Constants.UNCONFIRMED_POOL_DEPOSIT_NQT);
    }
    return totalAmountNQT;
  }

  protected Long calculateAttachmentTotalAmountNQT(Transaction transaction) {
    return 0L;
  }

  abstract boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount);

  final void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
    accountService.addToBalanceNQT(senderAccount, - (Convert.safeAdd(transaction.getAmountNQT(), transaction.getFeeNQT())));
    if (transaction.getReferencedTransactionFullHash() != null
            && transaction.getTimestamp() > Constants.REFERENCED_TRANSACTION_FULL_HASH_BLOCK_TIMESTAMP) {
      accountService.addToUnconfirmedBalanceNQT(senderAccount, Constants.UNCONFIRMED_POOL_DEPOSIT_NQT);
    }
    if (recipientAccount != null) {
      accountService.addToBalanceAndUnconfirmedBalanceNQT(recipientAccount, transaction.getAmountNQT());
    }
    if (logger.isTraceEnabled()) {
      logger.trace("applying transaction - id: {}, type: {}", transaction.getId(), transaction.getType());
    }
    applyAttachment(transaction, senderAccount, recipientAccount);
  }

  abstract void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount);

  public void parseAppendices(Transaction.Builder builder, JsonObject attachmentData) {
    builder.message(Appendix.Message.parse(attachmentData));
    builder.encryptedMessage(Appendix.EncryptedMessage.parse(attachmentData));
    builder.publicKeyAnnouncement((Appendix.PublicKeyAnnouncement.parse(attachmentData)));
    builder.encryptToSelfMessage(Appendix.EncryptToSelfMessage.parse(attachmentData));
  }

  public void parseAppendices(Transaction.Builder builder, int flags, byte version, ByteBuffer buffer) throws BurstException.ValidationException {
    int position = 1;
    if ((flags & position) != 0) {
      builder.message(new Appendix.Message(buffer, version));
    }
    position <<= 1;
    if ((flags & position) != 0) {
      builder.encryptedMessage(new Appendix.EncryptedMessage(buffer, version));
    }
    position <<= 1;
    if ((flags & position) != 0) {
      builder.publicKeyAnnouncement(new Appendix.PublicKeyAnnouncement(buffer, version));
    }
    position <<= 1;
    if ((flags & position) != 0) {
      builder.encryptToSelfMessage(new Appendix.EncryptToSelfMessage(buffer, version));
    }
  }

  public final void undoUnconfirmed(Transaction transaction, Account senderAccount) {
    undoAttachmentUnconfirmed(transaction, senderAccount);
    accountService.addToUnconfirmedBalanceNQT(senderAccount, Convert.safeAdd(transaction.getAmountNQT(), transaction.getFeeNQT()));
    if (transaction.getReferencedTransactionFullHash() != null
            && transaction.getTimestamp() > Constants.REFERENCED_TRANSACTION_FULL_HASH_BLOCK_TIMESTAMP) {
      accountService.addToUnconfirmedBalanceNQT(senderAccount, Constants.UNCONFIRMED_POOL_DEPOSIT_NQT);
    }
  }

  abstract void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount);

  public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
    return TransactionDuplicationKey.IS_NEVER_DUPLICATE;
  }

  public abstract boolean hasRecipient();

  public boolean isSigned() {
    return true;
  }

  @Override
  public final String toString() {
    return "type: " + getType() + ", subtype: " + getSubtype();
  }

  public abstract static class Payment extends TransactionType {

    private Payment() {
    }

    @Override
    public final byte getType() {
      return TransactionType.TYPE_PAYMENT;
    }

    @Override
    boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
      return true;
    }

    @Override
    void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
    }

    @Override
    void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
    }

    public static final TransactionType ORDINARY = new Payment() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_PAYMENT_ORDINARY_PAYMENT;
      }

      @Override
      public String getDescription() {
        return "Ordinary Payment";
      }

      @Override
      public Attachment.EmptyAttachment parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return Attachment.ORDINARY_PAYMENT;
      }

      @Override
      Attachment.EmptyAttachment parseAttachment(JsonObject attachmentData) {
        return Attachment.ORDINARY_PAYMENT;
      }

      @Override
      void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
        if (transaction.getAmountNQT() <= 0 || transaction.getAmountNQT() >= Constants.MAX_BALANCE_NQT) {
          throw new BurstException.NotValidException("Invalid ordinary payment");
        }
      }

      @Override
      public final boolean hasRecipient() {
        return true;
      }

    };

    public static final TransactionType MULTI_OUT = new Payment() {

      @Override
      public final byte getSubtype() { return TransactionType.SUBTYPE_PAYMENT_ORDINARY_PAYMENT_MULTI_OUT; }

      @Override
      public String getDescription() {
        return "Multi-out payment";
      }

      @Override
      public Attachment.PaymentMultiOutCreation parseAttachment(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
        return new Attachment.PaymentMultiOutCreation(buffer, transactionVersion);
      }

      @Override
      Attachment.PaymentMultiOutCreation parseAttachment(JsonObject attachmentData) throws BurstException.NotValidException {
        return new Attachment.PaymentMultiOutCreation(attachmentData);
      }

      @Override
      void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
        if (!fluxCapacitor.getValue(FluxValues.PRE_DYMAXION, transaction.getHeight())) {
          throw new BurstException.NotCurrentlyValidException("Multi Out Payments are not allowed before the Pre Dymaxion block");
        }

        Attachment.PaymentMultiOutCreation attachment = (Attachment.PaymentMultiOutCreation) transaction.getAttachment();
        Long amountNQT = attachment.getAmountNQT();
        if (amountNQT <= 0
                || amountNQT >= Constants.MAX_BALANCE_NQT
                || amountNQT != transaction.getAmountNQT()
                || attachment.getRecipients().size() < 2) {
          throw new BurstException.NotValidException("Invalid multi out payment");
        }
      }

      @Override
      final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.PaymentMultiOutCreation attachment = (Attachment.PaymentMultiOutCreation) transaction.getAttachment();
        for (List<Long> recipient : attachment.getRecipients()) {
          accountService.addToBalanceAndUnconfirmedBalanceNQT(accountService.getOrAddAccount(recipient.get(0)), recipient.get(1));
        }
      }

      @Override
      public final boolean hasRecipient() {
        return false;
      }

      @Override
      public void parseAppendices(Transaction.Builder builder, JsonObject attachmentData) {
        // No appendices
      }

      @Override
      public void parseAppendices(Transaction.Builder builder, int flags, byte version, ByteBuffer buffer) {
        // No appendices
      }
    };

    public static final TransactionType MULTI_SAME_OUT = new Payment() {

      @Override
      public final byte getSubtype() { return TransactionType.SUBTYPE_PAYMENT_ORDINARY_PAYMENT_MULTI_SAME_OUT; }

      @Override
      public String getDescription() {
        return "Multi-out Same Payment";
      }

      @Override
      public Attachment.PaymentMultiSameOutCreation parseAttachment(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
        return new Attachment.PaymentMultiSameOutCreation(buffer, transactionVersion);
      }

      @Override
      Attachment.PaymentMultiSameOutCreation parseAttachment(JsonObject attachmentData) throws BurstException.NotValidException {
        return new Attachment.PaymentMultiSameOutCreation(attachmentData);
      }

      @Override
      void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
        if (!fluxCapacitor.getValue(FluxValues.PRE_DYMAXION, transaction.getHeight())) {
          throw new BurstException.NotCurrentlyValidException("Multi Same Out Payments are not allowed before the Pre Dymaxion block");
        }

        Attachment.PaymentMultiSameOutCreation attachment = (Attachment.PaymentMultiSameOutCreation) transaction.getAttachment();
        if (attachment.getRecipients().size() < 2 && (transaction.getAmountNQT() % attachment.getRecipients().size() == 0 ) ) {
          throw new BurstException.NotValidException("Invalid multi out payment");
        }
      }

      @Override
      final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.PaymentMultiSameOutCreation attachment = (Attachment.PaymentMultiSameOutCreation) transaction.getAttachment();
        final long amountNQT = Convert.safeDivide(transaction.getAmountNQT(), attachment.getRecipients().size());
        attachment.getRecipients().forEach(a -> accountService.addToBalanceAndUnconfirmedBalanceNQT(accountService.getOrAddAccount(a), amountNQT));
      }

      @Override
      public final boolean hasRecipient() {
        return false;
      }

      @Override
      public void parseAppendices(Transaction.Builder builder, JsonObject attachmentData) {
        // No appendices
      }

      @Override
      public void parseAppendices(Transaction.Builder builder, int flags, byte version, ByteBuffer buffer) {
        // No appendices
      }
    };

  }

  public abstract static class Messaging extends TransactionType {

    private Messaging() {
    }

    @Override
    public final byte getType() {
      return TransactionType.TYPE_MESSAGING;
    }

    @Override
    final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
      return true;
    }

    @Override
    final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
    }

    public static final TransactionType ARBITRARY_MESSAGE = new Messaging() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_MESSAGING_ARBITRARY_MESSAGE;
      }

      @Override
      public String getDescription() {
        return "Arbitrary Message";
      }

      @Override
      public Attachment.EmptyAttachment parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return Attachment.ARBITRARY_MESSAGE;
      }

      @Override
      Attachment.EmptyAttachment parseAttachment(JsonObject attachmentData) {
        return Attachment.ARBITRARY_MESSAGE;
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        // No appendices
      }

      @Override
      void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
        Attachment attachment = transaction.getAttachment();
        if (transaction.getAmountNQT() != 0) {
          throw new BurstException.NotValidException("Invalid arbitrary message: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (! fluxCapacitor.getValue(FluxValues.DIGITAL_GOODS_STORE) && transaction.getMessage() == null) {
          throw new BurstException.NotCurrentlyValidException("Missing message appendix not allowed before DGS block");
        }
      }

      @Override
      public boolean hasRecipient() {
        return true;
      }

      @Override
      public void parseAppendices(Transaction.Builder builder, int flags, byte version, ByteBuffer buffer) throws BurstException.ValidationException {
        int position = 1;
        if ((flags & position) != 0 || (version == 0)) {
          builder.message(new Appendix.Message(buffer, version));
        }
        position <<= 1;
        if ((flags & position) != 0) {
          builder.encryptedMessage(new Appendix.EncryptedMessage(buffer, version));
        }
        position <<= 1;
        if ((flags & position) != 0) {
          builder.publicKeyAnnouncement(new Appendix.PublicKeyAnnouncement(buffer, version));
        }
        position <<= 1;
        if ((flags & position) != 0) {
          builder.encryptToSelfMessage(new Appendix.EncryptToSelfMessage(buffer, version));
        }
      }

    };

    public static final TransactionType ALIAS_ASSIGNMENT = new Messaging() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT;
      }

      @Override
      public String getDescription() {
        return "Alias Assignment";
      }

      @Override
      public Attachment.MessagingAliasAssignment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
        return new Attachment.MessagingAliasAssignment(buffer, transactionVersion);
      }

      @Override
      Attachment.MessagingAliasAssignment parseAttachment(JsonObject attachmentData) {
        return new Attachment.MessagingAliasAssignment(attachmentData);
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.MessagingAliasAssignment attachment = (Attachment.MessagingAliasAssignment) transaction.getAttachment();
        aliasService.addOrUpdateAlias(transaction, attachment);
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        Attachment.MessagingAliasAssignment attachment = (Attachment.MessagingAliasAssignment) transaction.getAttachment();
        return new TransactionDuplicationKey(Messaging.ALIAS_ASSIGNMENT, attachment.getAliasName().toLowerCase(Locale.ENGLISH));
      }

      @Override
      void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
        Attachment.MessagingAliasAssignment attachment = (Attachment.MessagingAliasAssignment) transaction.getAttachment();
        if (attachment.getAliasName().isEmpty()
                || Convert.toBytes(attachment.getAliasName()).length > Constants.MAX_ALIAS_LENGTH
                || attachment.getAliasURI().length() > Constants.MAX_ALIAS_URI_LENGTH) {
          throw new BurstException.NotValidException("Invalid alias assignment: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (!TextUtils.isInAlphabet(attachment.getAliasName())) {
          throw new BurstException.NotValidException("Invalid alias name: " + attachment.getAliasName());
        }
        Alias alias = aliasService.getAlias(attachment.getAliasName());
        if (alias != null && alias.getAccountId() != transaction.getSenderId()) {
          throw new BurstException.NotCurrentlyValidException("Alias already owned by another account: " + attachment.getAliasName());
        }
      }

      @Override
      public boolean hasRecipient() {
        return false;
      }

    };

    public static final TransactionType ALIAS_SELL = new Messaging() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_MESSAGING_ALIAS_SELL;
      }

      @Override
      public String getDescription() {
        return "Alias Sell";
      }

      @Override
      public Attachment.MessagingAliasSell parseAttachment(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
        return new Attachment.MessagingAliasSell(buffer, transactionVersion);
      }

      @Override
      Attachment.MessagingAliasSell parseAttachment(JsonObject attachmentData) {
        return new Attachment.MessagingAliasSell(attachmentData);
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        final Attachment.MessagingAliasSell attachment =
                (Attachment.MessagingAliasSell) transaction.getAttachment();
        aliasService.sellAlias(transaction, attachment);
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        Attachment.MessagingAliasSell attachment = (Attachment.MessagingAliasSell) transaction.getAttachment();
        // not a bug, uniqueness is based on Messaging.ALIAS_ASSIGNMENT
        return new TransactionDuplicationKey(Messaging.ALIAS_ASSIGNMENT, attachment.getAliasName().toLowerCase(Locale.ENGLISH));
      }

      @Override
      void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
        if (! fluxCapacitor.getValue(FluxValues.DIGITAL_GOODS_STORE, blockchain.getLastBlock().getHeight())) {
          throw new BurstException.NotYetEnabledException("Alias transfer not yet enabled at height " + blockchain.getLastBlock().getHeight());
        }
        if (transaction.getAmountNQT() != 0) {
          throw new BurstException.NotValidException("Invalid sell alias transaction: " + JSON.toJsonString(transaction.getJsonObject()));
        }
        final Attachment.MessagingAliasSell attachment =
                (Attachment.MessagingAliasSell) transaction.getAttachment();
        final String aliasName = attachment.getAliasName();
        if (aliasName == null || aliasName.isEmpty()) {
          throw new BurstException.NotValidException("Missing alias name");
        }
        long priceNQT = attachment.getPriceNQT();
        if (priceNQT < 0 || priceNQT > Constants.MAX_BALANCE_NQT) {
          throw new BurstException.NotValidException("Invalid alias sell price: " + priceNQT);
        }
        if (priceNQT == 0) {
          if (Genesis.CREATOR_ID == transaction.getRecipientId()) {
            throw new BurstException.NotValidException("Transferring aliases to Genesis account not allowed");
          } else if (transaction.getRecipientId() == 0) {
            throw new BurstException.NotValidException("Missing alias transfer recipient");
          }
        }
        final Alias alias = aliasService.getAlias(aliasName);
        if (alias == null) {
          throw new BurstException.NotCurrentlyValidException("Alias hasn't been registered yet: " + aliasName);
        } else if (alias.getAccountId() != transaction.getSenderId()) {
          throw new BurstException.NotCurrentlyValidException("Alias doesn't belong to sender: " + aliasName);
        }
      }

      @Override
      public boolean hasRecipient() {
        return true;
      }

    };

    public static final TransactionType ALIAS_BUY = new Messaging() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_MESSAGING_ALIAS_BUY;
      }

      @Override
      public String getDescription() {
        return "Alias Buy";
      }

      @Override
      public Attachment.MessagingAliasBuy parseAttachment(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
        return new Attachment.MessagingAliasBuy(buffer, transactionVersion);
      }

      @Override
      Attachment.MessagingAliasBuy parseAttachment(JsonObject attachmentData) {
        return new Attachment.MessagingAliasBuy(attachmentData);
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        final Attachment.MessagingAliasBuy attachment =
                (Attachment.MessagingAliasBuy) transaction.getAttachment();
        final String aliasName = attachment.getAliasName();
        aliasService.changeOwner(transaction.getSenderId(), aliasName, transaction.getBlockTimestamp());
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        Attachment.MessagingAliasBuy attachment = (Attachment.MessagingAliasBuy) transaction.getAttachment();
        // not a bug, uniqueness is based on Messaging.ALIAS_ASSIGNMENT
        return new TransactionDuplicationKey(Messaging.ALIAS_ASSIGNMENT, attachment.getAliasName().toLowerCase(Locale.ENGLISH));
      }

      @Override
      void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
        if (! fluxCapacitor.getValue(FluxValues.DIGITAL_GOODS_STORE, blockchain.getLastBlock().getHeight())) {
          throw new BurstException.NotYetEnabledException("Alias transfer not yet enabled at height " + blockchain.getLastBlock().getHeight());
        }
        final Attachment.MessagingAliasBuy attachment =
                (Attachment.MessagingAliasBuy) transaction.getAttachment();
        final String aliasName = attachment.getAliasName();
        final Alias alias = aliasService.getAlias(aliasName);
        if (alias == null) {
          throw new BurstException.NotCurrentlyValidException("Alias hasn't been registered yet: " + aliasName);
        } else if (alias.getAccountId() != transaction.getRecipientId()) {
          throw new BurstException.NotCurrentlyValidException("Alias is owned by account other than recipient: "
                  + Convert.toUnsignedLong(alias.getAccountId()));
        }
        Alias.Offer offer = aliasService.getOffer(alias);
        if (offer == null) {
          throw new BurstException.NotCurrentlyValidException("Alias is not for sale: " + aliasName);
        }
        if (transaction.getAmountNQT() < offer.getPriceNQT()) {
          String msg = "Price is too low for: " + aliasName + " ("
                  + transaction.getAmountNQT() + " < " + offer.getPriceNQT() + ")";
          throw new BurstException.NotCurrentlyValidException(msg);
        }
        if (offer.getBuyerId() != 0 && offer.getBuyerId() != transaction.getSenderId()) {
          throw new BurstException.NotCurrentlyValidException("Wrong buyer for " + aliasName + ": "
                  + Convert.toUnsignedLong(transaction.getSenderId()) + " expected: "
                  + Convert.toUnsignedLong(offer.getBuyerId()));
        }
      }

      @Override
      public boolean hasRecipient() {
        return true;
      }

    };

    public static final Messaging ACCOUNT_INFO = new Messaging() {

      @Override
      public byte getSubtype() {
        return TransactionType.SUBTYPE_MESSAGING_ACCOUNT_INFO;
      }

      @Override
      public String getDescription() {
        return "Account Info";
      }

      @Override
      public Attachment.MessagingAccountInfo parseAttachment(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
        return new Attachment.MessagingAccountInfo(buffer, transactionVersion);
      }

      @Override
      Attachment.MessagingAccountInfo parseAttachment(JsonObject attachmentData) {
        return new Attachment.MessagingAccountInfo(attachmentData);
      }

      @Override
      void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
        Attachment.MessagingAccountInfo attachment = (Attachment.MessagingAccountInfo)transaction.getAttachment();
        if (Convert.toBytes(attachment.getName()).length > Constants.MAX_ACCOUNT_NAME_LENGTH
                || Convert.toBytes(attachment.getDescription()).length > Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH
        ) {
          throw new BurstException.NotValidException("Invalid account info issuance: " + JSON.toJsonString(attachment.getJsonObject()));
        }
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.MessagingAccountInfo attachment = (Attachment.MessagingAccountInfo) transaction.getAttachment();
        accountService.setAccountInfo(senderAccount, attachment.getName(), attachment.getDescription());
      }

      @Override
      public boolean hasRecipient() {
        return false;
      }

    };

  }

  public abstract static class ColoredCoins extends TransactionType {

    private ColoredCoins() {}

    @Override
    public final byte getType() {
      return TransactionType.TYPE_COLORED_COINS;
    }

    public static final TransactionType ASSET_ISSUANCE = new ColoredCoins() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_COLORED_COINS_ASSET_ISSUANCE;
      }

      @Override
      public String getDescription() {
        return "Asset Issuance";
      }

      @Override
      public Fee getBaselineFee(int height) {
        return BASELINE_ASSET_ISSUANCE_FEE;
      }

      @Override
      public Attachment.ColoredCoinsAssetIssuance parseAttachment(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
        return new Attachment.ColoredCoinsAssetIssuance(buffer, transactionVersion);
      }

      @Override
      Attachment.ColoredCoinsAssetIssuance parseAttachment(JsonObject attachmentData) {
        return new Attachment.ColoredCoinsAssetIssuance(attachmentData);
      }

      @Override
      boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return true;
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.ColoredCoinsAssetIssuance attachment = (Attachment.ColoredCoinsAssetIssuance) transaction.getAttachment();
        long assetId = transaction.getId();
        assetExchange.addAsset(transaction, attachment);
        accountService.addToAssetAndUnconfirmedAssetBalanceQNT(senderAccount, assetId, attachment.getQuantityQNT());
      }

      @Override
      void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        // Nothing to undo
      }

      @Override
      void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
        Attachment.ColoredCoinsAssetIssuance attachment = (Attachment.ColoredCoinsAssetIssuance)transaction.getAttachment();
        if (attachment.getName().length() < Constants.MIN_ASSET_NAME_LENGTH
                || attachment.getName().length() > Constants.MAX_ASSET_NAME_LENGTH
                || attachment.getDescription().length() > Constants.MAX_ASSET_DESCRIPTION_LENGTH
                || attachment.getDecimals() < 0 || attachment.getDecimals() > 8
                || attachment.getQuantityQNT() <= 0
                || attachment.getQuantityQNT() > Constants.MAX_ASSET_QUANTITY_QNT
        ) {
          throw new BurstException.NotValidException("Invalid asset issuance: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (!TextUtils.isInAlphabet(attachment.getName())) {
          throw new BurstException.NotValidException("Invalid asset name: " + attachment.getName());
        }
      }

      @Override
      public boolean hasRecipient() {
        return false;
      }

    };

    public static final TransactionType ASSET_TRANSFER = new ColoredCoins() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_COLORED_COINS_ASSET_TRANSFER;
      }

      @Override
      public String getDescription() {
        return "Asset Transfer";
      }

      @Override
      public Attachment.ColoredCoinsAssetTransfer parseAttachment(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
        return new Attachment.ColoredCoinsAssetTransfer(buffer, transactionVersion);
      }

      @Override
      Attachment.ColoredCoinsAssetTransfer parseAttachment(JsonObject attachmentData) {
        return new Attachment.ColoredCoinsAssetTransfer(attachmentData);
      }

      @Override
      boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        logger.trace("TransactionType ASSET_TRANSFER");
        Attachment.ColoredCoinsAssetTransfer attachment = (Attachment.ColoredCoinsAssetTransfer) transaction.getAttachment();
        long unconfirmedAssetBalance = accountService.getUnconfirmedAssetBalanceQNT(senderAccount, attachment.getAssetId());
        if (unconfirmedAssetBalance >= 0 && unconfirmedAssetBalance >= attachment.getQuantityQNT()) {
          accountService.addToUnconfirmedAssetBalanceQNT(senderAccount, attachment.getAssetId(), -attachment.getQuantityQNT());
          return true;
        }
        return false;
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.ColoredCoinsAssetTransfer attachment = (Attachment.ColoredCoinsAssetTransfer) transaction.getAttachment();
        accountService.addToAssetBalanceQNT(senderAccount, attachment.getAssetId(), -attachment.getQuantityQNT());
        accountService.addToAssetAndUnconfirmedAssetBalanceQNT(recipientAccount, attachment.getAssetId(), attachment.getQuantityQNT());
        assetExchange.addAssetTransfer(transaction, attachment);
      }

      @Override
      void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        Attachment.ColoredCoinsAssetTransfer attachment = (Attachment.ColoredCoinsAssetTransfer) transaction.getAttachment();
        accountService.addToUnconfirmedAssetBalanceQNT(senderAccount,attachment.getAssetId(), attachment.getQuantityQNT());
      }

      @Override
      void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
        Attachment.ColoredCoinsAssetTransfer attachment = (Attachment.ColoredCoinsAssetTransfer)transaction.getAttachment();
        if (transaction.getAmountNQT() != 0
                || attachment.getComment() != null && attachment.getComment().length() > Constants.MAX_ASSET_TRANSFER_COMMENT_LENGTH
                || attachment.getAssetId() == 0) {
          throw new BurstException.NotValidException("Invalid asset transfer amount or comment: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (transaction.getVersion() > 0 && attachment.getComment() != null) {
          throw new BurstException.NotValidException("Asset transfer comments no longer allowed, use message " +
                  "or encrypted message appendix instead");
        }
        Asset asset = assetExchange.getAsset(attachment.getAssetId());
        if (attachment.getQuantityQNT() <= 0 || (asset != null && attachment.getQuantityQNT() > asset.getQuantityQNT())) {
          throw new BurstException.NotValidException("Invalid asset transfer asset or quantity: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (asset == null) {
          throw new BurstException.NotCurrentlyValidException("Asset " + Convert.toUnsignedLong(attachment.getAssetId()) +
                  " does not exist yet");
        }
      }

      @Override
      public boolean hasRecipient() {
        return true;
      }

    };

    abstract static class ColoredCoinsOrderPlacement extends ColoredCoins {

      @Override
      final void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
        Attachment.ColoredCoinsOrderPlacement attachment = (Attachment.ColoredCoinsOrderPlacement)transaction.getAttachment();
        if (attachment.getPriceNQT() <= 0 || attachment.getPriceNQT() > Constants.MAX_BALANCE_NQT
                || attachment.getAssetId() == 0) {
          throw new BurstException.NotValidException("Invalid asset order placement: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        Asset asset = assetExchange.getAsset(attachment.getAssetId());
        if (attachment.getQuantityQNT() <= 0 || (asset != null && attachment.getQuantityQNT() > asset.getQuantityQNT())) {
          throw new BurstException.NotValidException("Invalid asset order placement asset or quantity: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (asset == null) {
          throw new BurstException.NotCurrentlyValidException("Asset " + Convert.toUnsignedLong(attachment.getAssetId()) +
                  " does not exist yet");
        }
      }

      @Override
      public final boolean hasRecipient() {
        return false;
      }

    }

    public static final TransactionType ASK_ORDER_PLACEMENT = new ColoredCoinsOrderPlacement() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_COLORED_COINS_ASK_ORDER_PLACEMENT;
      }

      @Override
      public String getDescription() {
        return "Ask Order Placement";
      }

      @Override
      public Attachment.ColoredCoinsAskOrderPlacement parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return new Attachment.ColoredCoinsAskOrderPlacement(buffer, transactionVersion);
      }

      @Override
      Attachment.ColoredCoinsAskOrderPlacement parseAttachment(JsonObject attachmentData) {
        return new Attachment.ColoredCoinsAskOrderPlacement(attachmentData);
      }

      @Override
      boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        logger.trace("TransactionType ASK_ORDER_PLACEMENT");
        Attachment.ColoredCoinsAskOrderPlacement attachment = (Attachment.ColoredCoinsAskOrderPlacement) transaction.getAttachment();
        long unconfirmedAssetBalance = accountService.getUnconfirmedAssetBalanceQNT(senderAccount, attachment.getAssetId());
        if (unconfirmedAssetBalance >= 0 && unconfirmedAssetBalance >= attachment.getQuantityQNT()) {
          accountService.addToUnconfirmedAssetBalanceQNT(senderAccount, attachment.getAssetId(), -attachment.getQuantityQNT());
          return true;
        }
        return false;
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.ColoredCoinsAskOrderPlacement attachment = (Attachment.ColoredCoinsAskOrderPlacement) transaction.getAttachment();
        if (assetExchange.getAsset(attachment.getAssetId()) != null) {
          assetExchange.addAskOrder(transaction, attachment);
        }
      }

      @Override
      void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        Attachment.ColoredCoinsAskOrderPlacement attachment = (Attachment.ColoredCoinsAskOrderPlacement) transaction.getAttachment();
        accountService.addToUnconfirmedAssetBalanceQNT(senderAccount, attachment.getAssetId(), attachment.getQuantityQNT());
      }

    };

    public static final TransactionType BID_ORDER_PLACEMENT = new ColoredCoinsOrderPlacement() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_COLORED_COINS_BID_ORDER_PLACEMENT;
      }

      @Override
      public String getDescription() {
        return "Bid Order Placement";
      }

      @Override
      public Attachment.ColoredCoinsBidOrderPlacement parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return new Attachment.ColoredCoinsBidOrderPlacement(buffer, transactionVersion);
      }

      @Override
      Attachment.ColoredCoinsBidOrderPlacement parseAttachment(JsonObject attachmentData) {
        return new Attachment.ColoredCoinsBidOrderPlacement(attachmentData);
      }

      @Override
      boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        logger.trace("TransactionType BID_ORDER_PLACEMENT");
        Long totalAmountNQT = calculateAttachmentTotalAmountNQT(transaction);
        if (senderAccount.getUnconfirmedBalanceNQT() >= totalAmountNQT ) {
          accountService.addToUnconfirmedBalanceNQT(senderAccount, -totalAmountNQT);
          return true;
        }
        return false;
      }

      @Override
      public Long calculateAttachmentTotalAmountNQT(Transaction transaction) {
        Attachment.ColoredCoinsBidOrderPlacement attachment = (Attachment.ColoredCoinsBidOrderPlacement) transaction.getAttachment();
        return Convert.safeMultiply(attachment.getQuantityQNT(), attachment.getPriceNQT());
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.ColoredCoinsBidOrderPlacement attachment = (Attachment.ColoredCoinsBidOrderPlacement) transaction.getAttachment();
        if (assetExchange.getAsset(attachment.getAssetId()) != null) {
          assetExchange.addBidOrder(transaction, attachment);
        }
      }

      @Override
      void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        Long totalAmountNQT = calculateAttachmentTotalAmountNQT(transaction);
        accountService.addToUnconfirmedBalanceNQT(senderAccount, totalAmountNQT);
      }

    };

    abstract static class ColoredCoinsOrderCancellation extends ColoredCoins {

      @Override
      final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return true;
      }

      @Override
      final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
      }

      @Override
      public boolean hasRecipient() {
        return false;
      }

    }

    public static final TransactionType ASK_ORDER_CANCELLATION = new ColoredCoinsOrderCancellation() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_COLORED_COINS_ASK_ORDER_CANCELLATION;
      }

      @Override
      public String getDescription() {
        return "Ask Order Cancellation";
      }

      @Override
      public Attachment.ColoredCoinsAskOrderCancellation parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return new Attachment.ColoredCoinsAskOrderCancellation(buffer, transactionVersion);
      }

      @Override
      Attachment.ColoredCoinsAskOrderCancellation parseAttachment(JsonObject attachmentData) {
        return new Attachment.ColoredCoinsAskOrderCancellation(attachmentData);
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.ColoredCoinsAskOrderCancellation attachment = (Attachment.ColoredCoinsAskOrderCancellation) transaction.getAttachment();
        Order order = assetExchange.getAskOrder(attachment.getOrderId());
        assetExchange.removeAskOrder(attachment.getOrderId());
        if (order != null) {
          accountService.addToUnconfirmedAssetBalanceQNT(senderAccount, order.getAssetId(), order.getQuantityQNT());
        }
      }

      @Override
      void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
        Attachment.ColoredCoinsAskOrderCancellation attachment = (Attachment.ColoredCoinsAskOrderCancellation) transaction.getAttachment();
        Order ask = assetExchange.getAskOrder(attachment.getOrderId());
        if (ask == null) {
          throw new BurstException.NotCurrentlyValidException("Invalid ask order: " + Convert.toUnsignedLong(attachment.getOrderId()));
        }
        if (ask.getAccountId() != transaction.getSenderId()) {
          throw new BurstException.NotValidException("Order " + Convert.toUnsignedLong(attachment.getOrderId()) + " was created by account "
                  + Convert.toUnsignedLong(ask.getAccountId()));
        }
      }

    };

    public static final TransactionType BID_ORDER_CANCELLATION = new ColoredCoinsOrderCancellation() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_COLORED_COINS_BID_ORDER_CANCELLATION;
      }

      @Override
      public String getDescription() {
        return "Bid Order Cancellation";
      }

      @Override
      public Attachment.ColoredCoinsBidOrderCancellation parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return new Attachment.ColoredCoinsBidOrderCancellation(buffer, transactionVersion);
      }

      @Override
      Attachment.ColoredCoinsBidOrderCancellation parseAttachment(JsonObject attachmentData) {
        return new Attachment.ColoredCoinsBidOrderCancellation(attachmentData);
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.ColoredCoinsBidOrderCancellation attachment = (Attachment.ColoredCoinsBidOrderCancellation) transaction.getAttachment();
        Order order = assetExchange.getBidOrder(attachment.getOrderId());
        assetExchange.removeBidOrder(attachment.getOrderId());
        if (order != null) {
          accountService.addToUnconfirmedBalanceNQT(senderAccount, Convert.safeMultiply(order.getQuantityQNT(), order.getPriceNQT()));
        }
      }

      @Override
      void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
        Attachment.ColoredCoinsBidOrderCancellation attachment = (Attachment.ColoredCoinsBidOrderCancellation) transaction.getAttachment();
        Order bid = assetExchange.getBidOrder(attachment.getOrderId());
        if (bid == null) {
          throw new BurstException.NotCurrentlyValidException("Invalid bid order: " + Convert.toUnsignedLong(attachment.getOrderId()));
        }
        if (bid.getAccountId() != transaction.getSenderId()) {
          throw new BurstException.NotValidException("Order " + Convert.toUnsignedLong(attachment.getOrderId()) + " was created by account "
                  + Convert.toUnsignedLong(bid.getAccountId()));
        }
      }

    };
  }

  public abstract static class DigitalGoods extends TransactionType {

    private DigitalGoods() {
    }

    @Override
    public final byte getType() {
      return TransactionType.TYPE_DIGITAL_GOODS;
    }

    @Override
    boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
      return true;
    }

    @Override
    void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
    }

    @Override
    final void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
      if (! fluxCapacitor.getValue(FluxValues.DIGITAL_GOODS_STORE, blockchain.getLastBlock().getHeight())) {
        throw new BurstException.NotYetEnabledException("Digital goods listing not yet enabled at height " + blockchain.getLastBlock().getHeight());
      }
      if (transaction.getAmountNQT() != 0) {
        throw new BurstException.NotValidException("Invalid digital goods transaction");
      }
      doValidateAttachment(transaction);
    }

    abstract void doValidateAttachment(Transaction transaction) throws BurstException.ValidationException;


    public static final TransactionType LISTING = new DigitalGoods() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_DIGITAL_GOODS_LISTING;
      }

      @Override
      public String getDescription() {
        return "Listing";
      }

      @Override
      public Attachment.DigitalGoodsListing parseAttachment(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
        return new Attachment.DigitalGoodsListing(buffer, transactionVersion);
      }

      @Override
      Attachment.DigitalGoodsListing parseAttachment(JsonObject attachmentData) {
        return new Attachment.DigitalGoodsListing(attachmentData);
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.DigitalGoodsListing attachment = (Attachment.DigitalGoodsListing) transaction.getAttachment();
        dgsGoodsStoreService.listGoods(transaction, attachment);
      }

      @Override
      void doValidateAttachment(Transaction transaction) throws BurstException.ValidationException {
        Attachment.DigitalGoodsListing attachment = (Attachment.DigitalGoodsListing) transaction.getAttachment();
        if (attachment.getName().isEmpty()
                || attachment.getName().length() > Constants.MAX_DGS_LISTING_NAME_LENGTH
                || attachment.getDescription().length() > Constants.MAX_DGS_LISTING_DESCRIPTION_LENGTH
                || attachment.getTags().length() > Constants.MAX_DGS_LISTING_TAGS_LENGTH
                || attachment.getQuantity() < 0 || attachment.getQuantity() > Constants.MAX_DGS_LISTING_QUANTITY
                || attachment.getPriceNQT() <= 0 || attachment.getPriceNQT() > Constants.MAX_BALANCE_NQT) {
          throw new BurstException.NotValidException("Invalid digital goods listing: " + JSON.toJsonString(attachment.getJsonObject()));
        }
      }

      @Override
      public boolean hasRecipient() {
        return false;
      }

    };

    public static final TransactionType DELISTING = new DigitalGoods() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_DIGITAL_GOODS_DELISTING;
      }

      @Override
      public String getDescription() {
        return "Delisting";
      }

      @Override
      public Attachment.DigitalGoodsDelisting parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return new Attachment.DigitalGoodsDelisting(buffer, transactionVersion);
      }

      @Override
      Attachment.DigitalGoodsDelisting parseAttachment(JsonObject attachmentData) {
        return new Attachment.DigitalGoodsDelisting(attachmentData);
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.DigitalGoodsDelisting attachment = (Attachment.DigitalGoodsDelisting) transaction.getAttachment();
        dgsGoodsStoreService.delistGoods(attachment.getGoodsId());
      }

      @Override
      void doValidateAttachment(Transaction transaction) throws BurstException.ValidationException {
        Attachment.DigitalGoodsDelisting attachment = (Attachment.DigitalGoodsDelisting) transaction.getAttachment();
        DigitalGoodsStore.Goods goods = dgsGoodsStoreService.getGoods(attachment.getGoodsId());
        if (goods != null && transaction.getSenderId() != goods.getSellerId()) {
          throw new BurstException.NotValidException("Invalid digital goods delisting - seller is different: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (goods == null || goods.isDelisted()) {
          throw new BurstException.NotCurrentlyValidException("Goods " + Convert.toUnsignedLong(attachment.getGoodsId()) +
                  "not yet listed or already delisted");
        }
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        Attachment.DigitalGoodsDelisting attachment = (Attachment.DigitalGoodsDelisting) transaction.getAttachment();
        return new TransactionDuplicationKey(DigitalGoods.DELISTING, Convert.toUnsignedLong(attachment.getGoodsId()));
      }

      @Override
      public boolean hasRecipient() {
        return false;
      }

    };

    public static final TransactionType PRICE_CHANGE = new DigitalGoods() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_DIGITAL_GOODS_PRICE_CHANGE;
      }

      @Override
      public String getDescription() {
        return "Price Change";
      }

      @Override
      public Attachment.DigitalGoodsPriceChange parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return new Attachment.DigitalGoodsPriceChange(buffer, transactionVersion);
      }

      @Override
      Attachment.DigitalGoodsPriceChange parseAttachment(JsonObject attachmentData) {
        return new Attachment.DigitalGoodsPriceChange(attachmentData);
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.DigitalGoodsPriceChange attachment = (Attachment.DigitalGoodsPriceChange) transaction.getAttachment();
        dgsGoodsStoreService.changePrice(attachment.getGoodsId(), attachment.getPriceNQT());
      }

      @Override
      void doValidateAttachment(Transaction transaction) throws BurstException.ValidationException {
        Attachment.DigitalGoodsPriceChange attachment = (Attachment.DigitalGoodsPriceChange) transaction.getAttachment();
        DigitalGoodsStore.Goods goods = dgsGoodsStoreService.getGoods(attachment.getGoodsId());
        if (attachment.getPriceNQT() <= 0 || attachment.getPriceNQT() > Constants.MAX_BALANCE_NQT
                || (goods != null && transaction.getSenderId() != goods.getSellerId())) {
          throw new BurstException.NotValidException("Invalid digital goods price change: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (goods == null || goods.isDelisted()) {
          throw new BurstException.NotCurrentlyValidException("Goods " + Convert.toUnsignedLong(attachment.getGoodsId()) +
                  "not yet listed or already delisted");
        }
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        Attachment.DigitalGoodsPriceChange attachment = (Attachment.DigitalGoodsPriceChange) transaction.getAttachment();
        // not a bug, uniqueness is based on DigitalGoods.DELISTING
        return new TransactionDuplicationKey(DigitalGoods.DELISTING, Convert.toUnsignedLong(attachment.getGoodsId()));
      }

      @Override
      public boolean hasRecipient() {
        return false;
      }

    };

    public static final TransactionType QUANTITY_CHANGE = new DigitalGoods() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_DIGITAL_GOODS_QUANTITY_CHANGE;
      }

      @Override
      public String getDescription() {
        return "Quantity Change";
      }

      @Override
      public Attachment.DigitalGoodsQuantityChange parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return new Attachment.DigitalGoodsQuantityChange(buffer, transactionVersion);
      }

      @Override
      Attachment.DigitalGoodsQuantityChange parseAttachment(JsonObject attachmentData) {
        return new Attachment.DigitalGoodsQuantityChange(attachmentData);
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.DigitalGoodsQuantityChange attachment = (Attachment.DigitalGoodsQuantityChange) transaction.getAttachment();
        dgsGoodsStoreService.changeQuantity(attachment.getGoodsId(), attachment.getDeltaQuantity(), false);
      }

      @Override
      void doValidateAttachment(Transaction transaction) throws BurstException.ValidationException {
        Attachment.DigitalGoodsQuantityChange attachment = (Attachment.DigitalGoodsQuantityChange) transaction.getAttachment();
        DigitalGoodsStore.Goods goods = dgsGoodsStoreService.getGoods(attachment.getGoodsId());
        if (attachment.getDeltaQuantity() < -Constants.MAX_DGS_LISTING_QUANTITY
                || attachment.getDeltaQuantity() > Constants.MAX_DGS_LISTING_QUANTITY
                || (goods != null && transaction.getSenderId() != goods.getSellerId())) {
          throw new BurstException.NotValidException("Invalid digital goods quantity change: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (goods == null || goods.isDelisted()) {
          throw new BurstException.NotCurrentlyValidException("Goods " + Convert.toUnsignedLong(attachment.getGoodsId()) +
                  "not yet listed or already delisted");
        }
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        Attachment.DigitalGoodsQuantityChange attachment = (Attachment.DigitalGoodsQuantityChange) transaction.getAttachment();
        // not a bug, uniqueness is based on DigitalGoods.DELISTING
        return new TransactionDuplicationKey(DigitalGoods.DELISTING, Convert.toUnsignedLong(attachment.getGoodsId()));
      }

      @Override
      public boolean hasRecipient() {
        return false;
      }

    };

    public static final TransactionType PURCHASE = new DigitalGoods() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_DIGITAL_GOODS_PURCHASE;
      }

      @Override
      public String getDescription() {
        return "Purchase";
      }

      @Override
      public Attachment.DigitalGoodsPurchase parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return new Attachment.DigitalGoodsPurchase(buffer, transactionVersion);
      }

      @Override
      Attachment.DigitalGoodsPurchase parseAttachment(JsonObject attachmentData) {
        return new Attachment.DigitalGoodsPurchase(attachmentData);
      }

      @Override
      boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        logger.trace("TransactionType PURCHASE");
        Long totalAmountNQT = calculateAttachmentTotalAmountNQT(transaction);
        if (senderAccount.getUnconfirmedBalanceNQT() >= totalAmountNQT) {
          accountService.addToUnconfirmedBalanceNQT(senderAccount, -totalAmountNQT);
          return true;
        }
        return false;
      }

      @Override
      public Long calculateAttachmentTotalAmountNQT(Transaction transaction) {
        Attachment.DigitalGoodsPurchase attachment = (Attachment.DigitalGoodsPurchase) transaction.getAttachment();
        return Convert.safeMultiply(attachment.getQuantity(), attachment.getPriceNQT());
      }

      @Override
      void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        accountService.addToUnconfirmedBalanceNQT(senderAccount, calculateAttachmentTotalAmountNQT(transaction));
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.DigitalGoodsPurchase attachment = (Attachment.DigitalGoodsPurchase) transaction.getAttachment();
        dgsGoodsStoreService.purchase(transaction, attachment);
      }

      @Override
      void doValidateAttachment(Transaction transaction) throws BurstException.ValidationException {
        Attachment.DigitalGoodsPurchase attachment = (Attachment.DigitalGoodsPurchase) transaction.getAttachment();
        DigitalGoodsStore.Goods goods = dgsGoodsStoreService.getGoods(attachment.getGoodsId());
        if (attachment.getQuantity() <= 0 || attachment.getQuantity() > Constants.MAX_DGS_LISTING_QUANTITY
                || attachment.getPriceNQT() <= 0 || attachment.getPriceNQT() > Constants.MAX_BALANCE_NQT
                || (goods != null && goods.getSellerId() != transaction.getRecipientId())) {
          throw new BurstException.NotValidException("Invalid digital goods purchase: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (transaction.getEncryptedMessage() != null && ! transaction.getEncryptedMessage().isText()) {
          throw new BurstException.NotValidException("Only text encrypted messages allowed");
        }
        if (goods == null || goods.isDelisted()) {
          throw new BurstException.NotCurrentlyValidException("Goods " + Convert.toUnsignedLong(attachment.getGoodsId()) +
                  "not yet listed or already delisted");
        }
        if (attachment.getQuantity() > goods.getQuantity() || attachment.getPriceNQT() != goods.getPriceNQT()) {
          throw new BurstException.NotCurrentlyValidException("Goods price or quantity changed: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (attachment.getDeliveryDeadlineTimestamp() <= blockchain.getLastBlock().getTimestamp()) {
          throw new BurstException.NotCurrentlyValidException("Delivery deadline has already expired: " + attachment.getDeliveryDeadlineTimestamp());
        }
      }

      @Override
      public boolean hasRecipient() {
        return true;
      }

    };

    public static final TransactionType DELIVERY = new DigitalGoods() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_DIGITAL_GOODS_DELIVERY;
      }

      @Override
      public String getDescription() {
        return "Delivery";
      }

      @Override
      public Attachment.DigitalGoodsDelivery parseAttachment(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
        return new Attachment.DigitalGoodsDelivery(buffer, transactionVersion);
      }

      @Override
      Attachment.DigitalGoodsDelivery parseAttachment(JsonObject attachmentData) {
        return new Attachment.DigitalGoodsDelivery(attachmentData);
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.DigitalGoodsDelivery attachment = (Attachment.DigitalGoodsDelivery)transaction.getAttachment();
        dgsGoodsStoreService.deliver(transaction, attachment);
      }

      @Override
      void doValidateAttachment(Transaction transaction) throws BurstException.ValidationException {
        Attachment.DigitalGoodsDelivery attachment = (Attachment.DigitalGoodsDelivery) transaction.getAttachment();
        DigitalGoodsStore.Purchase purchase = dgsGoodsStoreService.getPendingPurchase(attachment.getPurchaseId());
        if (attachment.getGoods().getData().length > Constants.MAX_DGS_GOODS_LENGTH
                || attachment.getGoods().getData().length == 0
                || attachment.getGoods().getNonce().length != 32
                || attachment.getDiscountNQT() < 0 || attachment.getDiscountNQT() > Constants.MAX_BALANCE_NQT
                || (purchase != null &&
                (purchase.getBuyerId() != transaction.getRecipientId()
                        || transaction.getSenderId() != purchase.getSellerId()
                        || attachment.getDiscountNQT() > Convert.safeMultiply(purchase.getPriceNQT(), purchase.getQuantity())))) {
          throw new BurstException.NotValidException("Invalid digital goods delivery: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (purchase == null || purchase.getEncryptedGoods() != null) {
          throw new BurstException.NotCurrentlyValidException("Purchase does not exist yet, or already delivered: " + JSON.toJsonString(attachment.getJsonObject()));
        }
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        Attachment.DigitalGoodsDelivery attachment = (Attachment.DigitalGoodsDelivery) transaction.getAttachment();
        return new TransactionDuplicationKey(DigitalGoods.DELIVERY, Convert.toUnsignedLong(attachment.getPurchaseId()));
      }

      @Override
      public boolean hasRecipient() {
        return true;
      }

    };

    public static final TransactionType FEEDBACK = new DigitalGoods() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_DIGITAL_GOODS_FEEDBACK;
      }

      @Override
      public String getDescription() {
        return "Feedback";
      }

      @Override
      public Attachment.DigitalGoodsFeedback parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return new Attachment.DigitalGoodsFeedback(buffer, transactionVersion);
      }

      @Override
      Attachment.DigitalGoodsFeedback parseAttachment(JsonObject attachmentData) {
        return new Attachment.DigitalGoodsFeedback(attachmentData);
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.DigitalGoodsFeedback attachment = (Attachment.DigitalGoodsFeedback)transaction.getAttachment();
        dgsGoodsStoreService.feedback(attachment.getPurchaseId(), transaction.getEncryptedMessage(), transaction.getMessage());
      }

      @Override
      void doValidateAttachment(Transaction transaction) throws BurstException.ValidationException {
        Attachment.DigitalGoodsFeedback attachment = (Attachment.DigitalGoodsFeedback) transaction.getAttachment();
        DigitalGoodsStore.Purchase purchase = dgsGoodsStoreService.getPurchase(attachment.getPurchaseId());
        if (purchase != null &&
                (purchase.getSellerId() != transaction.getRecipientId()
                        || transaction.getSenderId() != purchase.getBuyerId())) {
          throw new BurstException.NotValidException("Invalid digital goods feedback: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (transaction.getEncryptedMessage() == null && transaction.getMessage() == null) {
          throw new BurstException.NotValidException("Missing feedback message");
        }
        if (transaction.getEncryptedMessage() != null && ! transaction.getEncryptedMessage().isText()) {
          throw new BurstException.NotValidException("Only text encrypted messages allowed");
        }
        if (transaction.getMessage() != null && ! transaction.getMessage().isText()) {
          throw new BurstException.NotValidException("Only text public messages allowed");
        }
        if (purchase == null || purchase.getEncryptedGoods() == null) {
          throw new BurstException.NotCurrentlyValidException("Purchase does not exist yet or not yet delivered");
        }
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        Attachment.DigitalGoodsFeedback attachment = (Attachment.DigitalGoodsFeedback) transaction.getAttachment();
        return new TransactionDuplicationKey(DigitalGoods.FEEDBACK, Convert.toUnsignedLong(attachment.getPurchaseId()));
      }

      @Override
      public boolean hasRecipient() {
        return true;
      }

    };

    public static final TransactionType REFUND = new DigitalGoods() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_DIGITAL_GOODS_REFUND;
      }

      @Override
      public String getDescription() {
        return "Refund";
      }

      @Override
      public Attachment.DigitalGoodsRefund parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return new Attachment.DigitalGoodsRefund(buffer, transactionVersion);
      }

      @Override
      Attachment.DigitalGoodsRefund parseAttachment(JsonObject attachmentData) {
        return new Attachment.DigitalGoodsRefund(attachmentData);
      }

      @Override
      boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        logger.trace("TransactionType REFUND");
        Long totalAmountNQT = calculateAttachmentTotalAmountNQT(transaction);
        if (senderAccount.getUnconfirmedBalanceNQT() >= totalAmountNQT) {
          accountService.addToUnconfirmedBalanceNQT(senderAccount, -totalAmountNQT);
          return true;
        }
        return false;
      }

      @Override
      public Long calculateAttachmentTotalAmountNQT(Transaction transaction) {
        Attachment.DigitalGoodsRefund attachment = (Attachment.DigitalGoodsRefund) transaction.getAttachment();
        return attachment.getRefundNQT();
      }

      @Override
      void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        accountService.addToUnconfirmedBalanceNQT(senderAccount, calculateAttachmentTotalAmountNQT(transaction));
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.DigitalGoodsRefund attachment = (Attachment.DigitalGoodsRefund) transaction.getAttachment();
        dgsGoodsStoreService.refund(transaction.getSenderId(), attachment.getPurchaseId(),
                attachment.getRefundNQT(), transaction.getEncryptedMessage());
      }

      @Override
      void doValidateAttachment(Transaction transaction) throws BurstException.ValidationException {
        Attachment.DigitalGoodsRefund attachment = (Attachment.DigitalGoodsRefund) transaction.getAttachment();
        DigitalGoodsStore.Purchase purchase = dgsGoodsStoreService.getPurchase(attachment.getPurchaseId());
        if (attachment.getRefundNQT() < 0 || attachment.getRefundNQT() > Constants.MAX_BALANCE_NQT
                || (purchase != null &&
                (purchase.getBuyerId() != transaction.getRecipientId()
                        || transaction.getSenderId() != purchase.getSellerId()))) {
          throw new BurstException.NotValidException("Invalid digital goods refund: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (transaction.getEncryptedMessage() != null && ! transaction.getEncryptedMessage().isText()) {
          throw new BurstException.NotValidException("Only text encrypted messages allowed");
        }
        if (purchase == null || purchase.getEncryptedGoods() == null || purchase.getRefundNQT() != 0) {
          throw new BurstException.NotCurrentlyValidException("Purchase does not exist or is not delivered or is already refunded");
        }
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        Attachment.DigitalGoodsRefund attachment = (Attachment.DigitalGoodsRefund) transaction.getAttachment();
        return new TransactionDuplicationKey(DigitalGoods.REFUND, Convert.toUnsignedLong(attachment.getPurchaseId()));
      }

      @Override
      public boolean hasRecipient() {
        return true;
      }

    };

  }

  public abstract static class AccountControl extends TransactionType {

    private AccountControl() {
    }

    @Override
    public final byte getType() {
      return TransactionType.TYPE_ACCOUNT_CONTROL;
    }

    @Override
    final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
      return true;
    }

    @Override
    final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
    }

    public static final TransactionType EFFECTIVE_BALANCE_LEASING = new AccountControl() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING;
      }

      @Override
      public String getDescription() {
        return "Effective Balance Leasing";
      }

      @Override
      public Attachment.AccountControlEffectiveBalanceLeasing parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return new Attachment.AccountControlEffectiveBalanceLeasing(buffer, transactionVersion);
      }

      @Override
      Attachment.AccountControlEffectiveBalanceLeasing parseAttachment(JsonObject attachmentData) {
        return new Attachment.AccountControlEffectiveBalanceLeasing(attachmentData);
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        // TODO: check if anyone's used this or if it's even possible to use this, and eliminate it if possible
      }

      @Override
      void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
        Attachment.AccountControlEffectiveBalanceLeasing attachment = (Attachment.AccountControlEffectiveBalanceLeasing)transaction.getAttachment();
        Account recipientAccount = accountService.getAccount(transaction.getRecipientId());
        if (transaction.getSenderId() == transaction.getRecipientId()
                || transaction.getAmountNQT() != 0
                || attachment.getPeriod() < 1440) {
          throw new BurstException.NotValidException("Invalid effective balance leasing: " + JSON.toJsonString(transaction.getJsonObject()) + " transaction " + transaction.getStringId());
        }
        if (recipientAccount == null
                || (recipientAccount.getPublicKey() == null && ! transaction.getStringId().equals("5081403377391821646"))) {
          throw new BurstException.NotCurrentlyValidException("Invalid effective balance leasing: "
                  + " recipient account " + transaction.getRecipientId() + " not found or no public key published");
        }
      }

      @Override
      public boolean hasRecipient() {
        return true;
      }

    };

  }

  public abstract static class BurstMining extends TransactionType {

    private BurstMining() {}

    @Override
    public final byte getType() {
      return TransactionType.TYPE_BURST_MINING;
    }

    @Override
    final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
      return true;
    }

    @Override
    final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {}

    public static final TransactionType REWARD_RECIPIENT_ASSIGNMENT = new BurstMining() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_BURST_MINING_REWARD_RECIPIENT_ASSIGNMENT;
      }

      @Override
      public String getDescription() {
        return "Reward Recipient Assignment";
      }

      @Override
      public Attachment.BurstMiningRewardRecipientAssignment
      parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return new Attachment.BurstMiningRewardRecipientAssignment(buffer, transactionVersion);
      }

      @Override
      Attachment.BurstMiningRewardRecipientAssignment parseAttachment(JsonObject attachmentData) {
        return new Attachment.BurstMiningRewardRecipientAssignment(attachmentData);
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        accountService.setRewardRecipientAssignment(senderAccount, recipientAccount.getId());
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        if (! fluxCapacitor.getValue(FluxValues.DIGITAL_GOODS_STORE)) {
          return TransactionDuplicationKey.IS_NEVER_DUPLICATE; // sync fails after 7007 without this
        }

        return new TransactionDuplicationKey(BurstMining.REWARD_RECIPIENT_ASSIGNMENT, Convert.toUnsignedLong(transaction.getSenderId()));
      }

      @Override
      void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
        int height = blockchain.getLastBlock().getHeight() + 1;
        Account sender = accountService.getAccount(transaction.getSenderId());

        if (sender == null) {
          throw new BurstException.NotCurrentlyValidException("Sender not yet known ?!");
        }

        Account.RewardRecipientAssignment rewardAssignment = accountService.getRewardRecipientAssignment(sender);
        if (rewardAssignment != null && rewardAssignment.getFromHeight() >= height) {
          throw new BurstException.NotCurrentlyValidException("Cannot reassign reward recipient before previous goes into effect: " + JSON.toJsonString(transaction.getJsonObject()));
        }
        Account recip = accountService.getAccount(transaction.getRecipientId());
        if (recip == null || recip.getPublicKey() == null) {
          throw new BurstException.NotValidException("Reward recipient must have public key saved in blockchain: " + JSON.toJsonString(transaction.getJsonObject()));
        }

        if (fluxCapacitor.getValue(FluxValues.PRE_DYMAXION)) {
          if (transaction.getAmountNQT() != 0 || transaction.getFeeNQT() < FEE_QUANT) {
            throw new BurstException.NotValidException("Reward recipient assignment transaction must have 0 send amount and at least minimum fee: " + JSON.toJsonString(transaction.getJsonObject()));
          }
        } else {
          if (transaction.getAmountNQT() != 0 || transaction.getFeeNQT() != Constants.ONE_BURST) {
            throw new BurstException.NotValidException("Reward recipient assignment transaction must have 0 send amount and 1 fee: " + JSON.toJsonString(transaction.getJsonObject()));
          }
        }

        if (!Burst.getFluxCapacitor().getValue(FluxValues.REWARD_RECIPIENT_ENABLE, height)) {
          throw new BurstException.NotCurrentlyValidException("Reward recipient assignment not allowed before block " + Burst.getFluxCapacitor().getStartingHeight(FluxValues.REWARD_RECIPIENT_ENABLE));
        }
      }

      @Override
      public boolean hasRecipient() {
        return true;
      }
    };
  }

  public abstract static class AdvancedPayment extends TransactionType {

    private AdvancedPayment() {}

    @Override
    public final byte getType() {
      return TransactionType.TYPE_ADVANCED_PAYMENT;
    }

    public static final TransactionType ESCROW_CREATION = new AdvancedPayment() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_ADVANCED_PAYMENT_ESCROW_CREATION;
      }

      @Override
      public String getDescription() {
        return "Escrow Creation";
      }

      @Override
      public Attachment.AdvancedPaymentEscrowCreation parseAttachment(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
        return new Attachment.AdvancedPaymentEscrowCreation(buffer, transactionVersion);
      }

      @Override
      Attachment.AdvancedPaymentEscrowCreation parseAttachment(JsonObject attachmentData) throws BurstException.NotValidException {
        return new Attachment.AdvancedPaymentEscrowCreation(attachmentData);
      }

      @Override
      final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        logger.trace("TransactionType ESCROW_CREATION");
        Long totalAmountNQT = calculateAttachmentTotalAmountNQT(transaction);
        if (senderAccount.getUnconfirmedBalanceNQT() < totalAmountNQT) {
          return false;
        }
        accountService.addToUnconfirmedBalanceNQT(senderAccount, -totalAmountNQT);
        return true;
      }

      @Override
      public Long calculateAttachmentTotalAmountNQT(Transaction transaction) {
        Attachment.AdvancedPaymentEscrowCreation attachment = (Attachment.AdvancedPaymentEscrowCreation) transaction.getAttachment();
        return Convert.safeAdd(attachment.getAmountNQT(), Convert.safeMultiply(attachment.getTotalSigners(), Constants.ONE_BURST));
      }

      @Override
      final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.AdvancedPaymentEscrowCreation attachment = (Attachment.AdvancedPaymentEscrowCreation) transaction.getAttachment();
        Long totalAmountNQT = calculateAttachmentTotalAmountNQT(transaction);
        accountService.addToBalanceNQT(senderAccount, -totalAmountNQT);
        Collection<Long> signers = attachment.getSigners();
        signers.forEach(signer -> accountService.addToBalanceAndUnconfirmedBalanceNQT(accountService.getOrAddAccount(signer), Constants.ONE_BURST));
        escrowService.addEscrowTransaction(senderAccount,
                recipientAccount,
                transaction.getId(),
                attachment.getAmountNQT(),
                attachment.getRequiredSigners(),
                attachment.getSigners(),
                transaction.getTimestamp() + attachment.getDeadline(),
                attachment.getDeadlineAction());
      }

      @Override
      final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        accountService.addToUnconfirmedBalanceNQT(senderAccount, calculateAttachmentTotalAmountNQT(transaction));
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        return TransactionDuplicationKey.IS_NEVER_DUPLICATE;
      }

      @Override
      void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
        Attachment.AdvancedPaymentEscrowCreation attachment = (Attachment.AdvancedPaymentEscrowCreation) transaction.getAttachment();
        Long totalAmountNQT = Convert.safeAdd(attachment.getAmountNQT(), transaction.getFeeNQT());
        if (transaction.getSenderId() == transaction.getRecipientId()) {
          throw new BurstException.NotValidException("Escrow must have different sender and recipient");
        }
        totalAmountNQT = Convert.safeAdd(totalAmountNQT, attachment.getTotalSigners() * Constants.ONE_BURST);
        if (transaction.getAmountNQT() != 0) {
          throw new BurstException.NotValidException("Transaction sent amount must be 0 for escrow");
        }
        if (totalAmountNQT.compareTo(0L) < 0 ||
                totalAmountNQT.compareTo(Constants.MAX_BALANCE_NQT) > 0)
        {
          throw new BurstException.NotValidException("Invalid escrow creation amount");
        }
        if (transaction.getFeeNQT() < Constants.ONE_BURST) {
          throw new BurstException.NotValidException("Escrow transaction must have a fee at least 1 burst");
        }
        if (attachment.getRequiredSigners() < 1 || attachment.getRequiredSigners() > 10) {
          throw new BurstException.NotValidException("Escrow required signers much be 1 - 10");
        }
        if (attachment.getRequiredSigners() > attachment.getTotalSigners()) {
          throw new BurstException.NotValidException("Cannot have more required than signers on escrow");
        }
        if (attachment.getTotalSigners() < 1 || attachment.getTotalSigners() > 10) {
          throw new BurstException.NotValidException("Escrow transaction requires 1 - 10 signers");
        }
        if (attachment.getDeadline() < 1 || attachment.getDeadline() > 7776000) { // max deadline 3 months
          throw new BurstException.NotValidException("Escrow deadline must be 1 - 7776000 seconds");
        }
        if (attachment.getDeadlineAction() == null || attachment.getDeadlineAction() == Escrow.DecisionType.UNDECIDED) {
          throw new BurstException.NotValidException("Invalid deadline action for escrow");
        }
        if (attachment.getSigners().contains(transaction.getSenderId()) ||
                attachment.getSigners().contains(transaction.getRecipientId())) {
          throw new BurstException.NotValidException("Escrow sender and recipient cannot be signers");
        }
        if (!escrowService.isEnabled()) {
          throw new BurstException.NotYetEnabledException("Escrow not yet enabled");
        }
      }

      @Override
      public final boolean hasRecipient() {
        return true;
      }
    };

    public static final TransactionType ESCROW_SIGN = new AdvancedPayment() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_ADVANCED_PAYMENT_ESCROW_SIGN;
      }

      @Override
      public String getDescription() {
        return "Escrow Sign";
      }

      @Override
      public Attachment.AdvancedPaymentEscrowSign parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return new Attachment.AdvancedPaymentEscrowSign(buffer, transactionVersion);
      }

      @Override
      Attachment.AdvancedPaymentEscrowSign parseAttachment(JsonObject attachmentData) {
        return new Attachment.AdvancedPaymentEscrowSign(attachmentData);
      }

      @Override
      final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return true;
      }

      @Override
      final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.AdvancedPaymentEscrowSign attachment = (Attachment.AdvancedPaymentEscrowSign) transaction.getAttachment();
        Escrow escrow = escrowService.getEscrowTransaction(attachment.getEscrowId());
        escrowService.sign(senderAccount.getId(), attachment.getDecision(), escrow);
      }

      @Override
      final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        // Nothing to undo.
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        Attachment.AdvancedPaymentEscrowSign attachment = (Attachment.AdvancedPaymentEscrowSign) transaction.getAttachment();
        String uniqueString = Convert.toUnsignedLong(attachment.getEscrowId()) + ":" +
                Convert.toUnsignedLong(transaction.getSenderId());
        return new TransactionDuplicationKey(AdvancedPayment.ESCROW_SIGN, uniqueString);
      }

      @Override
      void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
        Attachment.AdvancedPaymentEscrowSign attachment = (Attachment.AdvancedPaymentEscrowSign) transaction.getAttachment();
        if (transaction.getAmountNQT() != 0 || transaction.getFeeNQT() != Constants.ONE_BURST) {
          throw new BurstException.NotValidException("Escrow signing must have amount 0 and fee of 1");
        }
        if (attachment.getEscrowId() == null || attachment.getDecision() == null) {
          throw new BurstException.NotValidException("Escrow signing requires escrow id and decision set");
        }
        Escrow escrow = escrowService.getEscrowTransaction(attachment.getEscrowId());
        if (escrow == null) {
          throw new BurstException.NotValidException("Escrow transaction not found");
        }
        if (!escrowService.isIdSigner(transaction.getSenderId(), escrow) &&
                !escrow.getSenderId().equals(transaction.getSenderId()) &&
                !escrow.getRecipientId().equals(transaction.getSenderId())) {
          throw new BurstException.NotValidException("Sender is not a participant in specified escrow");
        }
        if (escrow.getSenderId().equals(transaction.getSenderId()) && attachment.getDecision() != Escrow.DecisionType.RELEASE) {
          throw new BurstException.NotValidException("Escrow sender can only release");
        }
        if (escrow.getRecipientId().equals(transaction.getSenderId()) && attachment.getDecision() != Escrow.DecisionType.REFUND) {
          throw new BurstException.NotValidException("Escrow recipient can only refund");
        }
        if (!escrowService.isEnabled()) {
          throw new BurstException.NotYetEnabledException("Escrow not yet enabled");
        }
      }

      @Override
      public final boolean hasRecipient() {
        return false;
      }
    };

    public static final TransactionType ESCROW_RESULT = new AdvancedPayment() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_ADVANCED_PAYMENT_ESCROW_RESULT;
      }

      @Override
      public String getDescription() {
        return "Escrow Result";
      }

      @Override
      public Attachment.AdvancedPaymentEscrowResult parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return new Attachment.AdvancedPaymentEscrowResult(buffer, transactionVersion);
      }

      @Override
      Attachment.AdvancedPaymentEscrowResult parseAttachment(JsonObject attachmentData) {
        return new Attachment.AdvancedPaymentEscrowResult(attachmentData);
      }

      @Override
      final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return false;
      }

      @Override
      final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        // Nothing to apply.
      }

      @Override
      final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        // Nothing to undo.
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        return TransactionDuplicationKey.IS_ALWAYS_DUPLICATE;
      }

      @Override
      void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
        throw new BurstException.NotValidException("Escrow result never validates");
      }

      @Override
      public final boolean hasRecipient() {
        return true;
      }

      @Override
      public final boolean isSigned() {
        return false;
      }
    };

    public static final TransactionType SUBSCRIPTION_SUBSCRIBE = new AdvancedPayment() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_SUBSCRIBE;
      }

      @Override
      public String getDescription() {
        return "Subscription Subscribe";
      }

      @Override
      public Attachment.AdvancedPaymentSubscriptionSubscribe parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return new Attachment.AdvancedPaymentSubscriptionSubscribe(buffer, transactionVersion);
      }

      @Override
      Attachment.AdvancedPaymentSubscriptionSubscribe parseAttachment(JsonObject attachmentData) {
        return new Attachment.AdvancedPaymentSubscriptionSubscribe(attachmentData);
      }

      @Override
      final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return true;
      }

      @Override
      final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.AdvancedPaymentSubscriptionSubscribe attachment = (Attachment.AdvancedPaymentSubscriptionSubscribe) transaction.getAttachment();
        subscriptionService.addSubscription(senderAccount, recipientAccount, transaction.getId(), transaction.getAmountNQT(), transaction.getTimestamp(), attachment.getFrequency());
      }

      @Override
      final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        // Nothing to undo.
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        return TransactionDuplicationKey.IS_NEVER_DUPLICATE;
      }

      @Override
      void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
        Attachment.AdvancedPaymentSubscriptionSubscribe attachment = (Attachment.AdvancedPaymentSubscriptionSubscribe) transaction.getAttachment();
        if (attachment.getFrequency() == null ||
                attachment.getFrequency() < Constants.BURST_SUBSCRIPTION_MIN_FREQ ||
                attachment.getFrequency() > Constants.BURST_SUBSCRIPTION_MAX_FREQ) {
          throw new BurstException.NotValidException("Invalid subscription frequency");
        }
        if (transaction.getAmountNQT() < Constants.ONE_BURST || transaction.getAmountNQT() > Constants.MAX_BALANCE_NQT) {
          throw new BurstException.NotValidException("Subscriptions must be at least one burst");
        }
        if (transaction.getSenderId() == transaction.getRecipientId()) {
          throw new BurstException.NotValidException("Cannot create subscription to same address");
        }
        if (!subscriptionService.isEnabled()) {
          throw new BurstException.NotYetEnabledException("Subscriptions not yet enabled");
        }
      }

      @Override
      public final boolean hasRecipient() {
        return true;
      }
    };

    public static final TransactionType SUBSCRIPTION_CANCEL = new AdvancedPayment() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_CANCEL;
      }

      @Override
      public String getDescription() {
        return "Subscription Cancel";
      }

      @Override
      public Attachment.AdvancedPaymentSubscriptionCancel parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return new Attachment.AdvancedPaymentSubscriptionCancel(buffer, transactionVersion);
      }

      @Override
      Attachment.AdvancedPaymentSubscriptionCancel parseAttachment(JsonObject attachmentData) {
        return new Attachment.AdvancedPaymentSubscriptionCancel(attachmentData);
      }

      @Override
      final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        logger.trace("TransactionType SUBSCRIPTION_CANCEL");
        Attachment.AdvancedPaymentSubscriptionCancel attachment = (Attachment.AdvancedPaymentSubscriptionCancel) transaction.getAttachment();
        subscriptionService.addRemoval(attachment.getSubscriptionId());
        return true;
      }

      @Override
      final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.AdvancedPaymentSubscriptionCancel attachment = (Attachment.AdvancedPaymentSubscriptionCancel) transaction.getAttachment();
        subscriptionService.removeSubscription(attachment.getSubscriptionId());
      }

      @Override
      final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        // Nothing to undo.
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        Attachment.AdvancedPaymentSubscriptionCancel attachment = (Attachment.AdvancedPaymentSubscriptionCancel) transaction.getAttachment();
        return new TransactionDuplicationKey(AdvancedPayment.SUBSCRIPTION_CANCEL, Convert.toUnsignedLong(attachment.getSubscriptionId()));
      }

      @Override
      void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
        Attachment.AdvancedPaymentSubscriptionCancel attachment = (Attachment.AdvancedPaymentSubscriptionCancel) transaction.getAttachment();
        if (attachment.getSubscriptionId() == null) {
          throw new BurstException.NotValidException("Subscription cancel must include subscription id");
        }

        Subscription subscription = subscriptionService.getSubscription(attachment.getSubscriptionId());
        if (subscription == null) {
          throw new BurstException.NotValidException("Subscription cancel must contain current subscription id");
        }

        if (!subscription.getSenderId().equals(transaction.getSenderId()) &&
                !subscription.getRecipientId().equals(transaction.getSenderId())) {
          throw new BurstException.NotValidException("Subscription cancel can only be done by participants");
        }

        if (!subscriptionService.isEnabled()) {
          throw new BurstException.NotYetEnabledException("Subscription cancel not yet enabled");
        }
      }

      @Override
      public final boolean hasRecipient() {
        return false;
      }
    };

    public static final TransactionType SUBSCRIPTION_PAYMENT = new AdvancedPayment() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_PAYMENT;
      }

      @Override
      public String getDescription() {
        return "Subscription Payment";
      }

      @Override
      public Attachment.AdvancedPaymentSubscriptionPayment parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return new Attachment.AdvancedPaymentSubscriptionPayment(buffer, transactionVersion);
      }

      @Override
      Attachment.AdvancedPaymentSubscriptionPayment parseAttachment(JsonObject attachmentData) {
        return new Attachment.AdvancedPaymentSubscriptionPayment(attachmentData);
      }

      @Override
      final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return false;
      }

      @Override
      final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        // Nothing to apply.
      }

      @Override
      final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        // Nothing to undo.
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        return TransactionDuplicationKey.IS_ALWAYS_DUPLICATE;
      }

      @Override
      void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
        throw new BurstException.NotValidException("Subscription payment never validates");
      }

      @Override
      public final boolean hasRecipient() {
        return true;
      }

      @Override
      public final boolean isSigned() {
        return false;
      }
    };
  }

  public abstract static class AutomatedTransactions extends TransactionType{
    private AutomatedTransactions() {

    }

    @Override
    public final byte getType(){
      return TransactionType.TYPE_AUTOMATED_TRANSACTIONS;
    }

    @Override
    boolean applyAttachmentUnconfirmed(Transaction transaction,Account senderAccount){
      return true;
    }

    @Override
    void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount){

    }

    @Override
    final void validateAttachment(Transaction transaction) throws BurstException.ValidationException {
      if (transaction.getAmountNQT() != 0) {
        throw new BurstException.NotValidException("Invalid automated transaction transaction");
      }
      doValidateAttachment(transaction);
    }

    abstract void doValidateAttachment(Transaction transaction) throws BurstException.ValidationException;


    public static final TransactionType AUTOMATED_TRANSACTION_CREATION = new AutomatedTransactions(){

      @Override
      public byte getSubtype() {
        return TransactionType.SUBTYPE_AT_CREATION;
      }

      @Override
      public String getDescription() {
        return "AT Creation";
      }

      @Override
      public AbstractAttachment parseAttachment(ByteBuffer buffer,
                                                byte transactionVersion) throws NotValidException {
        return new AutomatedTransactionsCreation(buffer,transactionVersion);
      }

      @Override
      AbstractAttachment parseAttachment(JsonObject attachmentData) {
        return new AutomatedTransactionsCreation(attachmentData);
      }

      @Override
      void doValidateAttachment(Transaction transaction)
              throws ValidationException {
        if (! fluxCapacitor.getValue(FluxValues.AUTOMATED_TRANSACTION_BLOCK, blockchain.getLastBlock().getHeight())) {
          throw new BurstException.NotYetEnabledException("Automated Transactions not yet enabled at height " + blockchain.getLastBlock().getHeight());
        }
        if (transaction.getSignature() != null && accountService.getAccount(transaction.getId()) != null) {
          Account existingAccount = accountService.getAccount(transaction.getId());
          if (existingAccount.getPublicKey() != null && !Arrays.equals(existingAccount.getPublicKey(), new byte[32]))
            throw new BurstException.NotValidException("Account with id already exists");
        }
        Attachment.AutomatedTransactionsCreation attachment = (Attachment.AutomatedTransactionsCreation) transaction.getAttachment();
        long totalPages;
        try {
          totalPages = AtController.checkCreationBytes(attachment.getCreationBytes(), blockchain.getHeight());
        }
        catch (AtException e) {
          throw new BurstException.NotCurrentlyValidException("Invalid AT creation bytes", e);
        }
        long requiredFee = totalPages * AtConstants.getInstance().costPerPage( transaction.getHeight() );
        if (transaction.getFeeNQT() <  requiredFee){
          throw new BurstException.NotValidException("Insufficient fee for AT creation. Minimum: " + Convert.toUnsignedLong(requiredFee / Constants.ONE_BURST));
        }
        if (fluxCapacitor.getValue(FluxValues.AT_FIX_BLOCK_3)) {
          if (attachment.getName().length() > Constants.MAX_AUTOMATED_TRANSACTION_NAME_LENGTH) {
            throw new BurstException.NotValidException("Name of automated transaction over size limit");
          }
          if (attachment.getDescription().length() > Constants.MAX_AUTOMATED_TRANSACTION_DESCRIPTION_LENGTH) {
            throw new BurstException.NotValidException("Description of automated transaction over size limit");
          }
        }
      }

      @Override
      void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.AutomatedTransactionsCreation attachment = (Attachment.AutomatedTransactionsCreation) transaction.getAttachment();
        AT.addAT( transaction.getId() , transaction.getSenderId() , attachment.getName() , attachment.getDescription() , attachment.getCreationBytes() , transaction.getHeight() );
      }


      @Override
      public boolean hasRecipient() {
        return false;
      }
    };

    public static final TransactionType AT_PAYMENT = new AutomatedTransactions() {
      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_AT_NXT_PAYMENT;
      }

      @Override
      public String getDescription() {
        return "AT Payment";
      }

      @Override
      public AbstractAttachment parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return Attachment.AT_PAYMENT;
      }

      @Override
      AbstractAttachment parseAttachment(JsonObject attachmentData) {
        return Attachment.AT_PAYMENT;
      }

      @Override
      void doValidateAttachment(Transaction transaction) throws BurstException.ValidationException {
        throw new BurstException.NotValidException("AT payment never validates");
      }

      @Override
      void applyAttachment(Transaction transaction,
                           Account senderAccount, Account recipientAccount) {
        // Nothing to apply
      }


      @Override
      public boolean hasRecipient() {
        return true;
      }

      @Override
      public final boolean isSigned() {
        return false;
      }
    };

  }

  public long minimumFeeNQT(int height, int appendagesSize) {
    if (height < BASELINE_FEE_HEIGHT) {
      return 0; // No need to validate fees before baseline block
    }
    Fee fee = getBaselineFee(height);
    return Convert.safeAdd(fee.getConstantFee(), Convert.safeMultiply(appendagesSize, fee.getAppendagesFee()));
  }

  protected Fee getBaselineFee(int height) {
    return new Fee((fluxCapacitor.getValue(FluxValues.PRE_DYMAXION, height) ? FEE_QUANT : ONE_BURST), 0);
  }

  public static final class Fee {
    private final long constantFee;
    private final long appendagesFee;

    Fee(long constantFee, long appendagesFee) {
      this.constantFee = constantFee;
      this.appendagesFee = appendagesFee;
    }

    long getConstantFee() {
      return constantFee;
    }

    long getAppendagesFee() {
      return appendagesFee;
    }

    @Override
    public String toString() {
      return "Fee{" +
              "constantFee=" + constantFee +
              ", appendagesFee=" + appendagesFee +
              '}';
    }
  }

}
