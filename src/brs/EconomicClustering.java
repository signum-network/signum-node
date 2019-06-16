package brs;

import brs.fluxcapacitor.FluxValues;
import brs.util.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Economic Clustering concept (EC) solves the most critical flaw of "classical" Proof-of-Stake - the problem called
 * "Nothing-at-Stake".
 *
 * I ought to respect BCNext's wish and say that this concept is inspired by Economic Majority idea of Meni Rosenfeld
 * (http://en.wikipedia.org/wiki/User:Meni_Rosenfeld).
 *
 * EC is a vital part of Transparent Forging. Words "Mining in Nxt relies on cooperation of people and even forces it"
 * (https://bitcointalk.org/index.php?topic=553205.0) were said about EC.
 *
 * Keep in mind that this concept has not been peer reviewed. You are very welcome to do it...
 *
 *                                                                              Come-from-Beyond (21.05.2014)
 */
public final class EconomicClustering {

  private static final Logger logger = LoggerFactory.getLogger(EconomicClustering.class);

  private final Blockchain blockchain;

  public EconomicClustering(Blockchain blockchain) {
    this.blockchain = blockchain;
  }

  public Block getECBlock(int timestamp) {
    Block block = blockchain.getLastBlock();
    if (timestamp < block.getTimestamp() - 15) {
      throw new IllegalArgumentException("Timestamp cannot be more than 15 s earlier than last block timestamp: " + block.getTimestamp());
    }
    int distance = 0;
    while (block.getTimestamp() > timestamp - Constants.EC_RULE_TERMINATOR && distance < Constants.EC_BLOCK_DISTANCE_LIMIT) {
      block = blockchain.getBlock(block.getPreviousBlockId());
      distance += 1;
    }
    return block;
  }

  public boolean verifyFork(Transaction transaction) {
    try {
      if (!Burst.getFluxCapacitor().getValue(FluxValues.DIGITAL_GOODS_STORE)) {
        return true;
      }
      if (transaction.getReferencedTransactionFullHash() != null) {
        return true;
      }
      if (blockchain.getHeight() < Constants.EC_CHANGE_BLOCK_1 && blockchain.getHeight() - transaction.getECBlockHeight() > Constants.EC_BLOCK_DISTANCE_LIMIT) {
        return false;
      }
      Block ecBlock = blockchain.getBlock(transaction.getECBlockId());
      return ecBlock != null && ecBlock.getHeight() == transaction.getECBlockHeight();
    }
    catch ( NullPointerException e ) {
      if (logger.isDebugEnabled()) {
        logger.debug("caught null pointer exception during verifyFork with transaction: {}", JSON.toJsonString(transaction.getJsonObject()));
      }
      throw e;
    }
  }

}
