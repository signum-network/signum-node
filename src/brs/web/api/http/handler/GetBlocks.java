package brs.web.api.http.handler;

import brs.Block;
import brs.Blockchain;
import brs.web.api.http.common.BlockPage;
import brs.web.api.http.common.LegacyDocTag;
import brs.web.api.http.common.ParameterParser;
import brs.web.api.http.common.Parameters;
import brs.services.BlockService;

import brs.web.api.http.ApiServlet;
import brs.web.api.http.common.JSONData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jakarta.servlet.http.HttpServletRequest;

import static brs.web.api.http.common.Parameters.*;
import static brs.web.api.http.common.ResultFields.NEXT_INDEX_RESPONSE;

/**
 * Returns a range of blocks, newest first. The response is bounded by a transaction budget, see
 * {@link BlockPage}; {@code nextIndex} tells the client where to continue.
 */
public final class GetBlocks extends ApiServlet.JsonRequestHandler {

  private static final int MAX_BLOCKS_PER_REQUEST = 100;

  private final Blockchain blockchain;
  private final BlockService blockService;
  private final int maxTransactions;

  public GetBlocks(Blockchain blockchain, BlockService blockService, int maxTransactions) {
    super(new LegacyDocTag[] {LegacyDocTag.BLOCKS}, FIRST_INDEX_PARAMETER, LAST_INDEX_PARAMETER, INCLUDE_TRANSACTIONS_PARAMETER);
    this.blockchain = blockchain;
    this.blockService = blockService;
    this.maxTransactions = maxTransactions;
  }

  @Override
  protected
  JsonElement processRequest(HttpServletRequest req) {

    int firstIndex = ParameterParser.getFirstIndex(req);
    int lastIndex = ParameterParser.getLastIndex(req);
    if (lastIndex < 0 || lastIndex - firstIndex > MAX_BLOCKS_PER_REQUEST - 1) {
      lastIndex = firstIndex + MAX_BLOCKS_PER_REQUEST - 1;
    }

    boolean includeTransactions = Parameters.isTrue(req.getParameter(Parameters.INCLUDE_TRANSACTIONS_PARAMETER));

    BlockPage page = BlockPage.of(blockchain.getBlocks(firstIndex, lastIndex), firstIndex, lastIndex, maxTransactions);
    int currentHeight = blockchain.getHeight();
    JsonArray blocksArray = new JsonArray();
    for (Block block : page.blocks()) {
      blocksArray.add(JSONData.block(block, includeTransactions, currentHeight, blockService.getBlockReward(block),
          blockService.getScoopNum(block)));
    }

    JsonObject response = new JsonObject();
    response.add("blocks", blocksArray);
    page.nextIndex().ifPresent(nextIndex -> response.addProperty(NEXT_INDEX_RESPONSE, nextIndex));
    return response;
  }
}
