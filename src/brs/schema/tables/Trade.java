/*
 * This file is generated by jOOQ.
 */
package brs.schema.tables;


import brs.schema.Db;
import brs.schema.Indexes;
import brs.schema.Keys;
import brs.schema.tables.records.TradeRecord;
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
        "jOOQ version:3.11.11"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Trade extends TableImpl<TradeRecord> {

    private static final long serialVersionUID = 1871748189;

    /**
     * The reference instance of <code>DB.trade</code>
     */
    public static final Trade TRADE = new Trade();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<TradeRecord> getRecordType() {
        return TradeRecord.class;
    }

    /**
     * The column <code>DB.trade.db_id</code>.
     */
    public final TableField<TradeRecord, Long> DB_ID = createField("db_id", org.jooq.impl.SQLDataType.BIGINT.nullable(false).identity(true), this, "");

    /**
     * The column <code>DB.trade.asset_id</code>.
     */
    public final TableField<TradeRecord, Long> ASSET_ID = createField("asset_id", org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>DB.trade.block_id</code>.
     */
    public final TableField<TradeRecord, Long> BLOCK_ID = createField("block_id", org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>DB.trade.ask_order_id</code>.
     */
    public final TableField<TradeRecord, Long> ASK_ORDER_ID = createField("ask_order_id", org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>DB.trade.bid_order_id</code>.
     */
    public final TableField<TradeRecord, Long> BID_ORDER_ID = createField("bid_order_id", org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>DB.trade.ask_order_height</code>.
     */
    public final TableField<TradeRecord, Integer> ASK_ORDER_HEIGHT = createField("ask_order_height", org.jooq.impl.SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>DB.trade.bid_order_height</code>.
     */
    public final TableField<TradeRecord, Integer> BID_ORDER_HEIGHT = createField("bid_order_height", org.jooq.impl.SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>DB.trade.seller_id</code>.
     */
    public final TableField<TradeRecord, Long> SELLER_ID = createField("seller_id", org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>DB.trade.buyer_id</code>.
     */
    public final TableField<TradeRecord, Long> BUYER_ID = createField("buyer_id", org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>DB.trade.quantity</code>.
     */
    public final TableField<TradeRecord, Long> QUANTITY = createField("quantity", org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>DB.trade.price</code>.
     */
    public final TableField<TradeRecord, Long> PRICE = createField("price", org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>DB.trade.timestamp</code>.
     */
    public final TableField<TradeRecord, Integer> TIMESTAMP = createField("timestamp", org.jooq.impl.SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>DB.trade.height</code>.
     */
    public final TableField<TradeRecord, Integer> HEIGHT = createField("height", org.jooq.impl.SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * Create a <code>DB.trade</code> table reference
     */
    public Trade() {
        this(DSL.name("trade"), null);
    }

    /**
     * Create an aliased <code>DB.trade</code> table reference
     */
    public Trade(String alias) {
        this(DSL.name(alias), TRADE);
    }

    /**
     * Create an aliased <code>DB.trade</code> table reference
     */
    public Trade(Name alias) {
        this(alias, TRADE);
    }

    private Trade(Name alias, Table<TradeRecord> aliased) {
        this(alias, aliased, null);
    }

    private Trade(Name alias, Table<TradeRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""));
    }

    public <O extends Record> Trade(Table<O> child, ForeignKey<O, TradeRecord> key) {
        super(child, key, TRADE);
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
        return Arrays.<Index>asList(Indexes.TRADE_PRIMARY, Indexes.TRADE_TRADE_ASK_BID_IDX, Indexes.TRADE_TRADE_ASSET_ID_IDX, Indexes.TRADE_TRADE_BUYER_ID_IDX, Indexes.TRADE_TRADE_SELLER_ID_IDX);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Identity<TradeRecord, Long> getIdentity() {
        return Keys.IDENTITY_TRADE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UniqueKey<TradeRecord> getPrimaryKey() {
        return Keys.KEY_TRADE_PRIMARY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UniqueKey<TradeRecord>> getKeys() {
        return Arrays.<UniqueKey<TradeRecord>>asList(Keys.KEY_TRADE_PRIMARY, Keys.KEY_TRADE_TRADE_ASK_BID_IDX);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Trade as(String alias) {
        return new Trade(DSL.name(alias), this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Trade as(Name alias) {
        return new Trade(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public Trade rename(String name) {
        return new Trade(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public Trade rename(Name name) {
        return new Trade(name, null);
    }
}