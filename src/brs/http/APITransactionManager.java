package brs.http;

import brs.Account;
import brs.Attachment;
import brs.BurstException;
import com.google.gson.JsonElement;

import javax.servlet.http.HttpServletRequest;

public interface APITransactionManager {

  JsonElement createTransaction(HttpServletRequest req, Account senderAccount, Long recipientId, long amountNQT, Attachment attachment, long minimumFeeNQT) throws BurstException;

}
