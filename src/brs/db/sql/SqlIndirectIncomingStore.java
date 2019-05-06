package brs.db.sql;

import brs.db.BurstKey;
import brs.db.store.DerivedTableManager;
import brs.db.store.IndirectIncomingStore;
import org.jooq.BatchBindStep;
import org.jooq.DSLContext;
import org.jooq.Record;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static brs.schema.Tables.INDIRECT_INCOMING;

public class SqlIndirectIncomingStore implements IndirectIncomingStore {

    private final EntitySqlTable<IndirectIncoming> indirectIncomingTable;

    public SqlIndirectIncomingStore(DerivedTableManager derivedTableManager) {
        BurstKey.LinkKeyFactory<IndirectIncoming> indirectIncomingDbKeyFactory = new DbKey.LinkKeyFactory<IndirectIncoming>("account_id", "transaction_id") {
            @Override
            public BurstKey newKey(IndirectIncoming indirectIncoming) {
                return newKey(indirectIncoming.getAccountId(), indirectIncoming.getTransactionId());
            }
        };

        this.indirectIncomingTable = new EntitySqlTable<IndirectIncoming>("indirect_incoming", INDIRECT_INCOMING, indirectIncomingDbKeyFactory, derivedTableManager) {
            @Override
            protected IndirectIncoming load(DSLContext ctx, Record rs) {
                return new IndirectIncoming(rs.get(INDIRECT_INCOMING.ACCOUNT_ID), rs.get(INDIRECT_INCOMING.TRANSACTION_ID), rs.get(INDIRECT_INCOMING.HEIGHT));
            }

            @Override
            void save(DSLContext ctx, IndirectIncoming indirectIncoming) {
                ctx.mergeInto(INDIRECT_INCOMING, INDIRECT_INCOMING.ACCOUNT_ID, INDIRECT_INCOMING.TRANSACTION_ID, INDIRECT_INCOMING.HEIGHT)
                        .key(INDIRECT_INCOMING.ACCOUNT_ID, INDIRECT_INCOMING.TRANSACTION_ID)
                        .values(indirectIncoming.getAccountId(), indirectIncoming.getTransactionId(), indirectIncoming.getHeight())
                        .execute();
            }

            @Override
            void save(DSLContext ctx, IndirectIncoming[] indirectIncomings) {
                BatchBindStep batch = ctx.batch(ctx.mergeInto(INDIRECT_INCOMING, INDIRECT_INCOMING.ACCOUNT_ID, INDIRECT_INCOMING.TRANSACTION_ID, INDIRECT_INCOMING.HEIGHT)
                                .key(INDIRECT_INCOMING.ACCOUNT_ID, INDIRECT_INCOMING.TRANSACTION_ID)
                                .values(0L, 0L, 0));
                for (IndirectIncoming indirectIncoming : indirectIncomings) {
                    batch.bind(indirectIncoming.getAccountId(), indirectIncoming.getTransactionId(), indirectIncoming.getHeight());
                }
                batch.execute();
            }
        };
    }

    @Override
    public void addIndirectIncomings(Collection<IndirectIncoming> indirectIncomings) {
        try (DSLContext ctx = Db.getDSLContext()) {
            indirectIncomingTable.save(ctx, indirectIncomings.toArray(new IndirectIncoming[0]));
        }
    }

    @Override
    public List<Long> getIndirectIncomings(long accountId, int from, int to) {
        return indirectIncomingTable.getManyBy(INDIRECT_INCOMING.ACCOUNT_ID.eq(accountId), from, to)
                .stream()
                .map(IndirectIncoming::getTransactionId)
                .collect(Collectors.toList());
    }
}
