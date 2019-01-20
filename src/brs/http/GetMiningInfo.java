package brs.http;

import brs.Block;
import brs.Blockchain;
import brs.Burst;
import brs.crypto.hash.Shabal256;
import brs.util.Convert;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;
import java.nio.ByteBuffer;

;

final class GetMiningInfo extends APIServlet.APIRequestHandler {

  private final Blockchain blockchain;

  GetMiningInfo(Blockchain blockchain) {
    super(new APITag[] {APITag.MINING, APITag.INFO});
    this.blockchain = blockchain;
  }
	
  @Override
  JsonElement processRequest(HttpServletRequest req) {
    JsonObject response = new JsonObject();
		
    response.addProperty("height", Long.toString((long)Burst.getBlockchain().getHeight() + 1));
		
    Block lastBlock = blockchain.getLastBlock();
    byte[] lastGenSig = lastBlock.getGenerationSignature();
    long lastGenerator = lastBlock.getGeneratorId();
		
    ByteBuffer buf = ByteBuffer.allocate(32 + 8);
    buf.put(lastGenSig);
    buf.putLong(lastGenerator);
		
    Shabal256 md = new Shabal256();
    md.update(buf.array());
    byte[] newGenSig = md.digest();
		
    response.addProperty("generationSignature", Convert.toHexString(newGenSig));
    response.addProperty("baseTarget", Long.toString(lastBlock.getBaseTarget()));
		
    return response;
  }
}
