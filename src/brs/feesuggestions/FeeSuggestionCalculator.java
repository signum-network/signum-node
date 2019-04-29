package brs.feesuggestions;

import brs.Block;
import brs.BlockchainProcessor;
import brs.BlockchainProcessor.Event;
import brs.db.store.BlockchainStore;

import java.util.Arrays;

import static brs.Constants.FEE_QUANT;

public class FeeSuggestionCalculator {

  // index 0 = oldest, length-1 = newest
  private final Block[] latestBlocks;

  private final BlockchainStore blockchainStore;

  private FeeSuggestion feeSuggestion;

  public FeeSuggestionCalculator(BlockchainProcessor blockchainProcessor, BlockchainStore blockchainStore, int historyLength) {
    latestBlocks = new Block[historyLength];
    this.blockchainStore = blockchainStore;
    blockchainProcessor.addListener(this::newBlockApplied, Event.AFTER_BLOCK_APPLY);
  }

  public FeeSuggestion giveFeeSuggestion() {
    if (latestBlocksIsEmpty()) {
      fillInitialHistory();
      recalculateSuggestion();
    }

    return feeSuggestion;
  }

  private void newBlockApplied(Block block) {
    if (latestBlocksIsEmpty()) {
      fillInitialHistory();
    }

    pushNewBlock(block);
    recalculateSuggestion();
  }

  private void fillInitialHistory() {
    blockchainStore.getLatestBlocks(latestBlocks.length).forEachRemaining(this::pushNewBlock);
  }

  private boolean latestBlocksIsEmpty() {
    for (Block latestBlock : latestBlocks) {
      if (latestBlock == null) return true;
    }
    return false;
  }

  private void pushNewBlock(Block block) {
    // Skip index 0 as we want to remove this one
    for (int i = 1; i < latestBlocks.length; i++) {
      latestBlocks[i-1] = latestBlocks[i];
    }
    latestBlocks[latestBlocks.length - 1] = block;
  }

  private void recalculateSuggestion() {
    int lowestAmountTransactionsNearHistory = Arrays.stream(latestBlocks).mapToInt(b -> b.getTransactions().size()).min().orElse(1);
    int averageAmountTransactionsNearHistory = (int) Math.ceil(Arrays.stream(latestBlocks).mapToInt(b -> b.getTransactions().size()).average().orElse(1));
    int highestAmountTransactionsNearHistory = Arrays.stream(latestBlocks).mapToInt(b -> b.getTransactions().size()).max().orElse(1);

    long cheapFee = (1 + lowestAmountTransactionsNearHistory) * FEE_QUANT;
    long standardFee = (1 + averageAmountTransactionsNearHistory) * FEE_QUANT;
    long priorityFee = (1 + highestAmountTransactionsNearHistory) * FEE_QUANT;

    feeSuggestion = new FeeSuggestion(cheapFee, standardFee, priorityFee);
  }
}
