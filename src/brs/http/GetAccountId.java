package brs.http;

import brs.Account;
import brs.crypto.Crypto;
import brs.util.Convert;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.JSONResponses.MISSING_SECRET_PHRASE_OR_PUBLIC_KEY;
import static brs.http.common.Parameters.PUBLIC_KEY_PARAMETER;
import static brs.http.common.Parameters.SECRET_PHRASE_PARAMETER;
import static brs.http.common.ResultFields.ACCOUNT_RESPONSE;
import static brs.http.common.ResultFields.PUBLIC_KEY_RESPONSE;

public final class GetAccountId extends APIServlet.JsonRequestHandler {

  static final GetAccountId instance = new GetAccountId();

  public GetAccountId() {
    super(new APITag[] {APITag.ACCOUNTS}, SECRET_PHRASE_PARAMETER, PUBLIC_KEY_PARAMETER);
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {

    long accountId;
    String secretPhrase = Convert.emptyToNull(req.getParameter(SECRET_PHRASE_PARAMETER));
    String publicKeyString = Convert.emptyToNull(req.getParameter(PUBLIC_KEY_PARAMETER));
    if (secretPhrase != null) {
      byte[] publicKey = Crypto.getPublicKey(secretPhrase);
      accountId = Account.getId(publicKey);
      publicKeyString = Convert.toHexString(publicKey);
    } else if (publicKeyString != null) {
      accountId = Account.getId(Convert.parseHexString(publicKeyString));
    } else {
      return MISSING_SECRET_PHRASE_OR_PUBLIC_KEY;
    }

    JsonObject response = new JsonObject();
    JSONData.putAccount(response, ACCOUNT_RESPONSE, accountId);
    response.addProperty(PUBLIC_KEY_RESPONSE, publicKeyString);

    return response;
  }

  @Override
  boolean requirePost() {
    return true;
  }

}
