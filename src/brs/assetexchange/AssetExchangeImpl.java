package brs.assetexchange;

import brs.Account.AccountAsset;
import brs.Asset;
import brs.AssetTransfer;
import brs.Attachment.ColoredCoinsAskOrderPlacement;
import brs.Attachment.ColoredCoinsAssetIssuance;
import brs.Attachment.ColoredCoinsAssetTransfer;
import brs.Attachment.ColoredCoinsBidOrderPlacement;
import brs.Order.Ask;
import brs.Order.Bid;
import brs.Trade;
import brs.Trade.Event;
import brs.Transaction;
import brs.db.store.*;
import brs.services.AccountService;
import brs.util.Listener;

import java.util.Collection;

public class AssetExchangeImpl implements AssetExchange {

  private final TradeServiceImpl tradeService;
  private final AssetAccountServiceImpl assetAccountService;
  private final AssetTransferServiceImpl assetTransferService;
  private final AssetServiceImpl assetService;
  private final OrderServiceImpl orderService;


  public AssetExchangeImpl(AccountService accountService, TradeStore tradeStore, AccountStore accountStore, AssetTransferStore assetTransferStore, AssetStore assetStore, OrderStore orderStore) {
    this.tradeService = new TradeServiceImpl(tradeStore);
    this.assetAccountService = new AssetAccountServiceImpl(accountStore);
    this.assetTransferService = new AssetTransferServiceImpl(assetTransferStore);
    this.assetService = new AssetServiceImpl(this.assetAccountService, tradeService, assetStore, assetTransferService);
    this.orderService = new OrderServiceImpl(orderStore, accountService, tradeService);
  }

  @Override
  public Collection<Asset> getAllAssets(int from, int to) {
    return assetService.getAllAssets(from, to);
  }

  @Override
  public Asset getAsset(long assetId) {
    return assetService.getAsset(assetId);
  }

  @Override
  public int getTradeCount(long assetId) {
    return tradeService.getTradeCount(assetId);
  }

  @Override
  public int getTransferCount(long assetId) {
    return assetTransferService.getTransferCount(assetId);
  }

  @Override
  public int getAssetAccountsCount(long assetId) {
    return assetAccountService.getAssetAccountsCount(assetId);
  }

  @Override
  public void addTradeListener(Listener<Trade> listener, Event eventType) {
    tradeService.addListener(listener, eventType);
  }

  @Override
  public Ask getAskOrder(long orderId) {
    return orderService.getAskOrder(orderId);
  }

  @Override
  public void addAsset(Transaction transaction, ColoredCoinsAssetIssuance attachment) {
    assetService.addAsset(transaction, attachment);
  }

  @Override
  public void addAssetTransfer(Transaction transaction, ColoredCoinsAssetTransfer attachment) {
    assetTransferService.addAssetTransfer(transaction, attachment);
  }

  @Override
  public void addAskOrder(Transaction transaction, ColoredCoinsAskOrderPlacement attachment) {
    orderService.addAskOrder(transaction, attachment);
  }

  @Override
  public void addBidOrder(Transaction transaction, ColoredCoinsBidOrderPlacement attachment) {
    orderService.addBidOrder(transaction, attachment);
  }

  @Override
  public void removeAskOrder(long orderId) {
    orderService.removeAskOrder(orderId);
  }

  @Override
  public Bid getBidOrder(long orderId) {
    return orderService.getBidOrder(orderId);
  }

  @Override
  public void removeBidOrder(long orderId) {
    orderService.removeBidOrder(orderId);
  }

  @Override
  public Collection<Trade> getAllTrades(int from, int to) {
    return tradeService.getAllTrades(from, to);
  }

  @Override
  public Collection<Trade> getTrades(long assetId, int from, int to) {
    return assetService.getTrades(assetId, from, to);
  }

  @Override
  public Collection<Trade> getAccountTrades(long id, int from, int to) {
    return tradeService.getAccountTrades(id, from, to);
  }

  @Override
  public Collection<Trade> getAccountAssetTrades(long accountId, long assetId, int from, int to) {
    return tradeService.getAccountAssetTrades(accountId, assetId, from, to);
  }

  @Override
  public int getAssetsCount() {
    return assetService.getAssetsCount();
  }

  @Override
  public Collection<AccountAsset> getAccountAssetsOverview(long id, int height, int from, int to) {
    return assetAccountService.getAssetAccounts(id, height, from, to);
  }

  @Override
  public Collection<Asset> getAssetsIssuedBy(long accountId, int from, int to) {
    return assetService.getAssetsIssuedBy(accountId, from, to);
  }

  @Override
  public Collection<AssetTransfer> getAssetTransfers(long assetId, int from, int to) {
    return assetTransferService.getAssetTransfers(assetId, from, to);
  }

  @Override
  public Collection<AssetTransfer> getAccountAssetTransfers(long accountId, long assetId, int from, int to) {
    return assetTransferService.getAccountAssetTransfers(accountId, assetId, from, to);
  }

  @Override
  public int getAskCount() {
    return orderService.getAskCount();
  }

  @Override
  public int getBidCount() {
    return orderService.getBidCount();
  }

  @Override
  public int getTradesCount() {
    return tradeService.getCount();
  }

  @Override
  public int getAssetTransferCount() {
    return assetTransferService.getAssetTransferCount();
  }

  @Override
  public Collection<Ask> getAskOrdersByAccount(long accountId, int from, int to) {
    return orderService.getAskOrdersByAccount(accountId, from, to);
  }

  @Override
  public Collection<Ask> getAskOrdersByAccountAsset(long accountId, long assetId, int from, int to) {
    return orderService.getAskOrdersByAccountAsset(accountId, assetId, from, to);
  }

  @Override
  public Collection<Bid> getBidOrdersByAccount(long accountId, int from, int to) {
    return orderService.getBidOrdersByAccount(accountId, from, to);
  }

  @Override
  public Collection<Bid> getBidOrdersByAccountAsset(long accountId, long assetId, int from, int to) {
    return orderService.getBidOrdersByAccountAsset(accountId, assetId, from, to);
  }

  @Override
  public Collection<Ask> getAllAskOrders(int from, int to) {
    return orderService.getAllAskOrders(from, to);
  }

  @Override
  public Collection<Bid> getAllBidOrders(int from, int to) {
    return orderService.getAllBidOrders(from, to);
  }

  @Override
  public Collection<Ask> getSortedAskOrders(long assetId, int from, int to) {
    return orderService.getSortedAskOrders(assetId, from, to);
  }

  @Override
  public Collection<Bid> getSortedBidOrders(long assetId, int from, int to) {
    return orderService.getSortedBidOrders(assetId, from, to);
  }
}
