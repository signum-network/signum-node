package brs.db.sql;

import brs.Asset;
import brs.Burst;
import brs.db.BurstKey;
import brs.db.store.AssetStore;
import brs.db.store.DerivedTableManager;
import org.jooq.DSLContext;
import org.jooq.Record;

import java.util.Collection;

import static brs.schema.tables.Asset.ASSET;

public class SqlAssetStore implements AssetStore {

  private final BurstKey.LongKeyFactory<Asset> assetDbKeyFactory = new DbKey.LongKeyFactory<Asset>(ASSET.ID) {

      @Override
      public BurstKey newKey(Asset asset) {
        return asset.dbKey;
      }

    };
  private final EntitySqlTable<Asset> assetTable;

  public SqlAssetStore(DerivedTableManager derivedTableManager) {
    assetTable = new EntitySqlTable<Asset>("asset", brs.schema.Tables.ASSET, assetDbKeyFactory, derivedTableManager) {

      @Override
      protected Asset load(DSLContext ctx, Record record) {
        return new SqlAsset(record);
      }

      @Override
      protected void save(DSLContext ctx, Asset asset) {
        saveAsset(ctx, asset);
      }
    };
  }

  private void saveAsset(DSLContext ctx, Asset asset) {
    ctx.insertInto(ASSET).
      set(ASSET.ID, asset.getId()).
      set(ASSET.ACCOUNT_ID, asset.getAccountId()).
      set(ASSET.NAME, asset.getName()).
      set(ASSET.DESCRIPTION, asset.getDescription()).
      set(ASSET.QUANTITY, asset.getQuantityQNT()).
      set(ASSET.DECIMALS, asset.getDecimals()).
      set(ASSET.HEIGHT, Burst.getBlockchain().getHeight()).execute();
  }

  @Override
  public BurstKey.LongKeyFactory<Asset> getAssetDbKeyFactory() {
    return assetDbKeyFactory;
  }

  @Override
  public EntitySqlTable<Asset> getAssetTable() {
    return assetTable;
  }

  @Override
  public Collection<Asset> getAssetsIssuedBy(long accountId, int from, int to) {
    return assetTable.getManyBy(ASSET.ACCOUNT_ID.eq(accountId), from, to);
  }

  private class SqlAsset extends Asset {

    private SqlAsset(Record record) {
      super(record.get(ASSET.ID),
            assetDbKeyFactory.newKey(record.get(ASSET.ID)),
            record.get(ASSET.ACCOUNT_ID),
            record.get(ASSET.NAME),
            record.get(ASSET.DESCRIPTION),
            record.get(ASSET.QUANTITY),
            record.get(ASSET.DECIMALS)
            );
    }
  }
}
