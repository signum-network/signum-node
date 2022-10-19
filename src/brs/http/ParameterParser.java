package brs.http;

import brs.Burst;
import brs.Constants;
import brs.crypto.EncryptedData;
import brs.fluxcapacitor.FluxValues;
import brs.http.common.Parameters;
import brs.util.Convert;
import burst.kit.entity.BurstAddress;

import javax.servlet.http.HttpServletRequest;

import org.bouncycastle.util.encoders.Hex;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static brs.http.JSONResponses.*;
import static brs.http.common.Parameters.*;

public final class ParameterParser {

  static long getFeeNQT(HttpServletRequest req) throws ParameterException {
    String feeValueNQT = Convert.emptyToNull(req.getParameter(FEE_NQT_PARAMETER));
    if (feeValueNQT == null) {
      throw new ParameterException(MISSING_FEE);
    }
    long feeNQT;
    try {
      feeNQT = Long.parseLong(feeValueNQT);
    } catch (RuntimeException e) {
      throw new ParameterException(INCORRECT_FEE);
    }
    if (feeNQT < 0 || feeNQT >= Constants.MAX_BALANCE_NQT) {
      throw new ParameterException(INCORRECT_FEE);
    }
    return feeNQT;
  }

  static long getPriceNQT(HttpServletRequest req) throws ParameterException {
    String priceValueNQT = Convert.emptyToNull(req.getParameter(PRICE_NQT_PARAMETER));
    if (priceValueNQT == null) {
      throw new ParameterException(MISSING_PRICE);
    }
    long priceNQT;
    try {
      priceNQT = Long.parseLong(priceValueNQT);
    } catch (RuntimeException e) {
      throw new ParameterException(INCORRECT_PRICE);
    }
    if (priceNQT <= 0 || priceNQT > Constants.MAX_BALANCE_NQT) {
      throw new ParameterException(INCORRECT_PRICE);
    }
    return priceNQT;
  }

  static long getQuantityQNT(HttpServletRequest req) throws ParameterException {
    long quantityQNT = parseQuantityQNT(req);
    if (quantityQNT <= 0 || quantityQNT > Constants.MAX_ASSET_QUANTITY_QNT) {
      throw new ParameterException(INCORRECT_ASSET_QUANTITY);
    }
    return quantityQNT;
  }

  static long getZeroableQuantityQNT(HttpServletRequest req) throws ParameterException {
    long quantityQNT = parseQuantityQNT(req);
    if (quantityQNT < 0 || quantityQNT > Constants.MAX_ASSET_QUANTITY_QNT) {
      throw new ParameterException(INCORRECT_ASSET_QUANTITY);
    }
    return quantityQNT;
  }


  private static long parseQuantityQNT(HttpServletRequest req) throws ParameterException {
    String quantityValueQNT = Convert.emptyToNull(req.getParameter(QUANTITY_QNT_PARAMETER));
    if (quantityValueQNT == null) {
      throw new ParameterException(MISSING_QUANTITY);
    }
    long quantityQNT;
    try {
      quantityQNT = Long.parseLong(quantityValueQNT);
    } catch (RuntimeException e) {
      throw new ParameterException(INCORRECT_QUANTITY);
    }
    return quantityQNT;
  }

  static long getOrderId(HttpServletRequest req) throws ParameterException {
    String orderValue = Convert.emptyToNull(req.getParameter(ORDER_PARAMETER));
    if (orderValue == null) {
      throw new ParameterException(MISSING_ORDER);
    }
    try {
      return Convert.parseUnsignedLong(orderValue);
    } catch (RuntimeException e) {
      throw new ParameterException(INCORRECT_ORDER);
    }
  }

  static int getGoodsQuantity(HttpServletRequest req) throws ParameterException {
    String quantityString = Convert.emptyToNull(req.getParameter(QUANTITY_PARAMETER));
    try {
      int quantity = Integer.parseInt(quantityString);
      if (quantity < 0 || quantity > Constants.MAX_DGS_LISTING_QUANTITY) {
        throw new ParameterException(INCORRECT_QUANTITY);
      }
      return quantity;
    } catch (NumberFormatException e) {
      throw new ParameterException(INCORRECT_QUANTITY);
    }
  }

  static EncryptedData getEncryptedGoods(HttpServletRequest req) throws ParameterException {
    String data = Convert.emptyToNull(req.getParameter(GOODS_DATA_PARAMETER));
    String nonce = Convert.emptyToNull(req.getParameter(GOODS_NONCE_PARAMETER));
    if (data != null && nonce != null) {
      try {
        return new EncryptedData(Convert.parseHexString(data), Convert.parseHexString(nonce));
      } catch (RuntimeException e) {
        throw new ParameterException(INCORRECT_DGS_ENCRYPTED_GOODS);
      }
    }
    return null;
  }

  static String getSecretPhrase(HttpServletRequest req) throws ParameterException {
    String secretPhrase = Convert.emptyToNull(req.getParameter(SECRET_PHRASE_PARAMETER));
    if (secretPhrase == null) {
      throw new ParameterException(MISSING_SECRET_PHRASE);
    }
    return secretPhrase;
  }

  static int getTimestamp(HttpServletRequest req) throws ParameterException {
    String timestampValue = Convert.emptyToNull(req.getParameter(TIMESTAMP_PARAMETER));
    if (timestampValue == null) {
      return 0;
    }
    int timestamp;
    try {
      timestamp = Integer.parseInt(timestampValue);
    } catch (NumberFormatException e) {
      throw new ParameterException(INCORRECT_TIMESTAMP);
    }
    if (timestamp < 0) {
      throw new ParameterException(INCORRECT_TIMESTAMP);
    }
    return timestamp;
  }

  public static long getRecipientId(HttpServletRequest req) throws ParameterException {
    String recipientValue = Convert.emptyToNull(req.getParameter(RECIPIENT_PARAMETER));
    if (recipientValue == null ||  Parameters.isZero(recipientValue)) {
      if(Burst.getFluxCapacitor().getValue(FluxValues.SMART_TOKEN)){
        return 0L;
      }
      throw new ParameterException(MISSING_RECIPIENT);
    }
    long recipientId;
    try {
      recipientId = Convert.parseAccountId(recipientValue);
    } catch (RuntimeException e) {
      throw new ParameterException(INCORRECT_RECIPIENT);
    }
    if (recipientId == 0) {
      throw new ParameterException(INCORRECT_RECIPIENT);
    }
    return recipientId;
  }

  static String getRecipientPublicKey(HttpServletRequest req) throws ParameterException {
    String recipientPublicKeyValue = Convert.emptyToNull(req.getParameter(RECIPIENT_PUBLIC_KEY_PARAMETER));
    if(recipientPublicKeyValue == null) {
      // check for extended addresses
      String recipientValue = Convert.emptyToNull(req.getParameter(RECIPIENT_PARAMETER));
      BurstAddress address = BurstAddress.fromEither(recipientValue);
      if(address!=null && address.getPublicKey() != null) {
        recipientPublicKeyValue = Hex.toHexString(address.getPublicKey());
      }
    }
    return recipientPublicKeyValue;
  }

  static long getSellerId(HttpServletRequest req) throws ParameterException {
    String sellerIdValue = Convert.emptyToNull(req.getParameter(SELLER_PARAMETER));
    try {
      return Convert.parseAccountId(sellerIdValue);
    } catch (RuntimeException e) {
      throw new ParameterException(INCORRECT_RECIPIENT);
    }
  }

  static long getBuyerId(HttpServletRequest req) throws ParameterException {
    String buyerIdValue = Convert.emptyToNull(req.getParameter(BUYER_PARAMETER));
    try {
      return Convert.parseAccountId(buyerIdValue);
    } catch (RuntimeException e) {
      throw new ParameterException(INCORRECT_RECIPIENT);
    }
  }

  static int getFirstIndex(HttpServletRequest req) {
    int firstIndex;
    try {
      firstIndex = Integer.parseInt(req.getParameter(FIRST_INDEX_PARAMETER));
      if (firstIndex < 0) {
        return 0;
      }
    } catch (NumberFormatException e) {
      return 0;
    }
    return firstIndex;
  }

  static int getLastIndex(HttpServletRequest req) {
    int firstIndex = getFirstIndex(req);
    int lastIndex;
    try {
      lastIndex = Integer.parseInt(req.getParameter(LAST_INDEX_PARAMETER));
      if (lastIndex < 0) {
        lastIndex = firstIndex + Constants.MAX_API_RETURNED_ITEMS;
      }
    } catch (NumberFormatException e) {
      lastIndex = firstIndex + Constants.MAX_API_RETURNED_ITEMS;
    }

    if (firstIndex > lastIndex) {
      throw new IllegalArgumentException("lastIndex must be greater than or equal to firstIndex");
    }
    if (lastIndex - firstIndex >= Constants.MAX_API_RETURNED_ITEMS) {
      // Don't reject the request, just limit it.
      lastIndex = firstIndex + Constants.MAX_API_RETURNED_ITEMS - 1;
    }
    return lastIndex;
  }

  private ParameterParser() {
  } // never

  public static byte[] getCreationBytes(HttpServletRequest req) throws ParameterException {
    try {
      String creationBytes = req.getParameter(CREATION_BYTES_PARAMETER);
      if(creationBytes == null) {
        // Check the body for the creationBytes as an optional alternative
        try {
          creationBytes = req.getReader().readLine();
          creationBytes = creationBytes.replace("\"", "");
        }
        catch (Exception ignored){
          return null;
        }
      }
      return Convert.parseHexString(creationBytes);
    } catch (RuntimeException e) {
      throw new ParameterException(INCORRECT_CREATION_BYTES);
    }
  }

  public static String getATLong(HttpServletRequest req) {
    String hex = req.getParameter(HEX_STRING_PARAMETER);
    ByteBuffer bf = ByteBuffer.allocate(8);
    bf.order(ByteOrder.LITTLE_ENDIAN);
    bf.put(Convert.parseHexString(hex));

      return Convert.toUnsignedLong(bf.getLong(0));
  }

  public static long getAmountNQT(HttpServletRequest req) throws ParameterException {
    String amountValueNQT = Convert.emptyToNull(req.getParameter(AMOUNT_NQT_PARAMETER));
    if (amountValueNQT == null) {
      throw new ParameterException(MISSING_AMOUNT);
    }
    long amountNQT;
    try {
      amountNQT = Long.parseLong(amountValueNQT);
    } catch (RuntimeException e) {
      throw new ParameterException(INCORRECT_AMOUNT);
    }
    if (amountNQT <= 0 || amountNQT >= Constants.MAX_BALANCE_NQT) {
      throw new ParameterException(INCORRECT_AMOUNT);
    }
    return amountNQT;
  }

  public static byte getByte(HttpServletRequest req, String param, boolean checkMissing) throws ParameterException {
    String retString = Convert.emptyToNull(req.getParameter(param));
    if (retString == null && checkMissing) {
      throw new ParameterException(JSONResponses.missing(param));
    }
    if (retString == null)
      return (byte)0;
    try {
      return Byte.parseByte(retString);
    } catch (NumberFormatException e) {
      throw new ParameterException(JSONResponses.incorrect(param));
    }
  }

  public static long getLong(HttpServletRequest req, String param, boolean checkMissing) throws ParameterException {
    String retString = Convert.emptyToNull(req.getParameter(param));
    if (retString == null && checkMissing) {
      throw new ParameterException(JSONResponses.missing(param));
    }
    try {
      return Convert.parseUnsignedLong(retString);
    } catch (NumberFormatException e) {
      throw new ParameterException(JSONResponses.incorrect(param));
    }
  }

  public static byte[] getByteArray(HttpServletRequest req, String param, boolean checkMissing) throws ParameterException {
    try {
      String stringValue = req.getParameter(param);
      if(stringValue == null && checkMissing) {
        throw new ParameterException(JSONResponses.missing(param));
      }
      if(stringValue == null) {
        return null;
      }

      return Convert.parseHexString(stringValue);
    } catch (RuntimeException e) {
      throw new ParameterException(JSONResponses.incorrect(param));
    }
  }

  public static String getString(HttpServletRequest req, String param, boolean checkMissing) throws ParameterException {
    String stringValue = req.getParameter(param);
    if(stringValue == null && checkMissing) {
      throw new ParameterException(JSONResponses.missing(param));
    }
    return stringValue;
  }
}
