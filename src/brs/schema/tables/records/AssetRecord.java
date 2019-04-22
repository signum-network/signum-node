/*
 * This file is generated by jOOQ.
 */
package brs.schema.tables.records;


import brs.schema.tables.Asset;
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
public class AssetRecord extends UpdatableRecordImpl<AssetRecord> implements Record8<Long, Long, Long, String, String, Long, Byte, Integer> {

    private static final long serialVersionUID = 1012252822;

    /**
     * Setter for <code>DB.asset.db_id</code>.
     */
    public void setDbId(Long value) {
        set(0, value);
    }

    /**
     * Getter for <code>DB.asset.db_id</code>.
     */
    public Long getDbId() {
        return (Long) get(0);
    }

    /**
     * Setter for <code>DB.asset.id</code>.
     */
    public void setId(Long value) {
        set(1, value);
    }

    /**
     * Getter for <code>DB.asset.id</code>.
     */
    public Long getId() {
        return (Long) get(1);
    }

    /**
     * Setter for <code>DB.asset.account_id</code>.
     */
    public void setAccountId(Long value) {
        set(2, value);
    }

    /**
     * Getter for <code>DB.asset.account_id</code>.
     */
    public Long getAccountId() {
        return (Long) get(2);
    }

    /**
     * Setter for <code>DB.asset.name</code>.
     */
    public void setName(String value) {
        set(3, value);
    }

    /**
     * Getter for <code>DB.asset.name</code>.
     */
    public String getName() {
        return (String) get(3);
    }

    /**
     * Setter for <code>DB.asset.description</code>.
     */
    public void setDescription(String value) {
        set(4, value);
    }

    /**
     * Getter for <code>DB.asset.description</code>.
     */
    public String getDescription() {
        return (String) get(4);
    }

    /**
     * Setter for <code>DB.asset.quantity</code>.
     */
    public void setQuantity(Long value) {
        set(5, value);
    }

    /**
     * Getter for <code>DB.asset.quantity</code>.
     */
    public Long getQuantity() {
        return (Long) get(5);
    }

    /**
     * Setter for <code>DB.asset.decimals</code>.
     */
    public void setDecimals(Byte value) {
        set(6, value);
    }

    /**
     * Getter for <code>DB.asset.decimals</code>.
     */
    public Byte getDecimals() {
        return (Byte) get(6);
    }

    /**
     * Setter for <code>DB.asset.height</code>.
     */
    public void setHeight(Integer value) {
        set(7, value);
    }

    /**
     * Getter for <code>DB.asset.height</code>.
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
    public Row8<Long, Long, Long, String, String, Long, Byte, Integer> fieldsRow() {
        return (Row8) super.fieldsRow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Row8<Long, Long, Long, String, String, Long, Byte, Integer> valuesRow() {
        return (Row8) super.valuesRow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Long> field1() {
        return Asset.ASSET.DB_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Long> field2() {
        return Asset.ASSET.ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Long> field3() {
        return Asset.ASSET.ACCOUNT_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<String> field4() {
        return Asset.ASSET.NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<String> field5() {
        return Asset.ASSET.DESCRIPTION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Long> field6() {
        return Asset.ASSET.QUANTITY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Byte> field7() {
        return Asset.ASSET.DECIMALS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Integer> field8() {
        return Asset.ASSET.HEIGHT;
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
        return getAccountId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String component4() {
        return getName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String component5() {
        return getDescription();
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
    public Byte component7() {
        return getDecimals();
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
        return getAccountId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String value4() {
        return getName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String value5() {
        return getDescription();
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
    public Byte value7() {
        return getDecimals();
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
    public AssetRecord value1(Long value) {
        setDbId(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AssetRecord value2(Long value) {
        setId(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AssetRecord value3(Long value) {
        setAccountId(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AssetRecord value4(String value) {
        setName(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AssetRecord value5(String value) {
        setDescription(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AssetRecord value6(Long value) {
        setQuantity(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AssetRecord value7(Byte value) {
        setDecimals(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AssetRecord value8(Integer value) {
        setHeight(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AssetRecord values(Long value1, Long value2, Long value3, String value4, String value5, Long value6, Byte value7, Integer value8) {
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
     * Create a detached AssetRecord
     */
    public AssetRecord() {
        super(Asset.ASSET);
    }

    /**
     * Create a detached, initialised AssetRecord
     */
    public AssetRecord(Long dbId, Long id, Long accountId, String name, String description, Long quantity, Byte decimals, Integer height) {
        super(Asset.ASSET);

        set(0, dbId);
        set(1, id);
        set(2, accountId);
        set(3, name);
        set(4, description);
        set(5, quantity);
        set(6, decimals);
        set(7, height);
    }
}
