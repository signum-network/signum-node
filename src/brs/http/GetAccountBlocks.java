package brs.http;

import brs.Account;
import brs.Block;
import brs.Blockchain;
import brs.BurstException;
import brs.http.common.Parameters;
import brs.services.BlockService;
import brs.services.ParameterService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.*;
import static brs.http.common.ResultFields.BLOCKS_RESPONSE;

public final class GetAccountBlocks extends APIServlet.JsonRequestHandler {

  private final Blockchain blockchain;
  private final ParameterService parameterService;
  private final BlockService blockService;

  GetAccountBlocks(Blockchain blockchain, ParameterService parameterService, BlockService blockService) {
    super(new APITag[] {APITag.ACCOUNTS}, ACCOUNT_PARAMETER, TIMESTAMP_PARAMETER, FIRST_INDEX_PARAMETER, LAST_INDEX_PARAMETER, INCLUDE_TRANSACTIONS_PARAMETER);
    this.blockchain = blockchain;
    this.parameterService = parameterService;
    this.blockService = blockService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {

    Account account = parameterService.getAccount(req);
    int timestamp = ParameterParser.getTimestamp(req);
    int firstIndex = ParameterParser.getFirstIndex(req);
    int lastIndex = ParameterParser.getLastIndex(req);

    boolean includeTransactions = Parameters.isTrue(req.getParameter(INCLUDE_TRANSACTIONS_PARAMETER));

    JsonArray blocks = new JsonArray();
    for (Block block : blockchain.getBlocks(account, timestamp, firstIndex, lastIndex)) {
      blocks.add(JSONData.block(block, includeTransactions, blockchain.getHeight(), blockService.getBlockReward(block), blockService.getScoopNum(block)));
    }

    JsonObject response = new JsonObject();
    response.add(BLOCKS_RESPONSE, blocks);

    return response;
  }

}
