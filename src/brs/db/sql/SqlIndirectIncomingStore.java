package brs.db.sql;

import brs.db.BurstIterator;
import brs.db.BurstKey;
import brs.db.store.DerivedTableManager;
import brs.db.store.IndirectIncomingStore;
import org.jooq.DSLContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
            protected IndirectIncoming load(DSLContext ctx, ResultSet rs) throws SQLException {
                return new IndirectIncoming(rs.getLong("account_id"), rs.getLong("transaction_id"), rs.getInt("height"));
            }

            @Override
            void save(DSLContext ctx, IndirectIncoming indirectIncoming) {
                ctx.insertInto(INDIRECT_INCOMING, INDIRECT_INCOMING.ACCOUNT_ID, INDIRECT_INCOMING.TRANSACTION_ID, INDIRECT_INCOMING.HEIGHT)
                        .values(indirectIncoming.getAccountId(), indirectIncoming.getTransactionId(), indirectIncoming.getHeight())
                        .execute();
            }
        };
    }

    @Override
    public void addIndirectIncomings(Collection<IndirectIncoming> indirectIncomings) {
        try (DSLContext ctx = Db.getDSLContext()) {
            indirectIncomings.forEach(indirectIncoming -> indirectIncomingTable.save(ctx, indirectIncoming));
        }
    }

    @Override
    public List<Long> getIndirectIncomings(long accountId, int from, int to) {
        BurstIterator<IndirectIncoming> result = indirectIncomingTable.getManyBy(INDIRECT_INCOMING.ACCOUNT_ID.eq(accountId), from, to);
        List<Long> transactionIDs = new ArrayList<>();
        result.forEachRemaining(indirectIncoming -> transactionIDs.add(indirectIncoming.getTransactionId()));
        return transactionIDs;
    }
}
