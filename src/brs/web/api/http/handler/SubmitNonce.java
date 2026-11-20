package brs.web.api.http.handler;

import brs.Account;
import brs.Blockchain;
import brs.Generator;
import brs.crypto.Crypto;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.AccountService;
import brs.util.Convert;
import brs.web.api.http.ApiServlet;
import brs.web.api.http.common.LegacyDocTag;
import signumj.crypto.SignumCrypto;
import signumj.entity.SignumAddress;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static brs.web.api.http.common.Parameters.*;


public final class SubmitNonce extends ApiServlet.JsonRequestHandler {

  private final Map<Long, String> passphrases;
  private final boolean allowOtherSoloMiners;
  private final AccountService accountService;
  private final Blockchain blockchain;
  private final Generator generator;
  private final int checkPointHeight;


  public SubmitNonce(PropertyService propertyService, AccountService accountService, Blockchain blockchain, Generator generator) {
    super(new LegacyDocTag[] {LegacyDocTag.MINING}, SECRET_PHRASE_PARAMETER, NONCE_PARAMETER, ACCOUNT_ID_PARAMETER, BLOCK_HEIGHT_PARAMETER, DEADLINE_PARAMETER);
    SignumCrypto signumCrypto = SignumCrypto.getInstance();
    this.passphrases = propertyService.getStringList(Props.SOLO_MINING_PASSPHRASES)
            .stream()
            .collect(Collectors.toMap(passphrase -> signumCrypto.getAddressFromPassphrase(passphrase).getSignedLongId(), Function.identity()));
    this.allowOtherSoloMiners = propertyService.getBoolean(Props.ALLOW_OTHER_SOLO_MINERS);
    this.checkPointHeight = propertyService.getInt(Props.BRS_CHECKPOINT_HEIGHT);

    List<String> soloRecipientPassphrases = propertyService.getStringList(Props.REWARD_RECIPIENT_PASSPHRASES);
    for(String soloRecipient : soloRecipientPassphrases){
      String[] idAndPassphrase = soloRecipient.split(":", 2);
      this.passphrases.put(SignumAddress.fromEither(idAndPassphrase[0]).getSignedLongId(), idAndPassphrase[1]);
    }

    this.accountService = accountService;
    this.blockchain = blockchain;
    this.generator = generator;
  }

  @Override
  protected
  JsonElement processRequest(HttpServletRequest req) {
    String secret = bodyFirst(req, SECRET_PHRASE_PARAMETER);
    long nonce = Convert.parseUnsignedLong(bodyFirst(req, NONCE_PARAMETER));

    String accountId = bodyFirst(req, ACCOUNT_ID_PARAMETER);

    String submissionHeight = Convert.emptyToNull(bodyFirst(req, BLOCK_HEIGHT_PARAMETER));

    JsonObject response = new JsonObject();

    if (submissionHeight != null) {
      try {
        int height = Integer.parseInt(submissionHeight);
        if (height < checkPointHeight) {
            response.addProperty("result", "Given block height smaller than the check point height");
            return response;
        }
        if (height != blockchain.getHeight() + 1) {
          response.addProperty("result", "Given block height does not match current blockchain height");
          return response;
        }
      } catch (NumberFormatException e) {
        response.addProperty("result", "Given block height is not a number");
        return response;
      }
    }

    if (secret == null || Objects.equals(secret, "")) {
      long accountIdLong;
      try {
        accountIdLong = SignumAddress.fromEither(accountId).getSignedLongId();
      } catch (Exception e) {
        response.addProperty("result", "Missing Passphrase and Account ID is malformed");
        return response;
      }
      if (passphrases.containsKey(accountIdLong)) {
        secret = passphrases.get(accountIdLong);
      } else {
        response.addProperty("result", "Missing Passphrase and account passphrase not in solo mining config");
        return response;
      }
    }

    if (!allowOtherSoloMiners && !passphrases.containsValue(secret)) {
      response.addProperty("result", "This account is not allowed to mine on this node as the whitelist is enabled and it is not whitelisted.");
      return response;
    }

    byte[] secretPublicKey = Crypto.getPublicKey(secret);
    Account secretAccount = accountService.getAccount(secretPublicKey);
    if(secretAccount != null) {
      try {
        verifySecretAccount(accountService, blockchain, secretAccount, Convert.parseUnsignedLong(accountId));
      } catch (Exception e) {
        response.addProperty("result", e.getMessage());
        return response;
      }
    }

    Generator.GeneratorState generatorState = null;
    if(accountId == null || secretAccount == null) {
      generatorState = generator.addNonce(secret, nonce);
    }
    else {
      Account genAccount = accountService.getAccount(Convert.parseUnsignedLong(accountId));
      if(genAccount == null || genAccount.getPublicKey() == null) {
        response.addProperty("result", "Passthrough mining requires public key in blockchain");
      }
      else {
        byte[] publicKey = genAccount.getPublicKey();
        generatorState = generator.addNonce(secret, nonce, publicKey);
      }
    }

    if(generatorState == null) {
      response.addProperty("result", "failed to create generator");
      return response;
    }

    response.addProperty("result", "success");
    response.addProperty("deadline", generatorState.getDeadlineLegacy());

    return response;
  }

  public static void verifySecretAccount(AccountService accountService, Blockchain blockchain, Account secretAccount, long accountId) throws Exception {
    Account genAccount;
    if (accountId != 0) {
      genAccount = accountService.getAccount(accountId);
    }
    else {
      genAccount = secretAccount;
    }

    if (genAccount != null) {
      Account.RewardRecipientAssignment assignment = accountService.getRewardRecipientAssignment(genAccount);
      long rewardId;
      if (assignment == null) {
        rewardId = genAccount.getId();
      } else if (assignment.getFromHeight() > blockchain.getLastBlock().getHeight() + 1) {
        rewardId = assignment.getPrevRecipientId();
      } else {
        rewardId = assignment.getRecipientId();
      }
      if (rewardId != secretAccount.getId()) {
        throw new Exception("Passphrase does not match reward recipient");
      }
    }
    else {
      throw new Exception("Passphrase is for a different account");
    }
  }
  /**
   * Reads a parameter, preferring the POST body over the query string: miners should send the
   * passphrase in the body, where it stays out of URLs and access logs. The query string still
   * works for older miners. The servlet API lists query string values before body values.
   */
  static String bodyFirst(HttpServletRequest req, String name) {
    String[] values = req.getParameterValues(name);
    return values == null || values.length == 0 ? null : values[values.length - 1];
  }

  boolean requirePost() {
    return true;
  }
}
