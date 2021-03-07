/*
 * Copyright (c) 2014 CIYAM Developers

 Distributed under the MIT/X11 software license, please refer to the file license.txt
 in the root project directory or http://www.opensource.org/licenses/mit-license.php.
*/

package brs.at;

import brs.crypto.Crypto;
import brs.Attachment.ATColoredCoinsAssetIssuance;
import brs.util.Convert;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.SortedMap;
import java.util.TreeMap;

public class AtTransaction {
    private static final SortedMap<Long, SortedMap<Long, AtTransaction>> all_AT_Txs = new TreeMap<>();
    private final byte[] message;
    private final long amount;
    private long assetId;
    private long assetAmount;
    private ATColoredCoinsAssetIssuance asset;
    private byte[] senderId;
    private byte[] recipientId;
    private byte[] fullHash;

    AtTransaction(byte[] senderId, byte[] recipientId, long amount, byte[] message) {
        this.senderId = senderId.clone();
        this.recipientId = recipientId.clone();
        this.amount = amount;
        this.message = (message != null) ? message.clone() : null;
        this.assetId = 0;
        this.assetAmount = 0;
        this.asset = null;
    }

    public static AtTransaction getATTransaction(Long atId, Long height) {
        if (all_AT_Txs.containsKey(atId)) {
            return all_AT_Txs.get(atId).get(height);
        }

        return null;
    }

    public Long getAmount() {
        return amount;
    }

    public byte[] getSenderId() {
        return senderId;
    }

    public byte[] getRecipientId() {
        return recipientId;
    }

    public byte[] getMessage() {
        return message;
    }

    public long getAssetId(){

        if(assetId == 0 && asset != null){

            int bytesLength = asset.getSize() + senderId.length;
            ByteBuffer buffer = ByteBuffer.allocate(bytesLength);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            asset.putBytes(buffer);
            buffer.put(senderId);

            fullHash = Crypto.sha256().digest(buffer.array());
            
            assetId = Convert.fullHashToId(fullHash);
        }

        return assetId;
    }

    public byte[] getFullHash(){
        return fullHash;
    }

    public long getAssetAmount(){
        return assetAmount;
    }

    public ATColoredCoinsAssetIssuance getAsset(){
        return asset;
    }

    public void addTransaction(long atId, Long height) {
        if (all_AT_Txs.containsKey(atId)) {
            all_AT_Txs.get(atId).put(height, this);
        } else {
            SortedMap<Long, AtTransaction> temp = new TreeMap<>();
            temp.put(height, this);
            all_AT_Txs.put(atId, temp);
        }
    }

    public void setAssetId(long assetId){
        this.assetId = assetId;
    }

    public void setAssetAmount(long assetAmount){
        this.assetAmount = assetAmount;
    }

    public long setAsset(byte[] bName, byte[] bDesc, long quantityQNT, byte decimals, int blockchainHeight){

        String name = Convert.toString(bName);
        String description = Convert.toString(bDesc);

        if (name == null) {
            return -1;
          }
      
        name = name.trim();
        if (name.length() < brs.Constants.MIN_ASSET_NAME_LENGTH || name.length() > brs.Constants.MAX_ASSET_NAME_LENGTH) {
        return -2;
        }
    
        if (!brs.util.TextUtils.isInAlphabet(name)) {
        return -3;
        }
    
        description = description.trim();
        if (description != null && description.length() > brs.Constants.MAX_ASSET_DESCRIPTION_LENGTH) {
        return -4;
        }

        if (quantityQNT <= 0 || quantityQNT > brs.Constants.MAX_ASSET_QUANTITY_QNT)
        return -5;

        if (decimals < 0 || decimals > 8)
        return -6;

        this.asset = new ATColoredCoinsAssetIssuance( name, description, quantityQNT, decimals, blockchainHeight);

        return 0;
    }
}
