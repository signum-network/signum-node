package brs.db.store;

import brs.db.BlockDb;
import brs.db.PeerDb;
import brs.db.TransactionDb;

public interface Dbs {

  BlockDb getBlockDb();

  TransactionDb getTransactionDb();

  PeerDb getPeerDb();

}
