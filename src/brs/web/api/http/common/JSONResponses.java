package brs.web.api.http.common;

import brs.Constants;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Arrays;

import static brs.web.api.http.common.Parameters.*;
import static brs.web.api.http.common.ResultFields.ERROR_CODE_RESPONSE;
import static brs.web.api.http.common.ResultFields.ERROR_DESCRIPTION_RESPONSE;

public final class JSONResponses {

  public static final JsonElement INCORRECT_ALIAS = incorrect(ALIAS_PARAMETER);
  public static final JsonElement INCORRECT_ALIAS_OWNER = incorrect(ALIAS_PARAMETER,"(invalid alias owner)");
  public static final JsonElement INCORRECT_ALIAS_LENGTH = incorrect(ALIAS_PARAMETER, "(length must be in [1.." + Constants.MAX_ALIAS_LENGTH + "] range)");
  public static final JsonElement INCORRECT_ALIAS_NAME = incorrect(ALIAS_PARAMETER, "(must contain only digits and latin letters)");
  public static final JsonElement INCORRECT_ALIAS_NOTFORSALE = incorrect(ALIAS_PARAMETER, "(alias is not for sale at the moment)");
  public static final JsonElement INCORRECT_URI_LENGTH = incorrect(URI_PARAMETER, "(length must be not longer than " + Constants.MAX_ALIAS_URI_LENGTH + " characters)");
  public static final JsonElement MISSING_SECRET_PHRASE = missing(SECRET_PHRASE_PARAMETER);
  public static final JsonElement WEAK_SECRET_PHRASE = incorrect(SECRET_PHRASE_PARAMETER, "(secret phrase must be at least " + Constants.MIN_SECRET_PHRASE_LENGTH + " characters long)");
  public static final JsonElement INCORRECT_PUBLIC_KEY = incorrect(PUBLIC_KEY_PARAMETER);
  public static final JsonElement MISSING_ALIAS_NAME = missing(ALIAS_NAME_PARAMETER);
  public static final JsonElement MISSING_ALIAS_OR_ALIAS_NAME = missing(ALIAS_PARAMETER, "aliasName");
  public static final JsonElement MISSING_FEE = missing(FEE_NQT_PARAMETER);
  public static final JsonElement MISSING_DEADLINE = missing(DEADLINE_PARAMETER);
  public static final JsonElement INCORRECT_DEADLINE = incorrect(DEADLINE_PARAMETER);
  public static final JsonElement INCORRECT_FEE = incorrect(FEE_PARAMETER);
  public static final JsonElement MISSING_TRANSACTION_BYTES_OR_JSON = missing(TRANSACTION_BYTES_PARAMETER, TRANSACTION_JSON_PARAMETER);
  public static final JsonElement MISSING_ORDER = missing(ORDER_PARAMETER);
  public static final JsonElement INCORRECT_ORDER = incorrect(ORDER_PARAMETER);
  public static final JsonElement UNKNOWN_ORDER = unknown(ORDER_PARAMETER);
  public static final JsonElement MISSING_WEBSITE = missing(WEBSITE_PARAMETER);
  public static final JsonElement INCORRECT_WEBSITE = incorrect(WEBSITE_PARAMETER);
  public static final JsonElement MISSING_TOKEN = missing(TOKEN_PARAMETER);
  public static final JsonElement MISSING_ACCOUNT = missing(ACCOUNT_PARAMETER);
  public static final JsonElement INCORRECT_ACCOUNT = incorrect(ACCOUNT_PARAMETER);
  public static final JsonElement INCORRECT_TIMESTAMP = incorrect(TIMESTAMP_PARAMETER);
  public static final JsonElement UNKNOWN_ACCOUNT = unknown(ACCOUNT_PARAMETER);
  public static final JsonElement UNKNOWN_ALIAS = unknown(ALIAS_PARAMETER);
  public static final JsonElement MISSING_ASSET = missing(ASSET_PARAMETER);
  public static final JsonElement UNKNOWN_ASSET = unknown(ASSET_PARAMETER);
  public static final JsonElement INCORRECT_ASSET = incorrect(ASSET_PARAMETER);
  public static final JsonElement UNKNOWN_BLOCK = unknown(BLOCK_PARAMETER);
  public static final JsonElement INCORRECT_BLOCK = incorrect(BLOCK_PARAMETER);
  public static final JsonElement INCORRECT_NUMBER_OF_CONFIRMATIONS = incorrect(NUMBER_OF_CONFIRMATIONS_PARAMETER);
  public static final JsonElement MISSING_PEER = missing(PEER_PARAMETER);
  public static final JsonElement UNKNOWN_PEER = unknown(PEER_PARAMETER);
  public static final JsonElement MISSING_TRANSACTION = missing(TRANSACTION_PARAMETER);
  public static final JsonElement UNKNOWN_TRANSACTION = unknown(TRANSACTION_PARAMETER);
  public static final JsonElement INCORRECT_TRANSACTION = incorrect(TRANSACTION_PARAMETER);
  public static final JsonElement INCORRECT_ASSET_DESCRIPTION = incorrect(DESCRIPTION_PARAMETER, "(length must not exceed " + Constants.MAX_ASSET_DESCRIPTION_LENGTH + " characters)");
  public static final JsonElement INCORRECT_ASSET_NAME = incorrect(NAME_PARAMETER, "(must contain only digits and latin letters)");
  public static final JsonElement INCORRECT_ASSET_NAME_LENGTH = incorrect(NAME_PARAMETER, "(length must be in [" + Constants.MIN_ASSET_NAME_LENGTH + ".." + Constants.MAX_ASSET_NAME_LENGTH + "] range)");
  public static final JsonElement MISSING_NAME = missing(NAME_PARAMETER);
  public static final JsonElement MISSING_QUANTITY = missing(QUANTITY_QNT_PARAMETER);
  public static final JsonElement INCORRECT_QUANTITY = incorrect(QUANTITY_PARAMETER);
  public static final JsonElement INCORRECT_ASSET_QUANTITY = incorrect(QUANTITY_PARAMETER, "(must be in [0.." + Constants.MAX_ASSET_QUANTITY_QNT + "] range)");
  public static final JsonElement INCORRECT_DECIMALS = incorrect(DECIMALS_PARAMETER);
  public static final JsonElement MISSING_HOST = missing(HOST_PARAMETER);
  public static final JsonElement MISSING_DATE = missing(DATE_PARAMETER);
  public static final JsonElement INCORRECT_HOST = incorrect(HOST_PARAMETER, "(the length exceeds 100 chars limit)");
  public static final JsonElement INCORRECT_DATE = incorrect(DATE_PARAMETER);
  public static final JsonElement MISSING_PRICE = missing(PRICE_NQT_PARAMETER);
  public static final JsonElement INCORRECT_PRICE = incorrect(PRICE_PARAMETER);
  public static final JsonElement INCORRECT_REFERENCED_TRANSACTION = incorrect(REFERENCED_TRANSACTION_FULL_HASH_PARAMETER);
  public static final JsonElement MISSING_RECIPIENT = missing(RECIPIENT_PARAMETER);
  public static final JsonElement INCORRECT_RECIPIENT = incorrect(RECIPIENT_PARAMETER);
  public static final JsonElement INCORRECT_ARBITRARY_MESSAGE = incorrect(MESSAGE_PARAMETER);
  public static final JsonElement MISSING_AMOUNT = missing(AMOUNT_NQT_PARAMETER);
  public static final JsonElement INCORRECT_AMOUNT = incorrect(AMOUNT_PARAMETER);
  public static final JsonElement INCORRECT_ACCOUNT_NAME_LENGTH = incorrect(NAME_PARAMETER, "(length must be less than " + Constants.MAX_ACCOUNT_NAME_LENGTH + " characters)");
  public static final JsonElement INCORRECT_ACCOUNT_DESCRIPTION_LENGTH = incorrect(DESCRIPTION_PARAMETER, "(length must be less than " + Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH + " characters)");
  public static final JsonElement MISSING_PERIOD = missing(PERIOD_PARAMETER);
  public static final JsonElement INCORRECT_PERIOD = incorrect(PERIOD_PARAMETER, "(period must be at least 1440 blocks)");
  public static final JsonElement MISSING_UNSIGNED_BYTES = missing(UNSIGNED_TRANSACTION_BYTES_PARAMETER);
  public static final JsonElement MISSING_SIGNATURE_HASH = missing(SIGNATURE_HASH_PARAMETER);
  public static final JsonElement INCORRECT_DGS_LISTING_NAME = incorrect(NAME_PARAMETER, "(length must be not longer than " + Constants.MAX_DGS_LISTING_NAME_LENGTH + " characters)");
  public static final JsonElement INCORRECT_DGS_LISTING_DESCRIPTION = incorrect(DESCRIPTION_PARAMETER, "(length must be not longer than " + Constants.MAX_DGS_LISTING_DESCRIPTION_LENGTH + " characters)");
  public static final JsonElement INCORRECT_DGS_LISTING_TAGS = incorrect(TAGS_PARAMETER, "(length must be not longer than " + Constants.MAX_DGS_LISTING_TAGS_LENGTH + " characters)");
  public static final JsonElement MISSING_GOODS = missing(GOODS_PARAMETER);
  public static final JsonElement INCORRECT_GOODS = incorrect(GOODS_PARAMETER);
  public static final JsonElement UNKNOWN_GOODS = unknown(GOODS_PARAMETER);
  public static final JsonElement INCORRECT_DELTA_QUANTITY = incorrect(DELTA_QUANTITY_PARAMETER);
  public static final JsonElement MISSING_DELTA_QUANTITY = missing(DELTA_QUANTITY_PARAMETER);
  public static final JsonElement MISSING_DELIVERY_DEADLINE_TIMESTAMP = missing(DELIVERY_DEADLINE_TIMESTAMP_PARAMETER);
  public static final JsonElement INCORRECT_DELIVERY_DEADLINE_TIMESTAMP = incorrect(DELIVERY_DEADLINE_TIMESTAMP_PARAMETER);
  public static final JsonElement INCORRECT_PURCHASE_QUANTITY = incorrect(QUANTITY_PARAMETER, "(quantity exceeds available goods quantity)");
  public static final JsonElement INCORRECT_PURCHASE_PRICE = incorrect(PRICE_NQT_PARAMETER, "(purchase price doesn't match goods price)");
  public static final JsonElement INCORRECT_PURCHASE = incorrect(PURCHASE_PARAMETER);
  public static final JsonElement MISSING_PURCHASE = missing(PURCHASE_PARAMETER);
  public static final JsonElement INCORRECT_DGS_GOODS = incorrect(GOODS_TO_ENCRYPT_PARAMETER);
  public static final JsonElement INCORRECT_DGS_DISCOUNT = incorrect(DISCOUNT_NQT_PARAMETER);
  public static final JsonElement INCORRECT_DGS_REFUND = incorrect(REFUND_NQT_PARAMETER);
  public static final JsonElement MISSING_SELLER = missing(SELLER_PARAMETER);
  public static final JsonElement INCORRECT_ENCRYPTED_MESSAGE = incorrect(ENCRYPTED_MESSAGE_DATA_PARAMETER);
  public static final JsonElement INCORRECT_DGS_ENCRYPTED_GOODS = incorrect(GOODS_DATA_PARAMETER);
  public static final JsonElement MISSING_SECRET_PHRASE_OR_PUBLIC_KEY = missing(SECRET_PHRASE_PARAMETER, PUBLIC_KEY_PARAMETER);
  public static final JsonElement INCORRECT_HEIGHT = incorrect(HEIGHT_PARAMETER);
  public static final JsonElement MISSING_HEIGHT = missing(HEIGHT_PARAMETER);
  public static final JsonElement INCORRECT_PLAIN_MESSAGE = incorrect(MESSAGE_TO_ENCRYPT_PARAMETER);

  public static final JsonElement INCORRECT_AUTOMATED_TRANSACTION_NAME_LENGTH = incorrect(DESCRIPTION_PARAMETER, "(length must not exceed " + Constants.MAX_AUTOMATED_TRANSACTION_NAME_LENGTH+ " characters)");
  public static final JsonElement INCORRECT_AUTOMATED_TRANSACTION_NAME = incorrect(NAME_PARAMETER, "(must contain only digits and latin letters)");
  public static final JsonElement INCORRECT_AUTOMATED_TRANSACTION_DESCRIPTION = incorrect(DESCRIPTION_PARAMETER, "(length must not exceed " + Constants.MAX_AUTOMATED_TRANSACTION_DESCRIPTION_LENGTH + " characters)");
  public static final JsonElement MISSING_AT = missing(AT_PARAMETER);
  public static final JsonElement MISSING_AT_KEY1 = missing(KEY1_PARAMETER);
  public static final JsonElement MISSING_AT_KEY2 = missing(KEY2_PARAMETER);
  public static final JsonElement UNKNOWN_AT = unknown(AT_PARAMETER);
  public static final JsonElement INCORRECT_AT = incorrect(AT_PARAMETER);
  public static final JsonElement INCORRECT_CREATION_BYTES = incorrect("incorrect creation bytes");

  public static final JsonElement MISSING_RECEIVER_ID = missing(RECEIVER_ID_PARAMETER);
  public static final JsonElement PAYLOAD_WITHOUT_ACTION = incorrect(PAYLOAD_PARAMETER, "With 'payload' parameter the 'action' parameter is mandatory");

  public static final JsonElement FEE_OR_FEE_SUGGESTION_REQUIRED = incorrect(FEE_SUGGESTION_TYPE_PARAMETER, "Either feeNQT or feeSuggestionType is a required parameter");
  public static final JsonElement FEE_SUGGESTION_TYPE_INVALID = incorrect(FEE_SUGGESTION_TYPE_PARAMETER, "feeSuggestionType is not valid");
  public static final JsonElement INCORRECT_MESSAGE_LENGTH = incorrect(MESSAGE_PARAMETER, "Message can have a max length of " + Constants.MAX_ARBITRARY_MESSAGE_LENGTH);

  public static final JsonElement NOT_ENOUGH_FUNDS;
  static {
    JsonObject response = new JsonObject();
    response.addProperty(ERROR_CODE_RESPONSE, 6);
    response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Not enough funds");
    NOT_ENOUGH_FUNDS = response;
  }

  public static final JsonElement NOT_ENOUGH_ASSETS;
  static {
    JsonObject response = new JsonObject();
    response.addProperty(ERROR_CODE_RESPONSE, 6);
    response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Not enough assets");
    NOT_ENOUGH_ASSETS = response;
  }

  public static final JsonElement ERROR_NOT_ALLOWED;
  static {
    JsonObject response = new JsonObject();
    response.addProperty(ERROR_CODE_RESPONSE, 7);
    response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Not allowed");
    ERROR_NOT_ALLOWED = response;
  }

  public static final JsonElement ERROR_INCORRECT_REQUEST;
  static {
    JsonObject response  = new JsonObject();
    response.addProperty(ERROR_CODE_RESPONSE, 1);
    response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Incorrect request");
    ERROR_INCORRECT_REQUEST = response;
  }

  public static final JsonElement ERROR_MISSING_REQUEST;
  static {
    JsonObject response  = new JsonObject();
    response.addProperty(ERROR_CODE_RESPONSE, 1);
    response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Request type does not exist");
    ERROR_MISSING_REQUEST = response;
  }

  public static final JsonElement POST_REQUIRED;
  static {
    JsonObject response = new JsonObject();
    response.addProperty(ERROR_CODE_RESPONSE, 1);
    response.addProperty(ERROR_DESCRIPTION_RESPONSE, "This request is only accepted using POST!");
    POST_REQUIRED = response;
  }

  public static final JsonElement FEATURE_NOT_AVAILABLE;
  static {
    JsonObject response = new JsonObject();
    response.addProperty(ERROR_CODE_RESPONSE, 9);
    response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Feature not available");
    FEATURE_NOT_AVAILABLE = response;
  }

  public static final JsonElement DECRYPTION_FAILED;
  static {
    JsonObject response = new JsonObject();
    response.addProperty(ERROR_CODE_RESPONSE, 8);
    response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Decryption failed");
    DECRYPTION_FAILED = response;
  }

  public static final JsonElement ALREADY_DELIVERED;
  static {
    JsonObject response = new JsonObject();
    response.addProperty(ERROR_CODE_RESPONSE, 8);
    response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Purchase already delivered");
    ALREADY_DELIVERED = response;
  }

  public static final JsonElement DUPLICATE_REFUND;
  static {
    JsonObject response = new JsonObject();
    response.addProperty(ERROR_CODE_RESPONSE, 8);
    response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Refund already sent");
    DUPLICATE_REFUND = response;
  }

  public static final JsonElement GOODS_NOT_DELIVERED;
  static {
    JsonObject response = new JsonObject();
    response.addProperty(ERROR_CODE_RESPONSE, 8);
    response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Goods have not been delivered yet");
    GOODS_NOT_DELIVERED = response;
  }

  public static final JsonElement NO_MESSAGE;
  static {
    JsonObject response = new JsonObject();
    response.addProperty(ERROR_CODE_RESPONSE, 8);
    response.addProperty(ERROR_DESCRIPTION_RESPONSE, "No attached message found");
    NO_MESSAGE = response;
  }

  public static final JsonElement HEIGHT_NOT_AVAILABLE;
  static {
    JsonObject response = new JsonObject();
    response.addProperty(ERROR_CODE_RESPONSE, 8);
    response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Requested height not available");
    HEIGHT_NOT_AVAILABLE = response;
  }

  public static JsonElement missing(String... paramNames) {
    JsonObject response = new JsonObject();
    response.addProperty(ERROR_CODE_RESPONSE, 3);
    if (paramNames.length == 1) {
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "\"" + paramNames[0] + "\"" + " not specified");
    } else {
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "At least one of " + Arrays.toString(paramNames) + " must be specified");
    }
    return response;
  }

  public static JsonElement incorrect(String paramName) {
    return incorrect(paramName, null);
  }

  private static JsonElement unknown(String objectName) {
    JsonObject response = new JsonObject();
    response.addProperty(ERROR_CODE_RESPONSE, 5);
    response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Unknown " + objectName);
    return response;
  }

  public static JsonElement incorrect(String paramName, String details) {
    JsonObject response = new JsonObject();
    response.addProperty(ERROR_CODE_RESPONSE, 4);
    response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Incorrect \"" + paramName + "\"" + (details == null ? "" : details));
    return response;
  }

  public static JsonElement incorrectUnknown(String paramName) {
    return incorrect(paramName, "param not known");
  }

  private JSONResponses() {} // never

}
