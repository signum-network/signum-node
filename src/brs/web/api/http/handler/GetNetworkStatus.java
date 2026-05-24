package brs.web.api.http.handler;

import brs.services.NetworkAnalysisService;
import brs.web.api.http.ApiServlet;
import brs.web.api.http.common.LegacyDocTag;
import com.google.gson.JsonElement;

import jakarta.servlet.http.HttpServletRequest;

public final class GetNetworkStatus extends ApiServlet.JsonRequestHandler {

    private final NetworkAnalysisService networkAnalysisService;

    public GetNetworkStatus(NetworkAnalysisService networkAnalysisService) {
        super(new LegacyDocTag[]{LegacyDocTag.INFO});
        this.networkAnalysisService = networkAnalysisService;
    }

    @Override
    protected JsonElement processRequest(HttpServletRequest req) {
        return networkAnalysisService.getNetworkStatus();
    }
}
