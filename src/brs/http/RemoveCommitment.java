package brs.http;

import brs.Account;
import brs.Attachment;
import brs.Blockchain;
import brs.BurstException;
import brs.Constants;
import brs.services.AccountService;
import brs.services.ParameterService;
import com.google.gson.JsonElement;

import javax.servlet.http.HttpServletRequest;

import static brs.http.JSONResponses.NOT_ENOUGH_FUNDS;
import static brs.http.JSONResponses.ERROR_NOT_ALLOWED;
import static brs.http.common.Parameters.AMOUNT_NQT_PARAMETER;

public final class RemoveCommitment extends CreateTransaction {

  private final ParameterService parameterService;
  private final Blockchain blockchain;

  public RemoveCommitment(ParameterService parameterService, Blockchain blockchain, AccountService accountService, APITransactionManager apiTransactionManager) {
    super(new APITag[] {APITag.ACCOUNTS, APITag.MINING, APITag.CREATE_TRANSACTION}, apiTransactionManager, AMOUNT_NQT_PARAMETER);
    this.parameterService = parameterService;
    this.blockchain = blockchain;
  }
	
  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    final Account account = parameterService.getSenderAccount(req);
    long amountNQT = ParameterParser.getAmountNQT(req);
    
    int nBlocksMined = blockchain.getBlocksCount(account, blockchain.getHeight() - Constants.MAX_ROLLBACK, blockchain.getHeight());
    if(nBlocksMined > 0) {
      // need to wait since the last block mined to remove any commitment
      return ERROR_NOT_ALLOWED;
    }
    
    long committedAmountNQT = blockchain.getCommittedAmount(account, blockchain.getHeight(), blockchain.getHeight());
    if (committedAmountNQT < amountNQT) {
      return NOT_ENOUGH_FUNDS;
    }
    Attachment attachment = new Attachment.CommitmentRemove(amountNQT, blockchain.getHeight());
    return createTransaction(req, account, attachment);
  }

}
