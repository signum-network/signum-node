package brs.web.api.http.handler;

import brs.Account;
import brs.Attachment;
import brs.Blockchain;
import brs.Constants;
import brs.Signum;
import brs.SignumException;
import brs.fluxcapacitor.FluxValues;
import brs.services.AccountService;
import brs.services.ParameterService;
import brs.util.Convert;

import brs.web.api.http.common.APITransactionManager;
import brs.web.api.http.common.LegacyDocTag;
import brs.web.api.http.common.ParameterParser;
import com.google.gson.JsonElement;

import jakarta.servlet.http.HttpServletRequest;

import static brs.web.api.http.common.JSONResponses.INCORRECT_COMMITMENT_EXCEEDS_MAX;
import static brs.web.api.http.common.JSONResponses.INCORRECT_FEE;
import static brs.web.api.http.common.JSONResponses.NOT_ENOUGH_FUNDS;
import static brs.web.api.http.common.Parameters.AMOUNT_NQT_PARAMETER;

public final class AddCommitment extends CreateTransaction {

  private final ParameterService parameterService;
  private final Blockchain blockchain;

  public AddCommitment(ParameterService parameterService, Blockchain blockchain, AccountService accountService, APITransactionManager apiTransactionManager) {
    super(new LegacyDocTag[] {LegacyDocTag.ACCOUNTS, LegacyDocTag.MINING, LegacyDocTag.CREATE_TRANSACTION}, apiTransactionManager, AMOUNT_NQT_PARAMETER);
    this.parameterService = parameterService;
    this.blockchain = blockchain;
  }

  @Override
  protected
  JsonElement processRequest(HttpServletRequest req) throws SignumException {
    final Account account = parameterService.getSenderAccount(req);
    long amountNQT = ParameterParser.getAmountNQT(req);

    long minimumFeeNQT = Signum.getFluxCapacitor().getValue(FluxValues.FEE_QUANT);
    long feeNQT = ParameterParser.getFeeNQT(req);
    if (feeNQT < minimumFeeNQT) {
      return INCORRECT_FEE;
    }

    try {
      if (Convert.safeAdd(amountNQT, feeNQT) > account.getUnconfirmedBalanceNqt()) {
        return NOT_ENOUGH_FUNDS;
      }
    } catch (ArithmeticException e) {
      return NOT_ENOUGH_FUNDS;
    }

    long totalCommitted = blockchain.getCommittedAmount(account.getId(), blockchain.getHeight(), blockchain.getHeight(), null);
    if (totalCommitted + amountNQT > Constants.MAX_TOTAL_COMMITMENT_NQT) {
      return INCORRECT_COMMITMENT_EXCEEDS_MAX;
    }

    Attachment attachment = new Attachment.CommitmentAdd(amountNQT, blockchain.getHeight());
    return createTransaction(req, account, attachment);
  }

}
