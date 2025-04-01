package brs;

import brs.crypto.Crypto;
import brs.db.TransactionDb;
import brs.fluxcapacitor.FluxValues;
import brs.peer.Peer;
import brs.util.Convert;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO: Create JavaDocs and remove this
@SuppressWarnings({ "checkstyle:MissingJavadocTypeCheck", "checkstyle:MissingJavadocMethodCheck" })

public class Block {

    private static final Logger logger = LoggerFactory.getLogger(Block.class);
    private final int version;
    private final int timestamp;
    private final long previousBlockId;
    private final byte[] generatorPublicKey;
    private final byte[] previousBlockHash;
    private final long totalAmountNqt;
    private final long totalFeeNqt;
    private final long totalFeeBurntNqt;
    private final long totalFeeCashBackNqt;
    private final int payloadLength;
    private final byte[] generationSignature;
    private final byte[] payloadHash;
    private final AtomicReference<List<Transaction>> blockTransactions = new AtomicReference<>();

    private byte[] blockSignature;

    private BigInteger cumulativeDifficulty = BigInteger.ZERO;

    private long baseTarget = Constants.INITIAL_BASE_TARGET;
    private final AtomicLong nextBlockId = new AtomicLong();
    private int height = -1;
    private final AtomicLong id = new AtomicLong();
    private final AtomicReference<String> stringId = new AtomicReference<>();
    private final AtomicLong generatorId = new AtomicLong();
    private long nonce;

    private BigInteger pocTime = null;
    private long commitment = 0L;

    private final byte[] blockAts;

    private Peer downloadedFrom = null;
    private int byteLength = 0;

    Block(
            int version,
            int timestamp,
            long previousBlockId,
            long totalAmountNqt,
            long totalFeeNqt,
            long totalFeeCashBackNqt,
            long totalFeeBurntNqt,
            int payloadLength,
            byte[] payloadHash,
            byte[] generatorPublicKey,
            byte[] generationSignature,
            byte[] blockSignature,
            byte[] previousBlockHash,
            List<Transaction> transactions,
            long nonce,
            byte[] blockAts,
            int height,
            long baseTarget)
            throws SignumException.ValidationException {

        if (payloadLength > Signum.getFluxCapacitor().getValue(
                FluxValues.MAX_PAYLOAD_LENGTH, height)
                || payloadLength < 0) {
            throw new SignumException.NotValidException(
                    "attempted to create a block with payloadLength "
                            + payloadLength + " height " + height + "previd "
                            + previousBlockId);
        }

        this.version = version;
        this.timestamp = timestamp;
        this.previousBlockId = previousBlockId;
        this.totalAmountNqt = totalAmountNqt;
        this.totalFeeNqt = totalFeeNqt;
        this.totalFeeCashBackNqt = totalFeeCashBackNqt;
        this.totalFeeBurntNqt = totalFeeBurntNqt;
        this.payloadLength = payloadLength;
        this.payloadHash = payloadHash;
        this.generatorPublicKey = generatorPublicKey;
        this.generationSignature = generationSignature;
        this.blockSignature = blockSignature;

        this.previousBlockHash = previousBlockHash;
        if (transactions != null) {
            this.blockTransactions.set(Collections.unmodifiableList(transactions));
            if (blockTransactions.get()
                    .size() > (Signum.getFluxCapacitor().getValue(
                            FluxValues.MAX_NUMBER_TRANSACTIONS, height))) {
                throw new SignumException.NotValidException(
                        "attempted to create a block with "
                                + blockTransactions.get().size() + " transactions");
            }
            long previousId = 0;
            for (Transaction transaction : this.blockTransactions.get()) {
                if (transaction.getId() <= previousId && previousId != 0) {
                    throw new SignumException.NotValidException(
                            "Block transactions are not sorted!");
                }
                previousId = transaction.getId();
            }
        }
        this.nonce = nonce;
        this.blockAts = blockAts;
        this.baseTarget = baseTarget;
    }

    public Block(
            int version,
            int timestamp,
            long previousBlockId,
            long totalAmountNqt,
            long totalFeeNqt,
            long totalFeeCashBackNqt,
            long totalFeeBurntNqt,
            int payloadLength,
            byte[] payloadHash,
            byte[] generatorPublicKey,
            byte[] generationSignature,
            byte[] blockSignature,
            byte[] previousBlockHash,
            BigInteger cumulativeDifficulty,
            long baseTarget,
            long nextBlockId,
            int height,
            Long id,
            long nonce,
            byte[] blockAts)
            throws SignumException.ValidationException {

        this(
                version,
                timestamp,
                previousBlockId,
                totalAmountNqt,
                totalFeeNqt,
                totalFeeCashBackNqt,
                totalFeeBurntNqt,
                payloadLength,
                payloadHash,
                generatorPublicKey,
                generationSignature,
                blockSignature,
                previousBlockHash,
                null,
                nonce,
                blockAts,
                height,
                baseTarget);

        this.cumulativeDifficulty = cumulativeDifficulty == null
                ? BigInteger.ZERO
                : cumulativeDifficulty;
        this.nextBlockId.set(nextBlockId);
        this.height = height;
        this.id.set(id);
    }

    private TransactionDb transactionDb() {
        return Signum.getDbs().getTransactionDb();
    }

    public boolean isVerified() {
        return pocTime != null;
    }

    public void setPeer(Peer peer) {
        this.downloadedFrom = peer;
    }

    public Peer getPeer() {
        return this.downloadedFrom;
    }

    public void setByteLength(int length) {
        this.byteLength = length;
    }

    public int getByteLength() {
        return this.byteLength;
    }

    public int getVersion() {
        return version;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public long getPreviousBlockId() {
        return previousBlockId;
    }

    public byte[] getGeneratorPublicKey() {
        return generatorPublicKey;
    }

    public byte[] getBlockHash() {
        return Crypto.sha256().digest(getBytes());
    }

    public byte[] getPreviousBlockHash() {
        return previousBlockHash;
    }

    public long getTotalAmountNqt() {
        return totalAmountNqt;
    }

    public long getTotalFeeNqt() {
        return totalFeeNqt;
    }

    public long getTotalFeeCashBackNqt() {
        return totalFeeCashBackNqt;
    }

    public long getTotalFeeBurntNqt() {
        return totalFeeBurntNqt;
    }

    public int getPayloadLength() {
        return payloadLength;
    }

    public byte[] getPayloadHash() {
        return payloadHash;
    }

    public byte[] getGenerationSignature() {
        return generationSignature;
    }

    public byte[] getBlockSignature() {
        return blockSignature;
    }

    public List<Transaction> getTransactions() {
        if (blockTransactions.get() == null) {
            this.blockTransactions
                    .set(Collections.unmodifiableList(
                            transactionDb().findBlockTransactions(getId(), true)));
            this.blockTransactions.get().forEach(transaction -> transaction.setBlock(this));
        }
        return blockTransactions.get();
    }

    public List<Transaction> getAllTransactions() {
        return Collections.unmodifiableList(transactionDb().findBlockTransactions(getId(), false));
    }

    public long getBaseTarget() {
        return baseTarget;
    }

    public long getCapacityBaseTarget() {
        long capacityBaseTarget = baseTarget;
        if (Signum.getFluxCapacitor().getValue(FluxValues.POC_PLUS, height)) {
            // Base target encoded as two floats, one for the commitment and the other the
            // classical base target
            float capacityBaseTargetFloat = Float.intBitsToFloat((int) (baseTarget & 0xFFFFFFFFL));
            capacityBaseTarget = (long) capacityBaseTargetFloat;
        }
        return capacityBaseTarget;
    }

    public long getAverageCommitment() {
        if (Signum.getFluxCapacitor().getValue(FluxValues.POC_PLUS, height)) {
            // Base target encoded as two floats, one for the commitment and the other the
            // classical base target
            float commitmentBaseTargetFloat = Float.intBitsToFloat((int) ((baseTarget) >> 32));
            return (long) commitmentBaseTargetFloat;
        }
        return Constants.INITIAL_COMMITMENT;
    }

    public void setBaseTarget(long baseTarget) {
        this.baseTarget = baseTarget;
    }

    public void setBaseTarget(long baseTargetCapacity, long averageCommitment) {
        this.baseTarget = ((long) Float.floatToIntBits((float) averageCommitment)) << 32
                | ((long) Float.floatToIntBits((float) baseTargetCapacity));
    }

    public BigInteger getCumulativeDifficulty() {
        return cumulativeDifficulty;
    }

    public long getNextBlockId() {
        return nextBlockId.get();
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public long getId() {
        if (id.get() == 0) {
            if (blockSignature == null) {
                throw new IllegalStateException("Block is not signed yet");
            }
            byte[] hash = Crypto.sha256().digest(getBytes());
            long longId = Convert.fullHashToId(hash);
            id.set(longId);
            stringId.set(Convert.toUnsignedLong(longId));
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

    public long getGeneratorId() {
        if (generatorId.get() == 0) {
            generatorId.set(Account.getId(generatorPublicKey));
        }
        return generatorId.get();
    }

    public Long getNonce() {
        return nonce;
    }

    public boolean equals(Object o) {
        return o instanceof Block && this.getId() == ((Block) o).getId();
    }

    public int hashCode() {
        return (int) (getId() ^ (getId() >>> 32));
    }

    public JsonObject getJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("version", version);
        json.addProperty("timestamp", timestamp);
        json.addProperty("previousBlock", Convert.toUnsignedLong(previousBlockId));
        json.addProperty("totalAmountNQT", totalAmountNqt);
        json.addProperty("totalFeeNQT", totalFeeNqt);
        json.addProperty("totalFeeCashBackNQT", totalFeeCashBackNqt);
        json.addProperty("totalFeeBurntNQT", totalFeeBurntNqt);
        json.addProperty("payloadLength", payloadLength);
        json.addProperty("payloadHash", Convert.toHexString(payloadHash));
        json.addProperty("generatorPublicKey", Convert.toHexString(generatorPublicKey));
        json.addProperty("generationSignature", Convert.toHexString(generationSignature));
        if (version > 1) {
            json.addProperty("previousBlockHash", Convert.toHexString(previousBlockHash));
        }
        json.addProperty("blockSignature", Convert.toHexString(blockSignature));
        JsonArray transactionsData = new JsonArray();
        getTransactions().forEach(transaction -> transactionsData.add(transaction.getJsonObject()));
        json.add("transactions", transactionsData);
        json.addProperty("nonce", Convert.toUnsignedLong(nonce));
        json.addProperty("baseTarget", Convert.toUnsignedLong(baseTarget));
        json.addProperty("blockATs", Convert.toHexString(blockAts));
        return json;
    }

    // TODO: See about removing this check suppression:
    // Option 1: Move variables closer to when they're needed
    // Option 2: Make variables final, if possible
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistanceCheck")
    static Block parseBlock(JsonObject blockData, int height)
            throws SignumException.ValidationException {
        try {
            int version = JSON.getAsInt(blockData.get("version"));
            int timestamp = JSON.getAsInt(blockData.get("timestamp"));
            long previousBlock = Convert.parseUnsignedLong(
                    JSON.getAsString(blockData.get("previousBlock")));
            long totalAmountNqt = JSON.getAsLong(blockData.get("totalAmountNQT"));
            long totalFeeNqt = JSON.getAsLong(blockData.get("totalFeeNQT"));
            long totalFeeCashBackNqt = 0L;
            long totalFeeBurntNqt = 0L;
            if (version > 3) {
                totalFeeCashBackNqt = JSON.getAsLong(blockData.get("totalFeeCashBackNQT"));
                totalFeeBurntNqt = JSON.getAsLong(blockData.get("totalFeeBurntNQT"));
            }
            int payloadLength = JSON.getAsInt(blockData.get("payloadLength"));
            byte[] payloadHash = Convert.parseHexString(
                    JSON.getAsString(blockData.get("payloadHash")));
            byte[] generatorPublicKey = Convert.parseHexString(
                    JSON.getAsString(blockData.get("generatorPublicKey")));
            byte[] generationSignature = Convert.parseHexString(
                    JSON.getAsString(blockData.get("generationSignature")));
            byte[] blockSignature = Convert.parseHexString(
                    JSON.getAsString(blockData.get("blockSignature")));
            byte[] previousBlockHash = version == 1 ? null
                    : Convert.parseHexString(JSON.getAsString(blockData.get("previousBlockHash")));
            long nonce = Convert.parseUnsignedLong(JSON.getAsString(blockData.get("nonce")));
            long baseTarget = Convert.parseUnsignedLong(
                    JSON.getAsString(blockData.get("baseTarget")));

            if (Signum.getFluxCapacitor().getValue(
                    FluxValues.POC_PLUS, height) && baseTarget == 0L) {
                throw new SignumException.NotValidException("Block received without a baseTarget");
            }

            SortedMap<Long, Transaction> blockTransactions = new TreeMap<>();
            JsonArray transactionsData = JSON.getAsJsonArray(blockData.get("transactions"));

            for (JsonElement transactionData : transactionsData) {
                Transaction transaction = Transaction.parseTransaction(
                        JSON.getAsJsonObject(transactionData), height);
                if (transaction.getSignature() != null
                        && blockTransactions.put(transaction.getId(), transaction) != null) {
                    throw new SignumException.NotValidException(
                            "Block contains duplicate transactions: " + transaction.getStringId());
                }
            }

            byte[] blockAts = Convert.parseHexString(JSON.getAsString(blockData.get("blockATs")));
            return new Block(
                    version,
                    timestamp,
                    previousBlock,
                    totalAmountNqt,
                    totalFeeNqt,
                    totalFeeCashBackNqt,
                    totalFeeBurntNqt,
                    payloadLength,
                    payloadHash,
                    generatorPublicKey,
                    generationSignature,
                    blockSignature,
                    previousBlockHash,
                    new ArrayList<>(blockTransactions.values()),
                    nonce,
                    blockAts,
                    height,
                    baseTarget);
        } catch (SignumException.ValidationException | RuntimeException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to parse block: {}", JSON.toJsonString(blockData));
            }
            throw e;
        }
    }

    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(
                4
                        + 4
                        + 8
                        + 4
                        + (version < 3 ? (4 + 4) : (8 + 8))
                        + 4
                        + 32
                        + 32
                        + (32 + 32)
                        + 8
                        + (blockAts != null ? blockAts.length : 0)
                        + 64);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(version);
        buffer.putInt(timestamp);
        buffer.putLong(previousBlockId);
        buffer.putInt(getTransactions().size());
        if (version < 3) {
            buffer.putInt((int) (totalAmountNqt / Constants.ONE_SIGNA));
            buffer.putInt((int) (totalFeeNqt / Constants.ONE_SIGNA));
        } else {
            buffer.putLong(totalAmountNqt);
            buffer.putLong(totalFeeNqt);
        }
        buffer.putInt(payloadLength);
        buffer.put(payloadHash);
        buffer.put(generatorPublicKey);
        buffer.put(generationSignature);
        if (version > 1) {
            buffer.put(previousBlockHash);
        }
        buffer.putLong(nonce);
        if (blockAts != null) {
            buffer.put(blockAts);
        }
        if (buffer.limit() - buffer.position() < blockSignature.length) {
            logger.error("Something is too large here "
                    + "- buffer should have {} bytes left but only has {}",
                    blockSignature.length,
                    (buffer.limit() - buffer.position()));
        }
        buffer.put(blockSignature);
        return buffer.array();
    }

    void sign(String secretPhrase) {
        if (blockSignature != null) {
            throw new IllegalStateException("Block already signed");
        }
        blockSignature = new byte[64];
        byte[] data = getBytes();
        byte[] data2 = new byte[data.length - 64];
        System.arraycopy(data, 0, data2, 0, data2.length);
        blockSignature = Crypto.sign(data2, secretPhrase);
    }

    public byte[] getBlockAts() {
        return blockAts;
    }

    public BigInteger getPocTime() {
        return pocTime;
    }

    public void setPocTime(BigInteger pocTime) {
        this.pocTime = pocTime;
    }

    public long getCommitment() {
        return this.commitment;
    }

    public void setCommitment(long commitment) {
        this.commitment = commitment;
    }

    public void setCumulativeDifficulty(BigInteger cumulativeDifficulty) {
        this.cumulativeDifficulty = cumulativeDifficulty;
    }
}
