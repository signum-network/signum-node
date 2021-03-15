package brs.feesuggestions;

import brs.Block;
import brs.BlockchainProcessor;
import brs.Burst;
import brs.BlockchainProcessor.Event;
import brs.unconfirmedtransactions.UnconfirmedTransactionStore;

import java.util.concurrent.atomic.AtomicReference;

import static brs.Constants.FEE_QUANT;

public class FeeSuggestionCalculator {

  private final UnconfirmedTransactionStore unconfirmedTransactionStore;

  private AtomicReference<FeeSuggestion> feeSuggestion = new AtomicReference<>();

  public FeeSuggestionCalculator(BlockchainProcessor blockchainProcessor, UnconfirmedTransactionStore unconfirmedTransactionStore) {
    this.unconfirmedTransactionStore = unconfirmedTransactionStore;
    blockchainProcessor.addListener(this::newBlockApplied, Event.AFTER_BLOCK_APPLY);
    
    // Just an initial guess until we have the unconfirmed transactions information
    long cheap = 1;
    long standard = 1;
    long priority = 3;
    if(Burst.getBlockchain() != null) {
      Block lastBlock = Burst.getBlockchain().getLastBlock();
      if(lastBlock != null) {
        standard = Math.max(1, lastBlock.getTransactions().size()-2);
        priority = lastBlock.getTransactions().size()+2;
      }
    }
    
    feeSuggestion.set(new FeeSuggestion(cheap * FEE_QUANT, standard * FEE_QUANT, priority * FEE_QUANT));
  }

  public FeeSuggestion giveFeeSuggestion() {
    return feeSuggestion.get();
  }

  private void newBlockApplied(Block block) {
    recalculateSuggestion();
  }

  private void recalculateSuggestion() {
    long cheap = unconfirmedTransactionStore.getFreeSlot(15); // should confirm in about 1 hour
    long standard = unconfirmedTransactionStore.getFreeSlot(3); // should confirm in about 15 min
    long priority = unconfirmedTransactionStore.getFreeSlot(1) + 2; // should confirm in the next block

    if(standard <= cheap) {
      standard = cheap + 1;
    }
    if(priority <= standard) {
      priority = standard + 1;
    }

    long cheapFee = cheap * FEE_QUANT;
    long standardFee = standard * FEE_QUANT;
    long priorityFee = priority * FEE_QUANT;

    feeSuggestion.set(new FeeSuggestion(cheapFee, standardFee, priorityFee));
  }
}
