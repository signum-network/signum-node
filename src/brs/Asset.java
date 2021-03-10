package brs;

import brs.Account.AccountAsset;
import brs.db.BurstKey;

public class Asset {

  private final long assetId;
  public final BurstKey dbKey;
  private final long accountId;
  private final String name;
  private final String description;
  private final long quantityQNT;
  private final long capabilityQNT;
  private final byte decimals;

  protected Asset(long assetId, BurstKey dbKey, long accountId, String name, String description, long quantityQNT, byte decimals) {
    this.assetId = assetId;
    this.dbKey = dbKey;
    this.accountId = accountId;
    this.name = name;
    this.description = description;
    this.capabilityQNT = quantityQNT;
    this.decimals = decimals;

    //quantity will be the circulating amount if the asset was issued by an AT, the total amount is moved to capabilityQNT
    if(brs.at.AT.getAT(accountId) != null ) {

      AccountAsset accountAsset = Burst.getStores().getAccountStore().getAccountAssetTable().getBy(brs.schema.Tables.ACCOUNT_ASSET.ACCOUNT_ID.eq(accountId)
                                                                                                  .and(brs.schema.Tables.ACCOUNT_ASSET.ASSET_ID.eq(assetId)));
      if(accountAsset != null ){
        this.quantityQNT = quantityQNT - accountAsset.getQuantityQNT();
      }
      else
        this.quantityQNT = quantityQNT;
    }
    else{
      this.quantityQNT = quantityQNT;
    }


  }

  public Asset(BurstKey dbKey, Transaction transaction, Attachment.ColoredCoinsAssetIssuance attachment) {
    this.dbKey = dbKey;
    this.assetId = transaction.getId();
    this.accountId = transaction.getSenderId();
    this.name = attachment.getName();
    this.description = attachment.getDescription();
    this.quantityQNT = attachment.getQuantityQNT();
    this.capabilityQNT = this.quantityQNT;
    this.decimals = attachment.getDecimals();
  }

  public long getId() {
    return assetId;
  }

  public long getAccountId() {
    return accountId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public long getQuantityQNT() {
    return quantityQNT;
  }

  public long getCapabilityQNT() {
    return capabilityQNT;
  }

  public byte getDecimals() {
    return decimals;
  }

}
