/*
 * This file is generated by jOOQ.
 */
package brs.schema.tables;


import brs.schema.Db;
import brs.schema.Indexes;
import brs.schema.Keys;
import brs.schema.tables.records.TransactionRecord;

import java.util.Arrays;
import java.util.List;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Record;
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
public class Transaction extends TableImpl<TransactionRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>DB.transaction</code>
     */
    public static final Transaction TRANSACTION = new Transaction();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<TransactionRecord> getRecordType() {
        return TransactionRecord.class;
    }

    /**
     * The column <code>DB.transaction.db_id</code>.
     */
    public final TableField<TransactionRecord, Long> DB_ID = createField(DSL.name("db_id"), SQLDataType.BIGINT.nullable(false).identity(true), this, "");

    /**
     * The column <code>DB.transaction.id</code>.
     */
    public final TableField<TransactionRecord, Long> ID = createField(DSL.name("id"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>DB.transaction.deadline</code>.
     */
    public final TableField<TransactionRecord, Short> DEADLINE = createField(DSL.name("deadline"), SQLDataType.SMALLINT.nullable(false), this, "");

    /**
     * The column <code>DB.transaction.sender_public_key</code>.
     */
    public final TableField<TransactionRecord, byte[]> SENDER_PUBLIC_KEY = createField(DSL.name("sender_public_key"), SQLDataType.VARBINARY(32).nullable(false), this, "");

    /**
     * The column <code>DB.transaction.recipient_id</code>.
     */
    public final TableField<TransactionRecord, Long> RECIPIENT_ID = createField(DSL.name("recipient_id"), SQLDataType.BIGINT.defaultValue(DSL.field("NULL", SQLDataType.BIGINT)), this, "");

    /**
     * The column <code>DB.transaction.amount</code>.
     */
    public final TableField<TransactionRecord, Long> AMOUNT = createField(DSL.name("amount"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>DB.transaction.fee</code>.
     */
    public final TableField<TransactionRecord, Long> FEE = createField(DSL.name("fee"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>DB.transaction.height</code>.
     */
    public final TableField<TransactionRecord, Integer> HEIGHT = createField(DSL.name("height"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>DB.transaction.block_id</code>.
     */
    public final TableField<TransactionRecord, Long> BLOCK_ID = createField(DSL.name("block_id"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>DB.transaction.signature</code>.
     */
    public final TableField<TransactionRecord, byte[]> SIGNATURE = createField(DSL.name("signature"), SQLDataType.VARBINARY(64).defaultValue(DSL.field("NULL", SQLDataType.VARBINARY)), this, "");

    /**
     * The column <code>DB.transaction.timestamp</code>.
     */
    public final TableField<TransactionRecord, Integer> TIMESTAMP = createField(DSL.name("timestamp"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>DB.transaction.type</code>.
     */
    public final TableField<TransactionRecord, Byte> TYPE = createField(DSL.name("type"), SQLDataType.TINYINT.nullable(false), this, "");

    /**
     * The column <code>DB.transaction.subtype</code>.
     */
    public final TableField<TransactionRecord, Byte> SUBTYPE = createField(DSL.name("subtype"), SQLDataType.TINYINT.nullable(false), this, "");

    /**
     * The column <code>DB.transaction.sender_id</code>.
     */
    public final TableField<TransactionRecord, Long> SENDER_ID = createField(DSL.name("sender_id"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>DB.transaction.block_timestamp</code>.
     */
    public final TableField<TransactionRecord, Integer> BLOCK_TIMESTAMP = createField(DSL.name("block_timestamp"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>DB.transaction.full_hash</code>.
     */
    public final TableField<TransactionRecord, byte[]> FULL_HASH = createField(DSL.name("full_hash"), SQLDataType.VARBINARY(32).nullable(false), this, "");

    /**
     * The column <code>DB.transaction.referenced_transaction_fullhash</code>.
     */
    public final TableField<TransactionRecord, byte[]> REFERENCED_TRANSACTION_FULLHASH = createField(DSL.name("referenced_transaction_fullhash"), SQLDataType.VARBINARY(32).defaultValue(DSL.field("NULL", SQLDataType.VARBINARY)), this, "");

    /**
     * The column <code>DB.transaction.attachment_bytes</code>.
     */
    public final TableField<TransactionRecord, byte[]> ATTACHMENT_BYTES = createField(DSL.name("attachment_bytes"), SQLDataType.BLOB.defaultValue(DSL.field("NULL", SQLDataType.BLOB)), this, "");

    /**
     * The column <code>DB.transaction.version</code>.
     */
    public final TableField<TransactionRecord, Byte> VERSION = createField(DSL.name("version"), SQLDataType.TINYINT.nullable(false), this, "");

    /**
     * The column <code>DB.transaction.has_message</code>.
     */
    public final TableField<TransactionRecord, Boolean> HAS_MESSAGE = createField(DSL.name("has_message"), SQLDataType.BOOLEAN.nullable(false).defaultValue(DSL.field("0", SQLDataType.BOOLEAN)), this, "");

    /**
     * The column <code>DB.transaction.has_encrypted_message</code>.
     */
    public final TableField<TransactionRecord, Boolean> HAS_ENCRYPTED_MESSAGE = createField(DSL.name("has_encrypted_message"), SQLDataType.BOOLEAN.nullable(false).defaultValue(DSL.field("0", SQLDataType.BOOLEAN)), this, "");

    /**
     * The column <code>DB.transaction.has_public_key_announcement</code>.
     */
    public final TableField<TransactionRecord, Boolean> HAS_PUBLIC_KEY_ANNOUNCEMENT = createField(DSL.name("has_public_key_announcement"), SQLDataType.BOOLEAN.nullable(false).defaultValue(DSL.field("0", SQLDataType.BOOLEAN)), this, "");

    /**
     * The column <code>DB.transaction.ec_block_height</code>.
     */
    public final TableField<TransactionRecord, Integer> EC_BLOCK_HEIGHT = createField(DSL.name("ec_block_height"), SQLDataType.INTEGER.defaultValue(DSL.field("NULL", SQLDataType.INTEGER)), this, "");

    /**
     * The column <code>DB.transaction.ec_block_id</code>.
     */
    public final TableField<TransactionRecord, Long> EC_BLOCK_ID = createField(DSL.name("ec_block_id"), SQLDataType.BIGINT.defaultValue(DSL.field("NULL", SQLDataType.BIGINT)), this, "");

    /**
     * The column <code>DB.transaction.has_encrypttoself_message</code>.
     */
    public final TableField<TransactionRecord, Boolean> HAS_ENCRYPTTOSELF_MESSAGE = createField(DSL.name("has_encrypttoself_message"), SQLDataType.BOOLEAN.nullable(false).defaultValue(DSL.field("0", SQLDataType.BOOLEAN)), this, "");

    /**
     * The column <code>DB.transaction.cash_back_id</code>.
     */
    public final TableField<TransactionRecord, Long> CASH_BACK_ID = createField(DSL.name("cash_back_id"), SQLDataType.BIGINT.defaultValue(DSL.field("0", SQLDataType.BIGINT)), this, "");

    private Transaction(Name alias, Table<TransactionRecord> aliased) {
        this(alias, aliased, null);
    }

    private Transaction(Name alias, Table<TransactionRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>DB.transaction</code> table reference
     */
    public Transaction(String alias) {
        this(DSL.name(alias), TRANSACTION);
    }

    /**
     * Create an aliased <code>DB.transaction</code> table reference
     */
    public Transaction(Name alias) {
        this(alias, TRANSACTION);
    }

    /**
     * Create a <code>DB.transaction</code> table reference
     */
    public Transaction() {
        this(DSL.name("transaction"), null);
    }

    public <O extends Record> Transaction(Table<O> child, ForeignKey<O, TransactionRecord> key) {
        super(child, key, TRANSACTION);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Db.DB;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.asList(Indexes.TRANSACTION_TRANSACTION_BLOCK_TIMESTAMP_IDX, Indexes.TRANSACTION_TRANSACTION_RECIPIENT_ID_AMOUNT_HEIGHT_IDX, Indexes.TRANSACTION_TRANSACTION_RECIPIENT_ID_IDX, Indexes.TRANSACTION_TRANSACTION_SENDER_ID_IDX, Indexes.TRANSACTION_TRANSACTION_TYPE_SUBTYPE_IDX, Indexes.TRANSACTION_TX_CASH_BACK_INDEX, Indexes.TRANSACTION_TX_SENDER_TYPE);
    }

    @Override
    public Identity<TransactionRecord, Long> getIdentity() {
        return (Identity<TransactionRecord, Long>) super.getIdentity();
    }

    @Override
    public UniqueKey<TransactionRecord> getPrimaryKey() {
        return Keys.KEY_TRANSACTION_PRIMARY;
    }

    @Override
    public List<UniqueKey<TransactionRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_TRANSACTION_TRANSACTION_ID_IDX, Keys.KEY_TRANSACTION_TRANSACTION_FULL_HASH_IDX);
    }

    @Override
    public List<ForeignKey<TransactionRecord, ?>> getReferences() {
        return Arrays.asList(Keys.CONSTRAINT_FF);
    }

    private transient Block _block;

    public Block block() {
        if (_block == null)
            _block = new Block(this, Keys.CONSTRAINT_FF);

        return _block;
    }

    @Override
    public Transaction as(String alias) {
        return new Transaction(DSL.name(alias), this);
    }

    @Override
    public Transaction as(Name alias) {
        return new Transaction(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public Transaction rename(String name) {
        return new Transaction(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public Transaction rename(Name name) {
        return new Transaction(name, null);
    }
}
