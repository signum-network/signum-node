package it.common;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

;

public class BlockMessageBuilder {

  private long payloadLength;
  private long totalAmountNQT;
  private long version;
  private String nonce;
  private long totalFeeNQT;
  private String blockATs;
  private String previousBlock;
  private String generationSignature;
  private String generatorPublicKey;
  private String payloadHash;
  private String blockSignature;
  private JsonArray transactions = new JsonArray();
  private long timestamp;
  private String previousBlockHash;

  public BlockMessageBuilder payloadLength(long payloadLength) {
    this.payloadLength = payloadLength;
    return this;
  }

  public BlockMessageBuilder totalAmountNQT(long totalAmountNQT) {
    this.totalAmountNQT = totalAmountNQT;
    return this;
  }

  public BlockMessageBuilder version(long version) {
    this.version = version;
    return this;
  }

  public BlockMessageBuilder nonce(String nonce) {
    this.nonce = nonce;
    return this;
  }

  public BlockMessageBuilder totalFeeNQT(long totalFeeNQT) {
    this.totalFeeNQT = totalFeeNQT;
    return this;
  }

  public BlockMessageBuilder blockATs(String blockATs) {
    this.blockATs = blockATs;
    return this;
  }

  public BlockMessageBuilder previousBlock(String previousBlock) {
    this.previousBlock = previousBlock;
    return this;
  }

  public BlockMessageBuilder generationSignature(String generationSignature) {
    this.generationSignature = generationSignature;
    return this;
  }

  public BlockMessageBuilder generatorPublicKey(String generatorPublicKey) {
    this.generatorPublicKey = generatorPublicKey;
    return this;
  }

  public BlockMessageBuilder payloadHash(String payloadHash) {
    this.payloadHash = payloadHash;
    return this;
  }

  public BlockMessageBuilder blockSignature(String blockSignature) {
    this.blockSignature = blockSignature;
    return this;
  }

  public BlockMessageBuilder transactions(JsonArray transactions) {
    if(transactions == null) {
      this.transactions = new JsonArray();
    } else {
      this.transactions = transactions;
    }
    return this;
  }

  public BlockMessageBuilder timestamp(long timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  public BlockMessageBuilder previousBlockHash(String previousBlockHash) {
    this.previousBlockHash = previousBlockHash;
    return this;
  }

  public JsonObject toJson() {
    final JsonObject overview = new JsonObject();

    overview.addProperty("payloadLength", payloadLength);
    overview.addProperty("totalAmountNQT", totalAmountNQT);
    overview.addProperty("version", version);
    overview.addProperty("nonce", nonce);
    overview.addProperty("totalFeeNQT", totalFeeNQT);
    overview.addProperty("blockATs", blockATs);
    overview.addProperty("previousBlock", previousBlock);
    overview.addProperty("generationSignature", generationSignature);
    overview.addProperty("generatorPublicKey", generatorPublicKey);
    overview.addProperty("payloadHash", payloadHash);
    overview.addProperty("blockSignature", blockSignature);
    overview.add("transactions", transactions);
    overview.addProperty("timestamp", timestamp);
    overview.addProperty("previousBlockHash", previousBlockHash);

    return overview;
  }
}
