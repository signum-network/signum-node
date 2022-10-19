package brs.http;

import brs.*;
import brs.fluxcapacitor.FluxValues;
import brs.services.ParameterService;
import brs.util.Convert;
import brs.util.TextUtils;
import com.google.gson.JsonElement;

import javax.servlet.http.HttpServletRequest;

import static brs.http.JSONResponses.*;
import static brs.http.common.Parameters.*;

public final class IssueAsset extends CreateTransaction {

  private final ParameterService parameterService;
  private final Blockchain blockchain;

  IssueAsset(ParameterService parameterService, Blockchain blockchain, APITransactionManager apiTransactionManager) {
    super(new APITag[]{APITag.AE, APITag.CREATE_TRANSACTION}, apiTransactionManager,
        NAME_PARAMETER, DESCRIPTION_PARAMETER, QUANTITY_QNT_PARAMETER, DECIMALS_PARAMETER, MINTABLE_PARAMETER);
    this.parameterService = parameterService;
    this.blockchain = blockchain;
  }

  @Override
  protected
  JsonElement processRequest(HttpServletRequest req) throws BurstException {

    String name = req.getParameter(NAME_PARAMETER);
    String description = req.getParameter(DESCRIPTION_PARAMETER);
    String decimalsValue = Convert.emptyToNull(req.getParameter(DECIMALS_PARAMETER));
    boolean mintable = "true".equals(req.getParameter(MINTABLE_PARAMETER));
    if(mintable && !Burst.getFluxCapacitor().getValue(FluxValues.SMART_TOKEN)) {
      //only after the fork we are allowed to have a mintable assset
      return JSONResponses.incorrect(MINTABLE_PARAMETER);
    }

    if (name == null) {
      return MISSING_NAME;
    }

    name = name.trim();
    if (name.length() < Constants.MIN_ASSET_NAME_LENGTH || name.length() > Constants.MAX_ASSET_NAME_LENGTH) {
      return INCORRECT_ASSET_NAME_LENGTH;
    }

    if (!TextUtils.isInAlphabet(name)) {
      return INCORRECT_ASSET_NAME;
    }

    if (description != null && description.length() > Constants.MAX_ASSET_DESCRIPTION_LENGTH) {
      return INCORRECT_ASSET_DESCRIPTION;
    }

    byte decimals = 0;
    if (decimalsValue != null) {
      try {
        decimals = Byte.parseByte(decimalsValue);
        if (decimals < 0 || decimals > 8) {
          return INCORRECT_DECIMALS;
        }
      } catch (NumberFormatException e) {
        return INCORRECT_DECIMALS;
      }
    }

    long quantityQNT = mintable ? ParameterParser.getZeroableQuantityQNT(req) : ParameterParser.getQuantityQNT(req);
    Account account = parameterService.getSenderAccount(req);
    Attachment attachment = new Attachment.ColoredCoinsAssetIssuance(name, description, quantityQNT, decimals, blockchain.getHeight(), mintable);
    return createTransaction(req, account, attachment);

  }

}
