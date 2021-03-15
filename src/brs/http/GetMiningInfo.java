package brs.http;

import brs.Block;
import brs.Blockchain;
import brs.Burst;
import brs.Constants;
import brs.Generator;
import brs.http.common.ResultFields;
import brs.services.BlockService;
import brs.util.Convert;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

final class GetMiningInfo extends APIServlet.JsonRequestHandler {

  private final Blockchain blockchain;
  private final BlockService blockService;
  private final Generator generator;

  GetMiningInfo(Blockchain blockchain, BlockService blockService, Generator generator) {
    super(new APITag[] {APITag.MINING, APITag.INFO});
    this.blockchain = blockchain;
    this.blockService = blockService;
    this.generator = generator;
  }
	
  @Override
  JsonElement processRequest(HttpServletRequest req) {
    JsonObject response = new JsonObject();
		
    response.addProperty("height", Long.toString((long)Burst.getBlockchain().getHeight() + 1));
		
    Block lastBlock = blockchain.getLastBlock();
    byte[] newGenSig = generator.calculateGenerationSignature(lastBlock.getGenerationSignature(), lastBlock.getGeneratorId());
		
    response.addProperty(ResultFields.GENERATION_SIGNATURE_RESPONSE, Convert.toHexString(newGenSig));
    response.addProperty(ResultFields.BASE_TARGET_RESPONSE, Long.toString(lastBlock.getCapacityBaseTarget()));
    response.addProperty(ResultFields.AVERAGE_COMMITMENT_NQT_RESPONSE, Long.toString(lastBlock.getAverageCommitment()));
    response.addProperty(ResultFields.LAST_BLOCK_REWARD_RESPONSE, Long.toString(blockService.getBlockReward(lastBlock)/Constants.ONE_BURST));
		
    return response;
  }
}
