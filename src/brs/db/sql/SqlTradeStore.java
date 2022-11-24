package brs.db.sql;

import brs.Trade;
import brs.db.BurstKey;
import brs.db.store.DerivedTableManager;
import brs.db.store.TradeStore;
import brs.schema.tables.records.TradeRecord;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectQuery;
import org.jooq.SortField;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static brs.schema.Tables.TRADE;

public class SqlTradeStore implements TradeStore {
  private final DbKey.LinkKeyFactory<Trade> tradeDbKeyFactory = new DbKey.LinkKeyFactory<Trade>("ask_order_id", "bid_order_id") {

      @Override
      public BurstKey newKey(Trade trade) {
        return trade.dbKey;
      }

    };

  private final EntitySqlTable<Trade> tradeTable;

  public SqlTradeStore(DerivedTableManager derivedTableManager) {
    tradeTable = new EntitySqlTable<Trade>("trade", TRADE, tradeDbKeyFactory, derivedTableManager) {

      @Override
      protected Trade load(DSLContext ctx, Record record) {
        return new SqlTrade(record);
      }

      @Override
      protected void save(DSLContext ctx, Trade trade) {
        saveTrade(ctx, trade);
      }

    };
  }

  @Override
  public Collection<Trade> getAllTrades(int from, int to) {
    return tradeTable.getAll(from, to);
  }

  @Override
  public Collection<Trade> getAssetTrades(long assetId, int from, int to) {
    List<SortField<?>> sort = new ArrayList<>();
    sort.add(TRADE.ASK_ORDER_HEIGHT.desc());
    sort.add(TRADE.BID_ORDER_HEIGHT.desc());
    return tradeTable.getManyBy(TRADE.ASSET_ID.eq(assetId), from, to, sort);
  }

  @Override
  public long getTradeVolume(long assetId, int heightStart, int heightEnd) {
    return Db.useDSLContext(ctx -> {
      return ctx.select(DSL.sum(TRADE.QUANTITY)).from(TRADE).where(TRADE.ASSET_ID.eq(assetId))
          .and(TRADE.HEIGHT.ge(heightStart))
          .and(TRADE.HEIGHT.le(heightEnd))
      .fetchOneInto(long.class);
    });
  }

  @Override
  public long getHighPrice(long assetId, int heightStart, int heightEnd) {
    return Db.useDSLContext(ctx -> {
      return ctx.select(DSL.max(TRADE.PRICE)).from(TRADE).where(TRADE.ASSET_ID.eq(assetId))
          .and(TRADE.HEIGHT.ge(heightStart))
          .and(TRADE.HEIGHT.le(heightEnd))
      .fetchOneInto(long.class);
    });
  }

  @Override
  public long getLowPrice(long assetId, int heightStart, int heightEnd) {
    return Db.useDSLContext(ctx -> {
      return ctx.select(DSL.min(TRADE.PRICE)).from(TRADE).where(TRADE.ASSET_ID.eq(assetId))
          .and(TRADE.HEIGHT.ge(heightStart))
          .and(TRADE.HEIGHT.le(heightEnd))
      .fetchOneInto(long.class);
    });
  }

  @Override
  public long getOpenPrice(long assetId, int heightStart, int heightEnd) {
     return Db.useDSLContext(ctx -> {
       Record record = ctx.select(TRADE.PRICE).from(TRADE).where(TRADE.ASSET_ID.eq(assetId))
          .and(TRADE.HEIGHT.ge(heightStart))
          .and(TRADE.HEIGHT.le(heightEnd))
          .orderBy(TRADE.TIMESTAMP.asc())
          .limit(1)
        .fetchOne();
       return record != null ? record.into(long.class) : 0L;
    });
  }

  @Override
  public long getClosePrice(long assetId, int heightStart, int heightEnd) {
    return Db.useDSLContext(ctx -> {
      Record record = ctx.select(TRADE.PRICE).from(TRADE).where(TRADE.ASSET_ID.eq(assetId))
        .and(TRADE.HEIGHT.ge(heightStart))
        .and(TRADE.HEIGHT.le(heightEnd))
        .orderBy(TRADE.TIMESTAMP.desc())
        .limit(1)
        .fetchOne();
      return record != null ? record.into(long.class) : 0L;
    });
  }


  @Override
  public Collection<Trade> getAccountTrades(long accountId, int from, int to) {
    return Db.useDSLContext(ctx -> {
      SelectQuery<TradeRecord> selectQuery = ctx
              .selectFrom(TRADE).where(
                      TRADE.SELLER_ID.eq(accountId)
              )
              .unionAll(
                      ctx.selectFrom(TRADE).where(
                              TRADE.BUYER_ID.eq(accountId).and(
                                      TRADE.SELLER_ID.ne(accountId)
                              )
                      )
              )
              .orderBy(TRADE.HEIGHT.desc())
              .getQuery();
      DbUtils.applyLimits(selectQuery, from, to);

      return tradeTable.getManyBy(ctx, selectQuery, false);
    });
  }

  @Override
  public Collection<Trade> getAccountAssetTrades(long accountId, long assetId, int from, int to) {
    return Db.useDSLContext(ctx -> {
      SelectQuery<TradeRecord> selectQuery = ctx
              .selectFrom(TRADE).where(
                      TRADE.SELLER_ID.eq(accountId).and(TRADE.ASSET_ID.eq(assetId))
              )
              .unionAll(
                      ctx.selectFrom(TRADE).where(
                              TRADE.BUYER_ID.eq(accountId)).and(
                              TRADE.SELLER_ID.ne(accountId)
                      ).and(TRADE.ASSET_ID.eq(assetId))
              )
              .orderBy(TRADE.HEIGHT.desc())
              .getQuery();
      DbUtils.applyLimits(selectQuery, from, to);

      return tradeTable.getManyBy(ctx, selectQuery, false);
    });
  }
  
  @Override
  public Collection<Trade> getOrderTrades(long orderId) {
    return Db.useDSLContext(ctx -> {
      SelectQuery<TradeRecord> selectQuery = ctx
              .selectFrom(TRADE).where(
                      TRADE.ASK_ORDER_ID.eq(orderId).or(TRADE.BID_ORDER_ID.eq(orderId))
              )
              .orderBy(TRADE.HEIGHT.desc())
              .getQuery();

      return tradeTable.getManyBy(ctx, selectQuery, false);
    });
  }

  @Override
  public int getTradeCount(long assetId) {
    return Db.useDSLContext(ctx -> {
      return ctx.fetchCount(ctx.selectFrom(TRADE).where(TRADE.ASSET_ID.eq(assetId)));
    });
  }

  private void saveTrade(DSLContext ctx, Trade trade) {
    ctx.insertInto(
      TRADE,
      TRADE.ASSET_ID, TRADE.BLOCK_ID, TRADE.ASK_ORDER_ID, TRADE.BID_ORDER_ID, TRADE.ASK_ORDER_HEIGHT,
      TRADE.BID_ORDER_HEIGHT, TRADE.SELLER_ID, TRADE.BUYER_ID, TRADE.QUANTITY, TRADE.PRICE,
      TRADE.TIMESTAMP, TRADE.HEIGHT
    ).values(
      trade.getAssetId(), trade.getBlockId(), trade.getAskOrderId(), trade.getBidOrderId(), trade.getAskOrderHeight(),
      trade.getBidOrderHeight(), trade.getSellerId(), trade.getBuyerId(), trade.getQuantityQNT(), trade.getPriceNQT(),
      trade.getTimestamp(), trade.getHeight()
    ).execute();
  }

  @Override
  public DbKey.LinkKeyFactory<Trade> getTradeDbKeyFactory() {
    return tradeDbKeyFactory;
  }

  @Override
  public EntitySqlTable<Trade> getTradeTable() {
    return tradeTable;
  }

  private class SqlTrade extends Trade {

    private SqlTrade(Record record) {
      super(
            record.get(TRADE.TIMESTAMP),
            record.get(TRADE.ASSET_ID),
            record.get(TRADE.BLOCK_ID),
            record.get(TRADE.HEIGHT),
            record.get(TRADE.ASK_ORDER_ID),
            record.get(TRADE.BID_ORDER_ID),
            record.get(TRADE.ASK_ORDER_HEIGHT),
            record.get(TRADE.BID_ORDER_HEIGHT),
            record.get(TRADE.SELLER_ID),
            record.get(TRADE.BUYER_ID),
            tradeDbKeyFactory.newKey(record.get(TRADE.ASK_ORDER_ID), record.get(TRADE.BID_ORDER_ID)),
            record.get(TRADE.QUANTITY),
            record.get(TRADE.PRICE)
            );
    }
  }
}
