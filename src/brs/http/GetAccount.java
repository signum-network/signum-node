package brs.http;

import brs.Account;
import brs.Block;
import brs.Blockchain;
import brs.BurstException;
import brs.Generator;
import brs.services.ParameterService;
import brs.util.Convert;
import burst.kit.crypto.BurstCrypto;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.ACCOUNT_PARAMETER;
import static brs.http.common.Parameters.HEIGHT_PARAMETER;
import static brs.http.common.Parameters.ESTIMATE_COMMITMENT_PARAMETER;
import static brs.http.common.ResultFields.*;

public final class GetAccount extends APIServlet.JsonRequestHandler {

  private final ParameterService parameterService;
  private final Blockchain blockchain;
  private final Generator generator;

  GetAccount(ParameterService parameterService, Blockchain blockchain, Generator generator) {
    super(new APITag[] {APITag.ACCOUNTS}, ACCOUNT_PARAMETER, HEIGHT_PARAMETER, ESTIMATE_COMMITMENT_PARAMETER);
    this.parameterService = parameterService;
    this.blockchain = blockchain;
    this.generator = generator;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {

    Account account = parameterService.getAccount(req);

    JsonObject response = JSONData.accountBalance(account);
    JSONData.putAccount(response, ACCOUNT_RESPONSE, account.getId());

    if (account.getPublicKey() != null) {
      response.addProperty(ACCOUNT_RESPONSE + "RSExtended", BurstCrypto.getInstance().getBurstAddressFromPublic(account.getPublicKey()).getExtendedAddress());
      response.addProperty(PUBLIC_KEY_RESPONSE, Convert.toHexString(account.getPublicKey()));
    }
    if (account.getName() != null) {
      response.addProperty(NAME_RESPONSE, account.getName());
    }
    if (account.getDescription() != null) {
      response.addProperty(DESCRIPTION_RESPONSE, account.getDescription());
    }
    
    int height = parameterService.getHeight(req);
    if(height < 0) {
      height = blockchain.getHeight();
    }
    
    if(parameterService.getEstimateCommitment(req)) {
      Block block = blockchain.getBlockAtHeight(height);
      long commitment = generator.estimateCommitment(account.getId(), block);
      response.addProperty(COMMITMENT_NQT_RESPONSE, Convert.toUnsignedLong(commitment));
    }

    return response;
  }

}
