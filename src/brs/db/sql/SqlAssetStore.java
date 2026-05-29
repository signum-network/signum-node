package brs.db.sql;

import brs.Asset;
import brs.Signum;
import brs.db.SignumKey;
import brs.db.store.AssetStore;
import brs.db.store.DerivedTableManager;
import brs.schema.tables.records.AssetRecord;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SelectQuery;
import org.jooq.impl.DSL;

import java.util.Collection;

import static brs.schema.Tables.ASSET;

public class SqlAssetStore implements AssetStore {

    private final SignumKey.LongKeyFactory<Asset> assetDbKeyFactory = new DbKey.LongKeyFactory<Asset>(ASSET.ID) {

        @Override
        public SignumKey newKey(Asset asset) {
            return asset.dbKey;
        }

    };
    private final EntitySqlTable<Asset> assetTable;

    public SqlAssetStore(DerivedTableManager derivedTableManager) {
        assetTable = new EntitySqlTable<Asset>("asset", brs.schema.Tables.ASSET, assetDbKeyFactory,
                derivedTableManager) {

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
        ctx.insertInto(ASSET).set(ASSET.ID, asset.getId()).set(ASSET.ACCOUNT_ID, asset.getAccountId())
                .set(ASSET.NAME, asset.getName()).set(ASSET.DESCRIPTION, asset.getDescription())
                .set(ASSET.QUANTITY, asset.getQuantityQnt()).set(ASSET.DECIMALS, asset.getDecimals())
                .set(ASSET.MINTABLE, asset.getMintable()).set(ASSET.HEIGHT, Signum.getBlockchain().getHeight())
                .execute();
    }

    @Override
    public SignumKey.LongKeyFactory<Asset> getAssetDbKeyFactory() {
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

    @Override
    public Asset getAsset(long assetId) {
        return assetTable.getBy(ASSET.ID.eq(assetId));
    }

    @Override
    public Collection<Asset> getAssetsByName(String name, int from, int to) {
        return Db.fetchWithDSLContext(ctx -> {
            SelectQuery<AssetRecord> query = ctx.selectFrom(ASSET)
                    .where(DSL.upper(ASSET.NAME).like("%" + name.toUpperCase() + "%")).getQuery();
            query.addOrderBy(ASSET.HEIGHT.asc(), ASSET.ID);
            DbUtils.applyLimits(query, from, to);

            return getAssets(ctx, query.fetch());
        });
    }

    public Collection<Asset> getAssets(DSLContext ctx, Result<AssetRecord> rs) {
        return rs.map(r -> {
            return new SqlAsset(r);
        });
    }

    private class SqlAsset extends Asset {

        private SqlAsset(Record record) {
            super(record.get(ASSET.ID),
                    assetDbKeyFactory.newKey(record.get(ASSET.ID)),
                    record.get(ASSET.ACCOUNT_ID),
                    record.get(ASSET.NAME),
                    record.get(ASSET.DESCRIPTION),
                    record.get(ASSET.QUANTITY),
                    record.get(ASSET.DECIMALS),
                    record.get(ASSET.MINTABLE));
        }
    }
}
