package brs.assetexchange;

import brs.AssetTransfer;
import brs.AssetTransfer.Event;
import brs.Attachment;
import brs.Transaction;
import brs.db.BurstKey;
import brs.db.BurstKey.LongKeyFactory;
import brs.db.sql.EntitySqlTable;
import brs.db.store.AssetTransferStore;
import brs.util.Listener;
import brs.util.Listeners;

import java.util.Collection;

class AssetTransferServiceImpl {

  private final Listeners<AssetTransfer, Event> listeners = new Listeners<>();

  private final AssetTransferStore assetTransferStore;
  private final EntitySqlTable<AssetTransfer> assetTransferTable;
  private final LongKeyFactory<AssetTransfer> transferDbKeyFactory;


  public AssetTransferServiceImpl(AssetTransferStore assetTransferStore) {
    this.assetTransferStore = assetTransferStore;
    this.assetTransferTable = assetTransferStore.getAssetTransferTable();
    this.transferDbKeyFactory = assetTransferStore.getTransferDbKeyFactory();
  }

  public boolean addListener(Listener<AssetTransfer> listener, Event eventType) {
    return listeners.addListener(listener, eventType);
  }

  public boolean removeListener(Listener<AssetTransfer> listener, Event eventType) {
    return listeners.removeListener(listener, eventType);
  }

  public Collection<AssetTransfer> getAssetTransfers(long assetId, int from, int to) {
    return assetTransferStore.getAssetTransfers(assetId, from, to);
  }

  public Collection<AssetTransfer> getAccountAssetTransfers(long accountId, long assetId, int from, int to) {
    return assetTransferStore.getAccountAssetTransfers(accountId, assetId, from, to);
  }

  public int getTransferCount(long assetId) {
    return assetTransferStore.getTransferCount(assetId);
  }

  public int getAssetTransferCount() {
    return assetTransferTable.getCount();
  }

  public AssetTransfer addAssetTransfer(Transaction transaction, Attachment.ColoredCoinsAssetTransfer attachment) {
    BurstKey dbKey = transferDbKeyFactory.newKey(transaction.getId());
    AssetTransfer assetTransfer = new AssetTransfer(dbKey, transaction, attachment);
    assetTransferTable.insert(assetTransfer);
    listeners.notify(assetTransfer, Event.ASSET_TRANSFER);
    return assetTransfer;
  }

}
