package brs.web.api.http.handler;

import brs.Account;
import brs.Block;
import brs.Blockchain;
import brs.SignumException;
import brs.web.api.http.common.LegacyDocTag;
import brs.web.api.http.common.ParameterParser;
import brs.web.api.http.common.Parameters;
import brs.services.BlockService;
import brs.services.ParameterService;
import brs.web.api.http.ApiServlet;
import brs.web.api.http.common.BlockPage;
import brs.web.api.http.common.JSONData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jakarta.servlet.http.HttpServletRequest;

import static brs.web.api.http.common.Parameters.*;
import static brs.web.api.http.common.ResultFields.BLOCKS_RESPONSE;
import static brs.web.api.http.common.ResultFields.NEXT_INDEX_RESPONSE;

/**
 * Returns the blocks an account forged, newest first. The response is bounded by a transaction
 * budget, see {@link BlockPage}; {@code nextIndex} tells the client where to continue.
 */
public final class GetAccountBlocks extends ApiServlet.JsonRequestHandler {

  private final Blockchain blockchain;
  private final ParameterService parameterService;
  private final BlockService blockService;
  private final int maxTransactions;

  public GetAccountBlocks(Blockchain blockchain, ParameterService parameterService, BlockService blockService,
      int maxTransactions) {
    super(new LegacyDocTag[] {LegacyDocTag.ACCOUNTS}, ACCOUNT_PARAMETER, TIMESTAMP_PARAMETER, FIRST_INDEX_PARAMETER, LAST_INDEX_PARAMETER, INCLUDE_TRANSACTIONS_PARAMETER);
    this.blockchain = blockchain;
    this.parameterService = parameterService;
    this.blockService = blockService;
    this.maxTransactions = maxTransactions;
  }

  @Override
  protected
  JsonElement processRequest(HttpServletRequest req) throws SignumException {

    Account account = parameterService.getAccount(req);
    int timestamp = ParameterParser.getTimestamp(req);
    int firstIndex = ParameterParser.getFirstIndex(req);
    int lastIndex = ParameterParser.getLastIndex(req);

    boolean includeTransactions = Parameters.isTrue(req.getParameter(INCLUDE_TRANSACTIONS_PARAMETER));

    BlockPage page = BlockPage.of(blockchain.getBlocks(account, timestamp, firstIndex, lastIndex).getCollection(),
        firstIndex, lastIndex, maxTransactions);
    int currentHeight = blockchain.getHeight();
    JsonArray blocks = new JsonArray();
    for (Block block : page.blocks()) {
      blocks.add(JSONData.block(block, includeTransactions, currentHeight, blockService.getBlockReward(block), blockService.getScoopNum(block)));
    }

    JsonObject response = new JsonObject();
    response.add(BLOCKS_RESPONSE, blocks);
    page.nextIndex().ifPresent(nextIndex -> response.addProperty(NEXT_INDEX_RESPONSE, nextIndex));

    return response;
  }

}
