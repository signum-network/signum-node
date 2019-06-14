package brs.db.store;

import brs.db.TransactionDb;
import brs.db.cache.DBCacheManagerImpl;
import brs.db.sql.*;
import brs.props.PropertyService;
import brs.services.TimeService;
import brs.unconfirmedtransactions.UnconfirmedTransactionStore;
import brs.unconfirmedtransactions.UnconfirmedTransactionStoreImpl;

public class Stores {
  private final AccountStore accountStore;
  private final AliasStore aliasStore;
  private final AssetTransferStore assetTransferStore;
  private final AssetStore assetStore;
  private final ATStore atStore;
  private final BlockchainStore blockchainStore;
  private final DigitalGoodsStoreStore digitalGoodsStoreStore;
  private final EscrowStore escrowStore;
  private final OrderStore orderStore;
  private final TradeStore tradeStore;
  private final SubscriptionStore subscriptionStore;
  private final UnconfirmedTransactionStore unconfirmedTransactionStore;
  private final IndirectIncomingStore indirectIncomingStore;

  public Stores(DerivedTableManager derivedTableManager, DBCacheManagerImpl dbCacheManager, TimeService timeService, PropertyService propertyService, TransactionDb transactionDb) {
    this.accountStore                = new SqlAccountStore(derivedTableManager, dbCacheManager);
    this.aliasStore                  = new SqlAliasStore(derivedTableManager);
    this.assetStore                  = new SqlAssetStore(derivedTableManager);
    this.assetTransferStore          = new SqlAssetTransferStore(derivedTableManager);
    this.atStore                     = new SqlATStore(derivedTableManager);
    this.digitalGoodsStoreStore      = new SqlDigitalGoodsStoreStore(derivedTableManager);
    this.escrowStore                 = new SqlEscrowStore(derivedTableManager);
    this.orderStore                  = new SqlOrderStore(derivedTableManager);
    this.tradeStore                  = new SqlTradeStore(derivedTableManager);
    this.subscriptionStore           = new SqlSubscriptionStore(derivedTableManager);
    this.unconfirmedTransactionStore = new UnconfirmedTransactionStoreImpl(timeService, propertyService, accountStore, transactionDb);
    this.indirectIncomingStore       = new SqlIndirectIncomingStore(derivedTableManager);
    this.blockchainStore             = new SqlBlockchainStore(indirectIncomingStore);
  }

  public AccountStore getAccountStore() {
    return accountStore;
  }

  public AliasStore getAliasStore() {
    return aliasStore;
  }

  public AssetStore getAssetStore() {
    return assetStore;
  }

  public AssetTransferStore getAssetTransferStore() {
    return assetTransferStore;
  }

  public ATStore getAtStore() {
    return atStore;
  }

  public BlockchainStore getBlockchainStore() {
    return blockchainStore;
  }

  public DigitalGoodsStoreStore getDigitalGoodsStoreStore() {
    return digitalGoodsStoreStore;
  }

  public void beginTransaction() {
    Db.beginTransaction();
  }

  public void commitTransaction() {
    Db.commitTransaction();
  }

  public void rollbackTransaction() {
    Db.rollbackTransaction();
  }

  public void endTransaction() {
    Db.endTransaction();
  }

  public EscrowStore getEscrowStore() {
    return escrowStore;
  }

  public OrderStore getOrderStore() {
    return orderStore;
  }

  public TradeStore getTradeStore() {
    return tradeStore;
  }

  public UnconfirmedTransactionStore getUnconfirmedTransactionStore() {
    return unconfirmedTransactionStore;
  }

  public SubscriptionStore getSubscriptionStore() {
    return subscriptionStore;
  }

  public IndirectIncomingStore getIndirectIncomingStore() {
    return indirectIncomingStore;
  }
}
