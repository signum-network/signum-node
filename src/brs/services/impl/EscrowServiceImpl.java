package brs.services.impl;

import brs.*;
import brs.Escrow.Decision;
import brs.Escrow.DecisionType;
import brs.db.BurstKey;
import brs.db.BurstKey.LongKeyFactory;
import brs.db.VersionedEntityTable;
import brs.db.sql.DbKey.LinkKeyFactory;
import brs.db.store.EscrowStore;
import brs.services.AccountService;
import brs.services.AliasService;
import brs.services.EscrowService;
import org.jooq.Condition;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;

import static brs.schema.Tables.ESCROW;

public class EscrowServiceImpl implements EscrowService {

  private final VersionedEntityTable<Escrow> escrowTable;
  private final LongKeyFactory<Escrow> escrowDbKeyFactory;
  private final VersionedEntityTable<Decision> decisionTable;
  private final LinkKeyFactory<Decision> decisionDbKeyFactory;
  private final EscrowStore escrowStore;
  private final Blockchain blockchain;
  private final AliasService aliasService;
  private final AccountService accountService;
  private final List<Transaction> resultTransactions;

  public EscrowServiceImpl(EscrowStore escrowStore, Blockchain blockchain, AliasService aliasService, AccountService accountService) {
    this.escrowStore = escrowStore;
    this.escrowTable = escrowStore.getEscrowTable();
    this.escrowDbKeyFactory = escrowStore.getEscrowDbKeyFactory();
    this.decisionTable = escrowStore.getDecisionTable();
    this.decisionDbKeyFactory = escrowStore.getDecisionDbKeyFactory();
    this.resultTransactions = escrowStore.getResultTransactions();
    this.blockchain = blockchain;
    this.aliasService = aliasService;
    this.accountService = accountService;
  }

  @Override
  public Collection<Escrow> getAllEscrowTransactions() {
    return escrowTable.getAll(0, -1);
  }

  @Override
  public Escrow getEscrowTransaction(Long id) {
    return escrowTable.get(escrowDbKeyFactory.newKey(id));
  }

  @Override
  public Collection<Escrow> getEscrowTransactionsByParticipant(Long accountId) {
    return escrowStore.getEscrowTransactionsByParticipant(accountId);
  }

  @Override
  public boolean isEnabled() {
    if(blockchain.getLastBlock().getHeight() >= Constants.BURST_ESCROW_START_BLOCK) {
      return true;
    }

    Alias escrowEnabled = aliasService.getAlias("featureescrow");
    return escrowEnabled != null && escrowEnabled.getAliasURI().equals("enabled");
  }

  @Override
  public void removeEscrowTransaction(Long id) {
    Escrow escrow = escrowTable.get(escrowDbKeyFactory.newKey(id));
    if(escrow == null) {
      return;
    }
    escrow.getDecisions().forEach(decisionTable::delete);
    escrowTable.delete(escrow);
  }


  @Override
  public void addEscrowTransaction(Account sender, Account recipient, Long id, Long amountNQT, int requiredSigners, Collection<Long> signers, int deadline, DecisionType deadlineAction) {
    final BurstKey dbKey = escrowDbKeyFactory.newKey(id);
    Escrow newEscrowTransaction = new Escrow(dbKey, sender, recipient, id, amountNQT, requiredSigners, deadline, deadlineAction);
    escrowTable.insert(newEscrowTransaction);
    BurstKey senderDbKey = decisionDbKeyFactory.newKey(id, sender.getId());
    Decision senderDecision = new Decision(senderDbKey, id, sender.getId(), DecisionType.UNDECIDED);
    decisionTable.insert(senderDecision);
    BurstKey recipientDbKey = decisionDbKeyFactory.newKey(id, recipient.getId());
    Decision recipientDecision = new Decision(recipientDbKey, id, recipient.getId(), DecisionType.UNDECIDED);
    decisionTable.insert(recipientDecision);
    for(Long signer : signers) {
      BurstKey signerDbKey = decisionDbKeyFactory.newKey(id, signer);
      Decision decision = new Decision(signerDbKey, id, signer, DecisionType.UNDECIDED);
      decisionTable.insert(decision);
    }
  }

  @Override
  public synchronized void sign(Long id, DecisionType decision, Escrow escrow) {
    if(id.equals(escrow.getSenderId()) && decision != DecisionType.RELEASE) {
      return;
    }

    if(id.equals(escrow.getRecipientId()) && decision != DecisionType.REFUND) {
      return;
    }

    Decision decisionChange = decisionTable.get(decisionDbKeyFactory.newKey(escrow.getId(), id));
    if(decisionChange == null) {
      return;
    }
    decisionChange.setDecision(decision);

    decisionTable.insert(decisionChange);

    updatedEscrowIds.add(escrow.getId());
  }

  @Override
  public DecisionType checkComplete(Escrow escrow) {
    Decision senderDecision = decisionTable.get(decisionDbKeyFactory.newKey(escrow.getId(), escrow.getSenderId()));
    if(senderDecision.getDecision() == DecisionType.RELEASE) {
      return DecisionType.RELEASE;
    }
    Decision recipientDecision = decisionTable.get(decisionDbKeyFactory.newKey(escrow.getId(), escrow.getRecipientId()));
    if(recipientDecision.getDecision() == DecisionType.REFUND) {
      return DecisionType.REFUND;
    }

    int countRelease = 0;
    int countRefund = 0;
    int countSplit = 0;

    for (Decision decision : Burst.getStores().getEscrowStore().getDecisions(escrow.getId())) {
      if(decision.getAccountId().equals(escrow.getSenderId()) ||
          decision.getAccountId().equals(escrow.getRecipientId())) {
        continue;
      }
      switch(decision.getDecision()) {
        case RELEASE:
          countRelease++;
          break;
        case REFUND:
          countRefund++;
          break;
        case SPLIT:
          countSplit++;
          break;
        default:
          break;
      }
    }

    if(countRelease >= escrow.getRequiredSigners()) {
      return DecisionType.RELEASE;
    }
    if(countRefund >= escrow.getRequiredSigners()) {
      return DecisionType.REFUND;
    }
    if(countSplit >= escrow.getRequiredSigners()) {
      return DecisionType.SPLIT;
    }

    return DecisionType.UNDECIDED;
  }

  private static Condition getUpdateOnBlockClause(final int timestamp) {
    return ESCROW.DEADLINE.lt(timestamp);
  }


  private final ConcurrentSkipListSet<Long> updatedEscrowIds = new ConcurrentSkipListSet<>();

  @Override
  public void updateOnBlock(Block block, int blockchainHeight) {
    resultTransactions.clear();

    escrowTable.getManyBy(getUpdateOnBlockClause(block.getTimestamp()), 0, -1).forEach(escrow -> updatedEscrowIds.add(escrow.getId()));

    if (!updatedEscrowIds.isEmpty()) {
      for (Long escrowId : updatedEscrowIds) {
        Escrow escrow = escrowTable.get(escrowDbKeyFactory.newKey(escrowId));
        Escrow.DecisionType result = checkComplete(escrow);
        if (result != Escrow.DecisionType.UNDECIDED || escrow.getDeadline() < block.getTimestamp()) {
          if (result == Escrow.DecisionType.UNDECIDED) {
            result = escrow.getDeadlineAction();
          }
          doPayout(result, block, blockchainHeight, escrow);

          removeEscrowTransaction(escrowId);
        }
      }
      if (!resultTransactions.isEmpty()) {
        Burst.getDbs().getTransactionDb().saveTransactions( resultTransactions);
      }
      updatedEscrowIds.clear();
    }
  }

  @Override
  public synchronized void doPayout(DecisionType result, Block block, int blockchainHeight, Escrow escrow) {
    switch(result) {
      case RELEASE:
        accountService.addToBalanceAndUnconfirmedBalanceNQT(accountService.getAccount(escrow.getRecipientId()), escrow.getAmountNQT());
        saveResultTransaction(block, escrow.getId(), escrow.getRecipientId(), escrow.getAmountNQT(), DecisionType.RELEASE, blockchainHeight);
        break;
      case REFUND:
        accountService.addToBalanceAndUnconfirmedBalanceNQT(accountService.getAccount(escrow.getSenderId()), escrow.getAmountNQT());
        saveResultTransaction(block, escrow.getId(), escrow.getSenderId(), escrow.getAmountNQT(), DecisionType.REFUND, blockchainHeight);
        break;
      case SPLIT:
        Long halfAmountNQT = escrow.getAmountNQT() / 2;
        accountService.addToBalanceAndUnconfirmedBalanceNQT(accountService.getAccount(escrow.getRecipientId()), halfAmountNQT);
        accountService.addToBalanceAndUnconfirmedBalanceNQT(accountService.getAccount(escrow.getSenderId()), escrow.getAmountNQT() - halfAmountNQT);
        saveResultTransaction(block, escrow.getId(), escrow.getRecipientId(), halfAmountNQT, DecisionType.SPLIT, blockchainHeight);
        saveResultTransaction(block, escrow.getId(), escrow.getSenderId(), escrow.getAmountNQT() - halfAmountNQT, DecisionType.SPLIT, blockchainHeight);
        break;
      default: // should never get here
        break;
    }
  }

  @Override
  public boolean isIdSigner(Long id, Escrow escrow) {
    return decisionTable.get(decisionDbKeyFactory.newKey(escrow.getId(), id)) != null;
  }

  @Override
  public void saveResultTransaction(Block block, Long escrowId, Long recipientId, Long amountNQT, DecisionType decision, int blockchainHeight) {
    Attachment.AbstractAttachment attachment = new Attachment.AdvancedPaymentEscrowResult(escrowId, decision, blockchainHeight);
    Transaction.Builder builder = new Transaction.Builder((byte)1, Genesis.getCreatorPublicKey(),
        amountNQT, 0L, block.getTimestamp(), (short)1440, attachment);
    builder.senderId(0L)
        .recipientId(recipientId)
        .blockId(block.getId())
        .height(block.getHeight())
        .blockTimestamp(block.getTimestamp())
        .ecBlockHeight(0)
        .ecBlockId(0L);

    Transaction transaction;
    try {
      transaction = builder.build();
    }
    catch(BurstException.NotValidException e) {
      throw new RuntimeException(e.toString(), e);
    }

    if(!Burst.getDbs().getTransactionDb().hasTransaction(transaction.getId())) {
      resultTransactions.add(transaction);
    }
  }
}
