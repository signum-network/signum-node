package brs.db.sql;

import brs.db.DerivedTable;
import brs.db.store.DerivedTableManager;
import org.jooq.Field;
import org.jooq.impl.TableImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DerivedSqlTable implements DerivedTable {
  private static final Logger logger = LoggerFactory.getLogger(DerivedSqlTable.class);
  final String table;
  final TableImpl<?> tableClass;

  final Field<Integer> heightField;
  final Field<Boolean> latestField;

  DerivedSqlTable(String table, TableImpl<?> tableClass, DerivedTableManager derivedTableManager) {
    this.table      = table;
    this.tableClass = tableClass;
    logger.trace("Creating derived table for {}", table);
    derivedTableManager.registerDerivedTable(this);
    this.heightField = tableClass.field("height", Integer.class);
    this.latestField = tableClass.field("latest", Boolean.class);
  }

  @Override
  public void rollback(int height) {
    if (!Db.isInTransaction()) {
      throw new IllegalStateException("Not in transaction");
    }
    Db.useDSLContext(ctx -> {
      ctx.delete(tableClass).where(heightField.gt(height)).execute();
    });
  }

  @Override
  public void truncate() {
    if (!Db.isInTransaction()) {
      throw new IllegalStateException("Not in transaction");
    }
    Db.useDSLContext(ctx -> {
      ctx.delete(tableClass).execute();
    });
  }

  @Override
  public void trim(int height) {
    //nothing to trim
  }

  @Override
  public void finish() {

  }

  @Override
  public void optimize() {
    Db.optimizeTable(table);
  }
}
