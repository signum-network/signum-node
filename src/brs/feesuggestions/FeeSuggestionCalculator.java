package brs.feesuggestions;

import brs.Block;
import brs.BlockchainProcessor;
import brs.BlockchainProcessor.Event;
import brs.db.store.BlockchainStore;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import static brs.Constants.FEE_QUANT;

public class FeeSuggestionCalculator {

  private final CircularFifoQueue<Block> latestBlocks;

  private final BlockchainStore blockchainStore;

  private FeeSuggestion feeSuggestion;

  public FeeSuggestionCalculator(BlockchainProcessor blockchainProcessor, BlockchainStore blockchainStore, int historyLength) {
    this.latestBlocks = new CircularFifoQueue<>(historyLength);

    this.blockchainStore = blockchainStore;

    blockchainProcessor.addListener(this::newBlockApplied, Event.AFTER_BLOCK_APPLY);
  }

  public FeeSuggestion giveFeeSuggestion() {
    if (latestBlocks.isEmpty()) {
      fillInitialHistory();
      recalculateSuggestion();
    }

    return feeSuggestion;
  }

  private void newBlockApplied(Block block) {
    if (latestBlocks.isEmpty()) {
      fillInitialHistory();
    }

    this.latestBlocks.add(block);
    recalculateSuggestion();
  }

  private void fillInitialHistory() {
    blockchainStore.getLatestBlocks(latestBlocks.maxSize()).forEachRemaining(latestBlocks::add);
  }

  private void recalculateSuggestion() {
    int lowestAmountTransactionsNearHistory = latestBlocks.stream().mapToInt(b -> b.getTransactions().size()).min().orElse(1);
    int averageAmountTransactionsNearHistory = (int) Math.ceil(latestBlocks.stream().mapToInt(b -> b.getTransactions().size()).average().orElse(1));
    int highestAmountTransactionsNearHistory = latestBlocks.stream().mapToInt(b -> b.getTransactions().size()).max().orElse(1);

    long cheapFee = (1 + lowestAmountTransactionsNearHistory) * FEE_QUANT;
    long standardFee = (1 + averageAmountTransactionsNearHistory) * FEE_QUANT;
    long priorityFee = (1 + highestAmountTransactionsNearHistory) * FEE_QUANT;

    feeSuggestion = new FeeSuggestion(cheapFee, standardFee, priorityFee);
  }
}
