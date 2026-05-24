package brs.web.api.http.handler;

import brs.services.NetworkAnalysisService;
import brs.web.api.http.ApiServlet;
import brs.web.api.http.common.LegacyDocTag;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

public final class GetForkHistory extends ApiServlet.JsonRequestHandler {

    private final NetworkAnalysisService networkAnalysisService;

    public GetForkHistory(NetworkAnalysisService networkAnalysisService) {
        super(new LegacyDocTag[]{LegacyDocTag.INFO}, "limit");
        this.networkAnalysisService = networkAnalysisService;
    }

    @Override
    protected JsonElement processRequest(HttpServletRequest req) {
        int limit = 50;
        String limitParam = req.getParameter("limit");
        if (limitParam != null) {
            try { limit = Integer.parseInt(limitParam); } catch (NumberFormatException ignored) {}
        }

        List<JsonObject> history = networkAnalysisService.getForkHistory(limit);
        JsonArray array = new JsonArray();
        history.forEach(array::add);

        JsonObject response = new JsonObject();
        response.add("forks", array);
        return response;
    }
}
