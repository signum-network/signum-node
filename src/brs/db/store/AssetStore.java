package brs.db.store;

import brs.Asset;
import brs.db.BurstKey;
import brs.db.sql.EntitySqlTable;

import java.util.Collection;

public interface AssetStore {
  BurstKey.LongKeyFactory<Asset> getAssetDbKeyFactory();

  EntitySqlTable<Asset> getAssetTable();

  Collection<Asset> getAssetsIssuedBy(long accountId, int from, int to);
}
