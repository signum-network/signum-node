package brs.assetexchange;

import brs.*;
import brs.Order.Ask;
import brs.Order.Bid;
import brs.db.BurstKey;
import brs.db.BurstKey.LongKeyFactory;
import brs.db.VersionedEntityTable;
import brs.db.store.OrderStore;
import brs.services.AccountService;
import brs.util.Convert;

import java.util.Collection;

class OrderServiceImpl {

  private final OrderStore orderStore;
  private final VersionedEntityTable<Ask> askOrderTable;
  private final LongKeyFactory<Ask> askOrderDbKeyFactory;
  private final VersionedEntityTable<Bid> bidOrderTable;
  private final LongKeyFactory<Bid> bidOrderDbKeyFactory;
  private final AccountService accountService;
  private final TradeServiceImpl tradeService;

  public OrderServiceImpl(OrderStore orderStore, AccountService accountService, TradeServiceImpl tradeService) {
    this.orderStore = orderStore;
    this.askOrderTable = orderStore.getAskOrderTable();
    this.askOrderDbKeyFactory = orderStore.getAskOrderDbKeyFactory();
    this.bidOrderTable = orderStore.getBidOrderTable();
    this.bidOrderDbKeyFactory = orderStore.getBidOrderDbKeyFactory();

    this.accountService = accountService;
    this.tradeService = tradeService;
  }

  public Ask getAskOrder(long orderId) {
    return askOrderTable.get(askOrderDbKeyFactory.newKey(orderId));
  }

  public Bid getBidOrder(long orderId) {
    return bidOrderTable.get(bidOrderDbKeyFactory.newKey(orderId));
  }

  public Collection<Ask> getAllAskOrders(int from, int to) {
    return askOrderTable.getAll(from, to);
  }

  public Collection<Bid> getAllBidOrders(int from, int to) {
    return bidOrderTable.getAll(from, to);
  }

  public Collection<Bid> getSortedBidOrders(long assetId, int from, int to) {
    return orderStore.getSortedBids(assetId, from, to);
  }

  public Collection<Ask> getAskOrdersByAccount(long accountId, int from, int to) {
    return orderStore.getAskOrdersByAccount(accountId, from, to);
  }

  public Collection<Ask> getAskOrdersByAccountAsset(final long accountId, final long assetId, int from, int to) {
    return orderStore.getAskOrdersByAccountAsset(accountId, assetId, from, to);
  }

  public Collection<Ask> getSortedAskOrders(long assetId, int from, int to) {
    return orderStore.getSortedAsks(assetId, from, to);
  }

  public int getBidCount() {
    return bidOrderTable.getCount();
  }

  public int getAskCount() {
    return askOrderTable.getCount();
  }

  public Collection<Bid> getBidOrdersByAccount(long accountId, int from, int to) {
    return orderStore.getBidOrdersByAccount(accountId, from, to);
  }

  public Collection<Bid> getBidOrdersByAccountAsset(final long accountId, final long assetId, int from, int to) {
    return orderStore.getBidOrdersByAccountAsset(accountId, assetId, from, to);
  }

  public void removeBidOrder(long orderId) {
    bidOrderTable.delete(getBidOrder(orderId));
  }

  public void removeAskOrder(long orderId) {
    askOrderTable.delete(getAskOrder(orderId));
  }

  public void addAskOrder(Transaction transaction, Attachment.ColoredCoinsAskOrderPlacement attachment) {
    BurstKey dbKey = askOrderDbKeyFactory.newKey(transaction.getId());
    Ask order = new Ask(dbKey, transaction, attachment);
    askOrderTable.insert(order);
    matchOrders(attachment.getAssetId());
  }

  public void addBidOrder(Transaction transaction, Attachment.ColoredCoinsBidOrderPlacement attachment) {
    BurstKey dbKey = bidOrderDbKeyFactory.newKey(transaction.getId());
    Bid order = new Bid(dbKey, transaction, attachment);
    bidOrderTable.insert(order);
    matchOrders(attachment.getAssetId());
  }

  private Ask getNextAskOrder(long assetId) {
    return Burst.getStores().getOrderStore().getNextOrder(assetId);
  }

  private Bid getNextBidOrder(long assetId) {
    return Burst.getStores().getOrderStore().getNextBid(assetId);
  }

  private void matchOrders(long assetId) {

    Order.Ask askOrder;
    Order.Bid bidOrder;

    while ((askOrder = getNextAskOrder(assetId)) != null
        && (bidOrder = getNextBidOrder(assetId)) != null) {

      if (askOrder.getPriceNQT() > bidOrder.getPriceNQT()) {
        break;
      }


      Trade trade = tradeService.addTrade(assetId, Burst.getBlockchain().getLastBlock(), askOrder, bidOrder);

      askOrderUpdateQuantityQNT(askOrder, Convert.safeSubtract(askOrder.getQuantityQNT(), trade.getQuantityQNT()));
      Account askAccount = accountService.getAccount(askOrder.getAccountId());
      accountService.addToBalanceAndUnconfirmedBalanceNQT(askAccount, Convert.safeMultiply(trade.getQuantityQNT(), trade.getPriceNQT()));
      accountService.addToAssetBalanceQNT(askAccount, assetId, -trade.getQuantityQNT());

      bidOrderUpdateQuantityQNT(bidOrder, Convert.safeSubtract(bidOrder.getQuantityQNT(), trade.getQuantityQNT()));
      Account bidAccount = accountService.getAccount(bidOrder.getAccountId());
      accountService.addToAssetAndUnconfirmedAssetBalanceQNT(bidAccount, assetId, trade.getQuantityQNT());
      accountService.addToBalanceNQT(bidAccount, -Convert.safeMultiply(trade.getQuantityQNT(), trade.getPriceNQT()));
      accountService.addToUnconfirmedBalanceNQT(bidAccount, Convert.safeMultiply(trade.getQuantityQNT(), (bidOrder.getPriceNQT() - trade.getPriceNQT())));

    }

  }

  private void askOrderUpdateQuantityQNT(Ask askOrder, long quantityQNT) {
    askOrder.setQuantityQNT(quantityQNT);
    if (quantityQNT > 0) {
      askOrderTable.insert(askOrder);
    } else if (quantityQNT == 0) {
      askOrderTable.delete(askOrder);
    } else {
      throw new IllegalArgumentException("Negative quantity: " + quantityQNT
          + " for order: " + Convert.toUnsignedLong(askOrder.getId()));
    }
  }

  private void bidOrderUpdateQuantityQNT(Bid bidOrder, long quantityQNT) {
    bidOrder.setQuantityQNT(quantityQNT);
    if (quantityQNT > 0) {
      bidOrderTable.insert(bidOrder);
    } else if (quantityQNT == 0) {
      bidOrderTable.delete(bidOrder);
    } else {
      throw new IllegalArgumentException("Negative quantity: " + quantityQNT
          + " for order: " + Convert.toUnsignedLong(bidOrder.getId()));
    }
  }


}
