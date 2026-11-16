package brs.web.api.http.handler;

import brs.Block;
import brs.Blockchain;
import brs.BlockchainProcessor;
import brs.Signum;
import brs.peer.Peer;
import brs.props.Props;
import brs.services.ClockDriftMonitor;
import brs.services.TimeService;
import brs.web.api.http.ApiServlet;
import brs.web.api.http.common.LegacyDocTag;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jakarta.servlet.http.HttpServletRequest;

import static brs.web.api.http.common.ResultFields.*;

public final class GetBlockchainStatus extends ApiServlet.JsonRequestHandler {

  private final BlockchainProcessor blockchainProcessor;
  private final Blockchain blockchain;
  private final TimeService timeService;
  private final ClockDriftMonitor clockDriftMonitor;

  public GetBlockchainStatus(BlockchainProcessor blockchainProcessor, Blockchain blockchain, TimeService timeService,
                             ClockDriftMonitor clockDriftMonitor) {
    super(new LegacyDocTag[]{LegacyDocTag.BLOCKS, LegacyDocTag.INFO});
    this.blockchainProcessor = blockchainProcessor;
    this.blockchain = blockchain;
    this.timeService = timeService;
    this.clockDriftMonitor = clockDriftMonitor;
  }

  @Override
  protected
  JsonElement processRequest(HttpServletRequest req) {
    JsonObject response = new JsonObject();
    response.addProperty("application", Signum.getPropertyService().getString(Props.APPLICATION));
    response.addProperty("version", Signum.getPropertyService().getString(Props.VERSION));
    response.addProperty(TIME_RESPONSE, timeService.getEpochTime());
    ClockDriftFields.addTo(response, clockDriftMonitor);
    Block lastBlock = blockchain.getLastBlock();
    response.addProperty("lastBlock", lastBlock.getStringId());
    response.addProperty("lastBlockTimestamp", lastBlock.getTimestamp());
    response.addProperty(CUMULATIVE_DIFFICULTY_RESPONSE, lastBlock.getCumulativeDifficulty().toString());
    response.addProperty(AVERAGE_COMMITMENT_NQT_RESPONSE, lastBlock.getAverageCommitment());
    response.addProperty("numberOfBlocks", lastBlock.getHeight() + 1);
    Peer lastBlockchainFeeder = blockchainProcessor.getLastBlockchainFeeder();
    response.addProperty("lastBlockchainFeeder", lastBlockchainFeeder == null ? null : lastBlockchainFeeder.getAnnouncedAddress());
    response.addProperty("lastBlockchainFeederHeight", blockchainProcessor.getLastBlockchainFeederHeight());
    response.addProperty("isScanning", blockchainProcessor.isScanning());
    return response;
  }

}
