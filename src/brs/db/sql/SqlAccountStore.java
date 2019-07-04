package brs.db.sql;

import brs.Account;
import brs.Burst;
import brs.db.VersionedBatchEntityTable;
import brs.db.VersionedEntityTable;
import brs.db.cache.DBCacheManagerImpl;
import brs.db.store.AccountStore;
import brs.db.store.DerivedTableManager;
import brs.util.Convert;
import org.jooq.*;
import org.slf4j.LoggerFactory;

import java.util.*;

import static brs.schema.Tables.*;

public class SqlAccountStore implements AccountStore {

  private static final DbKey.LongKeyFactory<Account> accountDbKeyFactory = new DbKey.LongKeyFactory<Account>(ACCOUNT.ID) {
      @Override
      public DbKey newKey(Account account) {
        return (DbKey) account.nxtKey;
      }
  };
  private static final DbKey.LongKeyFactory<Account.RewardRecipientAssignment> rewardRecipientAssignmentDbKeyFactory
    = new DbKey.LongKeyFactory<Account.RewardRecipientAssignment>(REWARD_RECIP_ASSIGN.ACCOUNT_ID) {
        @Override
        public DbKey newKey(Account.RewardRecipientAssignment assignment) {
          return (DbKey) assignment.burstKey;
        }
      };
  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SqlAccountStore.class);
  private static final DbKey.LinkKeyFactory<Account.AccountAsset> accountAssetDbKeyFactory
    = new DbKey.LinkKeyFactory<Account.AccountAsset>("account_id", "asset_id") {
        @Override
        public DbKey newKey(Account.AccountAsset accountAsset) {
          return (DbKey) accountAsset.burstKey;
        }
    };

  public SqlAccountStore(DerivedTableManager derivedTableManager, DBCacheManagerImpl dbCacheManager) {
    rewardRecipientAssignmentTable = new VersionedEntitySqlTable<Account.RewardRecipientAssignment>("reward_recip_assign", brs.schema.Tables.REWARD_RECIP_ASSIGN, rewardRecipientAssignmentDbKeyFactory, derivedTableManager) {

      @Override
      protected Account.RewardRecipientAssignment load(DSLContext ctx, Record rs) {
        return new SqlRewardRecipientAssignment(rs);
      }

      @Override
      protected void save(DSLContext ctx, Account.RewardRecipientAssignment assignment) {
        ctx.mergeInto(REWARD_RECIP_ASSIGN, REWARD_RECIP_ASSIGN.ACCOUNT_ID, REWARD_RECIP_ASSIGN.PREV_RECIP_ID, REWARD_RECIP_ASSIGN.RECIP_ID, REWARD_RECIP_ASSIGN.FROM_HEIGHT, REWARD_RECIP_ASSIGN.HEIGHT, REWARD_RECIP_ASSIGN.LATEST)
                .key(REWARD_RECIP_ASSIGN.ACCOUNT_ID, REWARD_RECIP_ASSIGN.HEIGHT)
                .values(assignment.accountId, assignment.getPrevRecipientId(), assignment.getRecipientId(), assignment.getFromHeight(), Burst.getBlockchain().getHeight(), true)
                .execute();
      }
    };

    accountAssetTable = new VersionedEntitySqlTable<Account.AccountAsset>("account_asset", brs.schema.Tables.ACCOUNT_ASSET, accountAssetDbKeyFactory, derivedTableManager) {
      private final List<SortField<?>> sort = initializeSort();

      private List<SortField<?>> initializeSort() {
        List<SortField<?>> sort = new ArrayList<>();
        sort.add(tableClass.field("quantity", Long.class).desc());
        sort.add(tableClass.field("account_id", Long.class).asc());
        sort.add(tableClass.field("asset_id", Long.class).asc());
        return Collections.unmodifiableList(sort);
      }

      @Override
      protected Account.AccountAsset load(DSLContext ctx, Record rs) {
        return new SQLAccountAsset(rs);
      }

      @Override
      protected void save(DSLContext ctx, Account.AccountAsset accountAsset) {
        ctx.mergeInto(ACCOUNT_ASSET, ACCOUNT_ASSET.ACCOUNT_ID, ACCOUNT_ASSET.ASSET_ID, ACCOUNT_ASSET.QUANTITY, ACCOUNT_ASSET.UNCONFIRMED_QUANTITY, ACCOUNT_ASSET.HEIGHT, ACCOUNT_ASSET.LATEST)
                .key(ACCOUNT_ASSET.ACCOUNT_ID, ACCOUNT_ASSET.ASSET_ID, ACCOUNT_ASSET.HEIGHT)
                .values(accountAsset.accountId, accountAsset.assetId, accountAsset.getQuantityQNT(), accountAsset.getUnconfirmedQuantityQNT(), Burst.getBlockchain().getHeight(), true)
                .execute();
      }

      @Override
      protected List<SortField<?>> defaultSort() {
        return sort;
      }
    };

    accountTable = new VersionedBatchEntitySqlTable<Account>("account", brs.schema.Tables.ACCOUNT, accountDbKeyFactory, derivedTableManager, dbCacheManager, Account.class) {
      @Override
      protected Account load(DSLContext ctx, Record rs) {
        return new SqlAccount(rs);
      }

      @Override
      protected void bulkInsert(DSLContext ctx, Collection<Account> accounts) {
        List<Query> accountQueries = new ArrayList<>();
        int height = Burst.getBlockchain().getHeight();
        for (Account account: accounts) {
          if (account == null) continue;
          accountQueries.add(
                  ctx.mergeInto(ACCOUNT, ACCOUNT.ID, ACCOUNT.HEIGHT, ACCOUNT.CREATION_HEIGHT, ACCOUNT.PUBLIC_KEY, ACCOUNT.KEY_HEIGHT, ACCOUNT.BALANCE,
                          ACCOUNT.UNCONFIRMED_BALANCE, ACCOUNT.FORGED_BALANCE, ACCOUNT.NAME, ACCOUNT.DESCRIPTION, ACCOUNT.LATEST)
                          .key(ACCOUNT.ID, ACCOUNT.HEIGHT)
                          .values(account.getId(), height, account.getCreationHeight(), account.getPublicKey(), account.getKeyHeight(),
                                  account.getBalanceNQT(), account.getUnconfirmedBalanceNQT(), account.getForgedBalanceNQT(), account.getName(), account.getDescription(), true)
          );
        }
        ctx.batch(accountQueries).execute();
      }
    };
  }

  private static Condition getAccountsWithRewardRecipientClause(final long id, final int height) {
    return REWARD_RECIP_ASSIGN.RECIP_ID.eq(id).and(REWARD_RECIP_ASSIGN.FROM_HEIGHT.le(height));
  }

  private final VersionedEntityTable<Account.AccountAsset> accountAssetTable;

  private final VersionedEntityTable<Account.RewardRecipientAssignment> rewardRecipientAssignmentTable;

  private final VersionedBatchEntityTable<Account> accountTable;

  @Override
  public VersionedBatchEntityTable<Account> getAccountTable() {
    return accountTable;
  }

  @Override
  public VersionedEntityTable<Account.RewardRecipientAssignment> getRewardRecipientAssignmentTable() {
    return rewardRecipientAssignmentTable;
  }

  @Override
  public DbKey.LongKeyFactory<Account.RewardRecipientAssignment> getRewardRecipientAssignmentKeyFactory() {
    return rewardRecipientAssignmentDbKeyFactory;
  }

  @Override
  public DbKey.LinkKeyFactory<Account.AccountAsset> getAccountAssetKeyFactory() {
    return accountAssetDbKeyFactory;
  }

  @Override
  public VersionedEntityTable<Account.AccountAsset> getAccountAssetTable() {
    return accountAssetTable;
  }

  @Override
  public int getAssetAccountsCount(long assetId) {
    return Db.useDSLContext(ctx -> {
      return ctx.selectCount().from(ACCOUNT_ASSET).where(ACCOUNT_ASSET.ASSET_ID.eq(assetId)).and(ACCOUNT_ASSET.LATEST.isTrue()).fetchOne(0, int.class);
    });
  }

  @Override
  public DbKey.LongKeyFactory<Account> getAccountKeyFactory() {
    return accountDbKeyFactory;
  }

  @Override
  public Collection<Account.RewardRecipientAssignment> getAccountsWithRewardRecipient(Long recipientId) {
    return getRewardRecipientAssignmentTable().getManyBy(getAccountsWithRewardRecipientClause(recipientId, Burst.getBlockchain().getHeight() + 1), 0, -1);
  }

  @Override
  public Collection<Account.AccountAsset> getAssets(int from, int to, Long id) {
    return getAccountAssetTable().getManyBy(ACCOUNT_ASSET.ACCOUNT_ID.eq(id), from, to);
  }

  @Override
  public Collection<Account.AccountAsset> getAssetAccounts(long assetId, int from, int to) {
    List<SortField<?>> sort = new ArrayList<>();
    sort.add(ACCOUNT_ASSET.field("quantity", Long.class).desc());
    sort.add(ACCOUNT_ASSET.field("account_id", Long.class).asc());
    return getAccountAssetTable().getManyBy(ACCOUNT_ASSET.ASSET_ID.eq(assetId), from, to, sort);
  }

  @Override
  public Collection<Account.AccountAsset> getAssetAccounts(long assetId, int height, int from, int to) {
    if (height < 0) {
      return getAssetAccounts(assetId, from, to);
    }

    List<SortField<?>> sort = new ArrayList<>();
    sort.add(ACCOUNT_ASSET.field("quantity", Long.class).desc());
    sort.add(ACCOUNT_ASSET.field("account_id", Long.class).asc());
    return getAccountAssetTable().getManyBy(ACCOUNT_ASSET.ASSET_ID.eq(assetId), height, from, to, sort);
  }

  @Override
  public boolean setOrVerify(Account acc, byte[] key, int height) {
    if (acc.getPublicKey() == null) {
      if (Db.isInTransaction()) {
        acc.setPublicKey(key);
        acc.setKeyHeight(-1);
        getAccountTable().insert(acc);
      }
      return true;
    } else if (Arrays.equals(acc.getPublicKey(), key)) {
      return true;
    } else if (acc.getKeyHeight() == -1) {
      if (logger.isInfoEnabled()) {
        logger.info("DUPLICATE KEY!!!");
        logger.info("Account key for {} was already set to a different one at the same height, current height is {}, rejecting new key", Convert.toUnsignedLong(acc.id), height);
      }
      return false;
    } else if (acc.getKeyHeight() >= height) {
      logger.info("DUPLICATE KEY!!!");
      if (Db.isInTransaction()) {
        if (logger.isInfoEnabled()) {
          logger.info("Changing key for account {} at height {}, was previously set to a different one at height {}", Convert.toUnsignedLong(acc.id), height, acc.getKeyHeight());
        }
        acc.setPublicKey(key);
        acc.setKeyHeight(height);
        getAccountTable().insert(acc);
      }
      return true;
    }
    if (logger.isInfoEnabled()) {
      logger.info("DUPLICATE KEY!!!");
      logger.info("Invalid key for account {} at height {}, was already set to a different one at height {}", Convert.toUnsignedLong(acc.id), height, acc.getKeyHeight());
    }
    return false;
  }

  static class SQLAccountAsset extends Account.AccountAsset {
    SQLAccountAsset(Record rs) {
      super(rs.get(ACCOUNT_ASSET.ACCOUNT_ID),
            rs.get(ACCOUNT_ASSET.ASSET_ID),
            rs.get(ACCOUNT_ASSET.QUANTITY),
            rs.get(ACCOUNT_ASSET.UNCONFIRMED_QUANTITY),
            accountAssetDbKeyFactory.newKey(rs.get(ACCOUNT_ASSET.ACCOUNT_ID), rs.get(ACCOUNT_ASSET.ASSET_ID))
            );
    }
  }

  class SqlAccount extends Account {
    SqlAccount(Record record) {
      super(record.get(ACCOUNT.ID), accountDbKeyFactory.newKey(record.get(ACCOUNT.ID)),
            record.get(ACCOUNT.CREATION_HEIGHT));
      this.setPublicKey(record.get(ACCOUNT.PUBLIC_KEY));
      this.setKeyHeight(record.get(ACCOUNT.KEY_HEIGHT));
      this.balanceNQT = record.get(ACCOUNT.BALANCE);
      this.unconfirmedBalanceNQT = record.get(ACCOUNT.UNCONFIRMED_BALANCE);
      this.forgedBalanceNQT = record.get(ACCOUNT.FORGED_BALANCE);
      this.name = record.get(ACCOUNT.NAME);
      this.description = record.get(ACCOUNT.DESCRIPTION);
    }
  }

  class SqlRewardRecipientAssignment extends Account.RewardRecipientAssignment {
    SqlRewardRecipientAssignment(Record record) {
      super(
              record.get(REWARD_RECIP_ASSIGN.ACCOUNT_ID),
              record.get(REWARD_RECIP_ASSIGN.PREV_RECIP_ID),
              record.get(REWARD_RECIP_ASSIGN.RECIP_ID),
              record.get(REWARD_RECIP_ASSIGN.FROM_HEIGHT),
              rewardRecipientAssignmentDbKeyFactory.newKey(record.get(REWARD_RECIP_ASSIGN.ACCOUNT_ID))
      );
    }
  }


}
