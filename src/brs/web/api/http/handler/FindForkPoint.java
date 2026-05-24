package brs.web.api.http.handler;

import brs.services.NetworkAnalysisService;
import brs.web.api.http.ApiServlet;
import brs.web.api.http.common.LegacyDocTag;
import com.google.gson.JsonElement;

import jakarta.servlet.http.HttpServletRequest;

import static brs.web.api.http.common.JSONResponses.MISSING_PEER;
import static brs.web.api.http.common.Parameters.PEER_PARAMETER;

public final class FindForkPoint extends ApiServlet.JsonRequestHandler {

    private final NetworkAnalysisService networkAnalysisService;

    public FindForkPoint(NetworkAnalysisService networkAnalysisService) {
        super(new LegacyDocTag[]{LegacyDocTag.INFO}, PEER_PARAMETER);
        this.networkAnalysisService = networkAnalysisService;
    }

    @Override
    protected JsonElement processRequest(HttpServletRequest req) {
        String peerAddress = req.getParameter(PEER_PARAMETER);
        if (peerAddress == null) {
            return MISSING_PEER;
        }
        return networkAnalysisService.findForkPoint(peerAddress);
    }
}
