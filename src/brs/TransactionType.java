package brs;

import brs.Account.AccountAsset;
import brs.Attachment.AbstractAttachment;
import brs.Attachment.AutomatedTransactionsCreation;
import brs.Attachment.CommitmentAdd;
import brs.Attachment.CommitmentRemove;
import brs.SignumException.NotValidException;
import brs.SignumException.ValidationException;
import brs.assetexchange.AssetExchange;
import brs.at.AT;
import brs.at.AtConstants;
import brs.at.AtController;
import brs.at.AtException;
import brs.at.AtMachineState;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.props.Props;
import brs.services.*;
import brs.transactionduplicates.TransactionDuplicationKey;
import brs.util.CollectionWithIndex;
import brs.util.Convert;
import brs.util.JSON;
import brs.util.TextUtils;
import signum.net.NetworkParameters;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;

import static brs.Constants.ONE_SIGNA;

public abstract class TransactionType {

  private static final Logger logger = LoggerFactory.getLogger(TransactionType.class);

  private static final Map<Type, Map<Byte, TransactionType>> TRANSACTION_TYPES = new HashMap<>();

  public static final Type TYPE_PAYMENT = new Type((byte)0, "Payment");
  public static final Type TYPE_MESSAGING = new Type((byte)1, "Messaging");
  public static final Type TYPE_COLORED_COINS = new Type((byte)2, "Colored coins");
  public static final Type TYPE_DIGITAL_GOODS = new Type((byte)3, "Digital Goods");
  public static final Type TYPE_ACCOUNT_CONTROL = new Type((byte)4, "Account Control");
  public static final Type TYPE_SIGNA_MINING = new Type((byte)20, "Mining");
  public static final Type TYPE_ADVANCED_PAYMENT = new Type((byte)21, "Advanced Payment");
  public static final Type TYPE_AUTOMATED_TRANSACTIONS = new Type((byte)22, "Automated Transactions");

  public static final byte SUBTYPE_PAYMENT_ORDINARY_PAYMENT = 0;
  public static final byte SUBTYPE_PAYMENT_ORDINARY_PAYMENT_MULTI_OUT = 1;
  public static final byte SUBTYPE_PAYMENT_ORDINARY_PAYMENT_MULTI_SAME_OUT = 2;

  public static final byte SUBTYPE_MESSAGING_ARBITRARY_MESSAGE = 0;
  public static final byte SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT = 1;
  public static final byte SUBTYPE_MESSAGING_ACCOUNT_INFO = 5;
  public static final byte SUBTYPE_MESSAGING_ALIAS_SELL = 6;
  public static final byte SUBTYPE_MESSAGING_ALIAS_BUY = 7;
  public static final byte SUBTYPE_MESSAGING_TLD_ASSIGNMENT = 8;

  public static final byte SUBTYPE_COLORED_COINS_ASSET_ISSUANCE = 0;
  public static final byte SUBTYPE_COLORED_COINS_ASSET_TRANSFER = 1;
  public static final byte SUBTYPE_COLORED_COINS_ASK_ORDER_PLACEMENT = 2;
  public static final byte SUBTYPE_COLORED_COINS_BID_ORDER_PLACEMENT = 3;
  public static final byte SUBTYPE_COLORED_COINS_ASK_ORDER_CANCELLATION = 4;
  public static final byte SUBTYPE_COLORED_COINS_BID_ORDER_CANCELLATION = 5;
  public static final byte SUBTYPE_COLORED_COINS_ASSET_MINT = 6;
  public static final byte SUBTYPE_COLORED_COINS_ADD_TREASURY_ACCOUNT = 7;
  public static final byte SUBTYPE_COLORED_COINS_DISTRIBUTE_TO_HOLDERS = 8;
  public static final byte SUBTYPE_COLORED_COINS_ASSET_MULTI_TRANSFER = 9;
  public static final byte SUBTYPE_COLORED_COINS_TRANSFER_OWNERSHIP = 10;

  public static final byte SUBTYPE_DIGITAL_GOODS_LISTING = 0;
  public static final byte SUBTYPE_DIGITAL_GOODS_DELISTING = 1;
  public static final byte SUBTYPE_DIGITAL_GOODS_PRICE_CHANGE = 2;
  public static final byte SUBTYPE_DIGITAL_GOODS_QUANTITY_CHANGE = 3;
  public static final byte SUBTYPE_DIGITAL_GOODS_PURCHASE = 4;
  public static final byte SUBTYPE_DIGITAL_GOODS_DELIVERY = 5;
  public static final byte SUBTYPE_DIGITAL_GOODS_FEEDBACK = 6;
  public static final byte SUBTYPE_DIGITAL_GOODS_REFUND = 7;

  public static final byte SUBTYPE_AT_CREATION = 0;
  public static final byte SUBTYPE_AT_NXT_PAYMENT = 1;

  public static final byte SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING = 0;

  public static final byte SUBTYPE_SIGNA_MINING_REWARD_RECIPIENT_ASSIGNMENT = 0;
  public static final byte SUBTYPE_SIGNA_MINING_COMMITMENT_ADD = 1;
  public static final byte SUBTYPE_SIGNA_MINING_COMMITMENT_REMOVE = 2;

  public static final byte SUBTYPE_ADVANCED_PAYMENT_ESCROW_CREATION = 0;
  public static final byte SUBTYPE_ADVANCED_PAYMENT_ESCROW_SIGN = 1;
  public static final byte SUBTYPE_ADVANCED_PAYMENT_ESCROW_RESULT = 2;
  public static final byte SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_SUBSCRIBE = 3;
  public static final byte SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_CANCEL = 4;
  public static final byte SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_PAYMENT = 5;

  private static final int BASELINE_FEE_HEIGHT = 1; // At release time must be less than current block - 1440
  private static final Fee BASELINE_ASSET_ISSUANCE_FEE = new Fee(Constants.ASSET_ISSUANCE_FEE_NQT, 0);

  public static final long BASELINE_ASSET_ISSUANCE_FACTOR = 15_000L;
  private static final long BASELINE_ALIAS_ASSIGNMENT_FACTOR = 20L;
  public static final long BASELINE_TLD_ASSIGNMENT_FACTOR = 10_000_000L;
  public static final long BASELINE_ALIAS_RENEWAL_FACTOR = 1250L;

  private static Blockchain blockchain;
  private static FluxCapacitor fluxCapacitor;
  private static AccountService accountService;
  private static DGSGoodsStoreService dgsGoodsStoreService;
  private static AliasService aliasService;
  private static AssetExchange assetExchange;
  private static SubscriptionService subscriptionService;
  private static EscrowService escrowService;

  public static class Type {
    private byte type;
    private String description;

    public Type(byte type, String description) {
      this.type = type;
      this.description = description;
    }

    public byte getType() {
      return type;
    }

    public String getDescription() {
      return description;
    }
  }

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
    messagingTypes.put(SUBTYPE_MESSAGING_TLD_ASSIGNMENT, Messaging.TLD_ASSIGNMENT);

    Map<Byte, TransactionType> coloredCoinsTypes = new HashMap<>();
    coloredCoinsTypes.put(SUBTYPE_COLORED_COINS_ASSET_ISSUANCE, ColoredCoins.ASSET_ISSUANCE);
    coloredCoinsTypes.put(SUBTYPE_COLORED_COINS_ASSET_TRANSFER, ColoredCoins.ASSET_TRANSFER);
    coloredCoinsTypes.put(SUBTYPE_COLORED_COINS_ASK_ORDER_PLACEMENT, ColoredCoins.ASK_ORDER_PLACEMENT);
    coloredCoinsTypes.put(SUBTYPE_COLORED_COINS_BID_ORDER_PLACEMENT, ColoredCoins.BID_ORDER_PLACEMENT);
    coloredCoinsTypes.put(SUBTYPE_COLORED_COINS_ASK_ORDER_CANCELLATION, ColoredCoins.ASK_ORDER_CANCELLATION);
    coloredCoinsTypes.put(SUBTYPE_COLORED_COINS_BID_ORDER_CANCELLATION, ColoredCoins.BID_ORDER_CANCELLATION);
    coloredCoinsTypes.put(SUBTYPE_COLORED_COINS_ASSET_MINT, ColoredCoins.ASSET_MINT);
    coloredCoinsTypes.put(SUBTYPE_COLORED_COINS_ADD_TREASURY_ACCOUNT, ColoredCoins.ASSET_ADD_TREASURY_ACCOUNT);
    coloredCoinsTypes.put(SUBTYPE_COLORED_COINS_DISTRIBUTE_TO_HOLDERS, ColoredCoins.ASSET_DISTRIBUTE_TO_HOLDERS);
    coloredCoinsTypes.put(SUBTYPE_COLORED_COINS_ASSET_MULTI_TRANSFER, ColoredCoins.ASSET_MULTI_TRANSFER);
    coloredCoinsTypes.put(SUBTYPE_COLORED_COINS_TRANSFER_OWNERSHIP, ColoredCoins.ASSET_TRANSFER_OWNERSHIP);

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

    Map<Byte, TransactionType> signumMiningTypes = new HashMap<>();
    signumMiningTypes.put(SUBTYPE_SIGNA_MINING_REWARD_RECIPIENT_ASSIGNMENT, SignaMining.REWARD_RECIPIENT_ASSIGNMENT);
    signumMiningTypes.put(SUBTYPE_SIGNA_MINING_COMMITMENT_ADD, SignaMining.COMMITMENT_ADD);
    signumMiningTypes.put(SUBTYPE_SIGNA_MINING_COMMITMENT_REMOVE, SignaMining.COMMITMENT_REMOVE);

    Map<Byte, TransactionType> advancedPaymentTypes = new HashMap<>();
    advancedPaymentTypes.put(SUBTYPE_ADVANCED_PAYMENT_ESCROW_CREATION, AdvancedPayment.ESCROW_CREATION);
    advancedPaymentTypes.put(SUBTYPE_ADVANCED_PAYMENT_ESCROW_SIGN, AdvancedPayment.ESCROW_SIGN);
    advancedPaymentTypes.put(SUBTYPE_ADVANCED_PAYMENT_ESCROW_RESULT, AdvancedPayment.ESCROW_RESULT);
    advancedPaymentTypes.put(SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_SUBSCRIBE, AdvancedPayment.SUBSCRIPTION_SUBSCRIBE);
    advancedPaymentTypes.put(SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_CANCEL, AdvancedPayment.SUBSCRIPTION_CANCEL);
    advancedPaymentTypes.put(SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_PAYMENT, AdvancedPayment.SUBSCRIPTION_PAYMENT);

    TRANSACTION_TYPES.put(TYPE_PAYMENT, paymentTypes);
    TRANSACTION_TYPES.put(TYPE_MESSAGING, messagingTypes);
    TRANSACTION_TYPES.put(TYPE_COLORED_COINS, coloredCoinsTypes);
    TRANSACTION_TYPES.put(TYPE_DIGITAL_GOODS, digitalGoodsTypes);
    TRANSACTION_TYPES.put(TYPE_ACCOUNT_CONTROL, accountControlTypes);
    TRANSACTION_TYPES.put(TYPE_SIGNA_MINING, signumMiningTypes);
    TRANSACTION_TYPES.put(TYPE_ADVANCED_PAYMENT, advancedPaymentTypes);
    TRANSACTION_TYPES.put(TYPE_AUTOMATED_TRANSACTIONS, atTypes);
  }

  public static void setNetworkParameters(NetworkParameters params) {
    params.adjustTransactionTypes(TRANSACTION_TYPES);
  }

  public static TransactionType findTransactionType(byte type, byte subtype) {
    for(Type t : TRANSACTION_TYPES.keySet()) {
      if(t.getType() == type) {
        Map<Byte, TransactionType> subtypes = TRANSACTION_TYPES.get(t);
        return subtypes == null ? null : subtypes.get(subtype);
      }
    }
    return null;
  }

  public static Map<Type, Map<Byte, TransactionType>> getTransactionTypes() {
    return Collections.unmodifiableMap(TRANSACTION_TYPES);
  }

  protected TransactionType() {
  }

  public abstract byte getType();

  public abstract byte getSubtype();

  public abstract String getDescription();

  public abstract Attachment.AbstractAttachment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SignumException.NotValidException;

  protected abstract Attachment.AbstractAttachment parseAttachment(JsonObject attachmentData) throws SignumException.NotValidException;

  protected abstract void validateAttachment(Transaction transaction) throws SignumException.ValidationException;

  // return false if double spending
  public final boolean applyUnconfirmed(Transaction transaction, Account senderAccount) {
    long totalAmountNQT = calculateTransactionAmountNQT(transaction);
    if (logger.isTraceEnabled()) {
      logger.trace("applyUnconfirmed: {} < totalamount: {} = false", senderAccount.getUnconfirmedBalanceNqt(), totalAmountNQT);
    }
    if (senderAccount.getUnconfirmedBalanceNqt() < totalAmountNQT) {
      return false;
    }
    accountService.addToUnconfirmedBalanceNQT(senderAccount, -totalAmountNQT);
    if (!applyAttachmentUnconfirmed(transaction, senderAccount)) {
      if (logger.isDebugEnabled()) {
        logger.debug("!applyAttachmentUnconfirmed({}, {})", transaction, senderAccount.getId());
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
    long totalAmountNQT = Convert.safeAdd(transaction.getAmountNqt(), transaction.getFeeNqt());
    if (transaction.getReferencedTransactionFullHash() != null &&
        !Signum.getFluxCapacitor().getValue(FluxValues.SIGNUM, transaction.getHeight()) ) {
      totalAmountNQT = Convert.safeAdd(totalAmountNQT, Constants.UNCONFIRMED_POOL_DEPOSIT_NQT);
    }
    return totalAmountNQT;
  }

  protected Long calculateAttachmentTotalAmountNQT(Transaction transaction) {
    return 0L;
  }

  protected abstract boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount);

  final void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
    accountService.addToBalanceNQT(senderAccount, - (Convert.safeAdd(transaction.getAmountNqt(), transaction.getFeeNqt())));
    if (transaction.getReferencedTransactionFullHash() != null &&
        !Signum.getFluxCapacitor().getValue(FluxValues.SIGNUM, transaction.getHeight())) {
      accountService.addToUnconfirmedBalanceNQT(senderAccount, Constants.UNCONFIRMED_POOL_DEPOSIT_NQT);
    }
    if (recipientAccount != null && transaction.getAmountNqt() > 0L && !transaction.getType().isIndirect() ) {
      accountService.addToBalanceAndUnconfirmedBalanceNQT(recipientAccount, transaction.getAmountNqt());
    }
    if (Signum.getFluxCapacitor().getValue(FluxValues.SMART_FEES, transaction.getHeight())) {
      Account cashBackAccount = accountService.getOrAddAccount(transaction.getCashBackId());
      long cashBackAmountNQT = transaction.getFeeNqt() / Signum.getPropertyService().getInt(Props.CASH_BACK_FACTOR);
      accountService.addToBalanceAndUnconfirmedBalanceNQT(cashBackAccount, cashBackAmountNQT);
    }
    if (logger.isTraceEnabled()) {
      logger.trace("applying transaction - id: {}, type: {}", transaction.getId(), transaction.getType());
    }
    applyAttachment(transaction, senderAccount, recipientAccount);
  }

  protected abstract void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount);

  public void parseAppendices(Transaction.Builder builder, JsonObject attachmentData) {
    builder.message(Appendix.Message.parse(attachmentData));
    builder.encryptedMessage(Appendix.EncryptedMessage.parse(attachmentData));
    builder.publicKeyAnnouncement((Appendix.PublicKeyAnnouncement.parse(attachmentData)));
    builder.encryptToSelfMessage(Appendix.EncryptToSelfMessage.parse(attachmentData));
  }

  public void parseAppendices(Transaction.Builder builder, int flags, byte version, ByteBuffer buffer) throws SignumException.ValidationException {
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
    accountService.addToUnconfirmedBalanceNQT(senderAccount, Convert.safeAdd(transaction.getAmountNqt(), transaction.getFeeNqt()));
  }

  protected abstract void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount);

  public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
    return TransactionDuplicationKey.IS_NEVER_DUPLICATE;
  }

  public abstract boolean hasRecipient();

  public boolean isIndirect() {
    return false;
  }

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
      return TransactionType.TYPE_PAYMENT.getType();
    }

    @Override
    protected boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
      return true;
    }

    @Override
    protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
    }

    @Override
    protected void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
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
      protected Attachment.EmptyAttachment parseAttachment(JsonObject attachmentData) {
        return Attachment.ORDINARY_PAYMENT;
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        if (transaction.getAmountNqt() <= 0 || transaction.getAmountNqt() >= Constants.MAX_BALANCE_NQT) {
          throw new SignumException.NotValidException("Invalid ordinary payment");
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
      public Attachment.PaymentMultiOutCreation parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SignumException.NotValidException {
        return new Attachment.PaymentMultiOutCreation(buffer, transactionVersion);
      }

      @Override
      protected Attachment.PaymentMultiOutCreation parseAttachment(JsonObject attachmentData) throws SignumException.NotValidException {
        return new Attachment.PaymentMultiOutCreation(attachmentData);
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        if (!fluxCapacitor.getValue(FluxValues.PRE_POC2, transaction.getHeight())) {
          throw new SignumException.NotCurrentlyValidException("Multi Out Payments are not allowed before the Pre POC2 block");
        }

        Attachment.PaymentMultiOutCreation attachment = (Attachment.PaymentMultiOutCreation) transaction.getAttachment();
        Long amountNQT = attachment.getAmountNqt();
        if (amountNQT <= 0
                || amountNQT >= Constants.MAX_BALANCE_NQT
                || amountNQT != transaction.getAmountNqt()
                || attachment.getRecipients().size() < 2) {
          throw new SignumException.NotValidException("Invalid multi out payment");
        }
      }

      @Override
      protected final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
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
      public boolean isIndirect() {
        return true;
      }

      @Override
      public Collection<IndirectIncoming> getIndirectIncomings(Transaction transaction) {
        Attachment.PaymentMultiOutCreation attachment = (Attachment.PaymentMultiOutCreation) transaction.getAttachment();

        ArrayList<IndirectIncoming> indirects = new ArrayList<IndirectIncoming>(attachment.getRecipients().size());
        for(List<Long> recipient : attachment.getRecipients()) {
          indirects.add(new IndirectIncoming(recipient.get(0), transaction.getId(),
              recipient.get(1), 0, transaction.getHeight()));
        }
        return indirects;
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
      public Attachment.PaymentMultiSameOutCreation parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SignumException.NotValidException {
        return new Attachment.PaymentMultiSameOutCreation(buffer, transactionVersion);
      }

      @Override
      protected Attachment.PaymentMultiSameOutCreation parseAttachment(JsonObject attachmentData) throws SignumException.NotValidException {
        return new Attachment.PaymentMultiSameOutCreation(attachmentData);
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        if (!fluxCapacitor.getValue(FluxValues.PRE_POC2, transaction.getHeight())) {
          throw new SignumException.NotCurrentlyValidException("Multi Same Out Payments are not allowed before the Pre POC2 block");
        }

        Attachment.PaymentMultiSameOutCreation attachment = (Attachment.PaymentMultiSameOutCreation) transaction.getAttachment();
        if (attachment.getRecipients().size() < 2 && (transaction.getAmountNqt() % attachment.getRecipients().size() == 0 ) ) {
          throw new SignumException.NotValidException("Invalid multi out payment");
        }
      }

      @Override
      protected final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.PaymentMultiSameOutCreation attachment = (Attachment.PaymentMultiSameOutCreation) transaction.getAttachment();
        final long amountNQT = Convert.safeDivide(transaction.getAmountNqt(), attachment.getRecipients().size());
        attachment.getRecipients().forEach(a -> accountService.addToBalanceAndUnconfirmedBalanceNQT(accountService.getOrAddAccount(a), amountNQT));
      }

      @Override
      public final boolean hasRecipient() {
        return false;
      }

      @Override
      public boolean isIndirect() {
        return true;
      }

      @Override
      public Collection<IndirectIncoming> getIndirectIncomings(Transaction transaction) {
        Attachment.PaymentMultiSameOutCreation attachment = (Attachment.PaymentMultiSameOutCreation) transaction.getAttachment();
        long amount = transaction.getAmountNqt() / attachment.getRecipients().size();
        ArrayList<IndirectIncoming> indirects = new ArrayList<IndirectIncoming>(attachment.getRecipients().size());
        for(Long recipient : attachment.getRecipients()) {
          indirects.add(new IndirectIncoming(recipient, transaction.getId(),
                    amount, 0, transaction.getHeight()));
        }
        return indirects;
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

    protected Messaging() {
    }

    @Override
    public final byte getType() {
      return TransactionType.TYPE_MESSAGING.getType();
    }

    @Override
    protected final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
      return true;
    }

    @Override
    protected final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
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
      protected Attachment.EmptyAttachment parseAttachment(JsonObject attachmentData) {
        return Attachment.ARBITRARY_MESSAGE;
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        // No appendices
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment attachment = transaction.getAttachment();
        if (transaction.getAmountNqt() != 0) {
          throw new SignumException.NotValidException("Invalid arbitrary message: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (! fluxCapacitor.getValue(FluxValues.DIGITAL_GOODS_STORE) && transaction.getMessage() == null) {
          throw new SignumException.NotCurrentlyValidException("Missing message appendix not allowed before DGS block");
        }
      }

      @Override
      public boolean hasRecipient() {
        return true;
      }

      @Override
      public void parseAppendices(Transaction.Builder builder, int flags, byte version, ByteBuffer buffer) throws SignumException.ValidationException {
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
      public Fee getBaselineFee(int height) {
        return fluxCapacitor.getValue(FluxValues.SPEEDWAY, height) ?
            new Fee(fluxCapacitor.getValue(FluxValues.FEE_QUANT, height) * BASELINE_ALIAS_ASSIGNMENT_FACTOR, 0) :
            super.getBaselineFee(height);
      }

      @Override
      public Attachment.MessagingAliasAssignment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SignumException.NotValidException {
        return new Attachment.MessagingAliasAssignment(buffer, transactionVersion);
      }

      @Override
      protected Attachment.MessagingAliasAssignment parseAttachment(JsonObject attachmentData) {
        return new Attachment.MessagingAliasAssignment(attachmentData);
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.MessagingAliasAssignment attachment = (Attachment.MessagingAliasAssignment) transaction.getAttachment();
        aliasService.addOrUpdateAlias(transaction, attachment);
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        Attachment.MessagingAliasAssignment attachment = (Attachment.MessagingAliasAssignment) transaction.getAttachment();
        return new TransactionDuplicationKey(Messaging.ALIAS_ASSIGNMENT, attachment.getAliasName().toLowerCase(Locale.ENGLISH)
                + (attachment.getVersion() > 1 ? Convert.toUnsignedLong(attachment.getTld()) : ""));
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.MessagingAliasAssignment attachment = (Attachment.MessagingAliasAssignment) transaction.getAttachment();
        if (attachment.getAliasName().isEmpty()
                || Convert.toBytes(attachment.getAliasName()).length > Constants.MAX_ALIAS_LENGTH
                || attachment.getAliasUri().length() > Constants.MAX_ALIAS_URI_LENGTH) {
          throw new SignumException.NotValidException("Invalid alias assignment: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (Signum.getFluxCapacitor().getValue(FluxValues.SMART_ALIASES)) {
          if (!TextUtils.isInAlphabetOrUnderline(attachment.getAliasName())) {
            throw new SignumException.NotValidException("Invalid alias name: " + attachment.getAliasName());
          }
        }
        else{
          if (!TextUtils.isInAlphabet(attachment.getAliasName())) {
            throw new SignumException.NotValidException("Invalid alias name: " + attachment.getAliasName());
          }
        }
        Alias alias = aliasService.getAlias(attachment.getAliasName(), attachment.getTld());
        if (alias != null && alias.getAccountId() != transaction.getSenderId()) {
          throw new SignumException.NotCurrentlyValidException("Alias already owned by another account: " + attachment.getAliasName());
        }
      }

      @Override
      public boolean hasRecipient() {
        return false;
      }

    };

    public static final TransactionType TLD_ASSIGNMENT = new Messaging() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_MESSAGING_TLD_ASSIGNMENT;
      }

      @Override
      public String getDescription() {
        return "TLD Assignment";
      }
      
      @Override
      public boolean isSigned() {
        return super.isSigned();
      }

      @Override
      public Attachment.MessagingTldAssignment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SignumException.NotValidException {
        return new Attachment.MessagingTldAssignment(buffer, transactionVersion);
      }

      @Override
      protected Attachment.MessagingTldAssignment parseAttachment(JsonObject attachmentData) {
        return new Attachment.MessagingTldAssignment(attachmentData);
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.MessagingTldAssignment attachment = (Attachment.MessagingTldAssignment) transaction.getAttachment();
        aliasService.addTLD(transaction.getId(), transaction, attachment);
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        Attachment.MessagingTldAssignment attachment = (Attachment.MessagingTldAssignment) transaction.getAttachment();
        return new TransactionDuplicationKey(Messaging.TLD_ASSIGNMENT, attachment.getTldName().toLowerCase(Locale.ENGLISH));
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        if(!fluxCapacitor.getValue(FluxValues.SMART_ALIASES, blockchain.getLastBlock().getHeight())) {
          throw new SignumException.NotCurrentlyValidException("Smart Alias not yet active");
        }
        Attachment.MessagingTldAssignment attachment = (Attachment.MessagingTldAssignment) transaction.getAttachment();
        if (attachment.getTldName().isEmpty()
                || Convert.toBytes(attachment.getTldName()).length > Constants.MAX_TLD_LENGTH) {
          throw new SignumException.NotValidException("Invalid TLD assignment: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (!TextUtils.isInAlphabet(attachment.getTldName())) {
          throw new SignumException.NotValidException("Invalid TLD name: " + attachment.getTldName());
        }
        Alias tld = aliasService.getTLD(attachment.getTldName());
        if (tld != null) {
          throw new SignumException.NotCurrentlyValidException("TLD already registered by another account: " + attachment.getTldName());
        }
        
        if(transaction.getRecipientId() != 0L
                || transaction.getAmountNqt() < BASELINE_TLD_ASSIGNMENT_FACTOR * fluxCapacitor.getValue(FluxValues.FEE_QUANT, blockchain.getLastBlock().getHeight())) {
          throw new SignumException.NotCurrentlyValidException("Invalid TLD assignment: " + attachment.getTldName());
        }
      }

      @Override
      public boolean hasRecipient() {
        return true;
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
      public Attachment.MessagingAliasSell parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SignumException.NotValidException {
        return new Attachment.MessagingAliasSell(buffer, transactionVersion);
      }

      @Override
      protected Attachment.MessagingAliasSell parseAttachment(JsonObject attachmentData) {
        return new Attachment.MessagingAliasSell(attachmentData);
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        final Attachment.MessagingAliasSell attachment =
                (Attachment.MessagingAliasSell) transaction.getAttachment();
        aliasService.sellAlias(transaction, attachment);
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        Attachment.MessagingAliasSell attachment = (Attachment.MessagingAliasSell) transaction.getAttachment();
        // not a bug, uniqueness is based on Messaging.ALIAS_ASSIGNMENT
        return new TransactionDuplicationKey(Messaging.ALIAS_ASSIGNMENT, attachment.getVersion() > 1 ? Convert.toUnsignedLong(attachment.getAliasId()) : attachment.getAliasName().toLowerCase(Locale.ENGLISH));
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        if (! fluxCapacitor.getValue(FluxValues.DIGITAL_GOODS_STORE, blockchain.getLastBlock().getHeight())) {
          throw new SignumException.NotYetEnabledException("Alias transfer not yet enabled at height " + blockchain.getLastBlock().getHeight());
        }
        if (transaction.getAmountNqt() != 0) {
          throw new SignumException.NotValidException("Invalid sell alias transaction: " + JSON.toJsonString(transaction.getJsonObject()));
        }
        final Attachment.MessagingAliasSell attachment =
                (Attachment.MessagingAliasSell) transaction.getAttachment();
        if(attachment.getVersion() > 1 && !fluxCapacitor.getValue(FluxValues.SMART_ALIASES)) {
          throw new SignumException.NotCurrentlyValidException("Attachment version mismatch");
        }
        if(attachment.getVersion() <= 1) {
          final String aliasName = attachment.getAliasName();
          if (aliasName == null || aliasName.isEmpty()) {
            throw new SignumException.NotValidException("Missing alias name");
          }
        }
        else if(attachment.getAliasId() == 0L) {
          throw new SignumException.NotValidException("Missing alias Id");
        }
        long priceNQT = attachment.getPriceNqt();
        if (priceNQT < 0 || priceNQT > Constants.MAX_BALANCE_NQT) {
          throw new SignumException.NotValidException("Invalid alias sell price: " + priceNQT);
        }
        if (priceNQT == 0) {
          if (Genesis.CREATOR_ID == transaction.getRecipientId()) {
            throw new SignumException.NotValidException("Transferring aliases to Genesis account not allowed");
          } else if (transaction.getRecipientId() == 0) {
            throw new SignumException.NotValidException("Missing alias transfer recipient");
          }
        }
        final Alias alias = attachment.getVersion() > 1 ? aliasService.getAlias(attachment.getAliasId()) : aliasService.getAlias(attachment.getAliasName(), 0L);
        if (alias == null) {
          throw new SignumException.NotCurrentlyValidException("Alias hasn't been registered yet");
        } else if (alias.getAccountId() != transaction.getSenderId()) {
          throw new SignumException.NotCurrentlyValidException("Alias doesn't belong to sender: " + alias.getAliasName());
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
      public Attachment.MessagingAliasBuy parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SignumException.NotValidException {
        return new Attachment.MessagingAliasBuy(buffer, transactionVersion);
      }

      @Override
      protected Attachment.MessagingAliasBuy parseAttachment(JsonObject attachmentData) {
        return new Attachment.MessagingAliasBuy(attachmentData);
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        final Attachment.MessagingAliasBuy attachment =
                (Attachment.MessagingAliasBuy) transaction.getAttachment();
        final Alias alias = attachment.getVersion() > 1 ? aliasService.getAlias(attachment.getAliasId()) : aliasService.getAlias(attachment.getAliasName(), 0L);
        aliasService.changeOwner(transaction.getSenderId(), alias, transaction.getBlockTimestamp(), true);
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        Attachment.MessagingAliasBuy attachment = (Attachment.MessagingAliasBuy) transaction.getAttachment();
        // not a bug, uniqueness is based on Messaging.ALIAS_ASSIGNMENT
        return new TransactionDuplicationKey(Messaging.ALIAS_ASSIGNMENT, attachment.getVersion() > 1 ? Convert.toUnsignedLong(attachment.getAliasId()) : attachment.getAliasName().toLowerCase(Locale.ENGLISH));
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        if (! fluxCapacitor.getValue(FluxValues.DIGITAL_GOODS_STORE, blockchain.getLastBlock().getHeight())) {
          throw new SignumException.NotYetEnabledException("Alias transfer not yet enabled at height " + blockchain.getLastBlock().getHeight());
        }
        final Attachment.MessagingAliasBuy attachment =
                (Attachment.MessagingAliasBuy) transaction.getAttachment();
        if(attachment.getVersion() > 1 && !fluxCapacitor.getValue(FluxValues.SMART_ALIASES)) {
          throw new SignumException.NotCurrentlyValidException("Attachment version mismatch");
        }
        final String aliasName = attachment.getAliasName();
        final Alias alias = attachment.getVersion() > 1 ? aliasService.getAlias(attachment.getAliasId()) : aliasService.getAlias(attachment.getAliasName(), 0L);
        if (alias == null) {
          throw new SignumException.NotCurrentlyValidException("Alias hasn't been registered yet: " + aliasName);
        } else if (alias.getAccountId() != transaction.getRecipientId()) {
          throw new SignumException.NotCurrentlyValidException("Alias is owned by account other than recipient: "
                  + Convert.toUnsignedLong(alias.getAccountId()));
        }
        Alias.Offer offer = aliasService.getOffer(alias);
        if (offer == null) {
          throw new SignumException.NotCurrentlyValidException("Alias is not for sale: " + aliasName);
        }
        if (transaction.getAmountNqt() < offer.getPriceNqt()) {
          String msg = "Price is too low for: " + aliasName + " ("
                  + transaction.getAmountNqt() + " < " + offer.getPriceNqt() + ")";
          throw new SignumException.NotCurrentlyValidException(msg);
        }
        if (offer.getBuyerId() != 0 && offer.getBuyerId() != transaction.getSenderId()) {
          throw new SignumException.NotCurrentlyValidException("Wrong buyer for " + aliasName + ": "
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
      public Attachment.MessagingAccountInfo parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SignumException.NotValidException {
        return new Attachment.MessagingAccountInfo(buffer, transactionVersion);
      }

      @Override
      protected Attachment.MessagingAccountInfo parseAttachment(JsonObject attachmentData) {
        return new Attachment.MessagingAccountInfo(attachmentData);
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.MessagingAccountInfo attachment = (Attachment.MessagingAccountInfo)transaction.getAttachment();
        if (Convert.toBytes(attachment.getName()).length > Constants.MAX_ACCOUNT_NAME_LENGTH
                || Convert.toBytes(attachment.getDescription()).length > Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH
        ) {
          throw new SignumException.NotValidException("Invalid account info issuance: " + JSON.toJsonString(attachment.getJsonObject()));
        }
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
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
      return TransactionType.TYPE_COLORED_COINS.getType();
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
        return fluxCapacitor.getValue(FluxValues.SPEEDWAY, height) ?
            new Fee(fluxCapacitor.getValue(FluxValues.FEE_QUANT, height) * BASELINE_ASSET_ISSUANCE_FACTOR, 0) :
            BASELINE_ASSET_ISSUANCE_FEE;
      }

      @Override
      public Attachment.ColoredCoinsAssetIssuance parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SignumException.NotValidException {
        return new Attachment.ColoredCoinsAssetIssuance(buffer, transactionVersion);
      }

      @Override
      protected Attachment.ColoredCoinsAssetIssuance parseAttachment(JsonObject attachmentData) {
        return new Attachment.ColoredCoinsAssetIssuance(attachmentData);
      }

      @Override
      protected boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return true;
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.ColoredCoinsAssetIssuance attachment = (Attachment.ColoredCoinsAssetIssuance) transaction.getAttachment();
        long assetId = transaction.getId();
        assetExchange.addAsset(transaction.getId(), transaction.getSenderId(), attachment);
        accountService.addToAssetAndUnconfirmedAssetBalanceQNT(senderAccount, assetId, attachment.getQuantityQnt());
      }

      @Override
      protected void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        // Nothing to undo
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.ColoredCoinsAssetIssuance attachment = (Attachment.ColoredCoinsAssetIssuance)transaction.getAttachment();
        if (attachment.getName().length() < Constants.MIN_ASSET_NAME_LENGTH
                || attachment.getName().length() > Constants.MAX_ASSET_NAME_LENGTH
                || attachment.getDescription().length() > Constants.MAX_ASSET_DESCRIPTION_LENGTH
                || attachment.getDecimals() < 0 || attachment.getDecimals() > 8
                || attachment.getQuantityQnt() < 0
                || attachment.getQuantityQnt() > Constants.MAX_ASSET_QUANTITY_QNT
                || (attachment.getVersion()>1 && !Signum.getFluxCapacitor().getValue(FluxValues.SMART_TOKEN))
                || (attachment.getMintable() && !Signum.getFluxCapacitor().getValue(FluxValues.SMART_TOKEN))
                || (attachment.getQuantityQnt() == 0 && !attachment.getMintable() && !Signum.getFluxCapacitor().getValue(FluxValues.PK_FREEZE2))
        ) {
          throw new SignumException.NotValidException("Invalid asset issuance: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (!TextUtils.isInAlphabet(attachment.getName())) {
          throw new SignumException.NotValidException("Invalid asset name: " + attachment.getName());
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
      public Attachment.ColoredCoinsAssetTransfer parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SignumException.NotValidException {
        return new Attachment.ColoredCoinsAssetTransfer(buffer, transactionVersion);
      }

      @Override
      protected Attachment.ColoredCoinsAssetTransfer parseAttachment(JsonObject attachmentData) {
        return new Attachment.ColoredCoinsAssetTransfer(attachmentData);
      }

      @Override
      protected boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        logger.trace("TransactionType ASSET_TRANSFER");
        Attachment.ColoredCoinsAssetTransfer attachment = (Attachment.ColoredCoinsAssetTransfer) transaction.getAttachment();
        long unconfirmedAssetBalance = accountService.getUnconfirmedAssetBalanceQNT(senderAccount, attachment.getAssetId());
        if (unconfirmedAssetBalance >= 0 && unconfirmedAssetBalance >= attachment.getQuantityQnt()) {
          accountService.addToUnconfirmedAssetBalanceQNT(senderAccount, attachment.getAssetId(), -attachment.getQuantityQnt());
          return true;
        }
        return false;
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.ColoredCoinsAssetTransfer attachment = (Attachment.ColoredCoinsAssetTransfer) transaction.getAttachment();
        accountService.addToAssetBalanceQNT(senderAccount, attachment.getAssetId(), -attachment.getQuantityQnt());
        accountService.addToAssetAndUnconfirmedAssetBalanceQNT(recipientAccount, attachment.getAssetId(), attachment.getQuantityQnt());
        assetExchange.addAssetTransfer(transaction, attachment.getAssetId(), attachment.getQuantityQnt());
      }

      @Override
      protected void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        Attachment.ColoredCoinsAssetTransfer attachment = (Attachment.ColoredCoinsAssetTransfer) transaction.getAttachment();
        accountService.addToUnconfirmedAssetBalanceQNT(senderAccount, attachment.getAssetId(), attachment.getQuantityQnt());
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.ColoredCoinsAssetTransfer attachment = (Attachment.ColoredCoinsAssetTransfer)transaction.getAttachment();
        if ((!Signum.getFluxCapacitor().getValue(FluxValues.SMART_TOKEN) && transaction.getAmountNqt() != 0)
                || attachment.getComment() != null && attachment.getComment().length() > Constants.MAX_ASSET_TRANSFER_COMMENT_LENGTH
                || attachment.getAssetId() == 0) {
          throw new SignumException.NotValidException("Invalid asset transfer amount or comment: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (transaction.getVersion() > 0 && attachment.getComment() != null) {
          throw new SignumException.NotValidException("Asset transfer comments no longer allowed, use message " +
                  "or encrypted message appendix instead");
        }
        Asset asset = assetExchange.getAsset(attachment.getAssetId());
        if (attachment.getQuantityQnt() <= 0) {
          throw new SignumException.NotValidException("Invalid asset transfer asset or quantity: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (asset == null) {
          throw new SignumException.NotCurrentlyValidException("Asset " + Convert.toUnsignedLong(attachment.getAssetId()) +
                  " does not exist yet");
        }
      }

      @Override
      public boolean hasRecipient() {
        return true;
      }

    };

    public static final TransactionType ASSET_TRANSFER_OWNERSHIP = new ColoredCoins() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_COLORED_COINS_TRANSFER_OWNERSHIP;
      }

      @Override
      public String getDescription() {
        return "Asset Transfer Ownership";
      }

      @Override
      public Fee getBaselineFee(int height) {
        return new Fee(fluxCapacitor.getValue(FluxValues.FEE_QUANT, height) * BASELINE_ASSET_ISSUANCE_FACTOR, 0);
      }

      @Override
      public Attachment.EmptyAttachment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SignumException.NotValidException {
        return Attachment.COLORED_COINS_ASSET_TRANSFER_OWNERSHIP;
      }

      @Override
      protected Attachment.EmptyAttachment parseAttachment(JsonObject attachmentData) throws NotValidException {
        return Attachment.COLORED_COINS_ASSET_TRANSFER_OWNERSHIP;
      }

      @Override
      protected boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        logger.trace("TransactionType ASSET_TRANSFER_OWNERSHIP");

        if(!Signum.getFluxCapacitor().getValue(FluxValues.PK_FREEZE2)){
          // not available yet
          return false;
        }

        if(transaction.getAmountNqt() != 0L){
          // no money sent along with this transaction
          return false;
        }

        String fullHashRef = transaction.getReferencedTransactionFullHash();
        Transaction assetIssuance = blockchain.getTransactionByFullHash(fullHashRef);
        if(assetIssuance == null){
          return false;
        }

        Asset asset = assetExchange.getAsset(assetIssuance.getId());
        if(asset == null || transaction.getSenderId() != asset.getAccountId()){
          // only the current owner can transfer it
          return false;
        }
        return true;
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        // if the transaction is confirmed, there is nothing else to do
      }

      @Override
      protected void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        // if the transaction is removed, there is nothing else to do
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Asset asset = null;
        String fullHashRef = transaction.getReferencedTransactionFullHash();
        Transaction assetIssuance = blockchain.getTransactionByFullHash(fullHashRef);
        if(assetIssuance != null){
          asset = assetExchange.getAsset(assetIssuance.getId());
        }

        if(!Signum.getFluxCapacitor().getValue(FluxValues.PK_FREEZE2)
          || transaction.getAmountNqt() != 0L
          || asset == null
          || transaction.getSenderId() != asset.getAccountId()){
          // only the current owner can transfer it
          throw new SignumException.NotValidException("Invalid asset ownership transfer");
        }
      }

      @Override
      public boolean hasRecipient() {
        return true;
      }

    };

    public static final TransactionType ASSET_MULTI_TRANSFER = new ColoredCoins() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_COLORED_COINS_ASSET_MULTI_TRANSFER;
      }

      @Override
      public String getDescription() {
        return "Asset Multi-Transfer";
      }

      @Override
      public Fee getBaselineFee(int height) {
        long FEE_QUANT = fluxCapacitor.getValue(FluxValues.FEE_QUANT, height);
        return new Fee(2*FEE_QUANT, FEE_QUANT);
      }

      @Override
      public Attachment.ColoredCoinsAssetMultiTransfer parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SignumException.NotValidException {
        return new Attachment.ColoredCoinsAssetMultiTransfer(buffer, transactionVersion);
      }

      @Override
      protected Attachment.ColoredCoinsAssetMultiTransfer parseAttachment(JsonObject attachmentData) throws NotValidException {
        return new Attachment.ColoredCoinsAssetMultiTransfer(attachmentData);
      }

      @Override
      protected boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        logger.trace("TransactionType ASSET_MULTI_TRANSFER");
        Attachment.ColoredCoinsAssetMultiTransfer attachment = (Attachment.ColoredCoinsAssetMultiTransfer) transaction.getAttachment();
        ArrayList<Long> assetIds = attachment.getAssetIds();
        ArrayList<Long> quantitiesQNT = attachment.getQuantitiesQnt();
        for(int i = 0; i < assetIds.size(); i++){
          long assetId = assetIds.get(i);
          long quantityQNT = quantitiesQNT.get(i);

          long unconfirmedAssetBalance = accountService.getUnconfirmedAssetBalanceQNT(senderAccount, assetId);
          if (unconfirmedAssetBalance >= 0 && unconfirmedAssetBalance >= quantityQNT) {
            accountService.addToUnconfirmedAssetBalanceQNT(senderAccount, assetId, -quantityQNT);
          }
          else {
            return false;
          }
        }
        return true;
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.ColoredCoinsAssetMultiTransfer attachment = (Attachment.ColoredCoinsAssetMultiTransfer) transaction.getAttachment();
        ArrayList<Long> assetIds = attachment.getAssetIds();
        ArrayList<Long> quantitiesQNT = attachment.getQuantitiesQnt();
        for(int i = 0; i < assetIds.size(); i++){
          long assetId = assetIds.get(i);
          long quantityQNT = quantitiesQNT.get(i);

          accountService.addToAssetBalanceQNT(senderAccount, assetId, -quantityQNT);
          accountService.addToAssetAndUnconfirmedAssetBalanceQNT(recipientAccount, assetId, quantityQNT);
          assetExchange.addAssetTransfer(transaction, assetId, quantityQNT);
        }
      }

      @Override
      protected void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        Attachment.ColoredCoinsAssetMultiTransfer attachment = (Attachment.ColoredCoinsAssetMultiTransfer) transaction.getAttachment();
        for(int i = 0; i < attachment.getAssetIds().size(); i++){
          long assetId = attachment.getAssetIds().get(i);
          long quantityQNT = attachment.getQuantitiesQnt().get(i);
          accountService.addToUnconfirmedAssetBalanceQNT(senderAccount, assetId, quantityQNT);
        }
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.ColoredCoinsAssetMultiTransfer attachment = (Attachment.ColoredCoinsAssetMultiTransfer)transaction.getAttachment();
        if ((!Signum.getFluxCapacitor().getValue(FluxValues.SMART_TOKEN))
                || attachment.getAssetIds().size() < 2 || attachment.getAssetIds().size() > Constants.MAX_MULTI_ASSET_IDS) {
          throw new SignumException.NotValidException("Invalid asset multi transfer: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        for(int i = 0; i < attachment.getAssetIds().size(); i++){
          long assetId = attachment.getAssetIds().get(i);
          long quantityQNT = attachment.getQuantitiesQnt().get(i);

          Asset asset = assetExchange.getAsset(assetId);
          if (quantityQNT <= 0) {
            throw new SignumException.NotValidException("Invalid asset transfer quantity: " + JSON.toJsonString(attachment.getJsonObject()));
          }
          if (asset == null) {
            throw new SignumException.NotCurrentlyValidException("Asset " + Convert.toUnsignedLong(assetId) +
                    " does not exist yet");
          }
        }
      }

      @Override
      public boolean hasRecipient() {
        return true;
      }

    };

    public static final TransactionType ASSET_MINT = new ColoredCoins() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_COLORED_COINS_ASSET_MINT;
      }

      @Override
      public String getDescription() {
        return "Asset Mint";
      }

      @Override
      public Attachment.ColoredCoinsAssetMint parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SignumException.NotValidException {
        return new Attachment.ColoredCoinsAssetMint(buffer, transactionVersion);
      }

      @Override
      protected Attachment.ColoredCoinsAssetMint parseAttachment(JsonObject attachmentData) {
        return new Attachment.ColoredCoinsAssetMint(attachmentData);
      }

      @Override
      protected boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        logger.trace("TransactionType ASSET_MINT");
        Attachment.ColoredCoinsAssetMint attachment = (Attachment.ColoredCoinsAssetMint) transaction.getAttachment();

        Asset asset = assetExchange.getAsset(attachment.getAssetId());
        if(asset == null || asset.getAccountId() != transaction.getSenderId() || !asset.getMintable()
            || attachment.getQuantityQnt() <= 0L
            || !Signum.getFluxCapacitor().getValue(FluxValues.SMART_TOKEN)) {
          return false;
        }

        boolean unconfirmed = !Signum.getFluxCapacitor().getValue(FluxValues.DISTRIBUTION_FIX);
        long circulatingSupply = assetExchange.getAssetCirculatingSupply(asset, false, unconfirmed);
        long newSupply = circulatingSupply + attachment.getQuantityQnt();
        if (newSupply > Constants.MAX_ASSET_QUANTITY_QNT) {
          return false;
        }

        return true;
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.ColoredCoinsAssetMint attachment = (Attachment.ColoredCoinsAssetMint) transaction.getAttachment();
        accountService.addToAssetAndUnconfirmedAssetBalanceQNT(senderAccount, attachment.getAssetId(), attachment.getQuantityQnt());
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        // All mint transactions from the same account are considered duplicates, so just the one with highest fee is kept
        return new TransactionDuplicationKey(ASSET_MINT, Convert.toUnsignedLong(transaction.getSenderId()));
      }

      @Override
      protected void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        // do nothing
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.ColoredCoinsAssetMint attachment = (Attachment.ColoredCoinsAssetMint)transaction.getAttachment();
        if (!Signum.getFluxCapacitor().getValue(FluxValues.SMART_TOKEN)) {
          throw new SignumException.NotValidException("Transaction type not yet enabled: " + JSON.toJsonString(attachment.getJsonObject()));
        }

        Asset asset = assetExchange.getAsset(attachment.getAssetId());
        if (asset == null) {
          throw new SignumException.NotCurrentlyValidException("Asset " + Convert.toUnsignedLong(attachment.getAssetId()) +
                  " does not exist yet");
        }
        if(transaction.getAmountNqt() != 0 || asset.getAccountId() != transaction.getSenderId()
            || !asset.getMintable() || attachment.getQuantityQnt() <= 0L) {
          throw new SignumException.NotValidException("Invalid asset mint: " + JSON.toJsonString(attachment.getJsonObject()));
        }

        boolean unconfirmed = !Signum.getFluxCapacitor().getValue(FluxValues.DISTRIBUTION_FIX);
        long circulatingSupply = assetExchange.getAssetCirculatingSupply(asset, false, unconfirmed);
        long newSupply = circulatingSupply + attachment.getQuantityQnt();
        if (newSupply > Constants.MAX_ASSET_QUANTITY_QNT) {
          throw new SignumException.NotCurrentlyValidException("Maximum circulating supply QNT is " + Constants.MAX_ASSET_QUANTITY_QNT);
        }
      }

      @Override
      public boolean hasRecipient() {
        return false;
      }

    };

    public static final TransactionType ASSET_ADD_TREASURY_ACCOUNT = new ColoredCoins() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_COLORED_COINS_ADD_TREASURY_ACCOUNT;
      }

      @Override
      public String getDescription() {
        return "Asset Add Treasury Account";
      }

      @Override
      public Attachment.AbstractAttachment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SignumException.NotValidException {
        return Attachment.ASSET_ADD_TREASURY_ACCOUNT_ATTACHMENT;
      }

      @Override
      protected Attachment.AbstractAttachment parseAttachment(JsonObject attachmentData) {
        return Attachment.ASSET_ADD_TREASURY_ACCOUNT_ATTACHMENT;
      }

      @Override
      protected boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        logger.trace("TransactionType ASSET_ADD_TREASURY_ACCOUNT");

        if (!Signum.getFluxCapacitor().getValue(FluxValues.SMART_TOKEN)) {
          return false;
        }

        Transaction assetCreationTransaction = Signum.getBlockchain().getTransactionByFullHash(transaction.getReferencedTransactionFullHash());
        if(transaction.getAmountNqt() != 0 || assetCreationTransaction == null)
          return false;

        Asset asset = assetExchange.getAsset(assetCreationTransaction.getId());
        if(asset == null || asset.getAccountId() != transaction.getSenderId()
            || !Signum.getFluxCapacitor().getValue(FluxValues.SMART_TOKEN)) {
          return false;
        }

        return true;
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        // not needed
      }

      @Override
      protected void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        // not needed
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        if (!Signum.getFluxCapacitor().getValue(FluxValues.SMART_TOKEN)) {
          throw new SignumException.NotValidException("Transaction type not yet enabled");
        }

        Transaction assetCreationTransaction = Signum.getBlockchain().getTransactionByFullHash(transaction.getReferencedTransactionFullHash());
        if(transaction.getAmountNqt() != 0 || assetCreationTransaction == null) {
          throw new SignumException.NotCurrentlyValidException("Invalid transaction amount or reference transaction");
        }
        Asset asset = assetExchange.getAsset(assetCreationTransaction.getId());
        if (asset == null) {
          throw new SignumException.NotCurrentlyValidException("Asset " + Convert.toUnsignedLong(assetCreationTransaction.getId()) +
              " does not exist yet");
        }
        
        if(asset.getAccountId()!= transaction.getSenderId() || !Signum.getFluxCapacitor().getValue(FluxValues.SMART_TOKEN)) {
          throw new SignumException.NotValidException("Invalid add treasury account transaction");
        }

      }

      @Override
      public boolean hasRecipient() {
        // recipient is the account being ignored
        return true;
      }

    };

    public static final TransactionType ASSET_DISTRIBUTE_TO_HOLDERS = new ColoredCoins() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_COLORED_COINS_DISTRIBUTE_TO_HOLDERS;
      }

      @Override
      public String getDescription() {
        return "Asset Distribute to Holders";
      }

      @Override
      public Attachment.ColoredCoinsAssetDistributeToHolders parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SignumException.NotValidException {
        return new Attachment.ColoredCoinsAssetDistributeToHolders(buffer, transactionVersion);
      }

      @Override
      protected Attachment.ColoredCoinsAssetDistributeToHolders parseAttachment(JsonObject attachmentData) {
        return new Attachment.ColoredCoinsAssetDistributeToHolders(attachmentData);
      }

      @Override
      public long minimumFeeNQT(int height, Transaction transaction) {
        long minFee = super.minimumFeeNQT(height, transaction);

        Attachment.ColoredCoinsAssetDistributeToHolders attachment = (Attachment.ColoredCoinsAssetDistributeToHolders) transaction.getAttachment();
        Asset asset = assetExchange.getAsset(attachment.getAssetId());
        boolean unconfirmed = !Signum.getFluxCapacitor().getValue(FluxValues.DISTRIBUTION_FIX, height);
        long numberOfHolders = assetExchange.getAssetAccountsCount(asset, attachment.getMinimumAssetQuantityQnt(), true, unconfirmed);
        long minFeeHolders = (numberOfHolders*minFee)/10L;
        minFee = Math.max(minFee, minFeeHolders);

        return minFee;
      }

      @Override
      protected boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        logger.trace("TransactionType ASSET_DISTRIBUTE_TO_HOLDERS");
        Attachment.ColoredCoinsAssetDistributeToHolders attachment = (Attachment.ColoredCoinsAssetDistributeToHolders) transaction.getAttachment();

        if (!Signum.getFluxCapacitor().getValue(FluxValues.SMART_TOKEN)) {
          return false;
        }

        long assetToDistribute = attachment.getAssetIdToDistribute();
        if(assetToDistribute != 0L && attachment.getQuantityQnt() > 0L) {
          long unconfirmedAssetBalance = accountService.getUnconfirmedAssetBalanceQNT(senderAccount, attachment.getAssetIdToDistribute());
          if(attachment.getQuantityQnt() > unconfirmedAssetBalance)
            return false;
        }

        accountService.addToUnconfirmedAssetBalanceQNT(senderAccount, assetToDistribute, -attachment.getQuantityQnt());

        Asset asset = assetExchange.getAsset(attachment.getAssetId());
        boolean unconfirmed = !Signum.getFluxCapacitor().getValue(FluxValues.DISTRIBUTION_FIX);
        CollectionWithIndex<AccountAsset> assetHolders = assetExchange.getAssetAccounts(asset, true, attachment.getMinimumAssetQuantityQnt(), unconfirmed, -1, -1);
        long circulatingQuantityQNT = 0L;
        for(AccountAsset holder : assetHolders) {
          if(Signum.getFluxCapacitor().getValue(FluxValues.DISTRIBUTION_FIX, transaction.getHeight()) && holder.getAccountId() == senderAccount.getId()){
            continue;
          }
          circulatingQuantityQNT += holder.getUnconfirmedQuantityQnt();
        }
        if(circulatingQuantityQNT <= 0L) {
          accountService.addToUnconfirmedAssetBalanceQNT(senderAccount, assetToDistribute, attachment.getQuantityQnt());
          return false;
        }
        return true;
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.ColoredCoinsAssetDistributeToHolders attachment = (Attachment.ColoredCoinsAssetDistributeToHolders) transaction.getAttachment();

        // subtract the asset balance from the sender, the amount was already subtracted by the transaction
        accountService.addToAssetBalanceQNT(senderAccount, attachment.getAssetIdToDistribute(), -attachment.getQuantityQnt());

        Collection<IndirectIncoming> incomings = getIndirectIncomings(transaction);
        for(IndirectIncoming incoming : incomings) {

          // add to the holders
          Account account = accountService.getOrAddAccount(incoming.getAccountId());

          long quantity = incoming.getQuantity();
          if(quantity > 0L) {
            accountService.addToAssetAndUnconfirmedAssetBalanceQNT(account, attachment.getAssetIdToDistribute(), quantity);
          }

          long amount = incoming.getAmount();
          if(amount > 0L) {
            accountService.addToBalanceAndUnconfirmedBalanceNQT(account, amount);
          }
        }
      }

      @Override
      protected void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        Attachment.ColoredCoinsAssetDistributeToHolders attachment = (Attachment.ColoredCoinsAssetDistributeToHolders) transaction.getAttachment();
        accountService.addToUnconfirmedAssetBalanceQNT(senderAccount, attachment.getAssetIdToDistribute(), attachment.getQuantityQnt());
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.ColoredCoinsAssetDistributeToHolders attachment = (Attachment.ColoredCoinsAssetDistributeToHolders) transaction.getAttachment();

        if (!Signum.getFluxCapacitor().getValue(FluxValues.SMART_TOKEN)) {
          throw new SignumException.NotValidException("Transaction type not yet enabled: " + JSON.toJsonString(attachment.getJsonObject()));
        }

        if (attachment.getAssetId() == 0L) {
          throw new SignumException.NotValidException("Invalid asset transfer id: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        Asset asset = assetExchange.getAsset(attachment.getAssetId());
        if (asset == null) {
          throw new SignumException.NotCurrentlyValidException("Asset " + Convert.toUnsignedLong(attachment.getAssetId()) +
                  " does not exist yet");
        }

        boolean unconfirmed = !Signum.getFluxCapacitor().getValue(FluxValues.DISTRIBUTION_FIX);
        long circulatingQuantity = assetExchange.getAssetCirculatingSupply(asset, true, unconfirmed);
        if (circulatingQuantity <= 0L) {
          throw new SignumException.NotValidException("Asset has no circulating supply: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (attachment.getQuantityQnt() == 0L && transaction.getAmountNqt() == 0L){
          throw new SignumException.NotValidException("Nothing to distribute");
        }
        if(attachment.getQuantityQnt() > 0L) {
          Asset assetToDistribute = assetExchange.getAsset(attachment.getAssetIdToDistribute());
          if (assetToDistribute == null) {
            throw new SignumException.NotCurrentlyValidException("Asset " + Convert.toUnsignedLong(attachment.getAssetId()) +
                    " does not exist yet");
          }
        }
      }

      @Override
      public boolean hasRecipient() {
        return false;
      }

      @Override
      public boolean isIndirect() {
        return true;
      }

      @Override
      public Collection<IndirectIncoming> getIndirectIncomings(Transaction transaction) {
        // TODO: rework this so we do not run this more than once
        Attachment.ColoredCoinsAssetDistributeToHolders attachment = (Attachment.ColoredCoinsAssetDistributeToHolders) transaction.getAttachment();

        boolean unconfirmed = !Signum.getFluxCapacitor().getValue(FluxValues.DISTRIBUTION_FIX, transaction.getHeight());
        CollectionWithIndex<AccountAsset> assetHolders = assetExchange.getAssetAccounts(
            assetExchange.getAsset(attachment.getAssetId()),
            true, attachment.getMinimumAssetQuantityQnt(), unconfirmed, -1, -1);
        long circulatingQuantityQNT = 0L;
        for(AccountAsset holder : assetHolders) {
          if(Signum.getFluxCapacitor().getValue(FluxValues.DISTRIBUTION_FIX, transaction.getHeight()) && holder.getAccountId() == transaction.getSenderId()){
            continue;
          }
          long holderQuantity = Signum.getFluxCapacitor().getValue(FluxValues.DISTRIBUTION_FIX, transaction.getHeight()) ?
            holder.getQuantityQnt() : holder.getUnconfirmedQuantityQnt();
          circulatingQuantityQNT += holderQuantity;
        }
        BigInteger circulatingQuantity = BigInteger.valueOf(circulatingQuantityQNT);

        ArrayList<IndirectIncoming> indirects = new ArrayList<IndirectIncoming>(assetHolders.size());

        BigInteger quantityToDistribute = BigInteger.valueOf(attachment.getQuantityQnt());
        BigInteger amountToDistribute = BigInteger.valueOf(transaction.getAmountNqt());

        long amountDistributed = 0L;
        long quantityDistributed = 0L;

        AccountAsset largestHolder = null;
        long largestHolderQuantity = 0L;
        IndirectIncoming largestIndirect = null;
        for(AccountAsset holder : assetHolders) {
          if(Signum.getFluxCapacitor().getValue(FluxValues.DISTRIBUTION_FIX, transaction.getHeight()) && holder.getAccountId() == transaction.getSenderId()){
            continue;
          }
          long holderQuantity = unconfirmed ? holder.getUnconfirmedQuantityQnt() : holder.getQuantityQnt();

          // add to the holders
          long quantity = 0L;
          if(attachment.getQuantityQnt() > 0L) {
            quantity = quantityToDistribute.multiply(BigInteger.valueOf(holderQuantity))
                .divide(circulatingQuantity).longValue();

            quantityDistributed += quantity;
          }

          long amount = 0L;
          if(transaction.getAmountNqt() > 0L) {
            amount = amountToDistribute.multiply(BigInteger.valueOf(holderQuantity))
                .divide(circulatingQuantity).longValue();
            amountDistributed += amount;
          }

          IndirectIncoming indirect = new IndirectIncoming(holder.getAccountId(), transaction.getId(),
              amount, quantity, transaction.getHeight());
          indirects.add(indirect);

          if(largestHolder == null || holderQuantity > largestHolderQuantity) {
            largestHolder = holder;
            largestHolderQuantity = holderQuantity;
            largestIndirect = indirect;
          }
        }

        // any "dust" goes to the largestHolder, if any
        if(largestIndirect != null){
          if(amountDistributed < transaction.getAmountNqt()) {
            largestIndirect.addAmount(transaction.getAmountNqt() - amountDistributed);
          }
          if(quantityDistributed < attachment.getQuantityQnt()) {
            largestIndirect.addQuantity(attachment.getQuantityQnt() - quantityDistributed);
          }
        }

        return indirects;
      }

    };


    abstract static class ColoredCoinsOrderPlacement extends ColoredCoins {

      @Override
      protected final void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.ColoredCoinsOrderPlacement attachment = (Attachment.ColoredCoinsOrderPlacement)transaction.getAttachment();
        if (attachment.getPriceNqt() <= 0 || attachment.getPriceNqt() > Constants.MAX_BALANCE_NQT
                || attachment.getAssetId() == 0) {
          throw new SignumException.NotValidException("Invalid asset order placement: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        Asset asset = assetExchange.getAsset(attachment.getAssetId());
        if (attachment.getQuantityQnt() <= 0 || (asset != null && !asset.getMintable() && attachment.getQuantityQnt() > asset.getQuantityQnt())) {
          throw new SignumException.NotValidException("Invalid asset order placement asset or quantity: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (asset == null) {
          throw new SignumException.NotCurrentlyValidException("Asset " + Convert.toUnsignedLong(attachment.getAssetId()) +
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
      protected Attachment.ColoredCoinsAskOrderPlacement parseAttachment(JsonObject attachmentData) {
        return new Attachment.ColoredCoinsAskOrderPlacement(attachmentData);
      }

      @Override
      protected boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        logger.trace("TransactionType ASK_ORDER_PLACEMENT");
        Attachment.ColoredCoinsAskOrderPlacement attachment = (Attachment.ColoredCoinsAskOrderPlacement) transaction.getAttachment();
        long unconfirmedAssetBalance = accountService.getUnconfirmedAssetBalanceQNT(senderAccount, attachment.getAssetId());
        if (unconfirmedAssetBalance >= 0 && unconfirmedAssetBalance >= attachment.getQuantityQnt()) {
          accountService.addToUnconfirmedAssetBalanceQNT(senderAccount, attachment.getAssetId(), -attachment.getQuantityQnt());
          return true;
        }
        return false;
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.ColoredCoinsAskOrderPlacement attachment = (Attachment.ColoredCoinsAskOrderPlacement) transaction.getAttachment();
        if (assetExchange.getAsset(attachment.getAssetId()) != null) {
          assetExchange.addAskOrder(transaction, attachment);
        }
      }

      @Override
      protected void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        Attachment.ColoredCoinsAskOrderPlacement attachment = (Attachment.ColoredCoinsAskOrderPlacement) transaction.getAttachment();
        accountService.addToUnconfirmedAssetBalanceQNT(senderAccount, attachment.getAssetId(), attachment.getQuantityQnt());
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
      protected Attachment.ColoredCoinsBidOrderPlacement parseAttachment(JsonObject attachmentData) {
        return new Attachment.ColoredCoinsBidOrderPlacement(attachmentData);
      }

      @Override
      protected boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        logger.trace("TransactionType BID_ORDER_PLACEMENT");
        Long totalAmountNQT = calculateAttachmentTotalAmountNQT(transaction);
        if (senderAccount.getUnconfirmedBalanceNqt() >= totalAmountNQT ) {
          accountService.addToUnconfirmedBalanceNQT(senderAccount, -totalAmountNQT);
          return true;
        }
        return false;
      }

      @Override
      public Long calculateAttachmentTotalAmountNQT(Transaction transaction) {
        Attachment.ColoredCoinsBidOrderPlacement attachment = (Attachment.ColoredCoinsBidOrderPlacement) transaction.getAttachment();
        return Convert.safeMultiply(attachment.getQuantityQnt(), attachment.getPriceNqt());
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.ColoredCoinsBidOrderPlacement attachment = (Attachment.ColoredCoinsBidOrderPlacement) transaction.getAttachment();
        if (assetExchange.getAsset(attachment.getAssetId()) != null) {
          assetExchange.addBidOrder(transaction, attachment);
        }
      }

      @Override
      protected void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        Long totalAmountNQT = calculateAttachmentTotalAmountNQT(transaction);
        accountService.addToUnconfirmedBalanceNQT(senderAccount, totalAmountNQT);
      }

    };

    abstract static class ColoredCoinsOrderCancellation extends ColoredCoins {

      @Override
      protected final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return true;
      }

      @Override
      protected final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
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
      protected Attachment.ColoredCoinsAskOrderCancellation parseAttachment(JsonObject attachmentData) {
        return new Attachment.ColoredCoinsAskOrderCancellation(attachmentData);
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.ColoredCoinsAskOrderCancellation attachment = (Attachment.ColoredCoinsAskOrderCancellation) transaction.getAttachment();
        Order order = assetExchange.getAskOrder(attachment.getOrderId());
        assetExchange.removeAskOrder(attachment.getOrderId());
        if (order != null) {
          accountService.addToUnconfirmedAssetBalanceQNT(senderAccount, order.getAssetId(), order.getQuantityQNT());
        }
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.ColoredCoinsAskOrderCancellation attachment = (Attachment.ColoredCoinsAskOrderCancellation) transaction.getAttachment();
        Order ask = assetExchange.getAskOrder(attachment.getOrderId());
        if (ask == null) {
          throw new SignumException.NotCurrentlyValidException("Invalid ask order: " + Convert.toUnsignedLong(attachment.getOrderId()));
        }
        if (ask.getAccountId() != transaction.getSenderId()) {
          throw new SignumException.NotValidException("Order " + Convert.toUnsignedLong(attachment.getOrderId()) + " was created by account "
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
      protected Attachment.ColoredCoinsBidOrderCancellation parseAttachment(JsonObject attachmentData) {
        return new Attachment.ColoredCoinsBidOrderCancellation(attachmentData);
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.ColoredCoinsBidOrderCancellation attachment = (Attachment.ColoredCoinsBidOrderCancellation) transaction.getAttachment();
        Order order = assetExchange.getBidOrder(attachment.getOrderId());
        assetExchange.removeBidOrder(attachment.getOrderId());
        if (order != null) {
          accountService.addToUnconfirmedBalanceNQT(senderAccount, Convert.safeMultiply(order.getQuantityQNT(), order.getPriceNQT()));
        }
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.ColoredCoinsBidOrderCancellation attachment = (Attachment.ColoredCoinsBidOrderCancellation) transaction.getAttachment();
        Order bid = assetExchange.getBidOrder(attachment.getOrderId());
        if (bid == null) {
          throw new SignumException.NotCurrentlyValidException("Invalid bid order: " + Convert.toUnsignedLong(attachment.getOrderId()));
        }
        if (bid.getAccountId() != transaction.getSenderId()) {
          throw new SignumException.NotValidException("Order " + Convert.toUnsignedLong(attachment.getOrderId()) + " was created by account "
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
      return TransactionType.TYPE_DIGITAL_GOODS.getType();
    }

    @Override
    protected boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
      return true;
    }

    @Override
    protected void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
    }

    @Override
    protected final void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
      if (! fluxCapacitor.getValue(FluxValues.DIGITAL_GOODS_STORE, blockchain.getLastBlock().getHeight())) {
        throw new SignumException.NotYetEnabledException("Digital goods listing not yet enabled at height " + blockchain.getLastBlock().getHeight());
      }
      if (transaction.getAmountNqt() != 0) {
        throw new SignumException.NotValidException("Invalid digital goods transaction");
      }
      doValidateAttachment(transaction);
    }

    abstract void doValidateAttachment(Transaction transaction) throws SignumException.ValidationException;


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
      public Attachment.DigitalGoodsListing parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SignumException.NotValidException {
        return new Attachment.DigitalGoodsListing(buffer, transactionVersion);
      }

      @Override
      protected Attachment.DigitalGoodsListing parseAttachment(JsonObject attachmentData) {
        return new Attachment.DigitalGoodsListing(attachmentData);
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.DigitalGoodsListing attachment = (Attachment.DigitalGoodsListing) transaction.getAttachment();
        dgsGoodsStoreService.listGoods(transaction, attachment);
      }

      @Override
      void doValidateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.DigitalGoodsListing attachment = (Attachment.DigitalGoodsListing) transaction.getAttachment();
        if (attachment.getName().isEmpty()
                || attachment.getName().length() > Constants.MAX_DGS_LISTING_NAME_LENGTH
                || attachment.getDescription().length() > Constants.MAX_DGS_LISTING_DESCRIPTION_LENGTH
                || attachment.getTags().length() > Constants.MAX_DGS_LISTING_TAGS_LENGTH
                || attachment.getQuantity() < 0 || attachment.getQuantity() > Constants.MAX_DGS_LISTING_QUANTITY
                || attachment.getPriceNqt() <= 0 || attachment.getPriceNqt() > Constants.MAX_BALANCE_NQT) {
          throw new SignumException.NotValidException("Invalid digital goods listing: " + JSON.toJsonString(attachment.getJsonObject()));
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
      protected Attachment.DigitalGoodsDelisting parseAttachment(JsonObject attachmentData) {
        return new Attachment.DigitalGoodsDelisting(attachmentData);
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.DigitalGoodsDelisting attachment = (Attachment.DigitalGoodsDelisting) transaction.getAttachment();
        dgsGoodsStoreService.delistGoods(attachment.getGoodsId());
      }

      @Override
      void doValidateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.DigitalGoodsDelisting attachment = (Attachment.DigitalGoodsDelisting) transaction.getAttachment();
        DigitalGoodsStore.Goods goods = dgsGoodsStoreService.getGoods(attachment.getGoodsId());
        if (goods != null && transaction.getSenderId() != goods.getSellerId()) {
          throw new SignumException.NotValidException("Invalid digital goods delisting - seller is different: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (goods == null || goods.isDelisted()) {
          throw new SignumException.NotCurrentlyValidException("Goods " + Convert.toUnsignedLong(attachment.getGoodsId()) +
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
      protected Attachment.DigitalGoodsPriceChange parseAttachment(JsonObject attachmentData) {
        return new Attachment.DigitalGoodsPriceChange(attachmentData);
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.DigitalGoodsPriceChange attachment = (Attachment.DigitalGoodsPriceChange) transaction.getAttachment();
        dgsGoodsStoreService.changePrice(attachment.getGoodsId(), attachment.getPriceNqt());
      }

      @Override
      void doValidateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.DigitalGoodsPriceChange attachment = (Attachment.DigitalGoodsPriceChange) transaction.getAttachment();
        DigitalGoodsStore.Goods goods = dgsGoodsStoreService.getGoods(attachment.getGoodsId());
        if (attachment.getPriceNqt() <= 0 || attachment.getPriceNqt() > Constants.MAX_BALANCE_NQT
                || (goods != null && transaction.getSenderId() != goods.getSellerId())) {
          throw new SignumException.NotValidException("Invalid digital goods price change: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (goods == null || goods.isDelisted()) {
          throw new SignumException.NotCurrentlyValidException("Goods " + Convert.toUnsignedLong(attachment.getGoodsId()) +
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
      protected Attachment.DigitalGoodsQuantityChange parseAttachment(JsonObject attachmentData) {
        return new Attachment.DigitalGoodsQuantityChange(attachmentData);
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.DigitalGoodsQuantityChange attachment = (Attachment.DigitalGoodsQuantityChange) transaction.getAttachment();
        dgsGoodsStoreService.changeQuantity(attachment.getGoodsId(), attachment.getDeltaQuantity(), false);
      }

      @Override
      void doValidateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.DigitalGoodsQuantityChange attachment = (Attachment.DigitalGoodsQuantityChange) transaction.getAttachment();
        DigitalGoodsStore.Goods goods = dgsGoodsStoreService.getGoods(attachment.getGoodsId());
        if (attachment.getDeltaQuantity() < -Constants.MAX_DGS_LISTING_QUANTITY
                || attachment.getDeltaQuantity() > Constants.MAX_DGS_LISTING_QUANTITY
                || (goods != null && transaction.getSenderId() != goods.getSellerId())) {
          throw new SignumException.NotValidException("Invalid digital goods quantity change: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (goods == null || goods.isDelisted()) {
          throw new SignumException.NotCurrentlyValidException("Goods " + Convert.toUnsignedLong(attachment.getGoodsId()) +
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
      protected Attachment.DigitalGoodsPurchase parseAttachment(JsonObject attachmentData) {
        return new Attachment.DigitalGoodsPurchase(attachmentData);
      }

      @Override
      protected boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        logger.trace("TransactionType PURCHASE");
        Long totalAmountNQT = calculateAttachmentTotalAmountNQT(transaction);
        if (senderAccount.getUnconfirmedBalanceNqt() >= totalAmountNQT) {
          accountService.addToUnconfirmedBalanceNQT(senderAccount, -totalAmountNQT);
          return true;
        }
        return false;
      }

      @Override
      public Long calculateAttachmentTotalAmountNQT(Transaction transaction) {
        Attachment.DigitalGoodsPurchase attachment = (Attachment.DigitalGoodsPurchase) transaction.getAttachment();
        return Convert.safeMultiply(attachment.getQuantity(), attachment.getPriceNqt());
      }

      @Override
      protected void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        accountService.addToUnconfirmedBalanceNQT(senderAccount, calculateAttachmentTotalAmountNQT(transaction));
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.DigitalGoodsPurchase attachment = (Attachment.DigitalGoodsPurchase) transaction.getAttachment();
        dgsGoodsStoreService.purchase(transaction, attachment);
      }

      @Override
      void doValidateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.DigitalGoodsPurchase attachment = (Attachment.DigitalGoodsPurchase) transaction.getAttachment();
        DigitalGoodsStore.Goods goods = dgsGoodsStoreService.getGoods(attachment.getGoodsId());
        if (attachment.getQuantity() <= 0 || attachment.getQuantity() > Constants.MAX_DGS_LISTING_QUANTITY
                || attachment.getPriceNqt() <= 0 || attachment.getPriceNqt() > Constants.MAX_BALANCE_NQT
                || (goods != null && goods.getSellerId() != transaction.getRecipientId())) {
          throw new SignumException.NotValidException("Invalid digital goods purchase: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (transaction.getEncryptedMessage() != null && ! transaction.getEncryptedMessage().isText()) {
          throw new SignumException.NotValidException("Only text encrypted messages allowed");
        }
        if (goods == null || goods.isDelisted()) {
          throw new SignumException.NotCurrentlyValidException("Goods " + Convert.toUnsignedLong(attachment.getGoodsId()) +
                  "not yet listed or already delisted");
        }
        if (attachment.getQuantity() > goods.getQuantity() || attachment.getPriceNqt() != goods.getPriceNQT()) {
          throw new SignumException.NotCurrentlyValidException("Goods price or quantity changed: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (attachment.getDeliveryDeadlineTimestamp() <= blockchain.getLastBlock().getTimestamp()) {
          throw new SignumException.NotCurrentlyValidException("Delivery deadline has already expired: " + attachment.getDeliveryDeadlineTimestamp());
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
      public Attachment.DigitalGoodsDelivery parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SignumException.NotValidException {
        return new Attachment.DigitalGoodsDelivery(buffer, transactionVersion);
      }

      @Override
      protected Attachment.DigitalGoodsDelivery parseAttachment(JsonObject attachmentData) {
        return new Attachment.DigitalGoodsDelivery(attachmentData);
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.DigitalGoodsDelivery attachment = (Attachment.DigitalGoodsDelivery)transaction.getAttachment();
        dgsGoodsStoreService.deliver(transaction, attachment);
      }

      @Override
      void doValidateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.DigitalGoodsDelivery attachment = (Attachment.DigitalGoodsDelivery) transaction.getAttachment();
        DigitalGoodsStore.Purchase purchase = dgsGoodsStoreService.getPendingPurchase(attachment.getPurchaseId());
        if (attachment.getGoods().getData().length > Constants.MAX_DGS_GOODS_LENGTH
                || attachment.getGoods().getData().length == 0
                || attachment.getGoods().getNonce().length != 32
                || attachment.getDiscountNqt() < 0 || attachment.getDiscountNqt() > Constants.MAX_BALANCE_NQT
                || (purchase != null &&
                (purchase.getBuyerId() != transaction.getRecipientId()
                        || transaction.getSenderId() != purchase.getSellerId()
                        || attachment.getDiscountNqt() > Convert.safeMultiply(purchase.getPriceNQT(), purchase.getQuantity())))) {
          throw new SignumException.NotValidException("Invalid digital goods delivery: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (purchase == null || purchase.getEncryptedGoods() != null) {
          throw new SignumException.NotCurrentlyValidException("Purchase does not exist yet, or already delivered: " + JSON.toJsonString(attachment.getJsonObject()));
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
      protected Attachment.DigitalGoodsFeedback parseAttachment(JsonObject attachmentData) {
        return new Attachment.DigitalGoodsFeedback(attachmentData);
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.DigitalGoodsFeedback attachment = (Attachment.DigitalGoodsFeedback)transaction.getAttachment();
        dgsGoodsStoreService.feedback(attachment.getPurchaseId(), transaction.getEncryptedMessage(), transaction.getMessage());
      }

      @Override
      void doValidateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.DigitalGoodsFeedback attachment = (Attachment.DigitalGoodsFeedback) transaction.getAttachment();
        DigitalGoodsStore.Purchase purchase = dgsGoodsStoreService.getPurchase(attachment.getPurchaseId());
        if (purchase != null &&
                (purchase.getSellerId() != transaction.getRecipientId()
                        || transaction.getSenderId() != purchase.getBuyerId())) {
          throw new SignumException.NotValidException("Invalid digital goods feedback: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (transaction.getEncryptedMessage() == null && transaction.getMessage() == null) {
          throw new SignumException.NotValidException("Missing feedback message");
        }
        if (transaction.getEncryptedMessage() != null && ! transaction.getEncryptedMessage().isText()) {
          throw new SignumException.NotValidException("Only text encrypted messages allowed");
        }
        if (transaction.getMessage() != null && ! transaction.getMessage().isText()) {
          throw new SignumException.NotValidException("Only text public messages allowed");
        }
        if (purchase == null || purchase.getEncryptedGoods() == null) {
          throw new SignumException.NotCurrentlyValidException("Purchase does not exist yet or not yet delivered");
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
      protected Attachment.DigitalGoodsRefund parseAttachment(JsonObject attachmentData) {
        return new Attachment.DigitalGoodsRefund(attachmentData);
      }

      @Override
      protected boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        logger.trace("TransactionType REFUND");
        Long totalAmountNQT = calculateAttachmentTotalAmountNQT(transaction);
        if (senderAccount.getUnconfirmedBalanceNqt() >= totalAmountNQT) {
          accountService.addToUnconfirmedBalanceNQT(senderAccount, -totalAmountNQT);
          return true;
        }
        return false;
      }

      @Override
      public Long calculateAttachmentTotalAmountNQT(Transaction transaction) {
        Attachment.DigitalGoodsRefund attachment = (Attachment.DigitalGoodsRefund) transaction.getAttachment();
        return attachment.getRefundNqt();
      }

      @Override
      protected void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        accountService.addToUnconfirmedBalanceNQT(senderAccount, calculateAttachmentTotalAmountNQT(transaction));
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.DigitalGoodsRefund attachment = (Attachment.DigitalGoodsRefund) transaction.getAttachment();
        dgsGoodsStoreService.refund(transaction.getSenderId(), attachment.getPurchaseId(),
                attachment.getRefundNqt(), transaction.getEncryptedMessage());
      }

      @Override
      void doValidateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.DigitalGoodsRefund attachment = (Attachment.DigitalGoodsRefund) transaction.getAttachment();
        DigitalGoodsStore.Purchase purchase = dgsGoodsStoreService.getPurchase(attachment.getPurchaseId());
        if (attachment.getRefundNqt() < 0 || attachment.getRefundNqt() > Constants.MAX_BALANCE_NQT
                || (purchase != null &&
                (purchase.getBuyerId() != transaction.getRecipientId()
                        || transaction.getSenderId() != purchase.getSellerId()))) {
          throw new SignumException.NotValidException("Invalid digital goods refund: " + JSON.toJsonString(attachment.getJsonObject()));
        }
        if (transaction.getEncryptedMessage() != null && ! transaction.getEncryptedMessage().isText()) {
          throw new SignumException.NotValidException("Only text encrypted messages allowed");
        }
        if (purchase == null || purchase.getEncryptedGoods() == null || purchase.getRefundNQT() != 0) {
          throw new SignumException.NotCurrentlyValidException("Purchase does not exist or is not delivered or is already refunded");
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
      return TransactionType.TYPE_ACCOUNT_CONTROL.getType();
    }

    @Override
    protected final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
      return true;
    }

    @Override
    protected final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
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
      protected Attachment.AccountControlEffectiveBalanceLeasing parseAttachment(JsonObject attachmentData) {
        return new Attachment.AccountControlEffectiveBalanceLeasing(attachmentData);
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        // This tx type is actually used before block 10k on mainnet, so we cannot actually remove it
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        if (Signum.getFluxCapacitor().getValue(FluxValues.SODIUM)) throw new SignumException.NotCurrentlyValidException("Effective Balance Leasing disabled after Sodium HF");

        Attachment.AccountControlEffectiveBalanceLeasing attachment = (Attachment.AccountControlEffectiveBalanceLeasing)transaction.getAttachment();
        Account recipientAccount = accountService.getAccount(transaction.getRecipientId());
        if (transaction.getSenderId() == transaction.getRecipientId()
                || transaction.getAmountNqt() != 0
                || attachment.getPeriod() < 1440) {
          throw new SignumException.NotValidException("Invalid effective balance leasing: " + JSON.toJsonString(transaction.getJsonObject()) + " transaction " + transaction.getStringId());
        }
        if (recipientAccount == null
                || (recipientAccount.getPublicKey() == null && ! transaction.getStringId().equals("5081403377391821646"))) {
          throw new SignumException.NotCurrentlyValidException("Invalid effective balance leasing: "
                  + " recipient account " + transaction.getRecipientId() + " not found or no public key published");
        }
      }

      @Override
      public boolean hasRecipient() {
        return true;
      }

    };

  }

  public abstract static class SignaMining extends TransactionType {

    private SignaMining() {}

    @Override
    public final byte getType() {
      return TransactionType.TYPE_SIGNA_MINING.getType();
    }

    public static final TransactionType REWARD_RECIPIENT_ASSIGNMENT = new SignaMining() {

      @Override
      protected final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return true;
      }

      @Override
      protected final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {}

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_SIGNA_MINING_REWARD_RECIPIENT_ASSIGNMENT;
      }

      @Override
      public String getDescription() {
        return "Reward Recipient Assignment";
      }

      @Override
      public Attachment.SignaMiningRewardRecipientAssignment
      parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return new Attachment.SignaMiningRewardRecipientAssignment(buffer, transactionVersion);
      }

      @Override
      protected Attachment.SignaMiningRewardRecipientAssignment parseAttachment(JsonObject attachmentData) {
        return new Attachment.SignaMiningRewardRecipientAssignment(attachmentData);
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        accountService.setRewardRecipientAssignment(senderAccount, recipientAccount.getId());
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        if (! fluxCapacitor.getValue(FluxValues.DIGITAL_GOODS_STORE)) {
          return TransactionDuplicationKey.IS_NEVER_DUPLICATE; // sync fails after 7007 without this
        }

        return new TransactionDuplicationKey(SignaMining.REWARD_RECIPIENT_ASSIGNMENT, Convert.toUnsignedLong(transaction.getSenderId()));
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        int height = blockchain.getLastBlock().getHeight() + 1;
        Account sender = accountService.getAccount(transaction.getSenderId());

        if (sender == null) {
          throw new SignumException.NotCurrentlyValidException("Sender not yet known ?!");
        }

        Account.RewardRecipientAssignment rewardAssignment = accountService.getRewardRecipientAssignment(sender);
        if (rewardAssignment != null && rewardAssignment.getFromHeight() >= height) {
          throw new SignumException.NotCurrentlyValidException("Cannot reassign reward recipient before previous goes into effect: " + JSON.toJsonString(transaction.getJsonObject()));
        }
        Account recip = accountService.getAccount(transaction.getRecipientId());
        if (recip == null || recip.getPublicKey() == null) {
          throw new SignumException.NotValidException("Reward recipient must have public key saved in blockchain: " + JSON.toJsonString(transaction.getJsonObject()));
        }

        if (transaction.getAmountNqt() != 0 || transaction.getFeeNqt() < fluxCapacitor.getValue(FluxValues.FEE_QUANT, height)) {
          throw new SignumException.NotValidException("Reward recipient assignment transaction must have 0 send amount and at least minimum fee: " + JSON.toJsonString(transaction.getJsonObject()));
        }

        if (!Signum.getFluxCapacitor().getValue(FluxValues.REWARD_RECIPIENT_ENABLE, height)) {
          throw new SignumException.NotCurrentlyValidException("Reward recipient assignment not allowed before block " + Signum.getFluxCapacitor().getStartingHeight(FluxValues.REWARD_RECIPIENT_ENABLE));
        }
      }

      @Override
      public boolean hasRecipient() {
        return true;
      }
    };

    public static final TransactionType COMMITMENT_ADD = new SignaMining() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_SIGNA_MINING_COMMITMENT_ADD;
      }

      @Override
      public String getDescription() {
        return "Add Commitment";
      }

      @Override
      public Attachment.CommitmentAdd
      parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return new Attachment.CommitmentAdd(buffer, transactionVersion);
      }

      @Override
      protected Attachment.CommitmentAdd parseAttachment(JsonObject attachmentData) {
        return new Attachment.CommitmentAdd(attachmentData);
      }

      protected Long calculateAttachmentTotalAmountNQT(Transaction transaction) {
        CommitmentAdd commitmentAdd = (CommitmentAdd) transaction.getAttachment();
        Long totalAmountNQT = commitmentAdd.getAmountNqt();
        return totalAmountNQT;
      }

      @Override
      protected boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        logger.trace("TransactionType COMMITMENT_ADD");
        CommitmentAdd commitmentAdd = (CommitmentAdd) transaction.getAttachment();
        Long totalAmountNQT = commitmentAdd.getAmountNqt();
        if(totalAmountNQT < 0L)
          return false;

        if (senderAccount.getUnconfirmedBalanceNqt() >= totalAmountNQT ) {
          accountService.addToUnconfirmedBalanceNQT(senderAccount, -totalAmountNQT);
          return true;
        }
        return false;
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        // Nothing to apply
      }

      @Override
      protected void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        CommitmentAdd commitmentAdd = (CommitmentAdd) transaction.getAttachment();
        Long totalAmountNQT = commitmentAdd.getAmountNqt();
        accountService.addToUnconfirmedBalanceNQT(senderAccount, totalAmountNQT);
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        CommitmentAdd attachment = (CommitmentAdd) transaction.getAttachment();
        return new TransactionDuplicationKey(SignaMining.COMMITMENT_ADD, Convert.toUnsignedLong(transaction.getSenderId()) + ":" + attachment.getAmountNqt());
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        int height = blockchain.getLastBlock().getHeight() + 1;
        Account sender = accountService.getAccount(transaction.getSenderId());

        if (sender == null) {
          throw new SignumException.NotCurrentlyValidException("Sender not yet known ?!");
        }

        if (!Signum.getFluxCapacitor().getValue(FluxValues.SIGNUM, height)) {
          throw new SignumException.NotCurrentlyValidException("Add commitment not allowed before block " + Signum.getFluxCapacitor().getStartingHeight(FluxValues.SIGNUM));
        }
      }

      @Override
      public boolean hasRecipient() {
        return false;
      }
    };

    public static final TransactionType COMMITMENT_REMOVE = new SignaMining() {

      @Override
      public final byte getSubtype() {
        return TransactionType.SUBTYPE_SIGNA_MINING_COMMITMENT_REMOVE;
      }

      @Override
      public String getDescription() {
        return "Remove Commitment";
      }

      @Override
      public Attachment.CommitmentRemove
      parseAttachment(ByteBuffer buffer, byte transactionVersion) {
        return new Attachment.CommitmentRemove(buffer, transactionVersion);
      }

      @Override
      protected Attachment.CommitmentRemove parseAttachment(JsonObject attachmentData) {
        return new Attachment.CommitmentRemove(attachmentData);
      }

      @Override
      protected boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        logger.trace("TransactionType COMMITMENT_REMOVE");
        CommitmentRemove commitmentRemove = (CommitmentRemove) transaction.getAttachment();
        long totalAmountNQT = commitmentRemove.getAmountNqt();
        if(totalAmountNQT < 0L)
          return false;

        blockchain = Signum.getBlockchain();
        int nBlocksMined = blockchain.getBlocksCount(senderAccount.getId(), blockchain.getHeight() - Constants.MAX_ROLLBACK, blockchain.getHeight());
        if(nBlocksMined > 0) {
          // need to wait since the last block mined to remove any commitment
          return false;
        }
        long amountCommitted = blockchain.getCommittedAmount(senderAccount.getId(), blockchain.getHeight(), blockchain.getHeight(), transaction);
        if (amountCommitted >= totalAmountNQT ) {
          accountService.addToUnconfirmedBalanceNQT(senderAccount, totalAmountNQT);
          return true;
        }
        return false;
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        // Nothing to apply
      }

      @Override
      protected void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        CommitmentRemove commitmentRemove = (CommitmentRemove) transaction.getAttachment();
        long totalAmountNQT = commitmentRemove.getAmountNqt();

        accountService.addToUnconfirmedBalanceNQT(senderAccount, -totalAmountNQT);
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        // All commitment remove transactions from the same account are considered duplicates, so just the one with highest fee is kept
        return new TransactionDuplicationKey(SignaMining.COMMITMENT_REMOVE, Convert.toUnsignedLong(transaction.getSenderId()));
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        int height = blockchain.getLastBlock().getHeight() + 1;
        Account sender = accountService.getAccount(transaction.getSenderId());

        if (sender == null) {
          throw new SignumException.NotCurrentlyValidException("Sender not yet known ?!");
        }

        if (!Signum.getFluxCapacitor().getValue(FluxValues.SIGNUM, height)) {
          throw new SignumException.NotCurrentlyValidException("Add commitment not allowed before block " + Signum.getFluxCapacitor().getStartingHeight(FluxValues.SIGNUM));
        }
      }

      @Override
      public boolean hasRecipient() {
        return false;
      }
    };

  }

  public abstract static class AdvancedPayment extends TransactionType {

    private AdvancedPayment() {}

    @Override
    public final byte getType() {
      return TransactionType.TYPE_ADVANCED_PAYMENT.getType();
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
      public Attachment.AdvancedPaymentEscrowCreation parseAttachment(ByteBuffer buffer, byte transactionVersion) throws SignumException.NotValidException {
        return new Attachment.AdvancedPaymentEscrowCreation(buffer, transactionVersion);
      }

      @Override
      protected Attachment.AdvancedPaymentEscrowCreation parseAttachment(JsonObject attachmentData) throws SignumException.NotValidException {
        return new Attachment.AdvancedPaymentEscrowCreation(attachmentData);
      }

      @Override
      protected final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        logger.trace("TransactionType ESCROW_CREATION");
        Long totalAmountNQT = calculateAttachmentTotalAmountNQT(transaction);
        if (senderAccount.getUnconfirmedBalanceNqt() < totalAmountNQT) {
          return false;
        }
        accountService.addToUnconfirmedBalanceNQT(senderAccount, -totalAmountNQT);
        return true;
      }

      @Override
      public Long calculateAttachmentTotalAmountNQT(Transaction transaction) {
        Attachment.AdvancedPaymentEscrowCreation attachment = (Attachment.AdvancedPaymentEscrowCreation) transaction.getAttachment();
        return Convert.safeAdd(attachment.getAmountNqt(), Convert.safeMultiply(attachment.getTotalSigners(), Constants.ONE_SIGNA));
      }

      @Override
      protected final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.AdvancedPaymentEscrowCreation attachment = (Attachment.AdvancedPaymentEscrowCreation) transaction.getAttachment();
        Long totalAmountNQT = calculateAttachmentTotalAmountNQT(transaction);
        accountService.addToBalanceNQT(senderAccount, -totalAmountNQT);
        Collection<Long> signers = attachment.getSigners();
        signers.forEach(signer -> accountService.addToBalanceAndUnconfirmedBalanceNQT(accountService.getOrAddAccount(signer), Constants.ONE_SIGNA));
        escrowService.addEscrowTransaction(senderAccount,
                recipientAccount,
                transaction.getId(),
                attachment.getAmountNqt(),
                attachment.getRequiredSigners(),
                attachment.getSigners(),
                transaction.getTimestamp() + attachment.getDeadline(),
                attachment.getDeadlineAction());
      }

      @Override
      protected final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        accountService.addToUnconfirmedBalanceNQT(senderAccount, calculateAttachmentTotalAmountNQT(transaction));
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        return TransactionDuplicationKey.IS_NEVER_DUPLICATE;
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.AdvancedPaymentEscrowCreation attachment = (Attachment.AdvancedPaymentEscrowCreation) transaction.getAttachment();
        Long totalAmountNQT = Convert.safeAdd(attachment.getAmountNqt(), transaction.getFeeNqt());
        if (transaction.getSenderId() == transaction.getRecipientId()) {
          throw new SignumException.NotValidException("Escrow must have different sender and recipient");
        }
        totalAmountNQT = Convert.safeAdd(totalAmountNQT, attachment.getTotalSigners() * Constants.ONE_SIGNA);
        if (transaction.getAmountNqt() != 0) {
          throw new SignumException.NotValidException("Transaction sent amount must be 0 for escrow");
        }
        if (totalAmountNQT.compareTo(0L) < 0 ||
                totalAmountNQT.compareTo(Constants.MAX_BALANCE_NQT) > 0)
        {
          throw new SignumException.NotValidException("Invalid escrow creation amount");
        }
        if (attachment.getRequiredSigners() < 1 || attachment.getRequiredSigners() > 10) {
          throw new SignumException.NotValidException("Escrow required signers much be 1 - 10");
        }
        if (attachment.getRequiredSigners() > attachment.getTotalSigners()) {
          throw new SignumException.NotValidException("Cannot have more required than signers on escrow");
        }
        if (attachment.getTotalSigners() < 1 || attachment.getTotalSigners() > 10) {
          throw new SignumException.NotValidException("Escrow transaction requires 1 - 10 signers");
        }
        if (attachment.getDeadline() < 1 || attachment.getDeadline() > 7776000) { // max deadline 3 months
          throw new SignumException.NotValidException("Escrow deadline must be 1 - 7776000 seconds");
        }
        if (attachment.getDeadlineAction() == null || attachment.getDeadlineAction() == Escrow.DecisionType.UNDECIDED) {
          throw new SignumException.NotValidException("Invalid deadline action for escrow");
        }
        if (attachment.getSigners().contains(transaction.getSenderId()) ||
                attachment.getSigners().contains(transaction.getRecipientId())) {
          throw new SignumException.NotValidException("Escrow sender and recipient cannot be signers");
        }
        if (!escrowService.isEnabled()) {
          throw new SignumException.NotYetEnabledException("Escrow not yet enabled");
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
      protected Attachment.AdvancedPaymentEscrowSign parseAttachment(JsonObject attachmentData) {
        return new Attachment.AdvancedPaymentEscrowSign(attachmentData);
      }

      @Override
      protected final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return true;
      }

      @Override
      protected final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.AdvancedPaymentEscrowSign attachment = (Attachment.AdvancedPaymentEscrowSign) transaction.getAttachment();
        Escrow escrow = escrowService.getEscrowTransaction(attachment.getEscrowId());
        escrowService.sign(senderAccount.getId(), attachment.getDecision(), escrow);
      }

      @Override
      protected final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
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
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.AdvancedPaymentEscrowSign attachment = (Attachment.AdvancedPaymentEscrowSign) transaction.getAttachment();
        if (transaction.getAmountNqt() != 0) {
          throw new SignumException.NotValidException("Escrow signing must have amount 0");
        }
        if (attachment.getEscrowId() == null || attachment.getDecision() == null) {
          throw new SignumException.NotValidException("Escrow signing requires escrow id and decision set");
        }
        Escrow escrow = escrowService.getEscrowTransaction(attachment.getEscrowId());
        if (escrow == null) {
          throw new SignumException.NotValidException("Escrow transaction not found");
        }
        if (!escrowService.isIdSigner(transaction.getSenderId(), escrow) &&
                !escrow.getSenderId().equals(transaction.getSenderId()) &&
                !escrow.getRecipientId().equals(transaction.getSenderId())) {
          throw new SignumException.NotValidException("Sender is not a participant in specified escrow");
        }
        if (escrow.getSenderId().equals(transaction.getSenderId()) && attachment.getDecision() != Escrow.DecisionType.RELEASE) {
          throw new SignumException.NotValidException("Escrow sender can only release");
        }
        if (escrow.getRecipientId().equals(transaction.getSenderId()) && attachment.getDecision() != Escrow.DecisionType.REFUND) {
          throw new SignumException.NotValidException("Escrow recipient can only refund");
        }
        if (!escrowService.isEnabled()) {
          throw new SignumException.NotYetEnabledException("Escrow not yet enabled");
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
      protected Attachment.AdvancedPaymentEscrowResult parseAttachment(JsonObject attachmentData) {
        return new Attachment.AdvancedPaymentEscrowResult(attachmentData);
      }

      @Override
      protected final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return false;
      }

      @Override
      protected final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        // Nothing to apply.
      }

      @Override
      protected final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        // Nothing to undo.
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        return TransactionDuplicationKey.IS_ALWAYS_DUPLICATE;
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        throw new SignumException.NotValidException("Escrow result never validates");
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
      protected Attachment.AdvancedPaymentSubscriptionSubscribe parseAttachment(JsonObject attachmentData) {
        return new Attachment.AdvancedPaymentSubscriptionSubscribe(attachmentData);
      }

      @Override
      protected final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return true;
      }

      @Override
      protected final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.AdvancedPaymentSubscriptionSubscribe attachment = (Attachment.AdvancedPaymentSubscriptionSubscribe) transaction.getAttachment();
        subscriptionService.addSubscription(senderAccount, recipientAccount.getId(), transaction.getId(), transaction.getAmountNqt(), transaction.getTimestamp(), attachment.getFrequency());
      }

      @Override
      protected final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        // Nothing to undo.
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        return TransactionDuplicationKey.IS_NEVER_DUPLICATE;
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.AdvancedPaymentSubscriptionSubscribe attachment = (Attachment.AdvancedPaymentSubscriptionSubscribe) transaction.getAttachment();
        if (attachment.getFrequency() == null ||
                attachment.getFrequency() < Constants.SIGNUM_SUBSCRIPTION_MIN_FREQ ||
                attachment.getFrequency() > Constants.SIGNUM_SUBSCRIPTION_MAX_FREQ) {
          throw new SignumException.NotValidException("Invalid subscription frequency");
        }
        if (transaction.getAmountNqt() < Constants.ONE_SIGNA || transaction.getAmountNqt() > Constants.MAX_BALANCE_NQT) {
          throw new SignumException.NotValidException("Subscriptions must be at least one " + Signum.getPropertyService().getString(Props.VALUE_SUFIX));
        }
        if (transaction.getSenderId() == transaction.getRecipientId()) {
          throw new SignumException.NotValidException("Cannot create subscription to same address");
        }
        if (!subscriptionService.isEnabled()) {
          throw new SignumException.NotYetEnabledException("Subscriptions not yet enabled");
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
      protected Attachment.AdvancedPaymentSubscriptionCancel parseAttachment(JsonObject attachmentData) {
        return new Attachment.AdvancedPaymentSubscriptionCancel(attachmentData);
      }

      @Override
      protected final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        logger.trace("TransactionType SUBSCRIPTION_CANCEL");
        Attachment.AdvancedPaymentSubscriptionCancel attachment = (Attachment.AdvancedPaymentSubscriptionCancel) transaction.getAttachment();
        subscriptionService.addRemoval(attachment.getSubscriptionId());
        return true;
      }

      @Override
      protected final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.AdvancedPaymentSubscriptionCancel attachment = (Attachment.AdvancedPaymentSubscriptionCancel) transaction.getAttachment();
        subscriptionService.removeSubscription(attachment.getSubscriptionId());
      }

      @Override
      protected final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        // Nothing to undo.
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        Attachment.AdvancedPaymentSubscriptionCancel attachment = (Attachment.AdvancedPaymentSubscriptionCancel) transaction.getAttachment();
        return new TransactionDuplicationKey(AdvancedPayment.SUBSCRIPTION_CANCEL, Convert.toUnsignedLong(attachment.getSubscriptionId()));
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        Attachment.AdvancedPaymentSubscriptionCancel attachment = (Attachment.AdvancedPaymentSubscriptionCancel) transaction.getAttachment();
        if (attachment.getSubscriptionId() == null) {
          throw new SignumException.NotValidException("Subscription cancel must include subscription id");
        }

        Subscription subscription = subscriptionService.getSubscription(attachment.getSubscriptionId());
        if (subscription == null) {
          throw new SignumException.NotValidException("Subscription cancel must contain current subscription id");
        }

        if (!subscription.getSenderId().equals(transaction.getSenderId()) &&
                !subscription.getRecipientId().equals(transaction.getSenderId())) {
          throw new SignumException.NotValidException("Subscription cancel can only be done by participants");
        }

        if (!subscriptionService.isEnabled()) {
          throw new SignumException.NotYetEnabledException("Subscription cancel not yet enabled");
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
      protected Attachment.AdvancedPaymentSubscriptionPayment parseAttachment(JsonObject attachmentData) {
        return new Attachment.AdvancedPaymentSubscriptionPayment(attachmentData);
      }

      @Override
      protected final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return false;
      }

      @Override
      protected final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        // Nothing to apply.
      }

      @Override
      protected final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        // Nothing to undo.
      }

      @Override
      public TransactionDuplicationKey getDuplicationKey(Transaction transaction) {
        return TransactionDuplicationKey.IS_ALWAYS_DUPLICATE;
      }

      @Override
      protected void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
        throw new SignumException.NotValidException("Subscription payment never validates");
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
      return TransactionType.TYPE_AUTOMATED_TRANSACTIONS.getType();
    }

    @Override
    protected boolean applyAttachmentUnconfirmed(Transaction transaction,Account senderAccount){
      return true;
    }

    @Override
    protected void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount){

    }

    @Override
    protected final void validateAttachment(Transaction transaction) throws SignumException.ValidationException {
      if (transaction.getAmountNqt() != 0) {
        throw new SignumException.NotValidException("Invalid automated transaction transaction");
      }
      doValidateAttachment(transaction);
    }

    abstract void doValidateAttachment(Transaction transaction) throws SignumException.ValidationException;


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
      protected AbstractAttachment parseAttachment(JsonObject attachmentData) {
        return new AutomatedTransactionsCreation(attachmentData);
      }

      @Override
      void doValidateAttachment(Transaction transaction)
              throws ValidationException {
        if (! fluxCapacitor.getValue(FluxValues.AUTOMATED_TRANSACTION_BLOCK, blockchain.getLastBlock().getHeight())) {
          throw new SignumException.NotYetEnabledException("Automated Transactions not yet enabled at height " + blockchain.getLastBlock().getHeight());
        }
        if (transaction.getSignature() != null && accountService.getAccount(transaction.getId()) != null) {
          Account existingAccount = accountService.getAccount(transaction.getId());
          if (existingAccount.getPublicKey() != null && !Arrays.equals(existingAccount.getPublicKey(), new byte[32]))
            throw new SignumException.NotValidException("Account with id already exists");
        }
        Attachment.AutomatedTransactionsCreation attachment = (Attachment.AutomatedTransactionsCreation) transaction.getAttachment();
        if (attachment.getCreationBytes() == null) {
          throw new SignumException.NotCurrentlyValidException("AT creation bytes cannot be null");
        }
        long totalPages;
        int minCodePages = 1;
        try {
          AtMachineState thisNewAtCreation = new AtMachineState(null, null, attachment.getCreationBytes(), 0);
          if(thisNewAtCreation.getApCodeBytes().length == 0) {
            // check if we have a reference for the code
            Transaction referenceTransaction = Signum.getBlockchain().getTransactionByFullHash(transaction.getReferencedTransactionFullHash());
            minCodePages = 0;

            if(referenceTransaction!=null && referenceTransaction.getAttachment() instanceof Attachment.AutomatedTransactionsCreation) {
              Attachment.AutomatedTransactionsCreation atCreationAttachmentRef = (Attachment.AutomatedTransactionsCreation)referenceTransaction.getAttachment();
              AtMachineState atCreationRef = new AtMachineState(null, null, atCreationAttachmentRef.getCreationBytes(), referenceTransaction.getHeight());
              // we need a code and also compatible page sizes
              if(atCreationRef.getApCodeBytes().length == 0
                  || atCreationRef.getDataPages() != thisNewAtCreation.getDataPages()
                  || atCreationRef.getCallStackPages() != thisNewAtCreation.getCallStackPages()
                  || atCreationRef.getUserStackPages() != thisNewAtCreation.getUserStackPages()) {
                referenceTransaction = null;
              }
            }
            if(referenceTransaction == null) {
              throw new SignumException.NotCurrentlyValidException("Invalid reference transaction for the AT code");
            }
          }
          totalPages = AtController.checkCreationBytes(attachment.getCreationBytes(), blockchain.getHeight(), minCodePages);
        }
        catch (AtException e) {
          throw new SignumException.NotCurrentlyValidException("Invalid AT creation bytes", e);
        }
        long requiredFee = totalPages * AtConstants.getInstance().costPerPage( blockchain.getHeight() );
        if (transaction.getFeeNqt() <  requiredFee){
          throw new SignumException.NotValidException("Insufficient fee for AT creation, using " + transaction.getFeeNqt()
            + ", minimum: " + requiredFee);
        }
        if (fluxCapacitor.getValue(FluxValues.AT_FIX_BLOCK_3)) {
          if (attachment.getName().length() > Constants.MAX_AUTOMATED_TRANSACTION_NAME_LENGTH) {
            throw new SignumException.NotValidException("Name of automated transaction over size limit");
          }
          if (attachment.getDescription().length() > Constants.MAX_AUTOMATED_TRANSACTION_DESCRIPTION_LENGTH) {
            throw new SignumException.NotValidException("Description of automated transaction over size limit");
          }
        }
      }

      @Override
      protected void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        Attachment.AutomatedTransactionsCreation attachment = (Attachment.AutomatedTransactionsCreation) transaction.getAttachment();

        long codeHashId = 0L;
        AtMachineState thisNewAtCreation = new AtMachineState(null, null, attachment.getCreationBytes(), 0);
        if(thisNewAtCreation.getApCodeBytes().length == 0) {
          Transaction referenceTransaction = Signum.getBlockchain().getTransactionByFullHash(transaction.getReferencedTransactionFullHash());
          Attachment.AutomatedTransactionsCreation atCreationAttachmentRef = (Attachment.AutomatedTransactionsCreation)referenceTransaction.getAttachment();
          AtMachineState atCreationRef = new AtMachineState(null, null, atCreationAttachmentRef.getCreationBytes(), referenceTransaction.getHeight());
          codeHashId = atCreationRef.getApCodeHashId();
        }

        AT.addAT( transaction.getId() , transaction.getSenderId() , attachment.getName() , attachment.getDescription() , attachment.getCreationBytes() , transaction.getHeight(), codeHashId );
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
      protected AbstractAttachment parseAttachment(JsonObject attachmentData) {
        return Attachment.AT_PAYMENT;
      }

      @Override
      void doValidateAttachment(Transaction transaction) throws SignumException.ValidationException {
        throw new SignumException.NotValidException("AT payment never validates");
      }

      @Override
      protected void applyAttachment(Transaction transaction,
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

  public long minimumFeeNQT(int height, Transaction transaction) {
    if (height < BASELINE_FEE_HEIGHT) {
      return 0; // No need to validate fees before baseline block
    }
    Fee fee = getBaselineFee(height);
    int appendageMultiplier = transaction.getAppendagesSize()/Constants.ORDINARY_TRANSACTION_BYTES;
    return Convert.safeAdd(fee.getConstantFee(), Convert.safeMultiply(appendageMultiplier, fee.getAppendagesFee()));
  }

  public Fee getBaselineFee(int height) {
    long FEE_QUANT = fluxCapacitor.getValue(FluxValues.FEE_QUANT, height);
    if(fluxCapacitor.getValue(FluxValues.SPEEDWAY, height)) {
      return new Fee(FEE_QUANT, FEE_QUANT);
    }
    return new Fee((fluxCapacitor.getValue(FluxValues.PRE_POC2, height) ? FEE_QUANT : ONE_SIGNA), 0);
  }

  public static final class Fee {
    private final long constantFee;
    private final long appendagesFee;

    public Fee(long constantFee, long appendagesFee) {
      this.constantFee = constantFee;
      this.appendagesFee = appendagesFee;
    }

    public long getConstantFee() {
      return constantFee;
    }

    public long getAppendagesFee() {
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

  private static final Collection<IndirectIncoming> NO_INDIRECTS = Collections.emptyList();
  public Collection<IndirectIncoming> getIndirectIncomings(Transaction transaction) {
    // by default there is no indirect incomings, transaction types should implement it
    return NO_INDIRECTS;
  }

}
