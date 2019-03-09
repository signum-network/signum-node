package brs.db.store;

import java.util.Collection;
import java.util.List;

public interface IndirectIncomingStore {
    void addIndirectIncomings(Collection<IndirectIncoming> indirectIncomings);
    List<Long> getIndirectIncomings(long accountId, int from, int to);

    class IndirectIncoming {
        private final long accountId;
        private final long transactionId;
        private final int height;

        public IndirectIncoming(long accountId, long transactionId, int height) {
            this.accountId = accountId;
            this.transactionId = transactionId;
            this.height = height;
        }

        public long getAccountId() {
            return accountId;
        }

        public long getTransactionId() {
            return transactionId;
        }

        public int getHeight() {
            return height;
        }
    }
}
