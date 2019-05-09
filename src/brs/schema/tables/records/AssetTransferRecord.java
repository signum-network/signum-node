/*
 * This file is generated by jOOQ.
 */
package brs.schema.tables.records;


import brs.schema.tables.AssetTransfer;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record8;
import org.jooq.Row8;
import org.jooq.impl.UpdatableRecordImpl;

import javax.annotation.Generated;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.11.11"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class AssetTransferRecord extends UpdatableRecordImpl<AssetTransferRecord> implements Record8<Long, Long, Long, Long, Long, Long, Integer, Integer> {

    private static final long serialVersionUID = 1466618958;

    /**
     * Setter for <code>DB.asset_transfer.db_id</code>.
     */
    public void setDbId(Long value) {
        set(0, value);
    }

    /**
     * Getter for <code>DB.asset_transfer.db_id</code>.
     */
    public Long getDbId() {
        return (Long) get(0);
    }

    /**
     * Setter for <code>DB.asset_transfer.id</code>.
     */
    public void setId(Long value) {
        set(1, value);
    }

    /**
     * Getter for <code>DB.asset_transfer.id</code>.
     */
    public Long getId() {
        return (Long) get(1);
    }

    /**
     * Setter for <code>DB.asset_transfer.asset_id</code>.
     */
    public void setAssetId(Long value) {
        set(2, value);
    }

    /**
     * Getter for <code>DB.asset_transfer.asset_id</code>.
     */
    public Long getAssetId() {
        return (Long) get(2);
    }

    /**
     * Setter for <code>DB.asset_transfer.sender_id</code>.
     */
    public void setSenderId(Long value) {
        set(3, value);
    }

    /**
     * Getter for <code>DB.asset_transfer.sender_id</code>.
     */
    public Long getSenderId() {
        return (Long) get(3);
    }

    /**
     * Setter for <code>DB.asset_transfer.recipient_id</code>.
     */
    public void setRecipientId(Long value) {
        set(4, value);
    }

    /**
     * Getter for <code>DB.asset_transfer.recipient_id</code>.
     */
    public Long getRecipientId() {
        return (Long) get(4);
    }

    /**
     * Setter for <code>DB.asset_transfer.quantity</code>.
     */
    public void setQuantity(Long value) {
        set(5, value);
    }

    /**
     * Getter for <code>DB.asset_transfer.quantity</code>.
     */
    public Long getQuantity() {
        return (Long) get(5);
    }

    /**
     * Setter for <code>DB.asset_transfer.timestamp</code>.
     */
    public void setTimestamp(Integer value) {
        set(6, value);
    }

    /**
     * Getter for <code>DB.asset_transfer.timestamp</code>.
     */
    public Integer getTimestamp() {
        return (Integer) get(6);
    }

    /**
     * Setter for <code>DB.asset_transfer.height</code>.
     */
    public void setHeight(Integer value) {
        set(7, value);
    }

    /**
     * Getter for <code>DB.asset_transfer.height</code>.
     */
    public Integer getHeight() {
        return (Integer) get(7);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public Record1<Long> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record8 type implementation
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public Row8<Long, Long, Long, Long, Long, Long, Integer, Integer> fieldsRow() {
        return (Row8) super.fieldsRow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Row8<Long, Long, Long, Long, Long, Long, Integer, Integer> valuesRow() {
        return (Row8) super.valuesRow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Long> field1() {
        return AssetTransfer.ASSET_TRANSFER.DB_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Long> field2() {
        return AssetTransfer.ASSET_TRANSFER.ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Long> field3() {
        return AssetTransfer.ASSET_TRANSFER.ASSET_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Long> field4() {
        return AssetTransfer.ASSET_TRANSFER.SENDER_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Long> field5() {
        return AssetTransfer.ASSET_TRANSFER.RECIPIENT_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Long> field6() {
        return AssetTransfer.ASSET_TRANSFER.QUANTITY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Integer> field7() {
        return AssetTransfer.ASSET_TRANSFER.TIMESTAMP;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Integer> field8() {
        return AssetTransfer.ASSET_TRANSFER.HEIGHT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long component1() {
        return getDbId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long component2() {
        return getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long component3() {
        return getAssetId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long component4() {
        return getSenderId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long component5() {
        return getRecipientId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long component6() {
        return getQuantity();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer component7() {
        return getTimestamp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer component8() {
        return getHeight();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long value1() {
        return getDbId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long value2() {
        return getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long value3() {
        return getAssetId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long value4() {
        return getSenderId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long value5() {
        return getRecipientId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long value6() {
        return getQuantity();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer value7() {
        return getTimestamp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer value8() {
        return getHeight();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AssetTransferRecord value1(Long value) {
        setDbId(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AssetTransferRecord value2(Long value) {
        setId(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AssetTransferRecord value3(Long value) {
        setAssetId(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AssetTransferRecord value4(Long value) {
        setSenderId(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AssetTransferRecord value5(Long value) {
        setRecipientId(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AssetTransferRecord value6(Long value) {
        setQuantity(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AssetTransferRecord value7(Integer value) {
        setTimestamp(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AssetTransferRecord value8(Integer value) {
        setHeight(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AssetTransferRecord values(Long value1, Long value2, Long value3, Long value4, Long value5, Long value6, Integer value7, Integer value8) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        value5(value5);
        value6(value6);
        value7(value7);
        value8(value8);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached AssetTransferRecord
     */
    public AssetTransferRecord() {
        super(AssetTransfer.ASSET_TRANSFER);
    }

    /**
     * Create a detached, initialised AssetTransferRecord
     */
    public AssetTransferRecord(Long dbId, Long id, Long assetId, Long senderId, Long recipientId, Long quantity, Integer timestamp, Integer height) {
        super(AssetTransfer.ASSET_TRANSFER);

        set(0, dbId);
        set(1, id);
        set(2, assetId);
        set(3, senderId);
        set(4, recipientId);
        set(5, quantity);
        set(6, timestamp);
        set(7, height);
    }
}
