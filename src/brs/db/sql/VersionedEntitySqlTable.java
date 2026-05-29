package brs.db.sql;

import brs.Signum;
import brs.db.SignumKey;
import brs.db.VersionedEntityTable;
import brs.db.store.DerivedTableManager;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class VersionedEntitySqlTable<T> extends EntitySqlTable<T> implements VersionedEntityTable<T> {

    private static final Logger logger = LoggerFactory.getLogger(VersionedEntitySqlTable.class);

    VersionedEntitySqlTable(String table, TableImpl<?> tableClass, SignumKey.Factory<T> dbKeyFactory,
            DerivedTableManager derivedTableManager) {
        super(table, tableClass, dbKeyFactory, true, derivedTableManager);
    }

    @Override
    public void rollback(int height) {
        rollback(table, tableClass, heightField, latestField, height, dbKeyFactory);
    }

    static void rollback(final String table, final TableImpl<?> tableClass, Field<Integer> heightField,
            Field<Boolean> latestField, final int height, final DbKey.Factory<?> dbKeyFactory) {
        if (!Db.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }

        Db.useDSLContext(ctx -> {
            // get dbKey's for entries whose stuff newer than height would be deleted, to
            // allow fixing
            // their latest flag of the "potential" remaining newest entry
            SelectQuery<Record> selectForDeleteQuery = ctx.selectQuery();
            selectForDeleteQuery.addFrom(tableClass);
            selectForDeleteQuery.addConditions(heightField.gt(height));
            for (String column : dbKeyFactory.getPKColumns()) {
                selectForDeleteQuery.addSelect(tableClass.field(column, Long.class));
            }
            selectForDeleteQuery.setDistinct(true);
            List<DbKey> dbKeys = selectForDeleteQuery.fetch(r -> (DbKey) dbKeyFactory.newKey(r));

            // delete all entries > height
            DeleteQuery deleteQuery = ctx.deleteQuery(tableClass);
            deleteQuery.addConditions(heightField.gt(height));
            deleteQuery.execute();

            // update latest flags for remaining entries, if there any remaining (per
            // deleted dbKey)
            for (DbKey dbKey : dbKeys) {
                SelectQuery<Record> selectMaxHeightQuery = ctx.selectQuery();
                selectMaxHeightQuery.addFrom(tableClass);
                selectMaxHeightQuery.addConditions(dbKey.getPKConditions(tableClass));
                selectMaxHeightQuery.addSelect(DSL.max(heightField));
                Integer maxHeight = selectMaxHeightQuery.fetchOne().get(DSL.max(heightField));

                if (maxHeight != null) {
                    UpdateQuery setLatestQuery = ctx.updateQuery(tableClass);
                    setLatestQuery.addConditions(dbKey.getPKConditions(tableClass));
                    setLatestQuery.addConditions(heightField.eq(maxHeight));
                    setLatestQuery.addValue(latestField, true);
                    setLatestQuery.execute();
                }
            }
        });
        Db.getCache(table).clear();
    }

    @Override
    public void trim(int height) {
        trim(tableClass, heightField, height, dbKeyFactory);
    }

    static void trim(
            final TableImpl<?> tableClass,
            final Field<Integer> heightField,
            final int trimHeight,
            final DbKey.Factory<?> dbKeyFactory) {

        if (!Db.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }

        final int selectBatchSize = 10_000;
        final int deleteBatchSize = 1_000;

        Db.useDSLContext(ctx -> {
            List<Field<Long>> pkFields = new ArrayList<>();
            for (String column : dbKeyFactory.getPKColumns()) {
                pkFields.add(tableClass.field(column, Long.class));
            }

            DbKey lastKey = null;
            int totalDeleted = 0;

            while (true) {
                SelectQuery<Record> selectMaxHeightQuery = ctx.selectQuery();
                selectMaxHeightQuery.addFrom(tableClass);
                selectMaxHeightQuery.addSelect(DSL.max(heightField).as("max_height"));
                pkFields.forEach(selectMaxHeightQuery::addSelect);
                pkFields.forEach(selectMaxHeightQuery::addGroupBy);
                selectMaxHeightQuery.addConditions(heightField.lt(trimHeight));
                selectMaxHeightQuery.addHaving(DSL.countDistinct(heightField).gt(1));

                // Keyset pagination
                if (lastKey != null) {
                    Condition pkCondition = null;
                    long[] lastValues = lastKey.getPKValues();
                    for (int i = 0; i < pkFields.size(); i++) {
                        Condition c = pkFields.get(i).gt(lastValues[i]);
                        for (int j = 0; j < i; j++) {
                            c = c.and(pkFields.get(j).eq(lastValues[j]));
                        }
                        pkCondition = (pkCondition == null) ? c : pkCondition.or(c);
                    }
                    selectMaxHeightQuery.addConditions(pkCondition);
                }

                selectMaxHeightQuery.addOrderBy(pkFields);
                selectMaxHeightQuery.addLimit(selectBatchSize);

                List<Record> records = selectMaxHeightQuery.fetch();
                if (records.isEmpty()) {
                    break;
                }

                DeleteQuery<?> deleteLowerHeightQuery = ctx.deleteQuery(tableClass);
                deleteLowerHeightQuery.addConditions(heightField.lt((Integer) null));
                for (String column : dbKeyFactory.getPKColumns()) {
                    Field<Long> pkField = tableClass.field(column, Long.class);
                    deleteLowerHeightQuery.addConditions(pkField.eq((Long) null));
                }

                BatchBindStep deleteBatch = ctx.batch(deleteLowerHeightQuery);
                int batchCount = 0;

                for (Record record : records) {
                    DbKey dbKey = (DbKey) dbKeyFactory.newKey(record);
                    int maxHeight = record.get("max_height", Integer.class);

                    Object[] binds = new Object[1 + pkFields.size()];
                    binds[0] = maxHeight;
                    long[] pkValues = dbKey.getPKValues();
                    for (int i = 0; i < pkValues.length; i++)
                        binds[i + 1] = pkValues[i];

                    deleteBatch.bind(binds);
                    batchCount++;
                    lastKey = dbKey;

                    if (batchCount % deleteBatchSize == 0) {
                        totalDeleted += Arrays.stream(deleteBatch.execute()).sum();
                        deleteBatch = ctx.batch(deleteLowerHeightQuery);
                    }
                }

                if (batchCount % deleteBatchSize != 0) {
                    totalDeleted += Arrays.stream(deleteBatch.execute()).sum();
                }

                logger.debug("Processed {} PKs in current batch, lastKey={}", records.size(), lastKey);
            }

            logger.debug("Total trimmed {} rows from {} below height {}", totalDeleted, tableClass.getName(),
                    trimHeight);
        });
    }

    @Override
    public boolean delete(T t) {
        if (t == null) {
            return false;
        }
        if (!Db.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }
        DbKey dbKey = (DbKey) dbKeyFactory.newKey(t);
        return Db.fetchWithDSLContext(ctx -> {
            try {
                SelectQuery<Record> countQuery = ctx.selectQuery();
                countQuery.addFrom(tableClass);
                countQuery.addConditions(dbKey.getPKConditions(tableClass));
                countQuery.addConditions(heightField.lt(Signum.getBlockchain().getHeight()));
                if (ctx.fetchCount(countQuery) > 0) {
                    UpdateQuery updateQuery = ctx.updateQuery(tableClass);
                    updateQuery.addValue(
                            latestField,
                            false);
                    updateQuery.addConditions(dbKey.getPKConditions(tableClass));
                    updateQuery.addConditions(latestField.isTrue());

                    updateQuery.execute();
                    save(ctx, t);
                    // delete after the save
                    updateQuery.execute();

                    return true;
                } else {
                    DeleteQuery deleteQuery = ctx.deleteQuery(tableClass);
                    deleteQuery.addConditions(dbKey.getPKConditions(tableClass));
                    return deleteQuery.execute() > 0;
                }
            } finally {
                Db.getCache(table).remove(dbKey);
            }
        });
    }
}
