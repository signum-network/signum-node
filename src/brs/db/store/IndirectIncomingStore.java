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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            IndirectIncoming that = (IndirectIncoming) o;

            if (accountId != that.accountId) return false;
            if (transactionId != that.transactionId) return false;
            return height == that.height;
        }

        @Override
        public int hashCode() {
            int result = (int) (accountId ^ (accountId >>> 32));
            result = 31 * result + (int) (transactionId ^ (transactionId >>> 32));
            result = 31 * result + height;
            return result;
        }
    }
}
