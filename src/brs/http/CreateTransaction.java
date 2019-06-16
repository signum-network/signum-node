package brs.http;

import brs.Account;
import brs.Attachment;
import brs.Burst;
import brs.BurstException;
import brs.fluxcapacitor.FluxValues;
import com.google.gson.JsonElement;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

import static brs.Constants.FEE_QUANT;
import static brs.Constants.ONE_BURST;
import static brs.http.common.Parameters.*;

abstract class CreateTransaction extends APIServlet.JsonRequestHandler {

  private static final String[] commonParameters = new String[] {
    SECRET_PHRASE_PARAMETER, PUBLIC_KEY_PARAMETER, FEE_NQT_PARAMETER,
    DEADLINE_PARAMETER, REFERENCED_TRANSACTION_FULL_HASH_PARAMETER, BROADCAST_PARAMETER,
    MESSAGE_PARAMETER, MESSAGE_IS_TEXT_PARAMETER,
    MESSAGE_TO_ENCRYPT_PARAMETER, MESSAGE_TO_ENCRYPT_IS_TEXT_PARAMETER, ENCRYPTED_MESSAGE_DATA_PARAMETER, ENCRYPTED_MESSAGE_NONCE_PARAMETER,
    MESSAGE_TO_ENCRYPT_TO_SELF_PARAMETER, MESSAGE_TO_ENCRYPT_TO_SELF_IS_TEXT_PARAMETER, ENCRYPT_TO_SELF_MESSAGE_DATA, ENCRYPT_TO_SELF_MESSAGE_NONCE,
    RECIPIENT_PUBLIC_KEY_PARAMETER};

  private final APITransactionManager apiTransactionManager;

  private static String[] addCommonParameters(String[] parameters) {
    String[] result = Arrays.copyOf(parameters, parameters.length + commonParameters.length);
    System.arraycopy(commonParameters, 0, result, parameters.length, commonParameters.length);
    return result;
  }

  CreateTransaction(APITag[] apiTags, APITransactionManager apiTransactionManager, boolean replaceParameters, String... parameters) {
    super(apiTags, replaceParameters ? parameters : addCommonParameters(parameters));
    this.apiTransactionManager = apiTransactionManager;
  }

  CreateTransaction(APITag[] apiTags, APITransactionManager apiTransactionManager, String... parameters) {
    super(apiTags, addCommonParameters(parameters));
    this.apiTransactionManager = apiTransactionManager;
  }

  final JsonElement createTransaction(HttpServletRequest req, Account senderAccount, Attachment attachment)
    throws BurstException {
    return createTransaction(req, senderAccount, null, 0, attachment);
  }

  final JsonElement createTransaction(HttpServletRequest req, Account senderAccount, Long recipientId, long amountNQT)
    throws BurstException {
    return createTransaction(req, senderAccount, recipientId, amountNQT, Attachment.ORDINARY_PAYMENT);
  }

  final JsonElement createTransaction(HttpServletRequest req, Account senderAccount, Long recipientId, long amountNQT, Attachment attachment) throws BurstException {
    return apiTransactionManager.createTransaction(req, senderAccount, recipientId, amountNQT, attachment, minimumFeeNQT());
  }

  @Override
  final boolean requirePost() {
    return true;
  }

  private long minimumFeeNQT() {
    return Burst.getFluxCapacitor().getValue(FluxValues.PRE_DYMAXION) ? FEE_QUANT : ONE_BURST;
  }

}
