package brs.db.store;

import brs.Trade;
import brs.db.BurstKey;
import brs.db.sql.EntitySqlTable;

import java.util.Collection;

public interface TradeStore {
  Collection<Trade> getAllTrades(int from, int to);

  Collection<Trade> getAssetTrades(long assetId, int from, int to);

  Collection<Trade> getAccountTrades(long accountId, int from, int to);

  Collection<Trade> getAccountAssetTrades(long accountId, long assetId, int from, int to);

  int getTradeCount(long assetId);

  BurstKey.LinkKeyFactory<Trade> getTradeDbKeyFactory();

  EntitySqlTable<Trade> getTradeTable();
}
