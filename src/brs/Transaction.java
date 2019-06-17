package brs;

import brs.Appendix.AbstractAppendix;
import brs.TransactionType.Payment;
import brs.crypto.Crypto;
import brs.fluxcapacitor.FluxValues;
import brs.transactionduplicates.TransactionDuplicationKey;
import brs.util.Convert;
import brs.util.JSON;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class Transaction implements Comparable<Transaction> {

  private static final Logger logger = LoggerFactory.getLogger(Transaction.class);

  public static class Builder {

    private final short deadline;
    private final byte[] senderPublicKey;
    private final long amountNQT;
    private final long feeNQT;
    private final TransactionType type;
    private final byte version;
    private final int timestamp;
    private final Attachment.AbstractAttachment attachment;

    private long recipientId;
    private String referencedTransactionFullHash;
    private byte[] signature;
    private Appendix.Message message;
    private Appendix.EncryptedMessage encryptedMessage;
    private Appendix.EncryptToSelfMessage encryptToSelfMessage;
    private Appendix.PublicKeyAnnouncement publicKeyAnnouncement;
    private long blockId;
    private int height = Integer.MAX_VALUE;
    private long id;
    private long senderId;
    private int blockTimestamp = -1;
    private String fullHash;
    private int ecBlockHeight;
    private long ecBlockId;

    public Builder(byte version, byte[] senderPublicKey, long amountNQT, long feeNQT, int timestamp, short deadline,
                       Attachment.AbstractAttachment attachment) {
      this.version = version;
      this.timestamp = timestamp;
      this.deadline = deadline;
      this.senderPublicKey = senderPublicKey;
      this.amountNQT = amountNQT;
      this.feeNQT = feeNQT;
      this.attachment = attachment;
      this.type = attachment.getTransactionType();
    }

    public Transaction build() throws BurstException.NotValidException {
      return new Transaction(this);
    }

    public Builder recipientId(long recipientId) {
      this.recipientId = recipientId;
      return this;
    }

    public Builder referencedTransactionFullHash(String referencedTransactionFullHash) {
      this.referencedTransactionFullHash = referencedTransactionFullHash;
      return this;
    }

    public Builder referencedTransactionFullHash(byte[] referencedTransactionFullHash) {
      if (referencedTransactionFullHash != null) {
        this.referencedTransactionFullHash = Convert.toHexString(referencedTransactionFullHash);
      }
      return this;
    }

    public Builder message(Appendix.Message message) {
      this.message = message;
      return this;
    }

    public Builder encryptedMessage(Appendix.EncryptedMessage encryptedMessage) {
      this.encryptedMessage = encryptedMessage;
      return this;
    }

    public Builder encryptToSelfMessage(Appendix.EncryptToSelfMessage encryptToSelfMessage) {
      this.encryptToSelfMessage = encryptToSelfMessage;
      return this;
    }

    public Builder publicKeyAnnouncement(Appendix.PublicKeyAnnouncement publicKeyAnnouncement) {
      this.publicKeyAnnouncement = publicKeyAnnouncement;
      return this;
    }

    public Builder id(long id) {
      this.id = id;
      return this;
    }

    public Builder signature(byte[] signature) {
      this.signature = signature;
      return this;
    }

    public Builder blockId(long blockId) {
      this.blockId = blockId;
      return this;
    }

    public Builder height(int height) {
      this.height = height;
      return this;
    }

    public Builder senderId(long senderId) {
      this.senderId = senderId;
      return this;
    }

    Builder fullHash(String fullHash) {
      this.fullHash = fullHash;
      return this;
    }

    public Builder fullHash(byte[] fullHash) {
      if (fullHash != null) {
        this.fullHash = Convert.toHexString(fullHash);
      }
      return this;
    }

    public Builder blockTimestamp(int blockTimestamp) {
      this.blockTimestamp = blockTimestamp;
      return this;
    }

    public Builder ecBlockHeight(int height) {
      this.ecBlockHeight = height;
      return this;
    }

    public Builder ecBlockId(long blockId) {
      this.ecBlockId = blockId;
      return this;
    }

  }

  private final short deadline;
  private final byte[] senderPublicKey;
  private final long recipientId;
  private final long amountNQT;
  private final long feeNQT;
  private final String referencedTransactionFullHash;
  private final TransactionType type;
  private final int ecBlockHeight;
  private final long ecBlockId;
  private final byte version;
  private final int timestamp;
  private final Attachment.AbstractAttachment attachment;
  private final Appendix.Message message;
  private final Appendix.EncryptedMessage encryptedMessage;
  private final Appendix.EncryptToSelfMessage encryptToSelfMessage;
  private final Appendix.PublicKeyAnnouncement publicKeyAnnouncement;

  private final List<Appendix.AbstractAppendix> appendages;
  private final int appendagesSize;

  private final AtomicInteger height = new AtomicInteger();
  private final AtomicLong blockId = new AtomicLong();
  private final AtomicReference<Block> block = new AtomicReference<>();
  private final AtomicReference<byte[]> signature = new AtomicReference<>();
  private final AtomicInteger blockTimestamp = new AtomicInteger();
  private final AtomicLong id = new AtomicLong();
  private final AtomicReference<String> stringId = new AtomicReference<>();
  private final AtomicLong senderId = new AtomicLong();
  private final AtomicReference<String> fullHash = new AtomicReference<>();

  private Transaction(Builder builder) throws BurstException.NotValidException {

    this.timestamp = builder.timestamp;
    this.deadline = builder.deadline;
    this.senderPublicKey = builder.senderPublicKey;
    this.recipientId = Optional.ofNullable(builder.recipientId).orElse(0L);
    this.amountNQT = builder.amountNQT;
    this.referencedTransactionFullHash = builder.referencedTransactionFullHash;
    this.signature.set(builder.signature);
    this.type = builder.type;
    this.version = builder.version;
    this.blockId.set(builder.blockId);
    this.height.set(builder.height);
    this.id.set(builder.id);
    this.senderId.set(builder.senderId);
    this.blockTimestamp.set(builder.blockTimestamp);
    this.fullHash.set(builder.fullHash);
    this.ecBlockHeight = builder.ecBlockHeight;
    this.ecBlockId = builder.ecBlockId;

    List<Appendix.AbstractAppendix> list = new ArrayList<>();
    if ((this.attachment = builder.attachment) != null) {
      list.add(this.attachment);
    }
    if ((this.message  = builder.message) != null) {
      list.add(this.message);
    }
    if ((this.encryptedMessage = builder.encryptedMessage) != null) {
      list.add(this.encryptedMessage);
    }
    if ((this.publicKeyAnnouncement = builder.publicKeyAnnouncement) != null) {
      list.add(this.publicKeyAnnouncement);
    }
    if ((this.encryptToSelfMessage = builder.encryptToSelfMessage) != null) {
      list.add(this.encryptToSelfMessage);
    }
    this.appendages = Collections.unmodifiableList(list);
    int countAppendeges = 0;
    for (Appendix appendage : appendages) {
      countAppendeges += appendage.getSize();
    }
    this.appendagesSize = countAppendeges;
    int effectiveHeight = (height.get() < Integer.MAX_VALUE ? height.get() : Burst.getBlockchain().getHeight());
    long minimumFeeNQT = type.minimumFeeNQT(effectiveHeight, countAppendeges);
    if(type == null || type.isSigned()) {
      if (builder.feeNQT > 0 && builder.feeNQT < minimumFeeNQT) {
        throw new BurstException.NotValidException(String.format("Requested fee %d less than the minimum fee %d",
                                                               builder.feeNQT, minimumFeeNQT));
      }
      if (builder.feeNQT <= 0) {
        feeNQT = minimumFeeNQT;
      } else {
        feeNQT = builder.feeNQT;
      }
    }
    else {
      feeNQT = builder.feeNQT;
    }

    if ((type == null || type.isSigned()) && (deadline < 1
            || feeNQT > Constants.MAX_BALANCE_NQT
            || amountNQT < 0
            || amountNQT > Constants.MAX_BALANCE_NQT
            || type == null)) {
      throw new BurstException.NotValidException("Invalid transaction parameters:\n type: " + type + ", timestamp: " + timestamp
              + ", deadline: " + deadline + ", fee: " + feeNQT + ", amount: " + amountNQT);
    }

    if (attachment == null || type != attachment.getTransactionType()) {
      throw new BurstException.NotValidException("Invalid attachment " + attachment + " for transaction of type " + type);
    }

    if (!type.hasRecipient() && attachment.getTransactionType() != Payment.MULTI_OUT && attachment.getTransactionType() != Payment.MULTI_SAME_OUT && (recipientId != 0 || getAmountNQT() != 0)) {
      throw new BurstException.NotValidException("Transactions of this type must have recipient == Genesis, amount == 0");
    }

    for (Appendix.AbstractAppendix appendage : appendages) {
      if (! appendage.verifyVersion(this.version)) {
        throw new BurstException.NotValidException("Invalid attachment version " + appendage.getVersion()
                                                 + " for transaction version " + this.version);
      }
    }

  }

  public short getDeadline() {
    return deadline;
  }

  public byte[] getSenderPublicKey() {
    return senderPublicKey;
  }

  public long getRecipientId() {
    return recipientId;
  }

  public long getAmountNQT() {
    return amountNQT;
  }

  public long getFeeNQT() {
    return feeNQT;
  }

  public String getReferencedTransactionFullHash() {
    return referencedTransactionFullHash;
  }

  public int getHeight() {
    return height.get();
  }

  public void setHeight(int height) {
    this.height.set(height);
  }

  public byte[] getSignature() {
    return signature.get();
  }

  public TransactionType getType() {
    return type;
  }

  public byte getVersion() {
    return version;
  }

  public long getBlockId() {
    return blockId.get();
  }

  public void setBlock(Block block) {
    this.block.set(block);
    this.blockId.set(block.getId());
    this.height.set(block.getHeight());
    this.blockTimestamp.set(block.getTimestamp());
  }

  void unsetBlock() {
    this.block.set(null);
    this.blockId.set(0);
    this.blockTimestamp.set(-1);
    // must keep the height set, as transactions already having been included in a popped-off block before
    // get priority when sorted for inclusion in a new block
  }

  public int getTimestamp() {
    return timestamp;
  }

  public int getBlockTimestamp() {
    return blockTimestamp.get();
  }

  public int getExpiration() {
    return timestamp + deadline * 60;
  }

  public Attachment getAttachment() {
    return attachment;
  }

  public List<AbstractAppendix> getAppendages() {
    return appendages;
  }

  public long getId() {
    if (id.get() == 0) {
      if (signature.get() == null && type.isSigned()) {
        throw new IllegalStateException("Transaction is not signed yet");
      }
      byte[] hash;
      if (useNQT()) {
        byte[] data = zeroSignature(getBytes());
        byte[] signatureHash = Crypto.sha256().digest(signature.get() != null ? signature.get() : new byte[64]);
        MessageDigest digest = Crypto.sha256();
        digest.update(data);
        hash = digest.digest(signatureHash);
      } else {
        hash = Crypto.sha256().digest(getBytes());
      }
      long longId = Convert.fullHashToId(hash);
      id.set(longId);
      stringId.set(Convert.toUnsignedLong(longId));
      fullHash.set(Convert.toHexString(hash));
    }
    return id.get();
  }

  public String getStringId() {
    if (stringId.get() == null) {
      getId();
      if (stringId.get() == null) {
        stringId.set(Convert.toUnsignedLong(id.get()));
      }
    }
    return stringId.get();
  }

  public String getFullHash() {
    if (fullHash.get() == null) {
      getId();
    }
    return fullHash.get();
  }

  public long getSenderId() {
    if (senderId.get() == 0 && (type == null || type.isSigned())) {
      senderId.set(Account.getId(senderPublicKey));
    }
    return senderId.get();
  }

  public Appendix.Message getMessage() {
    return message;
  }

  public Appendix.EncryptedMessage getEncryptedMessage() {
    return encryptedMessage;
  }

  public Appendix.EncryptToSelfMessage getEncryptToSelfMessage() {
    return encryptToSelfMessage;
  }

  public Appendix.PublicKeyAnnouncement getPublicKeyAnnouncement() {
    return publicKeyAnnouncement;
  }

  public byte[] getBytes() {
    try {
      ByteBuffer buffer = ByteBuffer.allocate(getSize());
      buffer.order(ByteOrder.LITTLE_ENDIAN);
      buffer.put(type.getType());
      buffer.put((byte) ((version << 4) | ( type.getSubtype() & 0xff ) ));
      buffer.putInt(timestamp);
      buffer.putShort(deadline);
      if(type.isSigned() || !Burst.getFluxCapacitor().getValue(FluxValues.AT_FIX_BLOCK_4)) {
        buffer.put(senderPublicKey);
      } else {
        buffer.putLong(senderId.get());
        buffer.put(new byte[24]);
      }
      buffer.putLong(type.hasRecipient() ? recipientId : Genesis.CREATOR_ID);
      if (useNQT()) {
        buffer.putLong(amountNQT);
        buffer.putLong(feeNQT);
        if (referencedTransactionFullHash != null) {
          buffer.put(Convert.parseHexString(referencedTransactionFullHash));
        } else {
          buffer.put(new byte[32]);
        }
      } else {
        buffer.putInt((int) (amountNQT / Constants.ONE_BURST));
        buffer.putInt((int) (feeNQT / Constants.ONE_BURST));
        if (referencedTransactionFullHash != null) {
          buffer.putLong(Convert.fullHashToId(Convert.parseHexString(referencedTransactionFullHash)));
        } else {
          buffer.putLong(0L);
        }
      }
      buffer.put(signature.get() != null ? signature.get() : new byte[64]);
      if (version > 0) {
        buffer.putInt(getFlags());
        buffer.putInt(ecBlockHeight);
        buffer.putLong(ecBlockId);
      }
      appendages.forEach(appendage -> appendage.putBytes(buffer));
      return buffer.array();
    } catch (RuntimeException e) {
      if (logger.isDebugEnabled()) {
        logger.debug("Failed to get transaction bytes for transaction: {}", JSON.toJsonString(getJsonObject()));
      }
      throw e;
    }
  }

  public static Transaction parseTransaction(byte[] bytes) throws BurstException.ValidationException {
    try {
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      buffer.order(ByteOrder.LITTLE_ENDIAN);
      byte type = buffer.get();
      byte subtype = buffer.get();
      byte version = (byte) ((subtype & 0xF0) >> 4);
      subtype = (byte) (subtype & 0x0F);
      int timestamp = buffer.getInt();
      short deadline = buffer.getShort();
      byte[] senderPublicKey = new byte[32];
      buffer.get(senderPublicKey);
      long recipientId = buffer.getLong();
      long amountNQT = buffer.getLong();
      long feeNQT = buffer.getLong();
      String referencedTransactionFullHash = null;
      byte[] referencedTransactionFullHashBytes = new byte[32];
      buffer.get(referencedTransactionFullHashBytes);
      if (Convert.emptyToNull(referencedTransactionFullHashBytes) != null) {
        referencedTransactionFullHash = Convert.toHexString(referencedTransactionFullHashBytes);
      }
      byte[] signature = new byte[64];
      buffer.get(signature);
      signature = Convert.emptyToNull(signature);
      int flags = 0;
      int ecBlockHeight = 0;
      long ecBlockId = 0;
      if (version > 0) {
        flags = buffer.getInt();
        ecBlockHeight = buffer.getInt();
        ecBlockId = buffer.getLong();
      }
      TransactionType transactionType = TransactionType.findTransactionType(type, subtype);
      Transaction.Builder builder = new Transaction.Builder(version, senderPublicKey, amountNQT, feeNQT,
                                                                            timestamp, deadline, transactionType.parseAttachment(buffer, version))
          .referencedTransactionFullHash(referencedTransactionFullHash)
          .signature(signature)
          .ecBlockHeight(ecBlockHeight)
          .ecBlockId(ecBlockId);
      if (transactionType.hasRecipient()) {
        builder.recipientId(recipientId);
      }

      transactionType.parseAppendices(builder, flags, version, buffer);

      return builder.build();
    } catch (BurstException.NotValidException|RuntimeException e) {
      if (logger.isDebugEnabled()) {
        logger.debug("Failed to parse transaction bytes: {}", Convert.toHexString(bytes));
      }
      throw e;
    }
  }

  public byte[] getUnsignedBytes() {
    return zeroSignature(getBytes());
  }

  public JsonObject getJsonObject() {
    JsonObject json = new JsonObject();
    json.addProperty("type", type.getType());
    json.addProperty("subtype", type.getSubtype());
    json.addProperty("timestamp", timestamp);
    json.addProperty("deadline", deadline);
    json.addProperty("senderPublicKey", Convert.toHexString(senderPublicKey));
    if (type.hasRecipient()) {
      json.addProperty("recipient", Convert.toUnsignedLong(recipientId));
    }
    json.addProperty("amountNQT", amountNQT);
    json.addProperty("feeNQT", feeNQT);
    if (referencedTransactionFullHash != null) {
      json.addProperty("referencedTransactionFullHash", referencedTransactionFullHash);
    }
    json.addProperty("ecBlockHeight", ecBlockHeight);
    json.addProperty("ecBlockId", Convert.toUnsignedLong(ecBlockId));
    json.addProperty("signature", Convert.toHexString(signature.get()));
    JsonObject attachmentJSON = new JsonObject();
    appendages.forEach(appendage -> JSON.addAll(attachmentJSON, appendage.getJsonObject()));
    json.add("attachment", attachmentJSON);
    json.addProperty("version", version);
    return json;
  }

  static Transaction parseTransaction(JsonObject transactionData, int height) throws BurstException.NotValidException {
    try {
      byte type = JSON.getAsByte(transactionData.get("type"));
      byte subtype = JSON.getAsByte(transactionData.get("subtype"));
      int timestamp = JSON.getAsInt(transactionData.get("timestamp"));
      short deadline = JSON.getAsShort(transactionData.get("deadline"));
      byte[] senderPublicKey = Convert.parseHexString(JSON.getAsString(transactionData.get("senderPublicKey")));
      long amountNQT = JSON.getAsLong(transactionData.get("amountNQT"));
      long feeNQT = JSON.getAsLong(transactionData.get("feeNQT"));
      String referencedTransactionFullHash = JSON.getAsString(transactionData.get("referencedTransactionFullHash"));
      byte[] signature = Convert.parseHexString(JSON.getAsString(transactionData.get("signature")));
      byte version = JSON.getAsByte(transactionData.get("version"));
      JsonObject attachmentData = JSON.getAsJsonObject(transactionData.get("attachment"));

      TransactionType transactionType = TransactionType.findTransactionType(type, subtype);
      if (transactionType == null) {
        throw new BurstException.NotValidException("Invalid transaction type: " + type + ", " + subtype);
      }
      Transaction.Builder builder = new Builder(version, senderPublicKey,
                                                                            amountNQT, feeNQT, timestamp, deadline,
                                                                            transactionType.parseAttachment(attachmentData))
          .referencedTransactionFullHash(referencedTransactionFullHash)
          .signature(signature)
          .height(height);
      if (transactionType.hasRecipient()) {
        long recipientId = Convert.parseUnsignedLong(JSON.getAsString(transactionData.get("recipient")));
        builder.recipientId(recipientId);
      }

      transactionType.parseAppendices(builder, attachmentData);

      if (version > 0) {
        builder.ecBlockHeight(JSON.getAsInt(transactionData.get("ecBlockHeight")));
        builder.ecBlockId(Convert.parseUnsignedLong(JSON.getAsString(transactionData.get("ecBlockId"))));
      }
      return builder.build();
    } catch (BurstException.NotValidException|RuntimeException e) {
      if (logger.isDebugEnabled()) {
        logger.debug("Failed to parse transaction: {}", JSON.toJsonString(transactionData));
      }
      throw e;
    }
  }


  public int getECBlockHeight() {
    return ecBlockHeight;
  }

  public long getECBlockId() {
    return ecBlockId;
  }

  public void sign(String secretPhrase) {
    if (signature.get() != null) {
      throw new IllegalStateException("Transaction already signed");
    }
    signature.set(Crypto.sign(getBytes(), secretPhrase));
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Transaction && this.getId() == ((Transaction)o).getId();
  }

  @Override
  public int hashCode() {
    return (int)(getId() ^ (getId() >>> 32));
  }

  public int compareTo(Transaction other) {
    return Long.compare(this.getId(), other.getId());
  }

  public boolean verifySignature() {
    byte[] data = zeroSignature(getBytes());
    return Crypto.verify(signature.get(), data, senderPublicKey, useNQT());
  }

  public int getSize() {
    return signatureOffset() + 64  + (version > 0 ? 4 + 4 + 8 : 0) + appendagesSize;
  }

  public int getAppendagesSize() {
    return appendagesSize;
  }

  private int signatureOffset() {
    return 1 + 1 + 4 + 2 + 32 + 8 + (useNQT() ? 8 + 8 + 32 : 4 + 4 + 8);
  }

  private boolean useNQT() {
    return this.height.get() > Constants.NQT_BLOCK
        && (this.height.get() < Integer.MAX_VALUE
            || Burst.getBlockchain().getHeight() >= Constants.NQT_BLOCK);
  }

  private byte[] zeroSignature(byte[] data) {
    int start = signatureOffset();
    for (int i = start; i < start + 64; i++) {
      data[i] = 0;
    }
    return data;
  }

  private int getFlags() {
    int flags = 0;
    int position = 1;
    if (message != null) {
      flags |= position;
    }
    position <<= 1;
    if (encryptedMessage != null) {
      flags |= position;
    }
    position <<= 1;
    if (publicKeyAnnouncement != null) {
      flags |= position;
    }
    position <<= 1;
    if (encryptToSelfMessage != null) {
      flags |= position;
    }
    return flags;
  }

  public TransactionDuplicationKey getDuplicationKey() {
    return type.getDuplicationKey(this);
  }
}
