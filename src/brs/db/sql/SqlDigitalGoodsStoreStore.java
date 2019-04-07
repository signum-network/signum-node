package brs.db.sql;

import brs.Burst;
import brs.DigitalGoodsStore;
import brs.crypto.EncryptedData;
import brs.db.BurstIterator;
import brs.db.BurstKey;
import brs.db.VersionedEntityTable;
import brs.db.VersionedValuesTable;
import brs.db.store.DerivedTableManager;
import brs.db.store.DigitalGoodsStoreStore;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SortField;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static brs.schema.Tables.*;

public class SqlDigitalGoodsStoreStore implements DigitalGoodsStoreStore {

  private static final DbKey.LongKeyFactory<DigitalGoodsStore.Purchase> feedbackDbKeyFactory
    = new DbKey.LongKeyFactory<DigitalGoodsStore.Purchase>("id") {
        @Override
        public BurstKey newKey(DigitalGoodsStore.Purchase purchase) {
          return purchase.dbKey;
        }
      };

  private final BurstKey.LongKeyFactory<DigitalGoodsStore.Purchase> purchaseDbKeyFactory
    = new DbKey.LongKeyFactory<DigitalGoodsStore.Purchase>("id") {
        @Override
        public BurstKey newKey(DigitalGoodsStore.Purchase purchase) {
          return purchase.dbKey;
        }
      };

  private final VersionedEntityTable<DigitalGoodsStore.Purchase> purchaseTable;

  @Deprecated
  private final VersionedValuesTable<DigitalGoodsStore.Purchase, EncryptedData> feedbackTable;

  private final DbKey.LongKeyFactory<DigitalGoodsStore.Purchase> publicFeedbackDbKeyFactory
    = new DbKey.LongKeyFactory<DigitalGoodsStore.Purchase>("id") {
        @Override
        public BurstKey newKey(DigitalGoodsStore.Purchase purchase) {
          return purchase.dbKey;
        }
      };

  private final VersionedValuesTable<DigitalGoodsStore.Purchase, String> publicFeedbackTable;

  private final BurstKey.LongKeyFactory<DigitalGoodsStore.Goods> goodsDbKeyFactory = new DbKey.LongKeyFactory<DigitalGoodsStore.Goods>("id") {
      @Override
      public BurstKey newKey(DigitalGoodsStore.Goods goods) {
        return goods.dbKey;
      }
    };

  private final VersionedEntityTable<DigitalGoodsStore.Goods> goodsTable;

  public SqlDigitalGoodsStoreStore(DerivedTableManager derivedTableManager) {
    purchaseTable = new VersionedEntitySqlTable<DigitalGoodsStore.Purchase>("purchase", brs.schema.Tables.PURCHASE, purchaseDbKeyFactory, derivedTableManager) {
      @Override
      protected DigitalGoodsStore.Purchase load(DSLContext ctx, ResultSet rs) throws SQLException {
        return new SQLPurchase(rs);
      }

      @Override
      protected void save(DSLContext ctx, DigitalGoodsStore.Purchase purchase) {
        savePurchase(ctx, purchase);
      }

      @Override
      protected List<SortField> defaultSort() {
        List<SortField> sort = new ArrayList<>();
        sort.add(tableClass.field("timestamp", Integer.class).desc());
        sort.add(tableClass.field("id", Long.class).asc());
        return sort;
      }
    };

    feedbackTable = new VersionedValuesSqlTable<DigitalGoodsStore.Purchase, EncryptedData>("purchase_feedback", brs.schema.Tables.PURCHASE_FEEDBACK, feedbackDbKeyFactory, derivedTableManager) {

      @Override
      protected EncryptedData load(DSLContext ctx, ResultSet rs) throws SQLException {
        byte[] data = rs.getBytes("feedback_data");
        byte[] nonce = rs.getBytes("feedback_nonce");
        return new EncryptedData(data, nonce);
      }

      @Override
      protected void save(DSLContext ctx, DigitalGoodsStore.Purchase purchase, EncryptedData encryptedData) {
        byte[] data  = null;
        byte[] nonce = null;
        if ( encryptedData.getData() != null ) {
          data  = encryptedData.getData();
          nonce = encryptedData.getNonce();
        }
        ctx.insertInto(
            PURCHASE_FEEDBACK,
            PURCHASE_FEEDBACK.ID,
            PURCHASE_FEEDBACK.FEEDBACK_DATA, PURCHASE_FEEDBACK.FEEDBACK_NONCE,
            PURCHASE_FEEDBACK.HEIGHT, PURCHASE_FEEDBACK.LATEST
        ).values(
            purchase.getId(),
            data, nonce,
            brs.Burst.getBlockchain().getHeight(), true
        ).execute();
      }
    };

    publicFeedbackTable
        = new VersionedValuesSqlTable<DigitalGoodsStore.Purchase, String>("purchase_public_feedback", brs.schema.Tables.PURCHASE_PUBLIC_FEEDBACK, publicFeedbackDbKeyFactory, derivedTableManager) {

      @Override
      protected String load(DSLContext ctx, ResultSet rs) throws SQLException {
        return rs.getString("public_feedback");
      }

      @Override
      protected void save(DSLContext ctx, DigitalGoodsStore.Purchase purchase, String publicFeedback) {
        brs.schema.tables.records.PurchasePublicFeedbackRecord feedbackRecord = ctx.newRecord(
            PURCHASE_PUBLIC_FEEDBACK
        );
        feedbackRecord.setId(purchase.getId());
        feedbackRecord.setPublicFeedback(publicFeedback);
        feedbackRecord.setHeight(brs.Burst.getBlockchain().getHeight());
        feedbackRecord.setLatest(true);
        DbUtils.mergeInto(
            ctx, feedbackRecord, PURCHASE_PUBLIC_FEEDBACK,
            ( new Field[] { feedbackRecord.field("id"), feedbackRecord.field("height") } )
        );
      }
    };

    goodsTable = new VersionedEntitySqlTable<DigitalGoodsStore.Goods>("goods", brs.schema.Tables.GOODS, goodsDbKeyFactory, derivedTableManager) {

      @Override
      protected DigitalGoodsStore.Goods load(DSLContext ctx, ResultSet rs) throws SQLException {
        return new SQLGoods(rs);
      }

      @Override
      protected void save(DSLContext ctx, DigitalGoodsStore.Goods goods) {
        saveGoods(ctx, goods);
      }

      @Override
      protected List<SortField> defaultSort() {
        List<SortField> sort = new ArrayList<>();
        sort.add(brs.schema.Tables.GOODS.field("timestamp", Integer.class).desc());
        sort.add(brs.schema.Tables.GOODS.field("id", Long.class).asc());
        return sort;
      }
    };
  }

  @Override
  public BurstIterator<DigitalGoodsStore.Purchase> getExpiredPendingPurchases(final int timestamp) {
    return getPurchaseTable().getManyBy(PURCHASE.DEADLINE.lt(timestamp).and(PURCHASE.PENDING.isTrue()), 0, -1);
  }

  private EncryptedData loadEncryptedData(ResultSet rs, String dataColumn, String nonceColumn) throws SQLException {
    byte[] data = rs.getBytes(dataColumn);
    if (data == null) {
      return null;
    }
    return new EncryptedData(data, rs.getBytes(nonceColumn));
  }

  @Override
  public BurstKey.LongKeyFactory<DigitalGoodsStore.Purchase> getFeedbackDbKeyFactory() {
    return feedbackDbKeyFactory;
  }

  @Override
  public BurstKey.LongKeyFactory<DigitalGoodsStore.Purchase> getPurchaseDbKeyFactory() {
    return purchaseDbKeyFactory;
  }

  @Override
  public VersionedEntityTable<DigitalGoodsStore.Purchase> getPurchaseTable() {
    return purchaseTable;
  }

  @Override
  public VersionedValuesTable<DigitalGoodsStore.Purchase, EncryptedData> getFeedbackTable() {
    return feedbackTable;
  }

  @Override
  public DbKey.LongKeyFactory<DigitalGoodsStore.Purchase> getPublicFeedbackDbKeyFactory() {
    return publicFeedbackDbKeyFactory;
  }

  public VersionedValuesTable<DigitalGoodsStore.Purchase, String> getPublicFeedbackTable() {
    return publicFeedbackTable;
  }

  @Override
  public BurstKey.LongKeyFactory<DigitalGoodsStore.Goods> getGoodsDbKeyFactory() {
    return goodsDbKeyFactory;
  }

  @Override
  public VersionedEntityTable<DigitalGoodsStore.Goods> getGoodsTable() {
    return goodsTable;
  }

  private void saveGoods(DSLContext ctx, DigitalGoodsStore.Goods goods) {
    brs.schema.tables.records.GoodsRecord goodsRecord = ctx.newRecord(GOODS);
    goodsRecord.setId(goods.getId());
    goodsRecord.setSellerId(goods.getSellerId());
    goodsRecord.setName(goods.getName());
    goodsRecord.setDescription(goods.getDescription());
    goodsRecord.setTags(goods.getTags());
    goodsRecord.setTimestamp(goods.getTimestamp());
    goodsRecord.setQuantity(goods.getQuantity());
    goodsRecord.setPrice(goods.getPriceNQT());
    goodsRecord.setDelisted(goods.isDelisted());
    goodsRecord.setHeight(brs.Burst.getBlockchain().getHeight());
    goodsRecord.setLatest(true);
    DbUtils.mergeInto(
      ctx, goodsRecord, GOODS,
      ( new Field[] { goodsRecord.field("id"), goodsRecord.field("height") } )
    );
  }

  private void savePurchase(DSLContext ctx, DigitalGoodsStore.Purchase purchase) {
    byte[] note        = null;
    byte[] nonce       = null;
    byte[] goods       = null;
    byte[] goodsNonce  = null;
    byte[] refundNote  = null;
    byte[] refundNonce = null;
    if ( purchase.getNote() != null ) {
      note  = purchase.getNote().getData();
      nonce = purchase.getNote().getNonce();
    }
    if ( purchase.getEncryptedGoods() != null ) {
      goods      = purchase.getEncryptedGoods().getData();
      goodsNonce = purchase.getEncryptedGoods().getNonce();
    }
    if ( purchase.getRefundNote() != null ) {
      refundNote  = purchase.getRefundNote().getData();
      refundNonce = purchase.getRefundNote().getNonce();
    }
    brs.schema.tables.records.PurchaseRecord purchaseRecord = ctx.newRecord(PURCHASE);
    purchaseRecord.setId(purchase.getId());
    purchaseRecord.setBuyerId(purchase.getBuyerId());
    purchaseRecord.setGoodsId(purchase.getGoodsId());
    purchaseRecord.setSellerId(purchase.getSellerId());
    purchaseRecord.setQuantity(purchase.getQuantity());
    purchaseRecord.setPrice(purchase.getPriceNQT());
    purchaseRecord.setDeadline(purchase.getDeliveryDeadlineTimestamp());
    purchaseRecord.setNote(note);
    purchaseRecord.setNonce(nonce);
    purchaseRecord.setTimestamp(purchase.getTimestamp());
    purchaseRecord.setPending(purchase.isPending());
    purchaseRecord.setGoods(goods);
    purchaseRecord.setGoodsNonce(goodsNonce);
    purchaseRecord.setRefundNote(refundNote);
    purchaseRecord.setRefundNonce(refundNonce);
    purchaseRecord.setHasFeedbackNotes(purchase.getFeedbackNotes() != null && purchase.getFeedbackNotes().size() > 0);
    purchaseRecord.setHasPublicFeedbacks(purchase.getPublicFeedback().size() > 0);
    purchaseRecord.setDiscount(purchase.getDiscountNQT());
    purchaseRecord.setRefund(purchase.getRefundNQT());
    purchaseRecord.setHeight(Burst.getBlockchain().getHeight());
    purchaseRecord.setLatest(true);
    DbUtils.mergeInto(
      ctx, purchaseRecord, PURCHASE,
      ( new Field[] { purchaseRecord.field("id"), purchaseRecord.field("height") } )
    );
  }

  @Override
  public BurstIterator<DigitalGoodsStore.Goods> getGoodsInStock(int from, int to) {
    return goodsTable.getManyBy(GOODS.DELISTED.isFalse().and(GOODS.QUANTITY.gt(0)), from, to);
  }

  @Override
  public BurstIterator<DigitalGoodsStore.Goods> getSellerGoods(final long sellerId, final boolean inStockOnly, int from, int to) {
    List<SortField> sort = new ArrayList<>();
    sort.add(GOODS.field("name", String.class).asc());
    sort.add(GOODS.field("timestamp", Integer.class).desc());
    sort.add(GOODS.field("id", Long.class).asc());
    return getGoodsTable().getManyBy(
      (
        inStockOnly
          ? GOODS.SELLER_ID.eq(sellerId).and(GOODS.DELISTED.isFalse()).and(GOODS.QUANTITY.gt(0))
          : GOODS.SELLER_ID.eq(sellerId)
      ),
      from, to, sort
    );
  }

  @Override
  public BurstIterator<DigitalGoodsStore.Purchase> getAllPurchases(int from, int to) {
    return purchaseTable.getAll(from, to);
  }

  @Override
  public BurstIterator<DigitalGoodsStore.Purchase> getSellerPurchases(long sellerId, int from, int to) {
    return purchaseTable.getManyBy(PURCHASE.SELLER_ID.eq(sellerId), from, to);
  }

  @Override
  public BurstIterator<DigitalGoodsStore.Purchase> getBuyerPurchases(long buyerId, int from, int to) {
    return purchaseTable.getManyBy(PURCHASE.BUYER_ID.eq(buyerId), from, to);
  }

  @Override
  public BurstIterator<DigitalGoodsStore.Purchase> getSellerBuyerPurchases(final long sellerId, final long buyerId, int from, int to) {
    return purchaseTable.getManyBy(PURCHASE.SELLER_ID.eq(sellerId).and(PURCHASE.BUYER_ID.eq(buyerId)), from, to);
  }

  @Override
  public BurstIterator<DigitalGoodsStore.Purchase> getPendingSellerPurchases(final long sellerId, int from, int to) {
    return purchaseTable.getManyBy(PURCHASE.SELLER_ID.eq(sellerId).and(PURCHASE.PENDING.isTrue()), from, to);
  }

  public DigitalGoodsStore.Purchase getPendingPurchase(long purchaseId) {
    DigitalGoodsStore.Purchase purchase =
        purchaseTable.get(purchaseDbKeyFactory.newKey(purchaseId));
    return purchase == null || !purchase.isPending() ? null : purchase;
  }



  private class SQLGoods extends DigitalGoodsStore.Goods {
    private SQLGoods(ResultSet rs) throws SQLException {
      super(
            rs.getLong("id"),
            goodsDbKeyFactory.newKey(rs.getLong("id")),
            rs.getLong("seller_id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("tags"),
            rs.getInt("timestamp"),
            rs.getInt("quantity"),
            rs.getLong("price"),
            rs.getBoolean("delisted")
            );
    }
  }




  class SQLPurchase extends DigitalGoodsStore.Purchase {

    SQLPurchase(ResultSet rs) throws SQLException {
      super(
            rs.getLong("id"),
            purchaseDbKeyFactory.newKey(rs.getLong("id")),
            rs.getLong("buyer_id"),
            rs.getLong("goods_id"),
            rs.getLong("seller_id"),
            rs.getInt("quantity"),
            rs.getLong("price"),
            rs.getInt("deadline"),
            loadEncryptedData(rs, "note", "nonce"),
            rs.getInt("timestamp"),
            rs.getBoolean("pending"),
            loadEncryptedData(rs, "goods", "goods_nonce"),
            loadEncryptedData(rs, "refund_note", "refund_nonce"),
            rs.getBoolean("has_feedback_notes"),
            rs.getBoolean("has_public_feedbacks"),
            rs.getLong("discount"),
            rs.getLong("refund")
            );
    }
  }

}
