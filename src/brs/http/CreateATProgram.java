package brs.http;

import brs.*;
import brs.at.AT_Constants;
import brs.services.ParameterService;
import brs.util.Convert;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static brs.http.JSONResponses.*;
import static brs.http.common.Parameters.*;
import static brs.http.common.ResultFields.ERROR_CODE_RESPONSE;
import static brs.http.common.ResultFields.ERROR_DESCRIPTION_RESPONSE;

final class CreateATProgram extends CreateTransaction {

  private final ParameterService parameterService;
  private final Blockchain blockchain;

  CreateATProgram(ParameterService parameterService, Blockchain blockchain, APITransactionManager apiTransactionManager) {
    super(new APITag[]{APITag.AT, APITag.CREATE_TRANSACTION}, apiTransactionManager,
        NAME_PARAMETER, DESCRIPTION_PARAMETER, CREATION_BYTES_PARAMETER, CODE_PARAMETER, DATA_PARAMETER, DPAGES_PARAMETER, CSPAGES_PARAMETER, USPAGES_PARAMETER, MIN_ACTIVATION_AMOUNT_NQT_PARAMETER);
    this.parameterService = parameterService;
    this.blockchain = blockchain;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    //String atVersion = req.getParameter("atVersion");		
    String name = req.getParameter(NAME_PARAMETER);
    String description = req.getParameter(DESCRIPTION_PARAMETER);

    if (name == null) {
      return MISSING_NAME;
    }

    name = name.trim();
    if (name.length() > Constants.MAX_AUTOMATED_TRANSACTION_NAME_LENGTH) {
      return INCORRECT_AUTOMATED_TRANSACTION_NAME_LENGTH;
    }
    String normalizedName = name.toLowerCase();
    for (int i = 0; i < normalizedName.length(); i++) {
      if (Constants.ALPHABET.indexOf(normalizedName.charAt(i)) < 0) {
        return INCORRECT_AUTOMATED_TRANSACTION_NAME;
      }
    }

    if (description != null && description.length() > Constants.MAX_AUTOMATED_TRANSACTION_DESCRIPTION_LENGTH) {
      return INCORRECT_AUTOMATED_TRANSACTION_DESCRIPTION;
    }

    byte[] creationBytes = null;

    if (req.getParameter(CODE_PARAMETER) != null) {
      try {
        String code = req.getParameter(CODE_PARAMETER);
        if ((code.length() & 1) != 0) {
          throw new IllegalArgumentException();
        }

        String data = req.getParameter(DATA_PARAMETER);
        if (data == null) {
          data = "";
        }
        if ((data.length() & 1) != 0) {
          throw new IllegalArgumentException();
        }

        int cpages = (code.length() / 2 / 256) + (((code.length() / 2) % 256) != 0 ? 1 : 0);
        int dpages = Integer.parseInt(req.getParameter(DPAGES_PARAMETER));
        int cspages = Integer.parseInt(req.getParameter(CSPAGES_PARAMETER));
        int uspages = Integer.parseInt(req.getParameter(USPAGES_PARAMETER));

        if (dpages < 0 || cspages < 0 || uspages < 0) {
          throw new IllegalArgumentException();
        }

        long minActivationAmount = Convert.parseUnsignedLong(req.getParameter(MIN_ACTIVATION_AMOUNT_NQT_PARAMETER));

        int creationLength = 4; // version + reserved
        creationLength += 8; // pages
        creationLength += 8; // minActivationAmount
        creationLength += cpages * 256 <= 256 ? 1 : (cpages * 256 <= 32767 ? 2 : 4); // code size
        creationLength += code.length() / 2;
        creationLength += dpages * 256 <= 256 ? 1 : (dpages * 256 <= 32767 ? 2 : 4); // data size
        creationLength += data.length() / 2;

        ByteBuffer creation = ByteBuffer.allocate(creationLength);
        creation.order(ByteOrder.LITTLE_ENDIAN);
        creation.putShort(AT_Constants.getInstance().AT_VERSION(blockchain.getHeight()));
        creation.putShort((short) 0);
        creation.putShort((short) cpages);
        creation.putShort((short) dpages);
        creation.putShort((short) cspages);
        creation.putShort((short) uspages);
        creation.putLong(minActivationAmount);
        if (cpages * 256 <= 256) {
          creation.put((byte) (code.length() / 2));
        } else if (cpages * 256 <= 32767) {
          creation.putShort((short) (code.length() / 2));
        } else {
          creation.putInt(code.length() / 2);
        }
        byte[] codeBytes = Convert.parseHexString(code);
        if (codeBytes != null) {
          creation.put(codeBytes);
        }
        if (dpages * 256 <= 256) {
          creation.put((byte) (data.length() / 2));
        } else if (dpages * 256 <= 32767) {
          creation.putShort((short) (data.length() / 2));
        } else {
          creation.putInt(data.length() / 2);
        }
        byte[] dataBytes = Convert.parseHexString(data);
        if (dataBytes != null) {
          creation.put(dataBytes);
        }

        creationBytes = creation.array();
      } catch (Exception e) {
        e.printStackTrace(System.out);
        JsonObject response = new JsonObject();
        response.addProperty(ERROR_CODE_RESPONSE, 5);
        response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Invalid or not specified parameters");
        return response;
      }
    }

    if (creationBytes == null) {
      creationBytes = ParameterParser.getCreationBytes(req);
    }

    Account account = parameterService.getSenderAccount(req);
    Attachment attachment = new Attachment.AutomatedTransactionsCreation(name, description, creationBytes, blockchain.getHeight());

    System.out.println("AT " + name + " added succesfully ..");
    System.out.println();
    System.out.println();
    return createTransaction(req, account, attachment);
  }

}
