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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a fundamental building block of the Signum blockchain.
 *
 * <p>
 * A {@code Block} groups a set of transactions and links to the previous block,
 * forming the immutable blockchain ledger. In addition to transactions, it
 * contains consensus-critical metadata such as the generation signature
 * (Proof of Capacity), timestamp, base target, and cumulative difficulty.
 *
 * <h2>Concurrency and Thread Safety</h2>
 * <p>
 * This class follows a lightweight concurrency model optimized for performance
 * and memory efficiency:
 * </p>
 * <ul>
 * <li>
 * <b>Volatile fields:</b> Lazily initialized and cached values
 * (e.g. {@code id}, {@code stringId}, serialized bytes, JSON representation)
 * are declared {@code volatile} to guarantee visibility across threads.
 * </li>
 * <li>
 * <b>Double-checked locking (DCL):</b> Expensive computations such as ID
 * calculation and byte serialization are performed at most once using
 * synchronized blocks with double-checked locking.
 * </li>
 * <li>
 * <b>Effective immutability:</b> After signing, the block’s core state does
 * not change. Transaction collections are exposed as unmodifiable lists.
 * </li>
 * </ul>
 *
 * <p>
 * After signing, the class is safe for concurrent read access.
 * </p>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 * <li>
 * <b>Construction (unsigned):</b> The block is created with transactions and
 * metadata but without a signature or ID.
 * </li>
 * <li>
 * <b>Signing:</b> Calling {@link #sign(String)} generates the block signature.
 * </li>
 * <li>
 * <b>Finalization (cached):</b> Accessing the ID or serialized bytes triggers
 * their computation and caching. The block is now ready to be linked into
 * the blockchain.
 * </li>
 * </ol>
 *
 * <p>
 * <b>Note:</b> Equality and {@code hashCode} are only well-defined after the
 * block has been signed.
 * </p>
 */

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

    private volatile List<Transaction> blockTransactions;
    private volatile List<Transaction> allBlockTransactions;

    private volatile byte[] cachedBytes;
    private volatile JsonObject cachedJsonObject;

    private volatile List<Transaction> atTransactions = Collections.emptyList();
    private volatile List<Transaction> subscriptionTransactions = Collections.emptyList();
    private volatile List<Transaction> escrowTransactions = Collections.emptyList();

    private volatile byte[] blockSignature;
    private BigInteger cumulativeDifficulty = BigInteger.ZERO;
    private long baseTarget = Constants.INITIAL_BASE_TARGET;
    private volatile long nextBlockId;
    private int height = -1;

    private volatile long id;
    private volatile String stringId;
    private volatile long generatorId;

    private long nonce;
    private BigInteger pocTime = null;
    private long commitment = 0L;
    private final byte[] blockAts;
    private volatile Peer downloadedFrom = null;
    private volatile int byteLength = 0;

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
            this.blockTransactions = Collections.unmodifiableList(transactions);
            if (blockTransactions.size() > (Signum.getFluxCapacitor().getValue(
                    FluxValues.MAX_NUMBER_TRANSACTIONS, height))) {
                throw new SignumException.NotValidException(
                        "attempted to create a block with "
                                + blockTransactions.size() + " transactions");
            }
            long previousId = 0;
            for (Transaction transaction : this.blockTransactions) {
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
        this.nextBlockId = nextBlockId;
        this.height = height;
        this.id = id;
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
        if (blockTransactions == null) {
            synchronized (this) {
                if (blockTransactions == null) {
                    List<Transaction> newTransactions = transactionDb().findBlockTransactions(getId(), true);
                    newTransactions.forEach(transaction -> transaction.setBlock(this));
                    blockTransactions = Collections.unmodifiableList(newTransactions);
                }
            }
        }
        return blockTransactions;
    }

    public List<Transaction> getAllTransactions() {
        if (allBlockTransactions == null) {
            synchronized (this) {
                if (allBlockTransactions == null) {
                    List<Transaction> newTransactions = transactionDb().findBlockTransactions(getId(), false);
                    newTransactions.forEach(transaction -> transaction.setBlock(this));
                    allBlockTransactions = Collections.unmodifiableList(newTransactions);
                }
            }
        }
        return allBlockTransactions;
    }

    public void setAtTransactions(List<Transaction> transactions) {
        this.atTransactions = transactions;
    }

    public List<Transaction> getAtTransactions() {
        return Collections.unmodifiableList(this.atTransactions);
    }

    public void setSubscriptionTransactions(List<Transaction> transactions) {
        this.subscriptionTransactions = transactions;
    }

    public List<Transaction> getSubscriptionTransactions() {
        return Collections.unmodifiableList(this.subscriptionTransactions);
    }

    public void setEscrowTransactions(List<Transaction> transactions) {
        this.escrowTransactions = transactions;
    }

    public List<Transaction> getEscrowTransactions() {
        return Collections.unmodifiableList(this.escrowTransactions);
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
        return nextBlockId;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public long getId() {
        if (id == 0) {
            synchronized (this) {
                if (id == 0) {
                    requireSigned();
                    byte[] hash = Crypto.sha256().digest(getBytes());
                    long longId = Convert.fullHashToId(hash);
                    stringId = Convert.toUnsignedLong(longId);
                    id = longId;
                }
            }
        }
        return id;
    }

    public String getStringId() {
        if (stringId == null) {
            getId();
            // If the block is initialized with a pre-existing id (e.g. loaded from DB),
            // getId() may return without initializing stringId. Ensure it is set here.
            if (stringId == null) {
                stringId = Convert.toUnsignedLong(id);
            }
        }
        return stringId;
    }

    public long getGeneratorId() {
        if (generatorId == 0) {
            synchronized (this) {
                if (generatorId == 0) {
                    generatorId = Account.getId(generatorPublicKey);
                }
            }
        }
        return generatorId;
    }

    public Long getNonce() {
        return nonce;
    }

    /**
     * Checks if the block is signed.
     * <p>
     * Only signed blocks have a valid ID and can be used in {@code equals} and
     * {@code hashCode}.
     * </p>
     * 
     * @return true if the block is signed, false otherwise.
     */
    public boolean isSigned() {
        return blockSignature != null;
    }

    private void requireSigned() {
        if (blockSignature == null) {
            throw new IllegalStateException("Unsigned block");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        requireSigned();
        return o instanceof Block && this.getId() == ((Block) o).getId();
    }

    @Override
    public int hashCode() {
        requireSigned();
        long blockId = getId();
        return (int) (blockId ^ (blockId >>> 32));
    }

    @Override
    public String toString() {
        return "Block{" +
                "height=" + height +
                ", id=" + (blockSignature != null ? getStringId() : "unsigned") +
                '}';
    }

    public JsonObject getJsonObject() {
        if (cachedJsonObject == null) {
            synchronized (this) {
                if (cachedJsonObject == null) {
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
                    cachedJsonObject = json;
                }
            }
        }
        return cachedJsonObject;
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
        if (cachedBytes == null) {
            synchronized (this) {
                if (cachedBytes == null) {
                    byte[] unsignedBytes = getUnsignedBytes();
                    ByteBuffer buffer = ByteBuffer.allocate(unsignedBytes.length + blockSignature.length);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    buffer.put(unsignedBytes);
                    if (buffer.limit() - buffer.position() < blockSignature.length) {
                        logger.error("Something is too large here "
                                + "- buffer should have {} bytes left but only has {}",
                                blockSignature.length,
                                (buffer.limit() - buffer.position()));
                    }
                    buffer.put(blockSignature);
                    cachedBytes = buffer.array();
                }
            }
        }
        return cachedBytes;
    }

    byte[] getUnsignedBytes() {
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
                        + (blockAts != null ? blockAts.length : 0));
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
        return buffer.array();
    }

    void sign(String secretPhrase) {
        synchronized (this) {
            if (blockSignature != null) {
                throw new IllegalStateException("Block already signed");
            }
            // 1. Calculate the unsigned bytes first.
            byte[] unsignedBytes = getUnsignedBytes();

            // 2. Sign the unsigned bytes to get the block signature.
            blockSignature = Crypto.sign(unsignedBytes, secretPhrase);

            // 3. Now that blockSignature is available, construct the full signed bytes
            // and cache them. This ensures cachedBytes always holds the final, signed
            // state.
            ByteBuffer buffer = ByteBuffer.allocate(unsignedBytes.length + blockSignature.length);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.put(unsignedBytes);
            if (buffer.limit() - buffer.position() < blockSignature.length) {
                logger.error("Something is too large here "
                        + "- buffer should have {} bytes left but only has {}",
                        blockSignature.length,
                        (buffer.limit() - buffer.position()));
            }
            buffer.put(blockSignature);
            cachedBytes = buffer.array(); // Cache the final, signed bytes
        }
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
