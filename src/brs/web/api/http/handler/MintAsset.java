package brs.web.api.http.handler;

import brs.*;
import brs.assetexchange.AssetExchange;
import brs.fluxcapacitor.FluxValues;
import brs.services.ParameterService;
import brs.util.Convert;
import brs.web.api.http.common.APITransactionManager;
import brs.web.api.http.common.JSONResponses;
import brs.web.api.http.common.LegacyDocTag;
import brs.web.api.http.common.ParameterException;
import brs.web.api.http.common.ParameterParser;
import com.google.gson.JsonElement;

import jakarta.servlet.http.HttpServletRequest;

import static brs.web.api.http.common.JSONResponses.INCORRECT_ASSET_QUANTITY;
import static brs.web.api.http.common.Parameters.*;

public final class MintAsset extends CreateTransaction {

  private final ParameterService parameterService;
  private final Blockchain blockchain;
  private final AssetExchange assetExchange;

  public MintAsset(ParameterService parameterService, Blockchain blockchain,
                   APITransactionManager apiTransactionManager, AssetExchange assetExchange) {
    super(new LegacyDocTag[] {LegacyDocTag.AE, LegacyDocTag.CREATE_TRANSACTION}, apiTransactionManager, ASSET_PARAMETER, QUANTITY_QNT_PARAMETER);
    this.parameterService = parameterService;
    this.blockchain = blockchain;
    this.assetExchange = assetExchange;
  }

  @Override
  protected
  JsonElement processRequest(HttpServletRequest req) throws SignumException {

    Asset asset = parameterService.getAsset(req);
    long quantityQNT = ParameterParser.getQuantityQNT(req);
    Account account = parameterService.getSenderAccount(req);

    if(asset.getMintable() == false) {
      return JSONResponses.incorrect("this asset is not mintable");
    }
    if(!Signum.getFluxCapacitor().getValue(FluxValues.SMART_TOKEN)) {
      return JSONResponses.incorrect("minting assets is not enabled yet");
    }
    if(asset.getAccountId() != account.getId()) {
      return JSONResponses.incorrect("only the current asset owner can mint new coins");
    }

    boolean unconfirmed = !Signum.getFluxCapacitor().getValue(FluxValues.DISTRIBUTION_FIX);
    long circulatingSupply = assetExchange.getAssetCirculatingSupply(asset, false, unconfirmed);
    long newSupply;
    try {
      newSupply = Convert.safeAdd(circulatingSupply, quantityQNT);
    } catch (ArithmeticException e) {
      throw new ParameterException(INCORRECT_ASSET_QUANTITY);
    }
    if (newSupply > Constants.MAX_ASSET_QUANTITY_QNT) {
      throw new ParameterException(INCORRECT_ASSET_QUANTITY);
    }

    Attachment attachment = new Attachment.ColoredCoinsAssetMint(asset.getId(), quantityQNT, blockchain.getHeight());
    return createTransaction(req, account, null, 0, attachment);

  }

}
