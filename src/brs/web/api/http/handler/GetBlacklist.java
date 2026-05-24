package brs.web.api.http.handler;

import brs.services.NetworkAnalysisService;
import brs.web.api.http.ApiServlet;
import brs.web.api.http.common.LegacyDocTag;
import com.google.gson.JsonElement;

import jakarta.servlet.http.HttpServletRequest;

public final class GetBlacklist extends ApiServlet.JsonRequestHandler {

    private final NetworkAnalysisService networkAnalysisService;

    public GetBlacklist(NetworkAnalysisService networkAnalysisService) {
        super(new LegacyDocTag[]{LegacyDocTag.INFO});
        this.networkAnalysisService = networkAnalysisService;
    }

    @Override
    protected JsonElement processRequest(HttpServletRequest req) {
        return networkAnalysisService.getBlacklist();
    }
}
