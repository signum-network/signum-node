/*
 * This file is generated by jOOQ.
 */
package brs.schema.tables;


import brs.schema.Db;
import brs.schema.Indexes;
import brs.schema.Keys;
import brs.schema.tables.records.AccountAssetRecord;

import java.util.Arrays;
import java.util.List;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row7;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class AccountAsset extends TableImpl<AccountAssetRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>DB.account_asset</code>
     */
    public static final AccountAsset ACCOUNT_ASSET = new AccountAsset();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<AccountAssetRecord> getRecordType() {
        return AccountAssetRecord.class;
    }

    /**
     * The column <code>DB.account_asset.db_id</code>.
     */
    public final TableField<AccountAssetRecord, Long> DB_ID = createField(DSL.name("db_id"), SQLDataType.BIGINT.nullable(false).identity(true), this, "");

    /**
     * The column <code>DB.account_asset.account_id</code>.
     */
    public final TableField<AccountAssetRecord, Long> ACCOUNT_ID = createField(DSL.name("account_id"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>DB.account_asset.asset_id</code>.
     */
    public final TableField<AccountAssetRecord, Long> ASSET_ID = createField(DSL.name("asset_id"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>DB.account_asset.quantity</code>.
     */
    public final TableField<AccountAssetRecord, Long> QUANTITY = createField(DSL.name("quantity"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>DB.account_asset.unconfirmed_quantity</code>.
     */
    public final TableField<AccountAssetRecord, Long> UNCONFIRMED_QUANTITY = createField(DSL.name("unconfirmed_quantity"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>DB.account_asset.height</code>.
     */
    public final TableField<AccountAssetRecord, Integer> HEIGHT = createField(DSL.name("height"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>DB.account_asset.latest</code>.
     */
    public final TableField<AccountAssetRecord, Boolean> LATEST = createField(DSL.name("latest"), SQLDataType.BOOLEAN.nullable(false).defaultValue(DSL.field("1", SQLDataType.BOOLEAN)), this, "");

    private AccountAsset(Name alias, Table<AccountAssetRecord> aliased) {
        this(alias, aliased, null);
    }

    private AccountAsset(Name alias, Table<AccountAssetRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>DB.account_asset</code> table reference
     */
    public AccountAsset(String alias) {
        this(DSL.name(alias), ACCOUNT_ASSET);
    }

    /**
     * Create an aliased <code>DB.account_asset</code> table reference
     */
    public AccountAsset(Name alias) {
        this(alias, ACCOUNT_ASSET);
    }

    /**
     * Create a <code>DB.account_asset</code> table reference
     */
    public AccountAsset() {
        this(DSL.name("account_asset"), null);
    }

    public <O extends Record> AccountAsset(Table<O> child, ForeignKey<O, AccountAssetRecord> key) {
        super(child, key, ACCOUNT_ASSET);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Db.DB;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.asList(Indexes.ACCOUNT_ASSET_ACCOUNT_ASSET_HEIGHT_IDX, Indexes.ACCOUNT_ASSET_ACCOUNT_ASSET_QUANTITY_IDX);
    }

    @Override
    public Identity<AccountAssetRecord, Long> getIdentity() {
        return (Identity<AccountAssetRecord, Long>) super.getIdentity();
    }

    @Override
    public UniqueKey<AccountAssetRecord> getPrimaryKey() {
        return Keys.KEY_ACCOUNT_ASSET_PRIMARY;
    }

    @Override
    public List<UniqueKey<AccountAssetRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_ACCOUNT_ASSET_ACCOUNT_ASSET_ID_HEIGHT_IDX);
    }

    @Override
    public AccountAsset as(String alias) {
        return new AccountAsset(DSL.name(alias), this);
    }

    @Override
    public AccountAsset as(Name alias) {
        return new AccountAsset(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public AccountAsset rename(String name) {
        return new AccountAsset(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public AccountAsset rename(Name name) {
        return new AccountAsset(name, null);
    }

    // -------------------------------------------------------------------------
    // Row7 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row7<Long, Long, Long, Long, Long, Integer, Boolean> fieldsRow() {
        return (Row7) super.fieldsRow();
    }
}
