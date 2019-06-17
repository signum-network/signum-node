package brs.http;

import brs.*;
import brs.services.ParameterService;
import brs.util.Convert;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;

import static brs.http.common.Parameters.*;
import static brs.http.common.ResultFields.ERROR_CODE_RESPONSE;
import static brs.http.common.ResultFields.ERROR_DESCRIPTION_RESPONSE;

final class SendMoneyEscrow extends CreateTransaction {
	
  private final ParameterService parameterService;
  private final Blockchain blockchain;
	
  SendMoneyEscrow(ParameterService parameterService, Blockchain blockchain, APITransactionManager apiTransactionManager) {
    super(new APITag[] {APITag.TRANSACTIONS, APITag.CREATE_TRANSACTION}, apiTransactionManager, RECIPIENT_PARAMETER, AMOUNT_NQT_PARAMETER, ESCROW_DEADLINE_PARAMETER, SIGNERS_PARAMETER, REQUIRED_SIGNERS_PARAMETER, DEADLINE_ACTION_PARAMETER);
    this.parameterService = parameterService;
    this.blockchain = blockchain;
  }
	
  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    Account sender = parameterService.getSenderAccount(req);
    Long recipient = ParameterParser.getRecipientId(req);
    Long amountNQT = ParameterParser.getAmountNQT(req);
    String signerString = Convert.emptyToNull(req.getParameter(SIGNERS_PARAMETER));
		
    long requiredSigners;
    try {
      requiredSigners = Long.parseLong(req.getParameter(REQUIRED_SIGNERS_PARAMETER));
      if(requiredSigners < 1 || requiredSigners > 10) {
        JsonObject response = new JsonObject();
        response.addProperty(ERROR_CODE_RESPONSE, 4);
        response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Invalid number of requiredSigners");
        return response;
      }
    }
    catch(Exception e) {
      JsonObject response = new JsonObject();
      response.addProperty(ERROR_CODE_RESPONSE, 4);
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Invalid requiredSigners parameter");
      return response;
    }
		
    if(signerString == null) {
      JsonObject response = new JsonObject();
      response.addProperty(ERROR_CODE_RESPONSE, 3);
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Signers not specified");
      return response;
    }
		
    String[] signersArray = signerString.split(";", 10);
		
    if(signersArray.length < 1 || signersArray.length > 10 || signersArray.length < requiredSigners) {
      JsonObject response = new JsonObject();
      response.addProperty(ERROR_CODE_RESPONSE, 4);
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Invalid number of signers");
      return response;
    }
		
    ArrayList<Long> signers = new ArrayList<>();
		
    try {
      for(String signer : signersArray) {
        long id = Convert.parseAccountId(signer);
        signers.add(id);
      }
    }
    catch(Exception e) {
      JsonObject response = new JsonObject();
      response.addProperty(ERROR_CODE_RESPONSE, 4);
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Invalid signers parameter");
      return response;
    }
		
    long totalAmountNQT = Convert.safeAdd(amountNQT, signers.size() * Constants.ONE_BURST);
    if(sender.getBalanceNQT() < totalAmountNQT) {
      JsonObject response = new JsonObject();
      response.addProperty(ERROR_CODE_RESPONSE, 6);
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Insufficient funds");
      return response;
    }
		
    long deadline;
    try {
      deadline = Long.parseLong(req.getParameter(ESCROW_DEADLINE_PARAMETER));
      if(deadline < 1 || deadline > 7776000) {
        JsonObject response = new JsonObject();
        response.addProperty(ERROR_CODE_RESPONSE, 4);
        response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Escrow deadline must be 1 - 7776000");
        return response;
      }
    }
    catch(Exception e) {
      JsonObject response = new JsonObject();
      response.addProperty(ERROR_CODE_RESPONSE, 4);
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Invalid " + ESCROW_DEADLINE_PARAMETER + " parameter");
      return response;
    }

    Escrow.DecisionType deadlineAction = Escrow.stringToDecision(req.getParameter(DEADLINE_ACTION_PARAMETER));
    if(deadlineAction == null || deadlineAction == Escrow.DecisionType.UNDECIDED) {
      JsonObject response = new JsonObject();
      response.addProperty(ERROR_CODE_RESPONSE, 4);
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Invalid " + DEADLINE_ACTION_PARAMETER + " parameter");
      return response;
    }
		
    Attachment.AdvancedPaymentEscrowCreation attachment = new Attachment.AdvancedPaymentEscrowCreation(amountNQT, (int) deadline, deadlineAction, (int) requiredSigners, signers, blockchain.getHeight());
		
    return createTransaction(req, sender, recipient, 0, attachment);
  }
}
