package brs.http;

import brs.Burst;
import brs.Constants;
import brs.Genesis;
import brs.TransactionType;
import brs.fluxcapacitor.FluxValues;
import brs.util.Convert;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

final class GetConstants extends APIServlet.JsonRequestHandler {

    static final GetConstants instance = new GetConstants();

    private final JsonElement constants;

    private GetConstants() {
        super(new APITag[] {APITag.INFO});
        JsonObject response = new JsonObject();
        response.addProperty("genesisBlockId", Convert.toUnsignedLong(Genesis.GENESIS_BLOCK_ID));
        response.addProperty("genesisAccountId", Convert.toUnsignedLong(Genesis.CREATOR_ID));
        response.addProperty("maxBlockPayloadLength", (Burst.getFluxCapacitor().getValue(FluxValues.MAX_PAYLOAD_LENGTH)));
        response.addProperty("maxArbitraryMessageLength", Constants.MAX_ARBITRARY_MESSAGE_LENGTH);

        JsonArray transactionTypes = new JsonArray();
        TransactionType.getTransactionTypes()
                .forEach((key, value) -> {
                    JsonObject transactionType = new JsonObject();
                    transactionType.addProperty("value", key);
                    transactionType.addProperty("description", TransactionType.getTypeDescription(key));
                    JsonArray transactionSubtypes = new JsonArray();
                    transactionSubtypes.addAll(value.entrySet().stream()
                            .map(entry -> {
                                JsonObject transactionSubtype = new JsonObject();
                                transactionSubtype.addProperty("value", entry.getKey());
                                transactionSubtype.addProperty("description", entry.getValue().getDescription());
                                return transactionSubtype;
                            })
                            .collect(JSON.jsonArrayCollector()));
                    transactionType.add("subtypes", transactionSubtypes);
                    transactionTypes.add(transactionType);
                });
        response.add("transactionTypes", transactionTypes);

        JsonArray peerStates = new JsonArray();
        JsonObject peerState = new JsonObject();
        peerState.addProperty("value", 0);
        peerState.addProperty("description", "Non-connected");
        peerStates.add(peerState);
        peerState = new JsonObject();
        peerState.addProperty("value", 1);
        peerState.addProperty("description", "Connected");
        peerStates.add(peerState);
        peerState = new JsonObject();
        peerState.addProperty("value", 2);
        peerState.addProperty("description", "Disconnected");
        peerStates.add(peerState);
        response.add("peerStates", peerStates);

        // TODO remove this always empty field
        response.add("requestTypes", new JsonObject());

        constants = response;
    }

    @Override
    JsonElement processRequest(HttpServletRequest req) {
        return constants;
    }

}
