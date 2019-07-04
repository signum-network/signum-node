package brs.db.sql;

import brs.Burst;
import brs.Order;
import brs.db.BurstKey;
import brs.db.VersionedEntityTable;
import brs.db.store.DerivedTableManager;
import brs.db.store.OrderStore;
import brs.schema.tables.records.AskOrderRecord;
import brs.schema.tables.records.BidOrderRecord;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectQuery;
import org.jooq.SortField;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static brs.schema.Tables.ASK_ORDER;
import static brs.schema.Tables.BID_ORDER;

public class SqlOrderStore implements OrderStore {
  private final DbKey.LongKeyFactory<Order.Ask> askOrderDbKeyFactory = new DbKey.LongKeyFactory<Order.Ask>(ASK_ORDER.ID) {

    @Override
    public BurstKey newKey(Order.Ask ask) {
      return ask.dbKey;
    }

  };
  private final VersionedEntityTable<Order.Ask> askOrderTable;
  private final DbKey.LongKeyFactory<Order.Bid> bidOrderDbKeyFactory = new DbKey.LongKeyFactory<Order.Bid>(BID_ORDER.ID) {

    @Override
    public BurstKey newKey(Order.Bid bid) {
      return bid.dbKey;
    }

  };

  public SqlOrderStore(DerivedTableManager derivedTableManager) {
    askOrderTable = new VersionedEntitySqlTable<Order.Ask>("ask_order", ASK_ORDER, askOrderDbKeyFactory, derivedTableManager) {
      @Override
      protected Order.Ask load(DSLContext ctx, Record record) {
        return new SqlAsk(record);
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

    bidOrderTable = new VersionedEntitySqlTable<Order.Bid>("bid_order", BID_ORDER, bidOrderDbKeyFactory, derivedTableManager) {

      @Override
      protected Order.Bid load(DSLContext ctx, Record rs) {
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
  private final VersionedEntityTable<Order.Bid> bidOrderTable;

  @Override
  public VersionedEntityTable<Order.Bid> getBidOrderTable() {
    return bidOrderTable;
  }

  @Override
  public Collection<Order.Ask> getAskOrdersByAccountAsset(final long accountId, final long assetId, int from, int to) {
    return askOrderTable.getManyBy(
            ASK_ORDER.ACCOUNT_ID.eq(accountId).and(
                    ASK_ORDER.ASSET_ID.eq(assetId)
            ),
            from,
            to
    );
  }

  @Override
  public Collection<Order.Ask> getSortedAsks(long assetId, int from, int to) {
    List<SortField<?>> sort = new ArrayList<>();
    sort.add(ASK_ORDER.field("price", Long.class).asc());
    sort.add(ASK_ORDER.field("creation_height", Integer.class).asc());
    sort.add(ASK_ORDER.field("id", Long.class).asc());
    return askOrderTable.getManyBy(ASK_ORDER.ASSET_ID.eq(assetId), from, to, sort);
  }

  @Override
  public Order.Ask getNextOrder(long assetId) {
    return Db.useDSLContext(ctx -> {
      SelectQuery<AskOrderRecord> query = ctx.selectFrom(ASK_ORDER)
              .where(ASK_ORDER.ASSET_ID.eq(assetId).and(ASK_ORDER.LATEST.isTrue()))
              .orderBy(ASK_ORDER.PRICE.asc(),
                      ASK_ORDER.CREATION_HEIGHT.asc(),
                      ASK_ORDER.ID.asc())
              .limit(1)
              .getQuery();
      Iterator<Order.Ask> result = askOrderTable.getManyBy(ctx, query, true).iterator();
      return result.hasNext() ? result.next() : null;
    });
  }

  @Override
  public Collection<Order.Ask> getAll(int from, int to) {
    return askOrderTable.getAll(from, to);
  }

  @Override
  public Collection<Order.Ask> getAskOrdersByAccount(long accountId, int from, int to) {
    return askOrderTable.getManyBy(ASK_ORDER.ACCOUNT_ID.eq(accountId), from, to);
  }

  @Override
  public Collection<Order.Ask> getAskOrdersByAsset(long assetId, int from, int to) {
    return askOrderTable.getManyBy(ASK_ORDER.ASSET_ID.eq(assetId), from, to);
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
  public Collection<Order.Bid> getBidOrdersByAccount(long accountId, int from, int to) {
    return bidOrderTable.getManyBy(BID_ORDER.ACCOUNT_ID.eq(accountId), from, to);
  }

  @Override
  public Collection<Order.Bid> getBidOrdersByAsset(long assetId, int from, int to) {
    return bidOrderTable.getManyBy(BID_ORDER.ASSET_ID.eq(assetId), from, to);
  }

  @Override
  public Collection<Order.Bid> getBidOrdersByAccountAsset(final long accountId, final long assetId, int from, int to) {
    return bidOrderTable.getManyBy(
            BID_ORDER.ACCOUNT_ID.eq(accountId).and(
                    BID_ORDER.ASSET_ID.eq(assetId)
            ),
            from,
            to
    );
  }

  @Override
  public Collection<Order.Bid> getSortedBids(long assetId, int from, int to) {
    List<SortField<?>> sort = new ArrayList<>();
    sort.add(BID_ORDER.field("price", Long.class).desc());
    sort.add(BID_ORDER.field("creation_height", Integer.class).asc());
    sort.add(BID_ORDER.field("id", Long.class).asc());
    return bidOrderTable.getManyBy(BID_ORDER.ASSET_ID.eq(assetId), from, to, sort);
  }

  @Override
  public Order.Bid getNextBid(long assetId) {
    return Db.useDSLContext(ctx -> {
      SelectQuery<BidOrderRecord> query = ctx.selectFrom(BID_ORDER)
              .where(BID_ORDER.ASSET_ID.eq(assetId)
                      .and(BID_ORDER.LATEST.isTrue()))
              .orderBy(BID_ORDER.PRICE.desc(),
                      BID_ORDER.CREATION_HEIGHT.asc(),
                      BID_ORDER.ID.asc())
              .limit(1)
              .getQuery();
      Iterator<Order.Bid> result = bidOrderTable.getManyBy(ctx, query, true).iterator();
      return result.hasNext() ? result.next() : null;
    });
  }

  private void saveBid(DSLContext ctx, Order.Bid bid) {
    ctx.mergeInto(BID_ORDER, BID_ORDER.ID, BID_ORDER.ACCOUNT_ID, BID_ORDER.ASSET_ID, BID_ORDER.PRICE, BID_ORDER.QUANTITY, BID_ORDER.CREATION_HEIGHT, BID_ORDER.HEIGHT, BID_ORDER.LATEST)
            .key(BID_ORDER.ID, BID_ORDER.HEIGHT)
            .values(bid.getId(), bid.getAccountId(), bid.getAssetId(), bid.getPriceNQT(), bid.getQuantityQNT(), bid.getHeight(), Burst.getBlockchain().getHeight(), true)
            .execute();
  }

  class SqlAsk extends Order.Ask {
    private SqlAsk(Record record) {
      super(
              record.get(ASK_ORDER.ID),
              record.get(ASK_ORDER.ACCOUNT_ID),
              record.get(ASK_ORDER.ASSET_ID),
              record.get(ASK_ORDER.PRICE),
              record.get(ASK_ORDER.CREATION_HEIGHT),
              record.get(ASK_ORDER.QUANTITY),
              askOrderDbKeyFactory.newKey(record.get(ASK_ORDER.ID))
      );
    }
  }

  class SqlBid extends Order.Bid {
    private SqlBid(Record record) {
      super(
              record.get(BID_ORDER.ID),
              record.get(BID_ORDER.ACCOUNT_ID),
              record.get(BID_ORDER.ASSET_ID),
              record.get(BID_ORDER.PRICE),
              record.get(BID_ORDER.CREATION_HEIGHT),
              record.get(BID_ORDER.QUANTITY),
              bidOrderDbKeyFactory.newKey(record.get(BID_ORDER.ID))
      );
    }
  }
}
