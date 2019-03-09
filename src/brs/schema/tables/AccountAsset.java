/*
 * This file is generated by jOOQ.
*/
package brs.schema.tables;


import brs.schema.Db;
import brs.schema.Indexes;
import brs.schema.Keys;
import brs.schema.tables.records.AccountAssetRecord;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;

import javax.annotation.Generated;
import java.util.Arrays;
import java.util.List;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.10.5"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class AccountAsset extends TableImpl<AccountAssetRecord> {

    private static final long serialVersionUID = -676161848;

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
    public final TableField<AccountAssetRecord, Long> DB_ID = createField("db_id", org.jooq.impl.SQLDataType.BIGINT.nullable(false).identity(true), this, "");

    /**
     * The column <code>DB.account_asset.account_id</code>.
     */
    public final TableField<AccountAssetRecord, Long> ACCOUNT_ID = createField("account_id", org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>DB.account_asset.asset_id</code>.
     */
    public final TableField<AccountAssetRecord, Long> ASSET_ID = createField("asset_id", org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>DB.account_asset.quantity</code>.
     */
    public final TableField<AccountAssetRecord, Long> QUANTITY = createField("quantity", org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>DB.account_asset.unconfirmed_quantity</code>.
     */
    public final TableField<AccountAssetRecord, Long> UNCONFIRMED_QUANTITY = createField("unconfirmed_quantity", org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>DB.account_asset.height</code>.
     */
    public final TableField<AccountAssetRecord, Integer> HEIGHT = createField("height", org.jooq.impl.SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>DB.account_asset.latest</code>.
     */
    public final TableField<AccountAssetRecord, Boolean> LATEST = createField("latest", org.jooq.impl.SQLDataType.BOOLEAN.nullable(false).defaultValue(org.jooq.impl.DSL.field("1", org.jooq.impl.SQLDataType.BOOLEAN)), this, "");

    /**
     * Create a <code>DB.account_asset</code> table reference
     */
    public AccountAsset() {
        this(DSL.name("account_asset"), null);
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

    private AccountAsset(Name alias, Table<AccountAssetRecord> aliased) {
        this(alias, aliased, null);
    }

    private AccountAsset(Name alias, Table<AccountAssetRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, "");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema() {
        return Db.DB;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Index> getIndexes() {
        return Arrays.<Index>asList(Indexes.ACCOUNT_ASSET_ACCOUNT_ASSET_ID_HEIGHT_IDX, Indexes.ACCOUNT_ASSET_ACCOUNT_ASSET_QUANTITY_IDX, Indexes.ACCOUNT_ASSET_PRIMARY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Identity<AccountAssetRecord, Long> getIdentity() {
        return Keys.IDENTITY_ACCOUNT_ASSET;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UniqueKey<AccountAssetRecord> getPrimaryKey() {
        return Keys.KEY_ACCOUNT_ASSET_PRIMARY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UniqueKey<AccountAssetRecord>> getKeys() {
        return Arrays.<UniqueKey<AccountAssetRecord>>asList(Keys.KEY_ACCOUNT_ASSET_PRIMARY, Keys.KEY_ACCOUNT_ASSET_ACCOUNT_ASSET_ID_HEIGHT_IDX);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AccountAsset as(String alias) {
        return new AccountAsset(DSL.name(alias), this);
    }

    /**
     * {@inheritDoc}
     */
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
}
