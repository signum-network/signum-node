package brs.db.sql;

import org.jooq.SelectQuery;

public final class DbUtils {

  private DbUtils() {
  } // never

  public static void close(AutoCloseable... closeables) {
    for (AutoCloseable closeable : closeables) {
      if (closeable != null) {
        try {
          closeable.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  public static void applyLimits(SelectQuery query, int from, int to ) {
    int limit = to >= 0 && to >= from && to < Integer.MAX_VALUE ? to - from + 1 : 0;
    if (limit > 0 && from > 0) {
      query.addLimit(from, limit);
    }
    else if (limit > 0) {
      query.addLimit(limit);
    }
    else if (from > 0) {
      query.addOffset(from);
    }
  }
}
