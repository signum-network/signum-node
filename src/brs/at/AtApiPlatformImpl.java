package brs.at;

import brs.Account;
import brs.Appendix;
import brs.Asset;
import brs.Attachment;
import brs.Attachment.ColoredCoinsAssetTransfer;
import brs.Signum;
import brs.Constants;
import brs.Transaction;
import brs.TransactionType;
import brs.Account.AccountAsset;
import brs.crypto.Crypto;
import brs.fluxcapacitor.FluxValues;
import brs.props.Props;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Arrays;

public class AtApiPlatformImpl extends AtApiImpl {

  private static final Logger logger = LoggerFactory.getLogger(AtApiPlatformImpl.class);

  private static final AtApiPlatformImpl instance = new AtApiPlatformImpl();


  private AtApiPlatformImpl() {
  }

  public static AtApiPlatformImpl getInstance() {
    return instance;
  }


  private static Long findTransaction(int startHeight, int endHeight, Long atID, int numOfTx, long minAmount) {
    ATProcessorCache cache = ATProcessorCache.getInstance();
    if (cache.isEnabled()) {
      try {
        return ATProcessorCache.getInstance().findTransactionId(startHeight, endHeight, atID, numOfTx, minAmount);
      } catch (ATProcessorCache.CacheMissException e) {
        // no op
      }
    }
    return Signum.getStores().getAtStore().findTransaction(startHeight, endHeight, atID, numOfTx, minAmount);
  }

  private static int findTransactionHeight(Long transactionId, int height, Long atID, long minAmount) {
    ATProcessorCache cache = ATProcessorCache.getInstance();
    if (cache.isEnabled()) {
      try {
        return ATProcessorCache.getInstance().findTransactionHeight(transactionId, height, atID, minAmount);
      } catch (ATProcessorCache.CacheMissException e) {
        // no op
      }
    }
    return Signum.getStores().getAtStore().findTransactionHeight(transactionId, height, atID, minAmount);

  }

  @Override
  public long getBlockTimestamp(AtMachineState state) {
    int height = state.getHeight();
    return AtApiHelper.getLongTimestamp(height, 0);
  }

  @Override
  public long getCreationTimestamp(AtMachineState state) {
    return AtApiHelper.getLongTimestamp(state.getCreationBlockHeight(), 0);
  }

  @Override
  public long getLastBlockTimestamp(AtMachineState state) {
    int height = state.getHeight() - 1;
    return AtApiHelper.getLongTimestamp(height, 0);
  }

  @Override
  public void putLastBlockHashInA(AtMachineState state) {
    ByteBuffer b = ByteBuffer.allocate(state.getA1().length * 4);
    b.order(ByteOrder.LITTLE_ENDIAN);

    b.put(Signum.getBlockchain().getBlockAtHeight(state.getHeight() - 1).getBlockHash());

    b.clear();

    byte[] temp = new byte[8];

    b.get(temp, 0, 8);
    state.setA1(temp);

    b.get(temp, 0, 8);
    state.setA2(temp);

    b.get(temp, 0, 8);
    state.setA3(temp);

    b.get(temp, 0, 8);
    state.setA4(temp);
  }

  @Override
  public void aToTxAfterTimestamp(long val, AtMachineState state) {

    int height = AtApiHelper.longToHeight(val);
    int numOfTx = AtApiHelper.longToNumOfTx(val);

    byte[] b = state.getId();
    logger.debug("aToTxAfterTimestamp, startHeight: {}, endHeight: {}, numOfTx: {}, minAct: {}", height, state.getHeight(), numOfTx, state.minActivationAmount());
    long tx = findTransaction(height, state.getHeight(), AtApiHelper.getLong(b), numOfTx, state.minActivationAmount());
    logger.debug("tx with id {} found", tx);
    clearA(state);
    state.setA1(AtApiHelper.getByteArray(tx));

  }

  @Override
  public long getTypeForTxInA(AtMachineState state) {
    long txid = AtApiHelper.getLong(state.getA1());

    Transaction tx = Signum.getBlockchain().getTransaction(txid);

    if (tx == null || (tx.getHeight() >= state.getHeight())) {
      return -1;
    }

    if (state.getVersion() >= 3) {
      return tx.getType().getType();
    }

    if (tx.getMessage() != null) {
      return 1;
    }

    return 0;
  }

  @Override
  public long getAmountForTxInA(AtMachineState state) {
    long txId = AtApiHelper.getLong(state.getA1());

    Transaction tx = Signum.getBlockchain().getTransaction(txId);

    if (tx == null || (tx.getHeight() >= state.getHeight())) {
      return -1;
    }

    if (state.getVersion() > 2) {
      long assetId = AtApiHelper.getLong(state.getA2());
      if (assetId != 0) {
        if (tx.getAttachment() instanceof Attachment.ColoredCoinsAssetTransfer) {
          Attachment.ColoredCoinsAssetTransfer assetTransfer = (ColoredCoinsAssetTransfer) tx.getAttachment();
          if (assetTransfer.getAssetId() == assetId) {
            return assetTransfer.getQuantityQnt();
          }
        } else if (tx.getAttachment() instanceof Attachment.ColoredCoinsAssetMultiTransfer) {
          Attachment.ColoredCoinsAssetMultiTransfer assetTransfer = (Attachment.ColoredCoinsAssetMultiTransfer) tx.getAttachment();
          for (int i = 0; i < assetTransfer.getAssetIds().size(); i++) {
            if (assetTransfer.getAssetIds().get(i) == assetId) {
              return assetTransfer.getQuantitiesQnt().get(i);
            }
          }
        }
        return 0L;
      }
    }
    if ((tx.getMessage() == null || Signum.getFluxCapacitor().getValue(FluxValues.AT_FIX_BLOCK_2, state.getHeight())) && state.minActivationAmount() <= tx.getAmountNqt()) {
      return tx.getAmountNqt() - state.minActivationAmount();
    }

    return 0;
  }

  @Override
  public long getMapValueKeysInA(AtMachineState state) {
    if (state.getVersion() < 3) {
      return 0;
    }
    long key1 = AtApiHelper.getLong(state.getA1());
    long key2 = AtApiHelper.getLong(state.getA2());

    long atId = AtApiHelper.getLong(state.getA3());
    if (atId == 0L) {
      atId = AtApiHelper.getLong(state.getId());
    }

    return state.getMapValue(atId, key1, key2);
  }

  @Override
  public void setMapValueKeysInA(AtMachineState state) {
    if (state.getVersion() < 3) {
      return;
    }
    long key1 = AtApiHelper.getLong(state.getA1());
    long key2 = AtApiHelper.getLong(state.getA2());
    long value = AtApiHelper.getLong(state.getA4());

    long atId = AtApiHelper.getLong(state.getId());

    state.addMapUpdate(atId, key1, key2, value);
  }

  @Override
  public long getTimestampForTxInA(AtMachineState state) {
    long txId = AtApiHelper.getLong(state.getA1());
    logger.debug("get timestamp for tx with id {} found", txId);
    // TODO: we might avoid this db access, when using cache here
    Transaction tx = Signum.getBlockchain().getTransaction(txId);

    if (tx == null || (tx.getHeight() >= state.getHeight())) {
      return -1;
    }

    byte[] b = state.getId();
    int blockHeight = tx.getHeight();
    int txHeight = findTransactionHeight(txId, blockHeight, AtApiHelper.getLong(b), state.minActivationAmount());

    return AtApiHelper.getLongTimestamp(blockHeight, txHeight);
  }

  @Override
  public long getRandomIdForTxInA(AtMachineState state) {
    long txId = AtApiHelper.getLong(state.getA1());

    Transaction tx = Signum.getBlockchain().getTransaction(txId);

    if (tx == null || (tx.getHeight() >= state.getHeight())) {
      return -1;
    }

    int txBlockHeight = tx.getHeight();
    int blockHeight = state.getHeight();

    if (blockHeight - txBlockHeight < AtConstants.getInstance().blocksForRandom(blockHeight)) { //for tests - for real case 1440
      state.setWaitForNumberOfBlocks((int) AtConstants.getInstance().blocksForRandom(blockHeight) - (blockHeight - txBlockHeight));
      state.getMachineState().pc -= 7;
      state.getMachineState().stopped = true;
      return 0;
    }

    MessageDigest digest = Crypto.sha256();

    byte[] senderPublicKey = tx.getSenderPublicKey();

    ByteBuffer bf = ByteBuffer.allocate((Signum.getFluxCapacitor().getValue(FluxValues.SODIUM)) ?
      32 + 8 + senderPublicKey.length :
      32 + Long.SIZE + senderPublicKey.length);
    bf.order(ByteOrder.LITTLE_ENDIAN);
    bf.put(Signum.getBlockchain().getBlockAtHeight(blockHeight - 1).getGenerationSignature());
    bf.putLong(tx.getId());
    bf.put(senderPublicKey);

    digest.update(bf.array());
    byte[] byteRandom = digest.digest();

    return Math.abs(AtApiHelper.getLong(Arrays.copyOfRange(byteRandom, 0, 8)));
  }

  @Override
  public long checkSignBWithA(AtMachineState state) {
    if (state.getVersion() > 2) {
      long txid = AtApiHelper.getLong(state.getA1());
      Transaction tx = Signum.getBlockchain().getTransaction(txid);
      if (tx == null || tx.getHeight() >= state.getHeight() || tx.getMessage() == null) {
        return 0L;
      }
      int page = Math.max(0, (int) AtApiHelper.getLong(state.getA2()));

      long accountId = AtApiHelper.getLong(state.getA3());
      Account account = Account.getAccount(accountId);
      if (account == null || account.getPublicKey() == null) {
        return 0L;
      }

      ByteBuffer message = ByteBuffer.allocate(32);
      message.order(ByteOrder.LITTLE_ENDIAN);

      message.put(state.getId());
      message.put(state.getB2());
      message.put(state.getB3());
      message.put(state.getB4());
      message.clear();

      ByteBuffer signature = ByteBuffer.allocate(64);
      signature.order(ByteOrder.LITTLE_ENDIAN);
      Appendix.Message txMessage = tx.getMessage();
      byte[] txMessageBytes = txMessage.getMessageBytes();
      if (txMessageBytes != null) {
        int start = page * 8 * 4;
        for (int i = 0; i < 64 && start + i < txMessageBytes.length; i++) {
          signature.put(txMessageBytes[start + i]);
        }
      }
      signature.clear();

      boolean verified = Crypto.verify(signature.array(), message.array(), account.getPublicKey(), true);

      return verified ? 1L : 0L;
    }
    return 0L;
  }

  @Override
  public void messageFromTxInAToB(AtMachineState state) {
    long txid = AtApiHelper.getLong(state.getA1());

    Transaction tx = Signum.getBlockchain().getTransaction(txid);
    if (tx != null && tx.getHeight() >= state.getHeight()) {
      tx = null;
    }
    int length = 8 * 4;
    ByteBuffer b = ByteBuffer.allocate(length);
    b.order(ByteOrder.LITTLE_ENDIAN);
    if (tx != null) {
      Appendix.Message txMessage = tx.getMessage();
      if (txMessage != null) {
        byte[] message = txMessage.getMessageBytes();
        if (state.getVersion() > 2) {
          // we now accept multiple pages
          int page = Math.max(0, (int) AtApiHelper.getLong(state.getA2()));
          int start = page * length;
          for (int i = 0; i < length && start + i < message.length; i++) {
            b.put(message[start + i]);
          }
        } else if (message.length <= length) {
          b.put(message);
        }
      }
    }

    b.clear();

    byte[] temp = new byte[8];

    b.get(temp, 0, 8);
    state.setB1(temp);

    b.get(temp, 0, 8);
    state.setB2(temp);

    b.get(temp, 0, 8);
    state.setB3(temp);

    b.get(temp, 0, 8);
    state.setB4(temp);

  }

  @Override
  public void bToAddressOfTxInA(AtMachineState state) {
    long txId = AtApiHelper.getLong(state.getA1());

    clearB(state);

    Transaction tx = Signum.getBlockchain().getTransaction(txId);
    if (tx != null && tx.getHeight() >= state.getHeight()) {
      tx = null;
    }
    if (tx != null) {
      long address = tx.getSenderId();
      state.setB1(AtApiHelper.getByteArray(address));
    }
  }

  @Override
  public void bToAssetsOfTxInA(AtMachineState state) {
    long txId = AtApiHelper.getLong(state.getA1());

    clearB(state);

    Transaction tx = Signum.getBlockchain().getTransaction(txId);
    if (tx != null && tx.getHeight() >= state.getHeight()) {
      tx = null;
    }
    if (tx == null)
      return;

    if (tx.getAttachment() instanceof Attachment.ColoredCoinsAssetTransfer) {
      Attachment.ColoredCoinsAssetTransfer assetTransfer = (Attachment.ColoredCoinsAssetTransfer) tx.getAttachment();
      state.setB1(AtApiHelper.getByteArray(assetTransfer.getAssetId()));
    } else if (tx.getAttachment() instanceof Attachment.ColoredCoinsAssetMultiTransfer) {
      Attachment.ColoredCoinsAssetMultiTransfer assetTransfer = (Attachment.ColoredCoinsAssetMultiTransfer) tx.getAttachment();
      if (assetTransfer.getAssetIds().size() > 0) {
        state.setB1(AtApiHelper.getByteArray(assetTransfer.getAssetIds().get(0)));
      }
      if (assetTransfer.getAssetIds().size() > 1) {
        state.setB2(AtApiHelper.getByteArray(assetTransfer.getAssetIds().get(1)));
      }
      if (assetTransfer.getAssetIds().size() > 2) {
        state.setB3(AtApiHelper.getByteArray(assetTransfer.getAssetIds().get(2)));
      }
      if (assetTransfer.getAssetIds().size() > 3) {
        state.setB4(AtApiHelper.getByteArray(assetTransfer.getAssetIds().get(3)));
      }
    }
  }

  @Override
  public void bToAddressOfCreator(AtMachineState state) {
    long creator = AtApiHelper.getLong(state.getCreator());
    if (state.getVersion() >= 3) {
      // we are allowed to ask for the creator of another AT (in B2)
      long atId = AtApiHelper.getLong(state.getB2());
      if (atId != 0L) {
        creator = 0L;
        // asking for the creator of the given at_id
        AT at = Signum.getStores().getAtStore().getAT(atId);
        if (at != null) {
          creator = AtApiHelper.getLong(at.getCreator());
        }
      }
    }

    clearB(state);

    state.setB1(AtApiHelper.getByteArray(creator));
  }

  @Override
  public long getCodeHashId(AtMachineState state) {
    if (state.getVersion() < 3) {
      return 0L;
    }
    long atId = AtApiHelper.getLong(state.getB2());
    if (atId == 0L) {
      atId = AtApiHelper.getLong(state.getId());
    }
    AT at = Signum.getStores().getAtStore().getAT(atId);
    if (at != null) {
      return at.getApCodeHashId();
    }
    return 0L;
  }

  @Override
  public long getActivationFee(AtMachineState state) {
    if (state.getVersion() < 3) {
      return 0L;
    }

    // we are allowed to ask for the creator of another AT (in B2)
    long atId = AtApiHelper.getLong(state.getB2());
    if (atId == 0L) {
      atId = AtApiHelper.getLong(state.getId());
    }
    // asking for the creator of the given at_id
    AT at = Signum.getStores().getAtStore().getAT(atId);
    if (at != null) {
      return at.minActivationAmount();
    }
    return 0L;
  }

  @Override
  public void putLastBlockGenerationSignatureInA(AtMachineState state) {
    ByteBuffer b = ByteBuffer.allocate(state.getA1().length * 4);
    b.order(ByteOrder.LITTLE_ENDIAN);

    b.put(Signum.getBlockchain().getBlockAtHeight(state.getHeight() - 1).getGenerationSignature());

    b.clear();

    byte[] temp = new byte[8];

    b.get(temp, 0, 8);
    state.setA1(temp);

    b.get(temp, 0, 8);
    state.setA2(temp);

    b.get(temp, 0, 8);
    state.setA3(temp);

    b.get(temp, 0, 8);
    state.setA4(temp);
  }

  @Override
  public long getCurrentBalance(AtMachineState state) {
    if (!Signum.getFluxCapacitor().getValue(FluxValues.AT_FIX_BLOCK_2, state.getHeight())) {
      return 0;
    }

    if (state.getVersion() >= 3) {
      long assetId = AtApiHelper.getLong(state.getB2());
      return state.getgBalance(assetId);
    }

    return state.getgBalance();
  }

  @Override
  public long getAccountBalance(AtMachineState state) {
    if (!Signum.getFluxCapacitor().getValue(FluxValues.PK_FREEZE2, state.getHeight())) {
      return 0;
    }

    long accountId = AtApiHelper.getLong(state.getB1());
    long assetId = AtApiHelper.getLong(state.getB2());

    if (assetId == 0L) {
      Account.Balance balance = Account.getAccountBalance(accountId);
      return balance == null ? 0L : balance.getBalanceNqt();
    }
    AccountAsset assetBalance = Account.getAccountAssetBalance(accountId, assetId);

    return assetBalance == null ? 0 : assetBalance.getQuantityQnt();
  }

  @Override
  public long getPreviousBalance(AtMachineState state) {
    if (!Signum.getFluxCapacitor().getValue(FluxValues.AT_FIX_BLOCK_2, state.getHeight())) {
      return 0;
    }

    return state.getpBalance();
  }

  @Override
  public void sendToAddressInB(long val, AtMachineState state) {
    if (val < 1) {
      logger.debug("sendToAddressInB aborted: val {} < 1", val);
      return;
    }

    logger.debug(
      "sendToAddressInB start - val: {}, version: {}",
      val,
      state.getVersion()
    );

    if (state.getVersion() > 2) {
      long assetId = AtApiHelper.getLong(state.getB2());
      logger.debug("sendToAddressInB asset path - assetId: {}", assetId);
      if (assetId != 0L) {
        long assetBalance = state.getgBalance(assetId);
        logger.debug("asset balance: {}", assetBalance);
        if (val > assetBalance) {
          logger.debug("adjusting val from {} to asset balance {}", val, assetBalance);
          val = assetBalance;
        }

        // optional coin amount besides the asset
        long amount = AtApiHelper.getLong(state.getB3());
        logger.debug("coin amount in B3 before adjustment: {}", amount);
        if (amount > 0L) {
          long balance = state.getgBalance();
          logger.debug("available coin balance: {}", balance);
          if (amount > balance) {
            logger.debug("adjusting coin amount from {} to balance {}", amount, balance);
            amount = balance;
          }
          state.setgBalance(balance - amount);
        } else {
          amount = 0L;
        }

        AtTransaction tx = new AtTransaction(TransactionType.ColoredCoins.ASSET_TRANSFER,
          state.getId(), state.getB1().clone(), amount, assetId, val, null);
        logger.debug(
          "asset transfer tx created - sender: {}, assetId: {}, amount: {}, quantity: {}",
          AtApiHelper.getLong(state.getId()),
          assetId,
          amount,
          val
        );
        state.addTransaction(tx);

        state.setgBalance(assetId, assetBalance - val);
        logger.debug(
          "sendToAddressInB asset path end - new asset gBalance: {}",
          state.getgBalance(assetId)
        );
        return;
      }
    }

    if (val > state.getgBalance()) {
      logger.debug(
        "adjusting val from {} to current gBalance {}",
        val,
        state.getgBalance()
      );
      val = state.getgBalance();
    }
    AtTransaction tx = new AtTransaction(TransactionType.Payment.ORDINARY, state.getId(), state.getB1().clone(), val, null);
    logger.debug(
      "ordinary payment tx created - sender: {}, amount: {}",
      AtApiHelper.getLong(state.getId()),
      val
    );
    state.addTransaction(tx);

    state.setgBalance(state.getgBalance() - val);
    logger.debug("sendToAddressInB end - new gBalance: {}", state.getgBalance());
  }

  @Override
  public void mintAsset(AtMachineState state) {
    if (state.getVersion() < 3) {
      logger.debug("mintAsset aborted: AT version {} < 3", state.getVersion());
      return;
    }

    long assetId = AtApiHelper.getLong(state.getB2());
    long accountId = AtApiHelper.getLong(state.getId());
    long quantity = AtApiHelper.getLong(state.getB1());

    logger.debug(
      "mintAsset input - assetId: {}, accountId: {}, quantity: {}, current gBalance: {}",
      assetId,
      accountId,
      quantity,
      state.getgBalance(assetId)
    );

    Asset asset = Signum.getStores().getAssetStore().getAsset(assetId);
    if (asset == null || asset.getAccountId() != accountId || quantity <= 0L) {
      // only assets that we have created internally and no burning by mint
      logger.debug(
        "mintAsset aborted: asset null={}, account check={}, quantity={} <= 0",
        asset == null,
        asset != null && asset.getAccountId() != accountId,
        quantity
      );
      return;
    }

    boolean unconfirmed = !Signum.getFluxCapacitor().getValue(FluxValues.DISTRIBUTION_FIX, state.getHeight());
    long circulatingSupply = Signum.getAssetExchange().getAssetCirculatingSupply(asset, false, unconfirmed);
    long newSupply = circulatingSupply + quantity;
    if (newSupply > Constants.MAX_ASSET_QUANTITY_QNT) {
      // do not mint extra to keep the limit
      logger.debug(
        "mintAsset aborted: newSupply {} > MAX_ASSET_QUANTITY_QNT {}",
        newSupply,
        Constants.MAX_ASSET_QUANTITY_QNT
      );
      return;
    }

    logger.debug(
      "mintAsset creating transaction - version: {}, newSupply: {}",
      state.getVersion(),
      newSupply
    );

    AtTransaction tx = new AtTransaction(TransactionType.ColoredCoins.ASSET_MINT,
      state.getId(), null, 0L, assetId, quantity, null);
    logger.debug(
      "mintAsset tx created - sender: {}, assetId: {}, quantity: {}",
      AtApiHelper.getLong(state.getId()),
      assetId,
      quantity
    );
    state.addTransaction(tx);

    state.setgBalance(assetId, state.getgBalance(assetId) + quantity);
  }

  @Override
  public void distToHolders(AtMachineState state) {
    if (state.getVersion() < 3) {
      return;
    }

    long minHolding = AtApiHelper.getLong(state.getB1());
    long assetId = AtApiHelper.getLong(state.getB2());

    long amount = Math.max(0L, AtApiHelper.getLong(state.getA1()));
    long assetToDistribute = AtApiHelper.getLong(state.getA3());
    long quantityToDistribute = 0L;

    Asset asset = Signum.getStores().getAssetStore().getAsset(assetId);
    if (asset == null) {
      // asset not found, do nothing
      return;
    }

    int maxIndirects = Signum.getPropertyService().getInt(Props.MAX_INDIRECTS_PER_BLOCK);
    boolean unconfirmed = !Signum.getFluxCapacitor().getValue(FluxValues.DISTRIBUTION_FIX, state.getHeight());
    int holdersCount = Signum.getAssetExchange().getAssetAccountsCount(asset, minHolding, true, unconfirmed);
    if (holdersCount == 0 || state.getIndirectsCount() + holdersCount > maxIndirects) {
      // no holders to distribute or over the maximum, so do not distribute
      return;
    }
    state.addIndirectsCount(holdersCount);

    if (assetToDistribute != 0) {
      quantityToDistribute = Math.max(0L, AtApiHelper.getLong(state.getA4()));
      if (quantityToDistribute > state.getgBalance(assetToDistribute)) {
        quantityToDistribute = state.getgBalance(assetToDistribute);
      }
      state.setgBalance(assetToDistribute, state.getgBalance(assetToDistribute) - quantityToDistribute);
    }
    if (amount > state.getgBalance()) {
      amount = state.getgBalance();
    }
    state.setgBalance(state.getgBalance() - amount);

    if (amount == 0L && quantityToDistribute == 0L) {
      // nothing to actually distribute
      return;
    }

    AtTransaction tx = new AtTransaction(TransactionType.ColoredCoins.ASSET_DISTRIBUTE_TO_HOLDERS,
      state.getId(), null, amount, assetId, quantityToDistribute, assetToDistribute, minHolding, null);
    state.addTransaction(tx);
  }

  @Override
  public long getAssetHoldersCount(AtMachineState state) {
    if (state.getVersion() < 3) {
      return 0L;
    }

    long minHolding = AtApiHelper.getLong(state.getB1());
    long assetId = AtApiHelper.getLong(state.getB2());

    Asset asset = Signum.getStores().getAssetStore().getAsset(assetId);
    if (asset == null) {
      // asset not found, no holders
      return 0L;
    }

    boolean unconfirmed = !Signum.getFluxCapacitor().getValue(FluxValues.DISTRIBUTION_FIX, state.getHeight());
    return Signum.getAssetExchange().getAssetAccountsCount(asset, minHolding, true, unconfirmed);
  }

  @Override
  public long getAssetCirculating(AtMachineState state) {
    if (state.getVersion() < 3) {
      return 0L;
    }

    long assetId = AtApiHelper.getLong(state.getB2());

    Asset asset = Signum.getStores().getAssetStore().getAsset(assetId);
    if (asset == null) {
      // asset not found, no supply
      return 0L;
    }

    boolean unconfirmed = !Signum.getFluxCapacitor().getValue(FluxValues.DISTRIBUTION_FIX, state.getHeight());
    return Signum.getAssetExchange().getAssetCirculatingSupply(asset, true, unconfirmed);
  }

  @Override
  public long issueAsset(AtMachineState state) {
    if (state.getVersion() < 3) {
      return 0;
    }

    ByteBuffer b = ByteBuffer.allocate(32);
    b.order(ByteOrder.LITTLE_ENDIAN);
    b.put(state.getA1());
    b.put(state.getA2());
    b.put(state.getA3());
    b.put(state.getA4());
    b.clear();

    long decimals = AtApiHelper.getLong(state.getB1());

    AtTransaction tx = new AtTransaction(TransactionType.ColoredCoins.ASSET_ISSUANCE, state.getId(), null, 0L, 0L, decimals, b.array());
    state.addTransaction(tx);

    return tx.getAssetId();
  }

  @Override
  public void sendAllToAddressInB(AtMachineState state) {
    AtTransaction tx = new AtTransaction(TransactionType.AutomatedTransactions.AT_PAYMENT, state.getId(), state.getB1().clone(), state.getgBalance(), null);
    state.addTransaction(tx);
    state.setgBalance(0L);
  }

  @Override
  public void sendOldToAddressInB(AtMachineState state) {
    if (state.getpBalance() > state.getgBalance()) {
      AtTransaction tx = new AtTransaction(TransactionType.AutomatedTransactions.AT_PAYMENT, state.getId(), state.getB1(), state.getgBalance(), null);
      state.addTransaction(tx);

      state.setgBalance(0L);
      state.setpBalance(0L);
    } else {
      AtTransaction tx = new AtTransaction(TransactionType.AutomatedTransactions.AT_PAYMENT, state.getId(), state.getB1(), state.getpBalance(), null);
      state.addTransaction(tx);

      state.setgBalance(state.getgBalance() - state.getpBalance());
      state.setpBalance(0L);
    }
  }

  @Override
  public void sendAToAddressInB(AtMachineState state) {
    ByteBuffer b = ByteBuffer.allocate(32);
    b.order(ByteOrder.LITTLE_ENDIAN);
    b.put(state.getA1());
    b.put(state.getA2());
    b.put(state.getA3());
    b.put(state.getA4());
    b.clear();

    AtTransaction tx = new AtTransaction(TransactionType.AutomatedTransactions.AT_PAYMENT, state.getId(), state.getB1(), 0L, 0L, 0L, b.array());
    state.addTransaction(tx);
  }

  @Override
  public long addMinutesToTimestamp(long val1, long val2, AtMachineState state) {
    int height = AtApiHelper.longToHeight(val1);
    int numOfTx = AtApiHelper.longToNumOfTx(val1);
    int addHeight = height + (int) (val2 / AtConstants.getInstance().averageBlockMinutes(state.getHeight()));

    return AtApiHelper.getLongTimestamp(addHeight, numOfTx);
  }
}
