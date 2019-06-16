package brs.http;

import brs.Block;
import brs.Blockchain;
import brs.Burst;
import brs.Generator;
import brs.util.Convert;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

final class GetMiningInfo extends APIServlet.JsonRequestHandler {

  private final Blockchain blockchain;
  private final Generator generator;

  GetMiningInfo(Blockchain blockchain, Generator generator) {
    super(new APITag[] {APITag.MINING, APITag.INFO});
    this.blockchain = blockchain;
    this.generator = generator;
  }
	
  @Override
  JsonElement processRequest(HttpServletRequest req) {
    JsonObject response = new JsonObject();
		
    response.addProperty("height", Long.toString((long)Burst.getBlockchain().getHeight() + 1));
		
    Block lastBlock = blockchain.getLastBlock();
    byte[] newGenSig = generator.calculateGenerationSignature(lastBlock.getGenerationSignature(), lastBlock.getGeneratorId());
		
    response.addProperty("generationSignature", Convert.toHexString(newGenSig));
    response.addProperty("baseTarget", Long.toString(lastBlock.getBaseTarget()));
		
    return response;
  }
}
