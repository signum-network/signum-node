package brs.assetexchange;

import brs.Block;
import brs.Order;
import brs.Trade;
import brs.Trade.Event;
import brs.db.BurstKey;
import brs.db.BurstKey.LinkKeyFactory;
import brs.db.sql.EntitySqlTable;
import brs.db.store.TradeStore;
import brs.util.Listener;
import brs.util.Listeners;

import java.util.Collection;

class TradeServiceImpl {

  private final Listeners<Trade,Event> listeners = new Listeners<>();

  private final TradeStore tradeStore;
  private final EntitySqlTable<Trade> tradeTable;
  private final LinkKeyFactory<Trade> tradeDbKeyFactory;


  public TradeServiceImpl(TradeStore tradeStore) {
    this.tradeStore = tradeStore;
    this.tradeTable = tradeStore.getTradeTable();
    this.tradeDbKeyFactory = tradeStore.getTradeDbKeyFactory();
  }

  public Collection<Trade> getAssetTrades(long assetId, int from, int to) {
    return tradeStore.getAssetTrades(assetId, from, to);
  }

  public Collection<Trade> getAccountAssetTrades(long accountId, long assetId, int from, int to) {
    return tradeStore.getAccountAssetTrades(accountId, assetId, from, to);
  }

  public Collection<Trade> getAccountTrades(long id, int from, int to) {
    return tradeStore.getAccountTrades(id, from, to);
  }

  public int getCount() {
    return tradeTable.getCount();
  }

  public int getTradeCount(long assetId) {
    return tradeStore.getTradeCount(assetId);
  }

  public Collection<Trade> getAllTrades(int from, int to) {
    return tradeTable.getAll(from, to);
  }

  public boolean addListener(Listener<Trade> listener, Event eventType) {
    return listeners.addListener(listener, eventType);
  }

  public boolean removeListener(Listener<Trade> listener, Event eventType) {
    return listeners.removeListener(listener, eventType);
  }

  public Trade addTrade(long assetId, Block block, Order.Ask askOrder, Order.Bid bidOrder) {
    BurstKey dbKey = tradeDbKeyFactory.newKey(askOrder.getId(), bidOrder.getId());
    Trade trade = new Trade(dbKey, assetId, block, askOrder, bidOrder);
    tradeTable.insert(trade);
    listeners.notify(trade, Event.TRADE);
    return trade;
  }
}
