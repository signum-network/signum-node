package brs;

import brs.crypto.EncryptedData;
import brs.fluxcapacitor.FluxValues;
import brs.props.Props;
import brs.util.Convert;
import brs.util.JSON;
import com.google.gson.JsonObject;
import java.nio.ByteBuffer;
import java.util.Arrays;

//TODO: Create JavaDocs and remove this
@SuppressWarnings({ "checkstyle:MissingJavadocTypeCheck", "checkstyle:MissingJavadocMethodCheck" })

public interface Appendix {

    int getSize();

    void putBytes(ByteBuffer buffer);

    JsonObject getJsonObject();

    byte getVersion();

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
            this.version = (byte) (Signum.getFluxCapacitor().getValue(
                    FluxValues.DIGITAL_GOODS_STORE, blockchainHeight)
                            ? 1
                            : 0);
        }

        protected abstract String getAppendixName();

        @Override
        public final int getSize() {
            return getMySize() + (version > 0 ? 1 : 0);
        }

        protected abstract int getMySize();

        @Override
        public final void putBytes(ByteBuffer buffer) {
            if (version > 0) {
                buffer.put(version);
            }
            putMyBytes(buffer);
        }

        protected abstract void putMyBytes(ByteBuffer buffer);

        @Override
        public final JsonObject getJsonObject() {
            JsonObject json = new JsonObject();
            if (version > 0) {
                json.addProperty("version." + getAppendixName(), version);
            }
            putMyJson(json);
            return json;
        }

        protected abstract void putMyJson(JsonObject json);

        @Override
        public final byte getVersion() {
            return version;
        }

        boolean verifyVersion(byte transactionVersion) {
            return transactionVersion == 0 ? version == 0 : version > 0;
        }

        public abstract void validate(Transaction transaction)
                throws SignumException.ValidationException;

        public abstract void apply(
                Transaction transaction,
                Account senderAccount,
                Account recipientAccount);
    }

    class Message extends AbstractAppendix {

        static Message parse(JsonObject attachmentData) {
            if (attachmentData.get("message") == null) {
                return null;
            }
            return new Message(attachmentData);
        }

        private static final int MESSAGE_TEXT_FLAG = 1 << 31;

        private static boolean hasTextFlag(int rawLength) {
            return (rawLength & MESSAGE_TEXT_FLAG) != 0;
        }

        private static int extractLength(int rawLength) {
            return rawLength & ~MESSAGE_TEXT_FLAG;
        }

        private final byte[] messageBytes;
        private final boolean isText;

        public Message(
                ByteBuffer buffer,
                byte transactionVersion)
                throws SignumException.NotValidException {
            super(buffer, transactionVersion);
            int rawLength = buffer.getInt();
            this.isText = hasTextFlag(rawLength);
            int messageLength = extractLength(rawLength);
            if (messageLength > Constants.MAX_ARBITRARY_MESSAGE_LENGTH) {
                throw new SignumException.NotValidException(
                        "Invalid arbitrary message length: " + messageLength);
            }
            this.messageBytes = new byte[messageLength];
            buffer.get(this.messageBytes);
        }

        Message(JsonObject attachmentData) {
            super(attachmentData);
            String messageString = JSON.getAsString(attachmentData.get("message"));
            this.isText = Boolean.TRUE.equals(
                    JSON.getAsBoolean(attachmentData.get("messageIsText")));
            this.messageBytes = isText
                    ? Convert.toBytes(messageString)
                    : Convert.parseHexString(messageString);
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

        @Override
        protected String getAppendixName() {
            return "Message";
        }

        @Override
        protected int getMySize() {
            return 4 + messageBytes.length;
        }

        @Override
        protected void putMyBytes(ByteBuffer buffer) {
            buffer.putInt(isText ? (messageBytes.length | MESSAGE_TEXT_FLAG) : messageBytes.length);
            buffer.put(messageBytes);
        }

        @Override
        protected void putMyJson(JsonObject json) {
            json.addProperty("message", isText
                    ? Convert.toString(messageBytes)
                    : Convert.toHexString(messageBytes));
            json.addProperty("messageIsText", isText);
        }

        @Override
        public void validate(Transaction transaction) throws SignumException.ValidationException {
            if (this.isText && transaction.getVersion() == 0) {
                throw new SignumException.NotValidException("Text messages not yet enabled");
            }
            if (transaction.getVersion() == 0
                    && transaction.getAttachment() != Attachment.ARBITRARY_MESSAGE) {
                throw new SignumException.NotValidException(
                        "Message attachments not enabled for version 0 transactions");
            }
            if (messageBytes.length > Constants.MAX_ARBITRARY_MESSAGE_LENGTH) {
                throw new SignumException.NotValidException(
                        "Invalid arbitrary message length: " + messageBytes.length);
            }
        }

        @Override
        public void apply(
                Transaction transaction,
                Account senderAccount,
                Account recipientAccount) {
            // Do nothing by default
        }

        public byte[] getMessageBytes() {
            return messageBytes;
        }

        public boolean isText() {
            return isText;
        }
    }

    abstract class AbstractEncryptedMessage extends AbstractAppendix {

        private final EncryptedData encryptedData;
        private final boolean isText;

        private AbstractEncryptedMessage(ByteBuffer buffer, byte transactionVersion)
                throws SignumException.NotValidException {
            super(buffer, transactionVersion);
            int length = buffer.getInt();
            this.isText = length < 0;
            if (length < 0) {
                length &= Integer.MAX_VALUE;
            }
            this.encryptedData = EncryptedData.readEncryptedData(buffer, length,
                    Constants.MAX_ENCRYPTED_MESSAGE_LENGTH);
        }

        private AbstractEncryptedMessage(
                JsonObject attachmentJson,
                JsonObject encryptedMessageJson) {
            super(attachmentJson);
            byte[] data = Convert.parseHexString(
                    JSON.getAsString(encryptedMessageJson.get("data")));
            byte[] nonce = Convert.parseHexString(
                    JSON.getAsString(encryptedMessageJson.get("nonce")));
            this.encryptedData = new EncryptedData(data, nonce);
            this.isText = Boolean.TRUE.equals(
                    JSON.getAsBoolean(encryptedMessageJson.get("isText")));
        }

        private AbstractEncryptedMessage(
                EncryptedData encryptedData,
                boolean isText,
                int blockchainHeight) {
            super(blockchainHeight);
            this.encryptedData = encryptedData;
            this.isText = isText;
        }

        @Override
        protected int getMySize() {
            return 4 + encryptedData.getSize();
        }

        @Override
        protected void putMyBytes(ByteBuffer buffer) {
            buffer.putInt(
                    isText
                            ? (encryptedData.getData().length | Integer.MIN_VALUE)
                            : encryptedData.getData().length);
            buffer.put(encryptedData.getData());
            buffer.put(encryptedData.getNonce());
        }

        @Override
        protected void putMyJson(JsonObject json) {
            json.addProperty("data", Convert.toHexString(encryptedData.getData()));
            json.addProperty("nonce", Convert.toHexString(encryptedData.getNonce()));
            json.addProperty("isText", isText);
        }

        @Override
        public void validate(Transaction transaction) throws SignumException.ValidationException {
            if (encryptedData.getData().length > Constants.MAX_ENCRYPTED_MESSAGE_LENGTH) {
                throw new SignumException.NotValidException(
                        "Max encrypted message length exceeded");
            }
            if ((encryptedData.getNonce().length != 32 && encryptedData.getData().length > 0)
                    || (encryptedData.getNonce().length != 0
                            && encryptedData.getData().length == 0)) {
                throw new SignumException.NotValidException(
                        "Invalid nonce length " + encryptedData.getNonce().length);
            }
        }

        public void apply(
                Transaction transaction,
                Account senderAccount,
                Account recipientAccount) {
        }

        public final EncryptedData getEncryptedData() {
            return encryptedData;
        }

        public final boolean isText() {
            return isText;
        }
    }

    class EncryptedMessage extends AbstractEncryptedMessage {

        static EncryptedMessage parse(JsonObject attachmentData) {
            if (attachmentData.get("encryptedMessage") == null) {
                return null;
            }
            return new EncryptedMessage(attachmentData);
        }

        public EncryptedMessage(
                ByteBuffer buffer,
                byte transactionVersion)
                throws SignumException.ValidationException {
            super(buffer, transactionVersion);
        }

        EncryptedMessage(JsonObject attachmentData) {
            super(attachmentData, JSON.getAsJsonObject(attachmentData.get("encryptedMessage")));
        }

        public EncryptedMessage(EncryptedData encryptedData, boolean isText, int blockchainHeight) {
            super(encryptedData, isText, blockchainHeight);
        }

        @Override
        protected String getAppendixName() {
            return "EncryptedMessage";
        }

        @Override
        protected void putMyJson(JsonObject json) {
            JsonObject encryptedMessageJson = new JsonObject();
            super.putMyJson(encryptedMessageJson);
            json.add("encryptedMessage", encryptedMessageJson);
        }

        @Override
        public void validate(Transaction transaction) throws SignumException.ValidationException {
            super.validate(transaction);
            if (!transaction.getType().hasRecipient()) {
                throw new SignumException.NotValidException(
                        "Encrypted messages cannot be attached to transactions with no recipient");
            }
            if (transaction.getVersion() == 0) {
                throw new SignumException.NotValidException(
                        "Encrypted message attachments not enabled for version 0 transactions");
            }
        }
    }

    class EncryptToSelfMessage extends AbstractEncryptedMessage {

        static EncryptToSelfMessage parse(JsonObject attachmentData) {
            if (attachmentData.get("encryptToSelfMessage") == null) {
                return null;
            }
            return new EncryptToSelfMessage(attachmentData);
        }

        public EncryptToSelfMessage(ByteBuffer buffer, byte transactionVersion)
                throws SignumException.ValidationException {
            super(buffer, transactionVersion);
        }

        EncryptToSelfMessage(JsonObject attachmentData) {
            super(attachmentData, JSON.getAsJsonObject(attachmentData.get("encryptToSelfMessage")));
        }

        public EncryptToSelfMessage(
                EncryptedData encryptedData,
                boolean isText,
                int blockchainHeight) {
            super(encryptedData, isText, blockchainHeight);
        }

        @Override
        protected String getAppendixName() {
            return "EncryptToSelfMessage";
        }

        @Override
        protected void putMyJson(JsonObject json) {
            JsonObject encryptToSelfMessageJson = new JsonObject();
            super.putMyJson(encryptToSelfMessageJson);
            json.add("encryptToSelfMessage", encryptToSelfMessageJson);
        }

        @Override
        public void validate(Transaction transaction) throws SignumException.ValidationException {
            super.validate(transaction);
            if (transaction.getVersion() == 0) {
                throw new SignumException.NotValidException(
                        "Encrypt-to-self message attachments"
                                + " not enabled for version 0 transactions");
            }
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
            this.publicKey = Convert.parseHexString(
                    JSON.getAsString(attachmentData.get("recipientPublicKey")));
        }

        public PublicKeyAnnouncement(byte[] publicKey, int blockchainHeight) {
            super(blockchainHeight);
            this.publicKey = publicKey;
        }

        @Override
        protected String getAppendixName() {
            return "PublicKeyAnnouncement";
        }

        @Override
        protected int getMySize() {
            return 32;
        }

        @Override
        protected void putMyBytes(ByteBuffer buffer) {
            buffer.put(publicKey);
        }

        @Override
        protected void putMyJson(JsonObject json) {
            json.addProperty("recipientPublicKey", Convert.toHexString(publicKey));
        }

        @Override
        public void validate(Transaction transaction) throws SignumException.ValidationException {
            if (!transaction.getType().hasRecipient()) {
                throw new SignumException.NotValidException(
                        "PublicKeyAnnouncement cannot be attached"
                                + "to transactions with no recipient");
            }
            if (publicKey.length != 32) {
                throw new SignumException.NotValidException(
                        "Invalid recipient public key length: " + Convert.toHexString(publicKey));
            }
            long recipientId = transaction.getRecipientId();
            if (Account.getId(this.publicKey) != recipientId) {
                throw new SignumException.NotValidException(
                        "Announced public key does not match recipient accountId");
            }
            if (transaction.getVersion() == 0) {
                throw new SignumException.NotValidException(
                        "Public key announcements not enabled for version 0 transactions");
            }
            Account recipientAccount = Account.getAccount(recipientId);
            if (recipientAccount != null && recipientAccount.getPublicKey() != null
                    && !Arrays.equals(publicKey, recipientAccount.getPublicKey())) {
                throw new SignumException.NotCurrentlyValidException(
                        "A different public key for this account has already been announced");
            }
            if (Signum.getFluxCapacitor().getValue(FluxValues.PK_FREEZE2)) {
                if (recipientAccount != null && recipientAccount.getPublicKey() == null
                        && Signum.getBlockchain().getHeight()
                                - recipientAccount.getCreationHeight() > Signum
                                        .getPropertyService().getInt(Props.PK_BLOCKS_PAST)) {
                    throw new SignumException.NotCurrentlyValidException(
                            "Setting a new key for an old inactivated account");
                }
            } else if (Signum.getFluxCapacitor().getValue(FluxValues.PK_FREEZE)
                    && Signum.getBlockchain().getHeight()
                            - recipientAccount.getCreationHeight() > Signum
                                    .getPropertyService().getInt(Props.PK_BLOCKS_PAST)) {
                // TODO: this entire final condition (else if) can be removed after PK_FREEZE2
                // is activated
                throw new SignumException.NotCurrentlyValidException(
                        "Setting a new key for an old inactivated account");
            }
        }

        @Override
        public void apply(
                Transaction transaction,
                Account senderAccount,
                Account recipientAccount) {
            if (recipientAccount.setOrVerify(publicKey, transaction.getHeight())) {
                recipientAccount.apply(this.publicKey, transaction.getHeight());
            }
        }

        public byte[] getPublicKey() {
            return publicKey;
        }

    }
}
