package brs.db.sql;

import brs.AssetTransfer;
import brs.db.BurstKey;
import brs.db.store.AssetTransferStore;
import brs.db.store.DerivedTableManager;
import brs.schema.tables.records.AssetTransferRecord;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectQuery;

import java.util.Collection;

import static brs.schema.Tables.ASSET_TRANSFER;

public class SqlAssetTransferStore implements AssetTransferStore {

  private static final BurstKey.LongKeyFactory<AssetTransfer> transferDbKeyFactory = new DbKey.LongKeyFactory<AssetTransfer>(ASSET_TRANSFER.ID) {

      @Override
      public BurstKey newKey(AssetTransfer assetTransfer) {
        return assetTransfer.dbKey;
      }
    };
  private final EntitySqlTable<AssetTransfer> assetTransferTable;

  public SqlAssetTransferStore(DerivedTableManager derivedTableManager) {
    assetTransferTable = new EntitySqlTable<AssetTransfer>("asset_transfer", brs.schema.Tables.ASSET_TRANSFER, transferDbKeyFactory, derivedTableManager) {

      @Override
      protected AssetTransfer load(DSLContext ctx, Record record) {
        return new SqlAssetTransfer(record);
      }

      @Override
      protected void save(DSLContext ctx, AssetTransfer assetTransfer) {
        saveAssetTransfer(assetTransfer);
      }
    };
  }

  private void saveAssetTransfer(AssetTransfer assetTransfer) {
    Db.useDSLContext(ctx -> {
      ctx.insertInto(
        ASSET_TRANSFER,
        ASSET_TRANSFER.ID, ASSET_TRANSFER.ASSET_ID, ASSET_TRANSFER.SENDER_ID, ASSET_TRANSFER.RECIPIENT_ID,
        ASSET_TRANSFER.QUANTITY, ASSET_TRANSFER.TIMESTAMP, ASSET_TRANSFER.HEIGHT
      ).values(
        assetTransfer.getId(), assetTransfer.getAssetId(), assetTransfer.getSenderId(), assetTransfer.getRecipientId(),
        assetTransfer.getQuantityQNT(), assetTransfer.getTimestamp(), assetTransfer.getHeight()
      ).execute();
    });
  }


  @Override
  public EntitySqlTable<AssetTransfer> getAssetTransferTable() {
    return assetTransferTable;
  }

  @Override
  public BurstKey.LongKeyFactory<AssetTransfer> getTransferDbKeyFactory() {
    return transferDbKeyFactory;
  }
  @Override
  public Collection<AssetTransfer> getAssetTransfers(long assetId, int from, int to) {
    return getAssetTransferTable().getManyBy(ASSET_TRANSFER.ASSET_ID.eq(assetId), from, to);
  }

  @Override
  public Collection<AssetTransfer> getAccountAssetTransfers(long accountId, int from, int to) {
    return Db.useDSLContext(ctx -> {
      SelectQuery selectQuery = ctx
              .selectFrom(ASSET_TRANSFER).where(
                      ASSET_TRANSFER.SENDER_ID.eq(accountId)
              )
              .unionAll(
                      ctx.selectFrom(ASSET_TRANSFER).where(
                              ASSET_TRANSFER.RECIPIENT_ID.eq(accountId).and(ASSET_TRANSFER.SENDER_ID.ne(accountId))
                      )
              )
              .orderBy(ASSET_TRANSFER.HEIGHT.desc())
              .getQuery();
      DbUtils.applyLimits(selectQuery, from, to);

      return getAssetTransferTable().getManyBy(ctx, selectQuery, false);
    });
  }

  @Override
  public Collection<AssetTransfer> getAccountAssetTransfers(long accountId, long assetId, int from, int to) {
    return Db.useDSLContext(ctx -> {
      SelectQuery<AssetTransferRecord> selectQuery = ctx
              .selectFrom(ASSET_TRANSFER).where(
                      ASSET_TRANSFER.SENDER_ID.eq(accountId).and(ASSET_TRANSFER.ASSET_ID.eq(assetId))
              )
              .unionAll(
                      ctx.selectFrom(ASSET_TRANSFER).where(
                              ASSET_TRANSFER.RECIPIENT_ID.eq(accountId)).and(
                              ASSET_TRANSFER.SENDER_ID.ne(accountId)
                      ).and(ASSET_TRANSFER.ASSET_ID.eq(assetId))
              )
              .orderBy(ASSET_TRANSFER.HEIGHT.desc())
              .getQuery();
      DbUtils.applyLimits(selectQuery, from, to);

      return getAssetTransferTable().getManyBy(ctx, selectQuery, false);
    });
  }

  @Override
  public int getTransferCount(long assetId) {
    return Db.useDSLContext(ctx -> {
      return ctx.fetchCount(ctx.selectFrom(ASSET_TRANSFER).where(ASSET_TRANSFER.ASSET_ID.eq(assetId)));
    });
  }

  class SqlAssetTransfer extends AssetTransfer {

    SqlAssetTransfer(Record record) {
      super(record.get(ASSET_TRANSFER.ID),
            transferDbKeyFactory.newKey(record.get(ASSET_TRANSFER.ID)),
            record.get(ASSET_TRANSFER.ASSET_ID),
            record.get(ASSET_TRANSFER.HEIGHT),
            record.get(ASSET_TRANSFER.SENDER_ID),
            record.get(ASSET_TRANSFER.RECIPIENT_ID),
            record.get(ASSET_TRANSFER.QUANTITY),
            record.get(ASSET_TRANSFER.TIMESTAMP)
            );
    }
  }


}
