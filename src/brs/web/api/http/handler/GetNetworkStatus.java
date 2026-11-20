package brs.web.api.http.handler;

import brs.services.NetworkAnalysisService;
import brs.web.api.http.ApiServlet;
import brs.web.api.http.common.LegacyDocTag;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jakarta.servlet.http.HttpServletRequest;

import java.util.function.LongSupplier;

public final class GetNetworkStatus extends ApiServlet.JsonRequestHandler {

    private final NetworkAnalysisService networkAnalysisService;
    private final LongSupplier clock;

    public GetNetworkStatus(NetworkAnalysisService networkAnalysisService) {
        this(networkAnalysisService, System::currentTimeMillis);
    }

    GetNetworkStatus(NetworkAnalysisService networkAnalysisService, LongSupplier clock) {
        super(new LegacyDocTag[]{LegacyDocTag.INFO});
        this.networkAnalysisService = networkAnalysisService;
        this.clock = clock;
    }

    /**
     * Returns the cached snapshot plus {@code serverTime} (epoch ms, the clock {@code cachedAt} and
     * {@code checkedAt} use), so clients can compute ages without depending on their own clock.
     */
    @Override
    protected JsonElement processRequest(HttpServletRequest req) {
        JsonObject snapshot = networkAnalysisService.getNetworkStatus();
        // copy: the snapshot is shared between requests
        JsonObject response = snapshot != null ? snapshot.deepCopy() : new JsonObject();
        response.addProperty("serverTime", clock.getAsLong());
        return response;
    }
}
