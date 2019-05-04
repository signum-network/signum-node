package brs.db.sql;

import brs.db.BurstIterator;
import org.jooq.DSLContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.NoSuchElementException;

class DbIterator<T> implements BurstIterator<T> {

  private final ResultSetReader<T> rsReader;
  private final ResultSet rs;
  private final DSLContext ctx;

  private boolean hasNext;

  public DbIterator(DSLContext ctx, ResultSet rs, ResultSetReader<T> rsReader) {
    this.ctx      = ctx;
    this.rsReader = rsReader;
    try {
      this.rs      = rs;
      this.hasNext = rs.next();
    }
    catch (SQLException e) {
      DbUtils.close(rs);
      throw new RuntimeException(e.toString(), e);
    }
  }

  @Override
  public boolean hasNext() {
    if (! hasNext) {
      DbUtils.close(rs);
    }
    return hasNext;
  }

  @Override
  public T next() {
    if (! hasNext) {
      DbUtils.close(rs);
      throw new NoSuchElementException();
    }
    try {
      T result = rsReader.get(ctx, rs);
      hasNext = rs.next();
      return result;
    } catch (Exception e) {
      DbUtils.close(rs);
      throw new RuntimeException(e.toString(), e);
    }
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("Removal not suported");
  }

  @Override
  public void close() {
    DbUtils.close(rs);
  }

}
