package brs.db.sql;

import brs.Account;
import brs.Account.AccountAsset;
import brs.Asset;
import brs.Signum;
import brs.Transaction;
import brs.TransactionType;
import brs.db.VersionedBatchEntityTable;
import brs.db.VersionedEntityTable;
import brs.db.cache.DBCacheManagerImpl;
import brs.db.store.AccountStore;
import brs.db.store.DerivedTableManager;
import brs.fluxcapacitor.FluxValues;
import brs.props.Props;
import brs.schema.tables.records.AccountBalanceRecord;
import brs.util.Convert;
import signumj.crypto.SignumCrypto;

import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;

import static brs.schema.Tables.*;

public class SqlAccountStore implements AccountStore {

  private static final DbKey.LongKeyFactory<Account> accountDbKeyFactory = new DbKey.LongKeyFactory<Account>(ACCOUNT.ID) {
    @Override
    public DbKey newKey(Account account) {
      return (DbKey) account.nxtKey;
    }
  };
  private static final DbKey.LongKeyFactory<Account.Balance> accountBalanceDbKeyFactory = new DbKey.LongKeyFactory<Account.Balance>(ACCOUNT_BALANCE.ID) {
    @Override
    public DbKey newKey(Account.Balance account) {
      return (DbKey) account.nxtKey;
    }
  };
  private static final DbKey.LongKeyFactory<Account.RewardRecipientAssignment> rewardRecipientAssignmentDbKeyFactory
    = new DbKey.LongKeyFactory<Account.RewardRecipientAssignment>(REWARD_RECIP_ASSIGN.ACCOUNT_ID) {
    @Override
    public DbKey newKey(Account.RewardRecipientAssignment assignment) {
      return (DbKey) assignment.signumKey;
    }
  };
  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SqlAccountStore.class);
  private static final DbKey.LinkKeyFactory<Account.AccountAsset> accountAssetDbKeyFactory
    = new DbKey.LinkKeyFactory<Account.AccountAsset>("account_id", "asset_id") {
    @Override
    public DbKey newKey(Account.AccountAsset accountAsset) {
      return (DbKey) accountAsset.signumKey;
    }
  };

  private static final Integer InsertMaxBatchSize = Signum.getPropertyService().getInt(Props.DB_INSERT_BATCH_MAX_SIZE);

  private static final Set<String> PK_CHECKS = Collections
    .unmodifiableSet(new HashSet<>(Signum.getPropertyService().getStringList(Props.BRS_PK_CHECKS)));

  public SqlAccountStore(DerivedTableManager derivedTableManager, DBCacheManagerImpl dbCacheManager) {
    rewardRecipientAssignmentTable = new VersionedEntitySqlTable<Account.RewardRecipientAssignment>("reward_recip_assign", brs.schema.Tables.REWARD_RECIP_ASSIGN, rewardRecipientAssignmentDbKeyFactory, derivedTableManager) {

      @Override
      protected Account.RewardRecipientAssignment load(DSLContext ctx, Record rs) {
        return new SqlRewardRecipientAssignment(rs);
      }

      @Override
      protected void save(DSLContext ctx, Account.RewardRecipientAssignment assignment) {

        ctx.insertInto(REWARD_RECIP_ASSIGN,
            REWARD_RECIP_ASSIGN.ACCOUNT_ID, REWARD_RECIP_ASSIGN.PREV_RECIP_ID,
            REWARD_RECIP_ASSIGN.RECIP_ID, REWARD_RECIP_ASSIGN.FROM_HEIGHT,
            REWARD_RECIP_ASSIGN.HEIGHT, REWARD_RECIP_ASSIGN.LATEST)
          .values(assignment.getAccountId(), assignment.getPrevRecipientId(),
            assignment.getRecipientId(), assignment.getFromHeight(),
            Signum.getBlockchain().getHeight(), true)
          .onConflict(REWARD_RECIP_ASSIGN.ACCOUNT_ID, REWARD_RECIP_ASSIGN.HEIGHT)
          .doUpdate()
          .set(REWARD_RECIP_ASSIGN.PREV_RECIP_ID, assignment.getPrevRecipientId())
          .set(REWARD_RECIP_ASSIGN.RECIP_ID, assignment.getRecipientId())
          .set(REWARD_RECIP_ASSIGN.FROM_HEIGHT, assignment.getFromHeight())
          .set(REWARD_RECIP_ASSIGN.LATEST, true)
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
        ctx.insertInto(ACCOUNT_ASSET,
            ACCOUNT_ASSET.ACCOUNT_ID, ACCOUNT_ASSET.ASSET_ID,
            ACCOUNT_ASSET.QUANTITY, ACCOUNT_ASSET.UNCONFIRMED_QUANTITY,
            ACCOUNT_ASSET.HEIGHT, ACCOUNT_ASSET.LATEST)
          .values(accountAsset.getAccountId(), accountAsset.getAssetId(),
            accountAsset.getQuantityQnt(), accountAsset.getUnconfirmedQuantityQnt(),
            Signum.getBlockchain().getHeight(), true)
          .onConflict(ACCOUNT_ASSET.ACCOUNT_ID, ACCOUNT_ASSET.ASSET_ID, ACCOUNT_ASSET.HEIGHT)
          .doUpdate()
          .set(ACCOUNT_ASSET.QUANTITY, accountAsset.getQuantityQnt())
          .set(ACCOUNT_ASSET.UNCONFIRMED_QUANTITY, accountAsset.getUnconfirmedQuantityQnt())
          .set(ACCOUNT_ASSET.LATEST, true)
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
        int height = Signum.getBlockchain().getHeight();

        for (Account account : accounts) {
          if (account == null) continue;
          accountQueries.add(
            ctx.insertInto(ACCOUNT)
              .columns(ACCOUNT.ID, ACCOUNT.HEIGHT, ACCOUNT.CREATION_HEIGHT, ACCOUNT.PUBLIC_KEY, ACCOUNT.KEY_HEIGHT,
                ACCOUNT.NAME, ACCOUNT.DESCRIPTION, ACCOUNT.LATEST)
              .values(account.getId(), height, account.getCreationHeight(), account.getPublicKey(), account.getKeyHeight(),
                account.getName(), account.getDescription(), true)
              .onConflict(ACCOUNT.ID, ACCOUNT.HEIGHT)
              .doUpdate()
              .set(ACCOUNT.CREATION_HEIGHT, account.getCreationHeight())
              .set(ACCOUNT.PUBLIC_KEY, account.getPublicKey())
              .set(ACCOUNT.KEY_HEIGHT, account.getKeyHeight())
              .set(ACCOUNT.NAME, account.getName())
              .set(ACCOUNT.DESCRIPTION, account.getDescription())
              .set(ACCOUNT.LATEST, true)
          );
        }
        ctx.batch(accountQueries).execute();
      }
    };

    accountBalanceTable = new VersionedBatchEntitySqlTable<Account.Balance>("account_balance", brs.schema.Tables.ACCOUNT_BALANCE, accountBalanceDbKeyFactory, derivedTableManager, dbCacheManager, Account.Balance.class) {
      @Override
      protected Account.Balance load(DSLContext ctx, Record rs) {
        return new SqlAccountBalance(rs);
      }

      @Override
      protected void bulkInsert(DSLContext ctx, Collection<Account.Balance> accounts) {
        int height = Signum.getBlockchain().getHeight();
        Iterator<Account.Balance> iterator = accounts.iterator();
        List<Record6<Long, Integer, Long, Long, Long, Boolean>> rows = new ArrayList<>();
        while (iterator.hasNext()) {
          Account.Balance balance = iterator.next();
          if(balance == null) {
            continue;
          }

          rows.add(ctx.newRecord(ACCOUNT_BALANCE.ID, ACCOUNT_BALANCE.HEIGHT,
              ACCOUNT_BALANCE.BALANCE, ACCOUNT_BALANCE.UNCONFIRMED_BALANCE,
              ACCOUNT_BALANCE.FORGED_BALANCE, ACCOUNT.LATEST)
            .values(balance.getId(), height,
              balance.getBalanceNqt(), balance.getUnconfirmedBalanceNqt(),
              balance.getForgedBalanceNqt(), true));

          if(rows.size() >= InsertMaxBatchSize){
            ctx.insertInto(ACCOUNT_BALANCE, ACCOUNT_BALANCE.ID, ACCOUNT_BALANCE.HEIGHT,
                ACCOUNT_BALANCE.BALANCE, ACCOUNT_BALANCE.UNCONFIRMED_BALANCE,
                ACCOUNT_BALANCE.FORGED_BALANCE, ACCOUNT.LATEST)
              .valuesOfRecords(rows)
              .execute();
            rows.clear();
          }
        }

        if(!rows.isEmpty()){
          ctx.insertInto(ACCOUNT_BALANCE, ACCOUNT_BALANCE.ID, ACCOUNT_BALANCE.HEIGHT,
              ACCOUNT_BALANCE.BALANCE, ACCOUNT_BALANCE.UNCONFIRMED_BALANCE,
              ACCOUNT_BALANCE.FORGED_BALANCE, ACCOUNT.LATEST)
            .valuesOfRecords(rows)
            .execute();
        }
      }
    };
  }

  private static Condition getAccountsWithRewardRecipientClause(final long id, final int height) {
    return REWARD_RECIP_ASSIGN.RECIP_ID.eq(id).and(REWARD_RECIP_ASSIGN.FROM_HEIGHT.le(height));
  }

  private final VersionedEntityTable<Account.AccountAsset> accountAssetTable;

  private final VersionedEntityTable<Account.RewardRecipientAssignment> rewardRecipientAssignmentTable;

  private final VersionedBatchEntityTable<Account> accountTable;

  private final VersionedBatchEntityTable<Account.Balance> accountBalanceTable;

  @Override
  public VersionedBatchEntityTable<Account> getAccountTable() {
    return accountTable;
  }

  @Override
  public VersionedBatchEntityTable<Account.Balance> getAccountBalanceTable() {
    return accountBalanceTable;
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
  public long getAllAccountsBalance() {
    return Db.useDSLContext(ctx -> {
      return ctx.select(DSL.sum(ACCOUNT_BALANCE.BALANCE)).from(ACCOUNT_BALANCE).where(ACCOUNT_BALANCE.LATEST.isTrue())
        .fetchOneInto(long.class);
    });
  }

  @Override
  public int getAssetAccountsCount(Asset asset, long minimumQuantity, boolean ignoreTreasury, boolean unconfirmed) {
    return Db.useDSLContext(ctx -> {

      SelectConditionStep<Record1<Integer>> select = ctx.selectCount().from(ACCOUNT_ASSET)
        .where(ACCOUNT_ASSET.ASSET_ID.eq(asset.getId())).and(ACCOUNT_ASSET.LATEST.isTrue())
        .and(ACCOUNT_ASSET.ACCOUNT_ID.ne(0L));
      if (minimumQuantity > 0L) {
        select = select.and((unconfirmed ? ACCOUNT_ASSET.UNCONFIRMED_QUANTITY : ACCOUNT_ASSET.QUANTITY).ge(minimumQuantity));
      }
      if (ignoreTreasury) {
        Transaction transaction = Signum.getBlockchain().getTransaction(asset.getId());
        if (transaction != null) {
          List<Long> ignoredAccounts = ctx.select(TRANSACTION.RECIPIENT_ID).from(TRANSACTION)
            .where(TRANSACTION.TYPE.eq(TransactionType.TYPE_COLORED_COINS.getType()))
            .and(TRANSACTION.SUBTYPE.eq(TransactionType.SUBTYPE_COLORED_COINS_ADD_TREASURY_ACCOUNT))
            .and(TRANSACTION.REFERENCED_TRANSACTION_FULLHASH.eq(Convert.parseHexString(transaction.getFullHash())))
            .fetch().getValues(TRANSACTION.RECIPIENT_ID);
          select = select.and(ACCOUNT_ASSET.ACCOUNT_ID.notIn(ignoredAccounts));
        }
      }
      return select.fetchOne(0, int.class);
    });
  }

  @Override
  public long getAssetCirculatingSupply(Asset asset, boolean ignoreTreasury, boolean unconfirmed) {
    return Db.useDSLContext(ctx -> {

      SelectConditionStep<Record1<BigDecimal>> select = ctx.select(DSL.sum(
          unconfirmed ? ACCOUNT_ASSET.UNCONFIRMED_QUANTITY : ACCOUNT_ASSET.QUANTITY))
        .from(ACCOUNT_ASSET).where(ACCOUNT_ASSET.ASSET_ID.eq(asset.getId()))
        .and(ACCOUNT_ASSET.LATEST.isTrue())
        .and(ACCOUNT_ASSET.ACCOUNT_ID.ne(0L));

      if (ignoreTreasury) {
        Transaction transaction = Signum.getBlockchain().getTransaction(asset.getId());
        if (transaction != null) {
          List<Long> ignoredAccounts = ctx.select(TRANSACTION.RECIPIENT_ID).from(TRANSACTION)
            .where(TRANSACTION.TYPE.eq(TransactionType.TYPE_COLORED_COINS.getType()))
            .and(TRANSACTION.SUBTYPE.eq(TransactionType.SUBTYPE_COLORED_COINS_ADD_TREASURY_ACCOUNT))
            .and(TRANSACTION.REFERENCED_TRANSACTION_FULLHASH.eq(Convert.parseHexString(transaction.getFullHash())))
            .fetch().getValues(TRANSACTION.RECIPIENT_ID);
          select = select.and(ACCOUNT_ASSET.ACCOUNT_ID.notIn(ignoredAccounts));
        }
      }

      return select.fetchOne(0, long.class);
    });
  }

  @Override
  public DbKey.LongKeyFactory<Account> getAccountKeyFactory() {
    return accountDbKeyFactory;
  }

  @Override
  public DbKey.LongKeyFactory<Account.Balance> getAccountBalanceKeyFactory() {
    return accountBalanceDbKeyFactory;
  }

  @Override
  public Collection<Account.RewardRecipientAssignment> getAccountsWithRewardRecipient(Long recipientId) {
    return getRewardRecipientAssignmentTable().getManyBy(getAccountsWithRewardRecipientClause(recipientId, Signum.getBlockchain().getHeight() + 1), 0, -1);
  }

  @Override
  public Collection<Account.AccountAsset> getAssets(int from, int to, Long id) {
    return getAccountAssetTable().getManyBy(ACCOUNT_ASSET.ACCOUNT_ID.eq(id), from, to);
  }

  @Override
  public Account.AccountAsset getAccountAsset(Long accountId, Long assetId) {
    return getAccountAssetTable().getBy(ACCOUNT_ASSET.ACCOUNT_ID.eq(accountId).and(ACCOUNT_ASSET.ASSET_ID.eq(assetId)));
  }

  @Override
  public Collection<Account.AccountAsset> getAssetAccounts(Asset asset, boolean ignoreTreasury, long minimumQuantity, boolean unconfirmed, int from, int to) {
    List<SortField<?>> sort = new ArrayList<>();
    sort.add(ACCOUNT_ASSET.field("quantity", Long.class).desc());
    sort.add(ACCOUNT_ASSET.field("account_id", Long.class).asc());

    Condition condition = ACCOUNT_ASSET.ASSET_ID.eq(asset.getId());
    if (minimumQuantity > 0L) {
      condition = condition.and((unconfirmed ? ACCOUNT_ASSET.UNCONFIRMED_QUANTITY : ACCOUNT_ASSET.QUANTITY).ge(minimumQuantity));
    }
    ArrayList<Long> treasuryAccounts = new ArrayList<>();
    // the 0 account should also be removed from the circulating
    treasuryAccounts.add(0L);
    Transaction transaction = Signum.getBlockchain().getTransaction(asset.getId());
    if (transaction != null) {
      treasuryAccounts.addAll(Db.useDSLContext(ctx -> {
        return ctx.select(TRANSACTION.RECIPIENT_ID).from(TRANSACTION).where(TRANSACTION.TYPE.eq(TransactionType.TYPE_COLORED_COINS.getType()))
          .and(TRANSACTION.SUBTYPE.eq(TransactionType.SUBTYPE_COLORED_COINS_ADD_TREASURY_ACCOUNT))
          .and(TRANSACTION.REFERENCED_TRANSACTION_FULLHASH.eq(Convert.parseHexString(transaction.getFullHash())))
          .fetch().getValues(TRANSACTION.RECIPIENT_ID);
      }));
    }
    if (ignoreTreasury) {
      condition = condition.and(ACCOUNT_ASSET.ACCOUNT_ID.notIn(treasuryAccounts));
    }
    Collection<AccountAsset> accounts = getAccountAssetTable().getManyBy(condition, from, to, sort);

    // flag treasury accounts
    for (AccountAsset account : accounts) {
      if (treasuryAccounts.contains(account.getAccountId())) {
        account.setTreasury(true);
      }
    }

    return accounts;
  }

  @Override
  public boolean setOrVerify(Account acc, byte[] key, int height) {
    if (acc.getPublicKey() == null) {
      if (Signum.getFluxCapacitor().getValue(FluxValues.PK_FREEZE)
        && Signum.getBlockchain().getHeight() - acc.getCreationHeight() > Signum.getPropertyService().getInt(Props.PK_BLOCKS_PAST)) {
        logger.info("Setting a new key for an old account {} is not allowed, created at height {}", Convert.toUnsignedLong(acc.id), acc.getCreationHeight());
        return false;
      }

      if (Db.isInTransaction()) {
        acc.setPublicKey(key);
        acc.setKeyHeight(-1);
        getAccountTable().insert(acc);
      }
      return true;
    } else if (Signum.getFluxCapacitor().getValue(FluxValues.PK_FREEZE)
      && PK_CHECKS.contains(Convert.toHexString(SignumCrypto.getInstance().longToBytesLE(acc.getId())))) {
      logger.info("Using the key for account {}", Convert.toUnsignedLong(acc.id));
      return false;
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
      this.name = record.get(ACCOUNT.NAME);
      this.description = record.get(ACCOUNT.DESCRIPTION);
    }
  }

  class SqlAccountBalance extends Account.Balance {
    SqlAccountBalance(Record record) {
      super(record.get(ACCOUNT_BALANCE.ID));
      this.balanceNqt = record.get(ACCOUNT_BALANCE.BALANCE);
      this.unconfirmedBalanceNqt = record.get(ACCOUNT_BALANCE.UNCONFIRMED_BALANCE);
      this.forgedBalanceNqt = record.get(ACCOUNT_BALANCE.FORGED_BALANCE);
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
