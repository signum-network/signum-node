package brs.db.store;

import brs.Alias;
import brs.db.BurstKey;
import brs.db.VersionedEntityTable;

import java.util.Collection;

public interface AliasStore {
  BurstKey.LongKeyFactory<Alias> getAliasDbKeyFactory();
  BurstKey.LongKeyFactory<Alias.Offer> getOfferDbKeyFactory();

  VersionedEntityTable<Alias> getAliasTable();

  VersionedEntityTable<Alias.Offer> getOfferTable();

  Collection<Alias> getAliasesByOwner(long accountId, int from, int to);

  Alias getAlias(String aliasName);
}
