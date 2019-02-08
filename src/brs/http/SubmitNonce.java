package brs.http;

import brs.Account;
import brs.Blockchain;
import brs.Generator;
import brs.crypto.Crypto;
import brs.services.AccountService;
import brs.util.Convert;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.*;


final class SubmitNonce extends APIServlet.APIRequestHandler {

  private final AccountService accountService;
  private final Blockchain blockchain;
  private final Generator generator;

  SubmitNonce(AccountService accountService, Blockchain blockchain, Generator generator) {
    super(new APITag[] {APITag.MINING}, SECRET_PHRASE_PARAMETER, NONCE_PARAMETER, ACCOUNT_ID_PARAMETER, BLOCK_HEIGHT_PARAMETER);

    this.accountService = accountService;
    this.blockchain = blockchain;
    this.generator = generator;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {
    String secret = req.getParameter(SECRET_PHRASE_PARAMETER);
    long nonce = Convert.parseUnsignedLong(req.getParameter(NONCE_PARAMETER));

    String accountId = req.getParameter(ACCOUNT_ID_PARAMETER);

    String submissionHeight = Convert.emptyToNull(req.getParameter(BLOCK_HEIGHT_PARAMETER));

    JsonObject response = new JsonObject();

    if (submissionHeight != null) {
      try {
        int height = Integer.parseInt(submissionHeight);
        if (height != blockchain.getHeight() + 1) {
          response.addProperty("result", "Given block height does not match current blockchain height");
          return response;
        }
      } catch (NumberFormatException e) {
        response.addProperty("result", "Given block height is not a number");
        return response;
      }
    }

    if(secret == null) {
      response.addProperty("result", "Missing Passphrase");
      return response;
    }

    byte[] secretPublicKey = Crypto.getPublicKey(secret);
    Account secretAccount = accountService.getAccount(secretPublicKey);
    if(secretAccount != null) {
      Account genAccount;
      if(accountId != null) {
        genAccount = accountService.getAccount(Convert.parseAccountId(accountId));
      }
      else {
        genAccount = secretAccount;
      }

      if(genAccount != null) {
        Account.RewardRecipientAssignment assignment = accountService.getRewardRecipientAssignment(genAccount);
        long rewardId;
        if(assignment == null) {
          rewardId = genAccount.getId();
        }
        else if(assignment.getFromHeight() > blockchain.getLastBlock().getHeight() + 1) {
          rewardId = assignment.getPrevRecipientId();
        }
        else {
          rewardId = assignment.getRecipientId();
        }
        if(rewardId != secretAccount.getId()) {
          response.addProperty("result", "Passphrase does not match reward recipient");
          return response;
        }
      }
      else {
        response.addProperty("result", "Passphrase is for a different account");
        return response;
      }
    }

    Generator.GeneratorState generatorState = null;
    if(accountId == null || secretAccount == null) {
      generatorState = generator.addNonce(secret, nonce);
    }
    else {
      Account genAccount = accountService.getAccount(Convert.parseUnsignedLong(accountId));
      if(genAccount == null || genAccount.getPublicKey() == null) {
        response.addProperty("result", "Passthrough mining requires public key in blockchain");
      }
      else {
        byte[] publicKey = genAccount.getPublicKey();
        generatorState = generator.addNonce(secret, nonce, publicKey);
      }
    }

    if(generatorState == null) {
      response.addProperty("result", "failed to create generator");
      return response;
    }

    //response.addProperty("result", "deadline: " + generator.getDeadline());
    response.addProperty("result", "success");
    response.addProperty("deadline", generatorState.getDeadline());

    return response;
  }

  @Override
  boolean requirePost() {
    return true;
  }
}
