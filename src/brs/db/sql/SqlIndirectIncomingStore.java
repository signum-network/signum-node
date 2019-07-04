package brs.db.sql;

import brs.db.BurstKey;
import brs.db.store.DerivedTableManager;
import brs.db.store.IndirectIncomingStore;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.Record;

import java.util.ArrayList;
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

            private Query getQuery(DSLContext ctx, IndirectIncoming indirectIncoming) {
                return ctx.mergeInto(INDIRECT_INCOMING, INDIRECT_INCOMING.ACCOUNT_ID, INDIRECT_INCOMING.TRANSACTION_ID, INDIRECT_INCOMING.HEIGHT)
                        .key(INDIRECT_INCOMING.ACCOUNT_ID, INDIRECT_INCOMING.TRANSACTION_ID)
                        .values(indirectIncoming.getAccountId(), indirectIncoming.getTransactionId(), indirectIncoming.getHeight());
            }

            @Override
            void save(DSLContext ctx, IndirectIncoming indirectIncoming) {
                getQuery(ctx, indirectIncoming).execute();
            }

            @Override
            void save(DSLContext ctx, IndirectIncoming[] indirectIncomings) {
                List<Query> queries = new ArrayList<>();
                for (IndirectIncoming indirectIncoming: indirectIncomings) {
                    queries.add(getQuery(ctx, indirectIncoming));
                }
                ctx.batch(queries).execute();
            }
        };
    }

    @Override
    public void addIndirectIncomings(Collection<IndirectIncoming> indirectIncomings) {
        Db.useDSLContext(ctx -> {
            indirectIncomingTable.save(ctx, indirectIncomings.toArray(new IndirectIncoming[0]));
        });
    }

    @Override
    public List<Long> getIndirectIncomings(long accountId, int from, int to) {
        return indirectIncomingTable.getManyBy(INDIRECT_INCOMING.ACCOUNT_ID.eq(accountId), from, to)
                .stream()
                .map(IndirectIncoming::getTransactionId)
                .collect(Collectors.toList());
    }
}
