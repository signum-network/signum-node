/*
 * Some portion .. Copyright (c) 2014 CIYAM Developers

 Distributed under the MIT/X11 software license, please refer to the file LICENSE.txt
*/

package brs.at;


import brs.*;
import brs.db.SignumKey;
import brs.db.TransactionDb;
import brs.db.VersionedEntityTable;
import brs.services.AccountService;
import brs.util.Convert;
import brs.util.Listener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class AT extends AtMachineState {

    private static final LinkedHashMap<Long, LinkedHashMap<Long, Long>> pendingFeesMap = new LinkedHashMap<>();
    private static final LinkedHashMap<Long, List<AtTransaction>> pendingTransactionsMap = new LinkedHashMap<>();
    private static final LinkedHashMap<Long, List<AtMapEntry>> pendingEntryUpdatesMap = new LinkedHashMap<>();
    public final SignumKey dbKey;
    private final String name;
    private final String description;
    private final int nextHeight;

    private AT(byte[] atId, byte[] creator, String name, String description, byte[] creationBytes, int height) {
        super(atId, creator, creationBytes, height);
        this.name = name.trim();
        this.description = description.trim();
        dbKey = atDbKeyFactory().newKey(AtApiHelper.getLong(atId));
        this.nextHeight = Signum.getBlockchain().getHeight();
    }

    public AT(byte[] atId, byte[] creator, String name, String description, short version,
              int height,
              byte[] stateBytes, int csize, int dsize, int cUserStackBytes, int cCallStackBytes,
              int creationBlockHeight, int sleepBetween, int nextHeight,
              boolean freezeWhenSameBalance, long minActivationAmount, byte[] apCode, long apCodeHashId) {
        super(atId, creator, version,
                height,
                stateBytes, csize, dsize, cUserStackBytes, cCallStackBytes,
                creationBlockHeight, sleepBetween,
                freezeWhenSameBalance, minActivationAmount, apCode, apCodeHashId);
        this.name = name.trim();
        this.description = description.trim();
        dbKey = atDbKeyFactory().newKey(AtApiHelper.getLong(atId));
        this.nextHeight = nextHeight;
    }

    public static void clearPending(int blockHeight, long generatorId) {
        // using height+id as hash
        pendingFeesMap.remove(blockHeight + generatorId);
        pendingTransactionsMap.remove(blockHeight + generatorId);
        pendingEntryUpdatesMap.remove(blockHeight + generatorId);
    }

    public static void addPendingFee(long id, long fee, int blockHeight, long generatorId) {
        long hash = blockHeight + generatorId;
        LinkedHashMap<Long, Long> pendingFees = pendingFeesMap.get(hash);
        if(pendingFees == null) {
          pendingFees = new LinkedHashMap<>();
          pendingFeesMap.put(hash, pendingFees);
        }
        pendingFees.put(id, fee);
    }

    public static void addPendingTransaction(AtTransaction atTransaction, int blockHeight, long generatorId) {
        long hash = blockHeight + generatorId;
        List<AtTransaction> pendingTransactions = pendingTransactionsMap.get(hash);
        if(pendingTransactions == null) {
          pendingTransactions = new ArrayList<>();
          pendingTransactionsMap.put(hash, pendingTransactions);
        }
        pendingTransactions.add(atTransaction);
    }

    public static void addMapUpdates(Collection<AtMapEntry> entries, int blockHeight, long generatorId) {
      if(entries == null)
        return;

      long hash = blockHeight + generatorId;
      List<AtMapEntry> pendingUpdates = pendingEntryUpdatesMap.get(hash);
      if(pendingUpdates == null) {
        pendingUpdates = new ArrayList<>();
        pendingEntryUpdatesMap.put(hash, pendingUpdates);
      }
      pendingUpdates.addAll(entries);
    }

    public static boolean findPendingTransaction(byte[] recipientId, int blockHeight, long generatorId) {
        long hash = blockHeight + generatorId;
        if(pendingTransactionsMap.get(hash) == null) {
          return false;
        }
        for (AtTransaction tx : pendingTransactionsMap.get(hash)) {
            if (Arrays.equals(recipientId, tx.getRecipientId())) {
                return true;
            }
        }
        return false;
    }

    private static SignumKey.LongKeyFactory<AT> atDbKeyFactory() {
        return Signum.getStores().getAtStore().getAtDbKeyFactory();
    }

    private static VersionedEntityTable<AT> atTable() {
        return Signum.getStores().getAtStore().getAtTable();
    }

    private static SignumKey.LongKeyFactory<ATState> atStateDbKeyFactory() {
        return Signum.getStores().getAtStore().getAtStateDbKeyFactory();
    }

    private static VersionedEntityTable<ATState> atStateTable() {
        return Signum.getStores().getAtStore().getAtStateTable();
    }

    public static AT getAT(byte[] id) {
        return getAT(AtApiHelper.getLong(id));
    }

    public static AT getAT(Long id) {
        return Signum.getStores().getAtStore().getAT(id, -1);
    }

    public static void addAT(Long atId, Long senderAccountId, String name, String description, byte[] creationBytes, int height, long atCodeHashId) {
        ByteBuffer bf = ByteBuffer.allocate(8 + 8);
        bf.order(ByteOrder.LITTLE_ENDIAN);

        bf.putLong(atId);

        byte[] id = new byte[8];

        bf.putLong(8, senderAccountId);

        byte[] creator = new byte[8];
        bf.clear();
        bf.get(id, 0, 8);
        bf.get(creator, 0, 8);

        AT at = new AT(id, creator, name, description, creationBytes, height);

        if(at.getApCodeHashId() == 0L)
          at.setApCodeHashId(atCodeHashId);

        AtController.resetMachine(at);

        atTable().insert(at);

        at.saveState();

        Account account = Account.getOrAddAccount(atId);
        account.apply(new byte[32], height);
    }

    public static List<Long> getOrderedATs() {
        return Signum.getStores().getAtStore().getOrderedATs();
    }

    public static byte[] compressState(byte[] stateBytes) {
        if (stateBytes == null || stateBytes.length == 0) {
            return null;
        }

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
                gzip.write(stateBytes);
                gzip.flush();
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static byte[] decompressState(byte[] stateBytes) {
        if (stateBytes == null || stateBytes.length == 0) {
            return null;
        }

        try (ByteArrayInputStream bis = new ByteArrayInputStream(stateBytes);
             GZIPInputStream gzip = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[256];
            int read;
            while ((read = gzip.read(buffer, 0, buffer.length)) > 0) {
                bos.write(buffer, 0, read);
            }
            bos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public void saveState() {
        int prevHeight = Signum.getBlockchain().getHeight();
        int newNextHeight = prevHeight + getWaitForNumberOfBlocks();
        ATState state = new ATState(AtApiHelper.getLong(this.getId()),
            getState(), newNextHeight, getSleepBetween(),
            getpBalance(), freezeOnSameBalance(), minActivationAmount());
        state.setPrevHeight(prevHeight);

        atStateTable().insert(state);
    }

    public static void saveMapUpdates(int blockHeight, long generatorId) {
      long hash = blockHeight+generatorId;
      List<AtMapEntry> updates = pendingEntryUpdatesMap.get(hash);
      if(updates != null) {
        VersionedEntityTable<AtMapEntry> table = Signum.getStores().getAtStore().getAtMapTable();
        for(AtMapEntry e : updates) {
          AtMapEntry cacheEntry = Signum.getStores().getAtStore().getMapValueEntry(e.getAtId(), e.getKey1(), e.getKey2());
          if(cacheEntry != null) {
            cacheEntry.setValue(e.getValue());
            e = cacheEntry;
          }
          table.insert(e);
        }
        updates.clear();
      }
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int nextHeight() {
        return nextHeight;
    }

    public static class HandleATBlockTransactionsListener implements Listener<Block> {
        private final AccountService accountService;
        private final TransactionDb transactionDb;

        public HandleATBlockTransactionsListener(AccountService accountService, TransactionDb transactionDb) {
            this.accountService = accountService;
            this.transactionDb = transactionDb;
        }

        @Override
        public void notify(Block block) {
            long hash = block.getHeight() + block.getGeneratorId();
            LinkedHashMap<Long, Long> pendingFees = pendingFeesMap.get(hash);
            if(pendingFees != null) {
              pendingFees.forEach((key, value) -> {
                Account atAccount = accountService.getAccount(key);
                accountService.addToBalanceAndUnconfirmedBalanceNQT(atAccount, -value);
              });
            }
            pendingFeesMap.remove(hash);

            List<Transaction> transactions = new ArrayList<>();
            List<AtTransaction> pendingTransactions = pendingTransactionsMap.get(hash);
            if(pendingTransactions != null) {
              for (AtTransaction atTransaction : pendingTransactions) {
                try {
                    Transaction transaction = atTransaction.build(block);

                    if (!transactionDb.hasTransaction(transaction.getIdCheckSignature(false))) {
                      atTransaction.apply(accountService, transaction);
                      transactions.add(transaction);
                    }
                } catch (SignumException.NotValidException e) {
                    throw new RuntimeException("Failed to construct AT payment transaction", e);
                }
              }
              pendingTransactionsMap.remove(hash);
            }

            if (!transactions.isEmpty()) {
                // WATCH: Replace after transactions are converted!
                transactionDb.saveTransactions(transactions);
            }
        }
    }

    public static class ATState {

        public final SignumKey dbKey;
        private final long atId;
        private byte[] state;
        private int prevHeight;
        private int nextHeight;
        private int sleepBetween;
        private long prevBalance;
        private boolean freezeWhenSameBalance;
        private long minActivationAmount;

        protected ATState(long atId, byte[] state,
                          int nextHeight, int sleepBetween, long prevBalance, boolean freezeWhenSameBalance, long minActivationAmount) {
            this.atId = atId;
            this.dbKey = atStateDbKeyFactory().newKey(this.atId);
            this.state = state;
            this.nextHeight = nextHeight;
            this.sleepBetween = sleepBetween;
            this.prevBalance = prevBalance;
            this.freezeWhenSameBalance = freezeWhenSameBalance;
            this.minActivationAmount = minActivationAmount;
        }


        public long getATId() {
            return atId;
        }

        public byte[] getState() {
            return state;
        }

        void setState(byte[] newState) {
            state = newState;
        }

        public int getPrevHeight() {
            return prevHeight;
        }

        void setPrevHeight(int prevHeight) {
            this.prevHeight = prevHeight;
        }

        public int getNextHeight() {
            return nextHeight;
        }

        void setNextHeight(int newNextHeight) {
            nextHeight = newNextHeight;
        }

        public int getSleepBetween() {
            return sleepBetween;
        }

        void setSleepBetween(int newSleepBetween) {
            this.sleepBetween = newSleepBetween;
        }

        public long getPrevBalance() {
            return prevBalance;
        }

        void setPrevBalance(long newPrevBalance) {
            this.prevBalance = newPrevBalance;
        }

        public boolean getFreezeWhenSameBalance() {
            return freezeWhenSameBalance;
        }

        void setFreezeWhenSameBalance(boolean newFreezeWhenSameBalance) {
            this.freezeWhenSameBalance = newFreezeWhenSameBalance;
        }

        public long getMinActivationAmount() {
            return minActivationAmount;
        }

        void setMinActivationAmount(long newMinActivationAmount) {
            this.minActivationAmount = newMinActivationAmount;
        }
    }

    public static class AtMapEntry {
      private long atId;
      private long key1;
      private long key2;
      private long value;

      public AtMapEntry(long atId, long key1, long key2, long value) {
        this.atId = atId;
        this.key1 = key1;
        this.key2 = key2;
        this.value = value;
      }

      public long getValue() {
        return value;
      }
      public void setValue(long value) {
        this.value = value;
      }
      public long getAtId() {
        return atId;
      }
      public long getKey1() {
        return key1;
      }
      public long getKey2() {
        return key2;
      }

    }
}
