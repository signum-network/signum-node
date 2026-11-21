package brs.web.api.http.handler;

import brs.services.NodeHealthService;
import brs.web.api.http.ApiServlet;
import brs.web.api.http.common.LegacyDocTag;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jakarta.servlet.http.HttpServletRequest;

import java.util.function.LongSupplier;

/** The node health for the web UI's dashboard, plus {@code serverTime} for ages on the node's clock. */
public final class GetNodeHealth extends ApiServlet.JsonRequestHandler {

    private final NodeHealthService nodeHealthService;
    private final LongSupplier clock;

    public GetNodeHealth(NodeHealthService nodeHealthService) {
        this(nodeHealthService, System::currentTimeMillis);
    }

    GetNodeHealth(NodeHealthService nodeHealthService, LongSupplier clock) {
        super(new LegacyDocTag[]{LegacyDocTag.INFO});
        this.nodeHealthService = nodeHealthService;
        this.clock = clock;
    }

    @Override
    protected JsonElement processRequest(HttpServletRequest req) {
        // copy: the service's object is shared between requests
        JsonObject response = nodeHealthService.getNodeHealth().deepCopy();
        response.addProperty("serverTime", clock.getAsLong());
        return response;
    }
}
