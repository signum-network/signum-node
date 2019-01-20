package brs.http;

import brs.Burst;
import brs.Constants;
import brs.Genesis;
import brs.TransactionType;
import brs.fluxcapacitor.FluxInt;
import brs.util.Convert;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

;
;

final class GetConstants extends APIServlet.APIRequestHandler {

  static final GetConstants instance = new GetConstants();

  private static final JsonElement CONSTANTS;

  static {

    JsonObject response = new JsonObject();
    response.addProperty("genesisBlockId", Convert.toUnsignedLong(Genesis.GENESIS_BLOCK_ID));
    response.addProperty("genesisAccountId", Convert.toUnsignedLong(Genesis.CREATOR_ID));
    response.addProperty("maxBlockPayloadLength", (Burst.getFluxCapacitor().getInt(FluxInt.MAX_PAYLOAD_LENGTH)));
    response.addProperty("maxArbitraryMessageLength", Constants.MAX_ARBITRARY_MESSAGE_LENGTH);

    JsonArray transactionTypes = new JsonArray();
    JsonObject transactionType = new JsonObject();
    transactionType.addProperty("value", TransactionType.Payment.ORDINARY.getType());
    transactionType.addProperty("description", "Payment");
    JsonArray subtypes = new JsonArray();
    JsonObject subtype = new JsonObject();
    subtype.addProperty("value", TransactionType.Payment.ORDINARY.getSubtype());
    subtype.addProperty("description", "Ordinary payment");
    subtypes.add(subtype);
    transactionType.add("subtypes", subtypes);
    transactionTypes.add(transactionType);
    transactionType = new JsonObject();
    transactionType.addProperty("value", TransactionType.Messaging.ARBITRARY_MESSAGE.getType());
    transactionType.addProperty("description", "Messaging");
    subtypes = new JsonArray();
    subtype = new JsonObject();
    subtype.addProperty("value", TransactionType.Messaging.ARBITRARY_MESSAGE.getSubtype());
    subtype.addProperty("description", "Arbitrary message");
    subtypes.add(subtype);
    subtype = new JsonObject();
    subtype.addProperty("value", TransactionType.Messaging.ALIAS_ASSIGNMENT.getSubtype());
    subtype.addProperty("description", "Alias assignment");
    subtypes.add(subtype);
    subtype = new JsonObject();
    subtype.addProperty("value", TransactionType.Messaging.ALIAS_SELL.getSubtype());
    subtype.addProperty("description", "Alias sell");
    subtypes.add(subtype);
    subtype = new JsonObject();
    subtype.addProperty("value", TransactionType.Messaging.ALIAS_BUY.getSubtype());
    subtype.addProperty("description", "Alias buy");
    subtypes.add(subtype);
    subtype = new JsonObject();
    subtype.addProperty("value", TransactionType.Messaging.ACCOUNT_INFO.getSubtype());
    subtype.addProperty("description", "Account info");
    subtypes.add(subtype);
    transactionType.add("subtypes", subtypes);
    transactionTypes.add(transactionType);
    transactionType = new JsonObject();
    transactionType.addProperty("value", TransactionType.ColoredCoins.ASSET_ISSUANCE.getType());
    transactionType.addProperty("description", "Colored coins");
    subtypes = new JsonArray();
    subtype = new JsonObject();
    subtype.addProperty("value", TransactionType.ColoredCoins.ASSET_ISSUANCE.getSubtype());
    subtype.addProperty("description", "Asset issuance");
    subtypes.add(subtype);
    subtype = new JsonObject();
    subtype.addProperty("value", TransactionType.ColoredCoins.ASSET_TRANSFER.getSubtype());
    subtype.addProperty("description", "Asset transfer");
    subtypes.add(subtype);
    subtype = new JsonObject();
    subtype.addProperty("value", TransactionType.ColoredCoins.ASK_ORDER_PLACEMENT.getSubtype());
    subtype.addProperty("description", "Ask order placement");
    subtypes.add(subtype);
    subtype = new JsonObject();
    subtype.addProperty("value", TransactionType.ColoredCoins.BID_ORDER_PLACEMENT.getSubtype());
    subtype.addProperty("description", "Bid order placement");
    subtypes.add(subtype);
    subtype = new JsonObject();
    subtype.addProperty("value", TransactionType.ColoredCoins.ASK_ORDER_CANCELLATION.getSubtype());
    subtype.addProperty("description", "Ask order cancellation");
    subtypes.add(subtype);
    subtype = new JsonObject();
    subtype.addProperty("value", TransactionType.ColoredCoins.BID_ORDER_CANCELLATION.getSubtype());
    subtype.addProperty("description", "Bid order cancellation");
    subtypes.add(subtype);
    transactionType.add("subtypes", subtypes);
    transactionTypes.add(transactionType);
    transactionType = new JsonObject();
    transactionType.addProperty("value", TransactionType.DigitalGoods.LISTING.getType());
    transactionType.addProperty("description", "Digital goods");
    subtypes = new JsonArray();
    subtype = new JsonObject();
    subtype.addProperty("value", TransactionType.DigitalGoods.LISTING.getSubtype());
    subtype.addProperty("description", "Listing");
    subtypes.add(subtype);
    subtype = new JsonObject();
    subtype.addProperty("value", TransactionType.DigitalGoods.DELISTING.getSubtype());
    subtype.addProperty("description", "Delisting");
    subtypes.add(subtype);
    subtype = new JsonObject();
    subtype.addProperty("value", TransactionType.DigitalGoods.PRICE_CHANGE.getSubtype());
    subtype.addProperty("description", "Price change");
    subtypes.add(subtype);
    subtype = new JsonObject();
    subtype.addProperty("value", TransactionType.DigitalGoods.QUANTITY_CHANGE.getSubtype());
    subtype.addProperty("description", "Quantity change");
    subtypes.add(subtype);
    subtype = new JsonObject();
    subtype.addProperty("value", TransactionType.DigitalGoods.PURCHASE.getSubtype());
    subtype.addProperty("description", "Purchase");
    subtypes.add(subtype);
    subtype = new JsonObject();
    subtype.addProperty("value", TransactionType.DigitalGoods.DELIVERY.getSubtype());
    subtype.addProperty("description", "Delivery");
    subtypes.add(subtype);
    subtype = new JsonObject();
    subtype.addProperty("value", TransactionType.DigitalGoods.FEEDBACK.getSubtype());
    subtype.addProperty("description", "Feedback");
    subtypes.add(subtype);
    subtype = new JsonObject();
    subtype.addProperty("value", TransactionType.DigitalGoods.REFUND.getSubtype());
    subtype.addProperty("description", "Refund");
    subtypes.add(subtype);
    transactionType.add("subtypes", subtypes);
    transactionTypes.add(transactionType);
    transactionType = new JsonObject();
    transactionType.addProperty("value", TransactionType.AccountControl.EFFECTIVE_BALANCE_LEASING.getType());
    transactionType.addProperty("description", "Account Control");
    subtypes = new JsonArray();
    subtype = new JsonObject();
    subtype.addProperty("value", TransactionType.AccountControl.EFFECTIVE_BALANCE_LEASING.getSubtype());
    subtype.addProperty("description", "Effective balance leasing");
    subtypes.add(subtype);
    transactionType.add("subtypes", subtypes);
    transactionTypes.add(transactionType);
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

    JsonObject requestTypes = new JsonObject();
    // for (Map.Entry<String, APIServlet.APIRequestHandler> handlerEntry : APIServlet.apiRequestHandlers.entrySet()) {
    //     JsonObject handlerJSON = JSONData.apiRequestHandler(handlerEntry.getValue());
    //     handlerJSON.addProperty("enabled", true);
    //     requestTypes.addProperty(handlerEntry.getKey(), handlerJSON);
    // }
    response.add("requestTypes", requestTypes);
        
    CONSTANTS = response;

  }

  private GetConstants() {
    super(new APITag[] {APITag.INFO});
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {
    return CONSTANTS;
  }

}
