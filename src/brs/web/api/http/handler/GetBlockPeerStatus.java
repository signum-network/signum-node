package brs.web.api.http.handler;

import brs.services.BlockPeerCheck;
import brs.services.NetworkAnalysisService;
import brs.util.Convert;
import brs.web.api.http.ApiServlet;
import brs.web.api.http.common.LegacyDocTag;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;

import java.util.function.LongSupplier;

import static brs.web.api.http.common.JSONResponses.INCORRECT_BLOCK;
import static brs.web.api.http.common.JSONResponses.MISSING_BLOCK;
import static brs.web.api.http.common.JSONResponses.UNKNOWN_BLOCK;
import static brs.web.api.http.common.Parameters.BLOCK_PARAMETER;

/**
 * Asks the connected peers whether they have a block. "busy" and "tooOld" are states the web UI
 * shows, not request errors, so they come with HTTP 200 and no {@code errorCode}.
 */
public final class GetBlockPeerStatus extends ApiServlet.JsonRequestHandler {

    private final NetworkAnalysisService networkAnalysisService;
    private final LongSupplier clock;

    public GetBlockPeerStatus(NetworkAnalysisService networkAnalysisService) {
        this(networkAnalysisService, System::currentTimeMillis);
    }

    GetBlockPeerStatus(NetworkAnalysisService networkAnalysisService, LongSupplier clock) {
        super(new LegacyDocTag[]{LegacyDocTag.INFO}, BLOCK_PARAMETER);
        this.networkAnalysisService = networkAnalysisService;
        this.clock = clock;
    }

    @Override
    protected JsonElement processRequest(HttpServletRequest req) {
        String blockParam = req.getParameter(BLOCK_PARAMETER);
        if (blockParam == null || blockParam.isBlank()) {
            return MISSING_BLOCK;
        }
        long blockId;
        try {
            blockId = Convert.parseUnsignedLong(blockParam);
        } catch (RuntimeException e) {
            return INCORRECT_BLOCK;
        }
        BlockPeerCheck check = networkAnalysisService.checkBlockPeers(blockId);
        return switch (check.outcome()) {
            case UNKNOWN_BLOCK -> UNKNOWN_BLOCK;
            case TOO_OLD -> status("tooOld");
            case BUSY -> status("busy");
            case OK -> counts(check);
        };
    }

    private static JsonObject status(String status) {
        JsonObject json = new JsonObject();
        json.addProperty("status", status);
        return json;
    }

    private JsonObject counts(BlockPeerCheck check) {
        JsonObject json = status("ok");
        json.addProperty("block", Convert.toUnsignedLong(check.blockId()));
        json.addProperty("height", check.height());
        json.addProperty("hasIt", check.hasIt());
        json.addProperty("missing", check.missing());
        json.addProperty("notYet", check.notYet());
        json.addProperty("noAnswer", check.noAnswer());
        json.addProperty("checkedAt", check.checkedAt());
        json.addProperty("serverTime", clock.getAsLong());
        return json;
    }
}
