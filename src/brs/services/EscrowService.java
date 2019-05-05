package brs.services;

import brs.Account;
import brs.Block;
import brs.Escrow;
import brs.Escrow.DecisionType;

import java.util.Collection;

public interface EscrowService {

  Collection<Escrow> getAllEscrowTransactions();

  Escrow getEscrowTransaction(Long id);

  Collection<Escrow> getEscrowTransactionsByParticipant(Long accountId);

  boolean isEnabled();

  void removeEscrowTransaction(Long id);

  void updateOnBlock(Block block, int blockchainHeight);

  void addEscrowTransaction(Account sender, Account recipient, Long id, Long amountNQT, int requiredSigners, Collection<Long> signers, int deadline, DecisionType deadlineAction);

  void sign(Long id, DecisionType decision, Escrow escrow);

  DecisionType checkComplete(Escrow escrow);

  void doPayout(DecisionType result, Block block, int blockchainHeight, Escrow escrow);

  boolean isIdSigner(Long id, Escrow escrow);

  void saveResultTransaction(Block block, Long escrowId, Long recipientId, Long amountNQT, DecisionType decision, int blockchainHeight);
}
