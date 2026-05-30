package brs.db.sql;

import brs.Signum;
import brs.Subscription;
import brs.db.SignumKey;
import brs.db.VersionedEntityTable;
import brs.db.store.DerivedTableManager;
import brs.db.store.SubscriptionStore;
import brs.props.Props;

import org.jooq.*;
import org.jooq.Record;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static brs.schema.Tables.SUBSCRIPTION;

public class SqlSubscriptionStore implements SubscriptionStore {

    private final SignumKey.LongKeyFactory<Subscription> subscriptionDbKeyFactory = new DbKey.LongKeyFactory<Subscription>(
            SUBSCRIPTION.ID) {
        @Override
        public SignumKey newKey(Subscription subscription) {
            return subscription.dbKey;
        }
    };

    private final VersionedEntityTable<Subscription> subscriptionTable;

    private static final int InsertMaxBatchSize = Signum.getPropertyService().getInt(Props.DB_INSERT_BATCH_MAX_SIZE);

    public SqlSubscriptionStore(DerivedTableManager derivedTableManager) {
        subscriptionTable = new VersionedEntitySqlTable<Subscription>("subscription", brs.schema.Tables.SUBSCRIPTION,
                subscriptionDbKeyFactory, derivedTableManager) {
            @Override
            protected Subscription load(DSLContext ctx, Record rs) {
                return new SqlSubscription(rs);
            }

            @Override
            protected void save(DSLContext ctx, Subscription subscription) {
                insertSubscription(ctx, subscription).execute();
            }

            @Override
            protected List<SortField<?>> defaultSort() {
                List<SortField<?>> sort = new ArrayList<>();
                sort.add(tableClass.field("time_next", Integer.class).asc());
                sort.add(tableClass.field("id", Long.class).asc());
                return sort;
            }
        };
    }

    private static Condition getByParticipantClause(final long id) {
        return SUBSCRIPTION.SENDER_ID.eq(id).or(SUBSCRIPTION.RECIPIENT_ID.eq(id));
    }

    private static Condition getUpdateOnBlockClause(final int timestamp) {
        return SUBSCRIPTION.TIME_NEXT.le(timestamp);
    }

    @Override
    public SignumKey.LongKeyFactory<Subscription> getSubscriptionDbKeyFactory() {
        return subscriptionDbKeyFactory;
    }

    @Override
    public VersionedEntityTable<Subscription> getSubscriptionTable() {
        return subscriptionTable;
    }

    @Override
    public Collection<Subscription> getSubscriptionsByParticipant(Long accountId) {
        return subscriptionTable.getManyBy(getByParticipantClause(accountId), 0, -1);
    }

    @Override
    public Collection<Subscription> getIdSubscriptions(Long accountId) {
        return subscriptionTable.getManyBy(SUBSCRIPTION.SENDER_ID.eq(accountId), 0, -1);
    }

    @Override
    public Collection<Subscription> getSubscriptionsToId(Long accountId) {
        return subscriptionTable.getManyBy(SUBSCRIPTION.RECIPIENT_ID.eq(accountId), 0, -1);
    }

    @Override
    public Collection<Subscription> getUpdateSubscriptions(int timestamp) {
        return subscriptionTable.getManyBy(getUpdateOnBlockClause(timestamp), 0, -1);
    }

    private Query insertSubscription(DSLContext ctx, Subscription subscription) {
        return ctx
                .insertInto(SUBSCRIPTION, SUBSCRIPTION.ID, SUBSCRIPTION.SENDER_ID, SUBSCRIPTION.RECIPIENT_ID,
                        SUBSCRIPTION.AMOUNT, SUBSCRIPTION.FREQUENCY, SUBSCRIPTION.TIME_NEXT, SUBSCRIPTION.HEIGHT,
                        SUBSCRIPTION.LATEST)
                .values(subscription.id, subscription.senderId, subscription.recipientId, subscription.amountNQT,
                        subscription.frequency, subscription.getTimeNext(), Signum.getBlockchain().getHeight(), true);
    }

    private class SqlSubscription extends Subscription {
        SqlSubscription(Record record) {
            super(
                    record.get(SUBSCRIPTION.SENDER_ID),
                    record.get(SUBSCRIPTION.RECIPIENT_ID),
                    record.get(SUBSCRIPTION.ID),
                    record.get(SUBSCRIPTION.AMOUNT),
                    record.get(SUBSCRIPTION.FREQUENCY),
                    record.get(SUBSCRIPTION.TIME_NEXT),
                    subscriptionDbKeyFactory.newKey(record.get(SUBSCRIPTION.ID)));
        }
    }

    @Override
    public void saveSubscriptions(Collection<Subscription> subscriptions) {
        if (!subscriptions.isEmpty()) {
            Db.useDSLContext(ctx -> {
                List<Subscription> subscriptionList = new ArrayList<>(subscriptions);
                int totalSize = subscriptionList.size();

                for (int i = 0; i < totalSize; i += InsertMaxBatchSize) {
                    int end = Math.min(i + InsertMaxBatchSize, totalSize);
                    List<Subscription> batch = subscriptionList.subList(i, end);

                    // remove the latest flag for past entries
                    List<Long> ids = batch.stream().map(s -> s.id).collect(Collectors.toList());
                    ctx.update(SUBSCRIPTION)
                            .set(SUBSCRIPTION.LATEST, false)
                            .where(SUBSCRIPTION.ID.in(ids).and(SUBSCRIPTION.LATEST.isTrue()))
                            .execute();

                    BatchBindStep insertBatch = ctx.batch(
                            ctx.insertInto(SUBSCRIPTION, SUBSCRIPTION.ID, SUBSCRIPTION.SENDER_ID,
                                    SUBSCRIPTION.RECIPIENT_ID,
                                    SUBSCRIPTION.AMOUNT, SUBSCRIPTION.FREQUENCY, SUBSCRIPTION.TIME_NEXT,
                                    SUBSCRIPTION.HEIGHT, SUBSCRIPTION.LATEST)
                                    .values((Long) null, null, null, null, null, null, null, null));
                    for (Subscription subscription : batch) {
                        insertBatch.bind(
                                subscription.id, subscription.senderId, subscription.recipientId,
                                subscription.amountNQT, subscription.frequency,
                                subscription.getTimeNext(), Signum.getBlockchain().getHeight(), true);
                    }
                    insertBatch.execute();
                }
            });
        }
    }
}
