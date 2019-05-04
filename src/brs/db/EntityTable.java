package brs.db;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SelectQuery;
import org.jooq.SortField;

import java.util.List;

public interface EntityTable<T> extends DerivedTable {
  void checkAvailable(int height);

  T get(BurstKey dbKey);

  T get(BurstKey dbKey, int height);

  T getBy(Condition condition);

  T getBy(Condition condition, int height);

  BurstIterator<T> getManyBy(Condition condition, int from, int to);

  BurstIterator<T> getManyBy(Condition condition, int from, int to, List<SortField<?>> sort);

  BurstIterator<T> getManyBy(Condition condition, int height, int from, int to);

  BurstIterator<T> getManyBy(Condition condition, int height, int from, int to, List<SortField<?>> sort);

  BurstIterator<T> getManyBy(DSLContext ctx, SelectQuery query, boolean cache);

  BurstIterator<T> getAll(int from, int to);

  BurstIterator<T> getAll(int from, int to, List<SortField<?>> sort);

  BurstIterator<T> getAll(int height, int from, int to);

  BurstIterator<T> getAll(int height, int from, int to, List<SortField<?>> sort);

  int getCount();

  int getRowCount();

  void insert(T t);

  @Override
  void rollback(int height);

  @Override
  void truncate();
}
