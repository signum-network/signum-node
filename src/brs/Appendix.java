package brs;

import brs.crypto.EncryptedData;
import brs.fluxcapacitor.FluxValues;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.util.Convert;
import brs.util.JSON;
import com.google.gson.JsonObject;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;

import java.nio.ByteBuffer;
import java.util.Arrays;

public interface Appendix {

  int getSize();
  void putBytes(ByteBuffer buffer);
  JsonObject getJsonObject();
  byte getVersion();
  Any getProtobufMessage();

  abstract class AbstractAppendix implements Appendix {

    private final byte version;

    AbstractAppendix(JsonObject attachmentData) {
      version = JSON.getAsByte(attachmentData.get("version." + getAppendixName()));
    }

    AbstractAppendix(ByteBuffer buffer, byte transactionVersion) {
        version = (transactionVersion == 0) ? 0 : buffer.get();
    }

    AbstractAppendix(byte version) {
      this.version = version;
    }

    AbstractAppendix(int blockchainHeight) {
      this.version = (byte)(Burst.getFluxCapacitor().getValue(FluxValues.DIGITAL_GOODS_STORE, blockchainHeight) ? 1 : 0);
    }

    abstract String getAppendixName();

    @Override
    public final int getSize() {
      return getMySize() + (version > 0 ? 1 : 0);
    }

    abstract int getMySize();

    @Override
    public final void putBytes(ByteBuffer buffer) {
      if (version > 0) {
        buffer.put(version);
      }
      putMyBytes(buffer);
    }

    abstract void putMyBytes(ByteBuffer buffer);

    @Override
    public final JsonObject getJsonObject() {
      JsonObject json = new JsonObject();
      if (version > 0) {
        json.addProperty("version." + getAppendixName(), version);
      }
      putMyJSON(json);
      return json;
    }

    abstract void putMyJSON(JsonObject json);

    @Override
    public final byte getVersion() {
      return version;
    }

    boolean verifyVersion(byte transactionVersion) {
      return transactionVersion == 0 ? version == 0 : version > 0;
    }

    public abstract void validate(Transaction transaction) throws BurstException.ValidationException;

    public abstract void apply(Transaction transaction, Account senderAccount, Account recipientAccount);
  }

  class Message extends AbstractAppendix {

    static Message parse(JsonObject attachmentData) {
      if (attachmentData.get("message") == null) {
        return null;
      }
      return new Message(attachmentData);
    }

    private final byte[] messageBytes;
    private final boolean isText;

    public Message(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
      super(buffer, transactionVersion);
      int messageLength = buffer.getInt();
      this.isText = messageLength < 0; // ugly hack
      if (messageLength < 0) {
        messageLength &= Integer.MAX_VALUE;
      }
      if (messageLength > Constants.MAX_ARBITRARY_MESSAGE_LENGTH) {
        throw new BurstException.NotValidException("Invalid arbitrary message length: " + messageLength);
      }
      this.messageBytes = new byte[messageLength];
      buffer.get(this.messageBytes);
    }

    Message(JsonObject attachmentData) {
      super(attachmentData);
      String messageString = JSON.getAsString(attachmentData.get("message"));
      this.isText = Boolean.TRUE.equals(JSON.getAsBoolean(attachmentData.get("messageIsText")));
      this.messageBytes = isText ? Convert.toBytes(messageString) : Convert.parseHexString(messageString);
    }

    public Message(byte[] message, int blockchainHeight) {
      super(blockchainHeight);
      this.messageBytes = message;
      this.isText = false;
    }

    public Message(String string, int blockchainHeight) {
      super(blockchainHeight);
      this.messageBytes = Convert.toBytes(string);
      this.isText = true;
    }

    public Message(BrsApi.MessageAppendix messageAppendix, int blockchainHeight) {
      super(blockchainHeight);
      this.messageBytes = messageAppendix.getMessage().toByteArray();
      this.isText = messageAppendix.getIsText();
    }

    @Override
    String getAppendixName() {
      return "Message";
    }

    @Override
    int getMySize() {
      return 4 + messageBytes.length;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.putInt(isText ? (messageBytes.length | Integer.MIN_VALUE) : messageBytes.length);
      buffer.put(messageBytes);
    }

    @Override
    void putMyJSON(JsonObject json) {
      json.addProperty("message", isText ? Convert.toString(messageBytes) : Convert.toHexString(messageBytes));
      json.addProperty("messageIsText", isText);
    }

    @Override
    public void validate(Transaction transaction) throws BurstException.ValidationException {
      if (this.isText && transaction.getVersion() == 0) {
        throw new BurstException.NotValidException("Text messages not yet enabled");
      }
      if (transaction.getVersion() == 0 && transaction.getAttachment() != Attachment.ARBITRARY_MESSAGE) {
        throw new BurstException.NotValidException("Message attachments not enabled for version 0 transactions");
      }
      if (messageBytes.length > Constants.MAX_ARBITRARY_MESSAGE_LENGTH) {
        throw new BurstException.NotValidException("Invalid arbitrary message length: " + messageBytes.length);
      }
    }

    @Override
    public void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
      // Do nothing by default
    }

    public byte[] getMessageBytes() {
      return messageBytes;
    }

    public boolean isText() {
      return isText;
    }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.MessageAppendix.newBuilder()
              .setVersion(super.getVersion())
              .setMessage(ByteString.copyFrom(messageBytes))
              .setIsText(isText)
              .build());
    }
  }

  abstract class AbstractEncryptedMessage extends AbstractAppendix {

    private final EncryptedData encryptedData;
    private final boolean isText;

    private AbstractEncryptedMessage(ByteBuffer buffer, byte transactionVersion) throws BurstException.NotValidException {
      super(buffer, transactionVersion);
      int length = buffer.getInt();
      this.isText = length < 0;
      if (length < 0) {
        length &= Integer.MAX_VALUE;
      }
      this.encryptedData = EncryptedData.readEncryptedData(buffer, length, Constants.MAX_ENCRYPTED_MESSAGE_LENGTH);
    }

    private AbstractEncryptedMessage(JsonObject attachmentJSON, JsonObject encryptedMessageJSON) {
      super(attachmentJSON);
      byte[] data = Convert.parseHexString(JSON.getAsString(encryptedMessageJSON.get("data")));
      byte[] nonce = Convert.parseHexString(JSON.getAsString(encryptedMessageJSON.get("nonce")));
      this.encryptedData = new EncryptedData(data, nonce);
      this.isText = Boolean.TRUE.equals(JSON.getAsBoolean(encryptedMessageJSON.get("isText")));
    }

    private AbstractEncryptedMessage(EncryptedData encryptedData, boolean isText, int blockchainHeight) {
      super(blockchainHeight);
      this.encryptedData = encryptedData;
      this.isText = isText;
    }

    private AbstractEncryptedMessage(BrsApi.EncryptedMessageAppendix encryptedMessageAppendix, int blockchainHeight) {
      super(blockchainHeight);
      this.encryptedData = ProtoBuilder.parseEncryptedData(encryptedMessageAppendix.getEncryptedData());
      this.isText = encryptedMessageAppendix.getIsText();
    }

    @Override
    int getMySize() {
      return 4 + encryptedData.getSize();
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.putInt(isText ? (encryptedData.getData().length | Integer.MIN_VALUE) : encryptedData.getData().length);
      buffer.put(encryptedData.getData());
      buffer.put(encryptedData.getNonce());
    }

    @Override
    void putMyJSON(JsonObject json) {
      json.addProperty("data", Convert.toHexString(encryptedData.getData()));
      json.addProperty("nonce", Convert.toHexString(encryptedData.getNonce()));
      json.addProperty("isText", isText);
    }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.EncryptedMessageAppendix.newBuilder()
              .setVersion(super.getVersion())
              .setEncryptedData(ProtoBuilder.buildEncryptedData(encryptedData))
              .setType(getType())
              .build());
    }

    @Override
    public void validate(Transaction transaction) throws BurstException.ValidationException {
      if (encryptedData.getData().length > Constants.MAX_ENCRYPTED_MESSAGE_LENGTH) {
        throw new BurstException.NotValidException("Max encrypted message length exceeded");
      }
      if ((encryptedData.getNonce().length != 32 && encryptedData.getData().length > 0)
          || (encryptedData.getNonce().length != 0 && encryptedData.getData().length == 0)) {
        throw new BurstException.NotValidException("Invalid nonce length " + encryptedData.getNonce().length);
      }
    }

    public void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {}

    public final EncryptedData getEncryptedData() {
      return encryptedData;
    }

    public final boolean isText() {
      return isText;
    }

    protected abstract BrsApi.EncryptedMessageAppendix.Type getType();
  }

  class EncryptedMessage extends AbstractEncryptedMessage {

    static EncryptedMessage parse(JsonObject attachmentData) {
      if (attachmentData.get("encryptedMessage") == null ) {
        return null;
      }
      return new EncryptedMessage(attachmentData);
    }

    public EncryptedMessage(ByteBuffer buffer, byte transactionVersion) throws BurstException.ValidationException {
      super(buffer, transactionVersion);
    }

    EncryptedMessage(JsonObject attachmentData) {
      super(attachmentData, JSON.getAsJsonObject(attachmentData.get("encryptedMessage")));
    }

    public EncryptedMessage(EncryptedData encryptedData, boolean isText, int blockchainHeight) {
      super(encryptedData, isText, blockchainHeight);
    }

    public EncryptedMessage(BrsApi.EncryptedMessageAppendix encryptedMessageAppendix, int blockchainHeight) {
      super(encryptedMessageAppendix, blockchainHeight);
      if (encryptedMessageAppendix.getType() != BrsApi.EncryptedMessageAppendix.Type.TO_RECIPIENT) throw new IllegalArgumentException();
    }

    @Override
    String getAppendixName() {
      return "EncryptedMessage";
    }

    @Override
    void putMyJSON(JsonObject json) {
      JsonObject encryptedMessageJSON = new JsonObject();
      super.putMyJSON(encryptedMessageJSON);
      json.add("encryptedMessage", encryptedMessageJSON);
    }

    @Override
    public void validate(Transaction transaction) throws BurstException.ValidationException {
      super.validate(transaction);
      if (! transaction.getType().hasRecipient()) {
        throw new BurstException.NotValidException("Encrypted messages cannot be attached to transactions with no recipient");
      }
      if (transaction.getVersion() == 0) {
        throw new BurstException.NotValidException("Encrypted message attachments not enabled for version 0 transactions");
      }
    }

    @Override
    protected BrsApi.EncryptedMessageAppendix.Type getType() {
      return BrsApi.EncryptedMessageAppendix.Type.TO_RECIPIENT;
    }

  }

  class EncryptToSelfMessage extends AbstractEncryptedMessage {

    static EncryptToSelfMessage parse(JsonObject attachmentData) {
      if (attachmentData.get("encryptToSelfMessage") == null ) {
        return null;
      }
      return new EncryptToSelfMessage(attachmentData);
    }

    public  EncryptToSelfMessage(ByteBuffer buffer, byte transactionVersion) throws BurstException.ValidationException {
      super(buffer, transactionVersion);
    }

    EncryptToSelfMessage(JsonObject attachmentData) {
      super(attachmentData, JSON.getAsJsonObject(attachmentData.get("encryptToSelfMessage")));
    }

    public EncryptToSelfMessage(EncryptedData encryptedData, boolean isText, int blockchainHeight) {
      super(encryptedData, isText, blockchainHeight);
    }

    public EncryptToSelfMessage(BrsApi.EncryptedMessageAppendix encryptedMessageAppendix, int blockchainHeight) {
      super(encryptedMessageAppendix, blockchainHeight);
      if (encryptedMessageAppendix.getType() != BrsApi.EncryptedMessageAppendix.Type.TO_SELF) throw new IllegalArgumentException();
    }

    @Override
    String getAppendixName() {
      return "EncryptToSelfMessage";
    }

    @Override
    void putMyJSON(JsonObject json) {
      JsonObject encryptToSelfMessageJSON = new JsonObject();
      super.putMyJSON(encryptToSelfMessageJSON);
      json.add("encryptToSelfMessage", encryptToSelfMessageJSON);
    }

    @Override
    public void validate(Transaction transaction) throws BurstException.ValidationException {
      super.validate(transaction);
      if (transaction.getVersion() == 0) {
        throw new BurstException.NotValidException("Encrypt-to-self message attachments not enabled for version 0 transactions");
      }
    }

    @Override
    protected BrsApi.EncryptedMessageAppendix.Type getType() {
      return BrsApi.EncryptedMessageAppendix.Type.TO_SELF;
    }

  }

  class PublicKeyAnnouncement extends AbstractAppendix {

    static PublicKeyAnnouncement parse(JsonObject attachmentData) {
      if (attachmentData.get("recipientPublicKey") == null) {
        return null;
      }
      return new PublicKeyAnnouncement(attachmentData);
    }

    private final byte[] publicKey;

    public PublicKeyAnnouncement(ByteBuffer buffer, byte transactionVersion) {
      super(buffer, transactionVersion);
      this.publicKey = new byte[32];
      buffer.get(this.publicKey);
    }

    PublicKeyAnnouncement(JsonObject attachmentData) {
      super(attachmentData);
      this.publicKey = Convert.parseHexString(JSON.getAsString(attachmentData.get("recipientPublicKey")));
    }

    public PublicKeyAnnouncement(byte[] publicKey, int blockchainHeight) {
      super(blockchainHeight);
      this.publicKey = publicKey;
    }

    public PublicKeyAnnouncement(BrsApi.PublicKeyAnnouncementAppendix publicKeyAnnouncementAppendix, int blockchainHeight) {
      super(blockchainHeight);
      this.publicKey = publicKeyAnnouncementAppendix.getRecipientPublicKey().toByteArray();
    }

    @Override
    String getAppendixName() {
      return "PublicKeyAnnouncement";
    }

    @Override
    int getMySize() {
      return 32;
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
      buffer.put(publicKey);
    }

    @Override
    void putMyJSON(JsonObject json) {
      json.addProperty("recipientPublicKey", Convert.toHexString(publicKey));
    }

    @Override
    public void validate(Transaction transaction) throws BurstException.ValidationException {
      if (! transaction.getType().hasRecipient()) {
        throw new BurstException.NotValidException("PublicKeyAnnouncement cannot be attached to transactions with no recipient");
      }
      if (publicKey.length != 32) {
        throw new BurstException.NotValidException("Invalid recipient public key length: " + Convert.toHexString(publicKey));
      }
      long recipientId = transaction.getRecipientId();
      if (Account.getId(this.publicKey) != recipientId) {
        throw new BurstException.NotValidException("Announced public key does not match recipient accountId");
      }
      if (transaction.getVersion() == 0) {
        throw new BurstException.NotValidException("Public key announcements not enabled for version 0 transactions");
      }
      Account recipientAccount = Account.getAccount(recipientId);
      if (recipientAccount != null && recipientAccount.getPublicKey() != null && ! Arrays.equals(publicKey, recipientAccount.getPublicKey())) {
        throw new BurstException.NotCurrentlyValidException("A different public key for this account has already been announced");
      }
    }

    @Override
    public void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
      if (recipientAccount.setOrVerify(publicKey, transaction.getHeight())) {
        recipientAccount.apply(this.publicKey, transaction.getHeight());
      }
    }

    public byte[] getPublicKey() {
      return publicKey;
    }

    @Override
    public Any getProtobufMessage() {
      return Any.pack(BrsApi.PublicKeyAnnouncementAppendix.newBuilder()
              .setVersion(super.getVersion())
              .setRecipientPublicKey(ByteString.copyFrom(publicKey))
              .build());
    }
  }
}
