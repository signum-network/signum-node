package brs.db.sql;

import brs.Burst;
import brs.Order;
import brs.db.BurstIterator;
import brs.db.BurstKey;
import brs.db.VersionedEntityTable;
import brs.db.store.DerivedTableManager;
import brs.db.store.OrderStore;
import org.jooq.DSLContext;
import org.jooq.SelectQuery;
import org.jooq.SortField;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static brs.schema.tables.AskOrder.ASK_ORDER;
import static brs.schema.tables.BidOrder.BID_ORDER;

public class SqlOrderStore implements OrderStore {
  private final DbKey.LongKeyFactory<Order.Ask> askOrderDbKeyFactory = new DbKey.LongKeyFactory<Order.Ask>("id") {

      @Override
      public BurstKey newKey(Order.Ask ask) {
        return ask.dbKey;
      }

    };
  private final VersionedEntityTable<Order.Ask> askOrderTable;

  public SqlOrderStore(DerivedTableManager derivedTableManager) {
    askOrderTable = new VersionedEntitySqlTable<Order.Ask>("ask_order", brs.schema.Tables.ASK_ORDER, askOrderDbKeyFactory, derivedTableManager) {
      @Override
      protected Order.Ask load(DSLContext ctx, ResultSet rs) throws SQLException {
        return new SqlAsk(rs);
      }

      @Override
      protected void save(DSLContext ctx, Order.Ask ask) {
        saveAsk(ctx, ask);
      }

      @Override
      protected List<SortField<?>> defaultSort() {
        List<SortField<?>> sort = new ArrayList<>();
        sort.add(tableClass.field("creation_height", Integer.class).desc());
        return sort;
      }
    };

    bidOrderTable = new VersionedEntitySqlTable<Order.Bid>("bid_order", brs.schema.Tables.BID_ORDER, bidOrderDbKeyFactory, derivedTableManager) {

      @Override
      protected Order.Bid load(DSLContext ctx, ResultSet rs) throws SQLException {
        return new SqlBid(rs);
      }

      @Override
      protected void save(DSLContext ctx, Order.Bid bid) {
        saveBid(ctx, bid);
      }

      @Override
      protected List<SortField<?>> defaultSort() {
        List<SortField<?>> sort = new ArrayList<>();
        sort.add(tableClass.field("creation_height", Integer.class).desc());
        return sort;
      }

    };

  }

  private final DbKey.LongKeyFactory<Order.Bid> bidOrderDbKeyFactory = new DbKey.LongKeyFactory<Order.Bid>("id") {

      @Override
      public BurstKey newKey(Order.Bid bid) {
        return bid.dbKey;
      }

    };
  private final VersionedEntityTable<Order.Bid> bidOrderTable;

  @Override
  public VersionedEntityTable<Order.Bid> getBidOrderTable() {
    return bidOrderTable;
  }

  @Override
  public BurstIterator<Order.Ask> getAskOrdersByAccountAsset(final long accountId, final long assetId, int from, int to) {
    return askOrderTable.getManyBy(
      brs.schema.Tables.ASK_ORDER.ACCOUNT_ID.eq(accountId).and(
        brs.schema.Tables.ASK_ORDER.ASSET_ID.eq(assetId)
      ),
      from,
      to
    );
  }

  @Override
  public BurstIterator<Order.Ask> getSortedAsks(long assetId, int from, int to) {
    List<SortField<?>> sort = new ArrayList<>();
    sort.add(brs.schema.Tables.ASK_ORDER.field("price", Long.class).asc());
    sort.add(brs.schema.Tables.ASK_ORDER.field("creation_height", Integer.class).asc());
    sort.add(brs.schema.Tables.ASK_ORDER.field("id", Long.class).asc());
    return askOrderTable.getManyBy(brs.schema.Tables.ASK_ORDER.ASSET_ID.eq(assetId), from, to, sort);
  }

  @Override
  public Order.Ask getNextOrder(long assetId) {
    DSLContext ctx = Db.getDSLContext();
    SelectQuery query = ctx.selectFrom(brs.schema.Tables.ASK_ORDER).where(
      brs.schema.Tables.ASK_ORDER.ASSET_ID.eq(assetId).and(brs.schema.Tables.ASK_ORDER.LATEST.isTrue())
    ).orderBy(
      brs.schema.Tables.ASK_ORDER.PRICE.asc(),
      brs.schema.Tables.ASK_ORDER.CREATION_HEIGHT.asc(),
      brs.schema.Tables.ASK_ORDER.ID.asc()
    ).limit(1).getQuery();
    try (BurstIterator<Order.Ask> askOrders = askOrderTable.getManyBy(ctx, query, true)) {
      return askOrders.hasNext() ? askOrders.next() : null;
    }
  }

  @Override
  public BurstIterator<Order.Ask> getAll(int from, int to) {
    return askOrderTable.getAll(from, to);
  }

  @Override
  public BurstIterator<Order.Ask> getAskOrdersByAccount(long accountId, int from, int to) {
    return askOrderTable.getManyBy(brs.schema.Tables.ASK_ORDER.ACCOUNT_ID.eq(accountId), from, to);
  }

  @Override
  public BurstIterator<Order.Ask> getAskOrdersByAsset(long assetId, int from, int to) {
    return askOrderTable.getManyBy(brs.schema.Tables.ASK_ORDER.ASSET_ID.eq(assetId), from, to);
  }

  private void saveAsk(DSLContext ctx, Order.Ask ask) {
    ctx.mergeInto(ASK_ORDER, ASK_ORDER.ID, ASK_ORDER.ACCOUNT_ID, ASK_ORDER.ASSET_ID, ASK_ORDER.PRICE, ASK_ORDER.QUANTITY, ASK_ORDER.CREATION_HEIGHT, ASK_ORDER.HEIGHT, ASK_ORDER.LATEST)
            .key(ASK_ORDER.ID, ASK_ORDER.HEIGHT)
            .values(ask.getId(), ask.getAccountId(), ask.getAssetId(), ask.getPriceNQT(), ask.getQuantityQNT(), ask.getHeight(), Burst.getBlockchain().getHeight(), true)
            .execute();
  }

  @Override
  public DbKey.LongKeyFactory<Order.Ask> getAskOrderDbKeyFactory() {
    return askOrderDbKeyFactory;
  }

  @Override
  public VersionedEntityTable<Order.Ask> getAskOrderTable() {
    return askOrderTable;
  }

  @Override
  public DbKey.LongKeyFactory<Order.Bid> getBidOrderDbKeyFactory() {
    return bidOrderDbKeyFactory;
  }

  @Override
  public BurstIterator<Order.Bid> getBidOrdersByAccount(long accountId, int from, int to) {
    return bidOrderTable.getManyBy(brs.schema.Tables.BID_ORDER.ACCOUNT_ID.eq(accountId), from, to);
  }

  @Override
  public BurstIterator<Order.Bid> getBidOrdersByAsset(long assetId, int from, int to) {
    return bidOrderTable.getManyBy(brs.schema.Tables.BID_ORDER.ASSET_ID.eq(assetId), from, to);
  }

  @Override
  public BurstIterator<Order.Bid> getBidOrdersByAccountAsset(final long accountId, final long assetId, int from, int to) {
    return bidOrderTable.getManyBy(
      brs.schema.Tables.BID_ORDER.ACCOUNT_ID.eq(accountId).and(
        brs.schema.Tables.BID_ORDER.ASSET_ID.eq(assetId)
      ),
      from,
      to
    );
  }

  @Override
  public BurstIterator<Order.Bid> getSortedBids(long assetId, int from, int to) {
    List<SortField<?>> sort = new ArrayList<>();
    sort.add(brs.schema.Tables.BID_ORDER.field("price", Long.class).desc());
    sort.add(brs.schema.Tables.BID_ORDER.field("creation_height", Integer.class).asc());
    sort.add(brs.schema.Tables.BID_ORDER.field("id", Long.class).asc());
    return bidOrderTable.getManyBy(brs.schema.Tables.BID_ORDER.ASSET_ID.eq(assetId), from, to, sort);
  }

  @Override
  public Order.Bid getNextBid(long assetId) {
    DSLContext ctx = Db.getDSLContext();
    SelectQuery query = ctx.selectFrom(brs.schema.Tables.BID_ORDER).where(
      brs.schema.Tables.BID_ORDER.ASSET_ID.eq(assetId).and(brs.schema.Tables.BID_ORDER.LATEST.isTrue())
    ).orderBy(
      brs.schema.Tables.BID_ORDER.PRICE.desc(),
      brs.schema.Tables.BID_ORDER.CREATION_HEIGHT.asc(),
      brs.schema.Tables.BID_ORDER.ID.asc()
    ).limit(1).getQuery();
    try (BurstIterator<Order.Bid> bidOrders = bidOrderTable.getManyBy(ctx, query, true)) {
      return bidOrders.hasNext() ? bidOrders.next() : null;
    }
  }

  private void saveBid(DSLContext ctx, Order.Bid bid) {
    ctx.mergeInto(BID_ORDER, BID_ORDER.ID, BID_ORDER.ACCOUNT_ID, BID_ORDER.ASSET_ID, BID_ORDER.PRICE, BID_ORDER.QUANTITY, BID_ORDER.CREATION_HEIGHT, BID_ORDER.HEIGHT, BID_ORDER.LATEST)
            .key(BID_ORDER.ID, BID_ORDER.HEIGHT)
            .values(bid.getId(), bid.getAccountId(), bid.getAssetId(), bid.getPriceNQT(), bid.getQuantityQNT(), bid.getHeight(), Burst.getBlockchain().getHeight(), true)
            .execute();
  }

  class SqlAsk extends Order.Ask {
    private SqlAsk(ResultSet rs) throws SQLException {
      super(
            rs.getLong("id"),
            rs.getLong("account_id"),
            rs.getLong("asset_id"),
            rs.getLong("price"),
            rs.getInt("creation_height"),
            rs.getLong("quantity"),
            askOrderDbKeyFactory.newKey(rs.getLong("id"))
            );
    }
  }

  class SqlBid extends Order.Bid {
    private SqlBid(ResultSet rs) throws SQLException {
      super(
            rs.getLong("id"),
            rs.getLong("account_id"),
            rs.getLong("asset_id"),
            rs.getLong("price"),
            rs.getInt("creation_height"),
            rs.getLong("quantity"),
            bidOrderDbKeyFactory.newKey(rs.getLong("id"))
            );
    }


  }

}
