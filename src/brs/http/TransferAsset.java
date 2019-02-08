package brs.http;

import brs.*;
import brs.services.AccountService;
import brs.services.ParameterService;
import com.google.gson.JsonElement;

import javax.servlet.http.HttpServletRequest;

import static brs.http.JSONResponses.NOT_ENOUGH_ASSETS;
import static brs.http.common.Parameters.*;

public final class TransferAsset extends CreateTransaction {

  private final ParameterService parameterService;
  private final Blockchain blockchain;
  private final AccountService accountService;

  public TransferAsset(ParameterService parameterService, Blockchain blockchain, APITransactionManager apiTransactionManager, AccountService accountService) {
    super(new APITag[] {APITag.AE, APITag.CREATE_TRANSACTION}, apiTransactionManager, RECIPIENT_PARAMETER, ASSET_PARAMETER, QUANTITY_QNT_PARAMETER);
    this.parameterService = parameterService;
    this.blockchain = blockchain;
    this.accountService = accountService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {

    long recipient = ParameterParser.getRecipientId(req);

    Asset asset = parameterService.getAsset(req);
    long quantityQNT = ParameterParser.getQuantityQNT(req);
    Account account = parameterService.getSenderAccount(req);

    long assetBalance = accountService.getUnconfirmedAssetBalanceQNT(account, asset.getId());
    if (assetBalance < 0 || quantityQNT > assetBalance) {
      return NOT_ENOUGH_ASSETS;
    }

    Attachment attachment = new Attachment.ColoredCoinsAssetTransfer(asset.getId(), quantityQNT, blockchain.getHeight());
    return createTransaction(req, account, recipient, 0, attachment);

  }

}
