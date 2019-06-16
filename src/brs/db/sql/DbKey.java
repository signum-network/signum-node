package brs.db.sql;

import brs.db.BurstKey;
import brs.util.StringUtils;
import org.jooq.*;

import java.util.ArrayList;
import java.util.Collection;

public interface DbKey extends BurstKey {

  abstract class Factory<T> implements BurstKey.Factory<T> {

    private final String pkClause;
    private final String[] pkColumns;
    private final String selfJoinClause;
    private final int pkVariables;

    Factory(String pkClause, String[] pkColumns, String selfJoinClause) {
      this.pkClause = pkClause;
      this.pkColumns = pkColumns;
      this.selfJoinClause = selfJoinClause;
      this.pkVariables = StringUtils.countMatches(pkClause, "?");
    }

    public final String getPKClause() {
      return pkClause;
    }

    public final String[] getPKColumns() {
      return pkColumns;
    }

    // expects tables to be named a and b
    public final String getSelfJoinClause() {
      return selfJoinClause;
    }

    /** @return The number of variables in PKClause */
    public int getPkVariables() {
      return pkVariables;
    }

    public abstract void applySelfJoin(SelectQuery<Record> query, Table<?> queryTable, Table<?> otherTable);
  }

  Collection<Condition> getPKConditions(Table<?> tableClass);

  long[] getPKValues();

  abstract class LongKeyFactory<T> extends Factory<T> implements BurstKey.LongKeyFactory<T> {

    private final Field<Long> idColumn;

    public LongKeyFactory(Field<Long> idColumn) {
      super(" WHERE " + idColumn.getName() + " = ? ",
            new String[] {idColumn.getName()},
            " a." + idColumn.getName() + " = b." + idColumn.getName() + " ");
      this.idColumn = idColumn;
    }

    @Override
    public BurstKey newKey(Record record) {
      Long result = record.get(idColumn);
      return new LongKey(result, idColumn.getName());
    }

    public BurstKey newKey(long id) {
      return new LongKey(id, idColumn.getName());
    }

    @Override
    public void applySelfJoin(SelectQuery<Record> query, Table<?> queryTable, Table<?> otherTable) {
      query.addConditions(
        queryTable.field(idColumn.getName(), Long.class).eq(
          otherTable.field(idColumn.getName(), Long.class)
        )
      );
    }
  }

  abstract class LinkKeyFactory<T> extends Factory<T> implements BurstKey.LinkKeyFactory<T> {

    private final String idColumnA;
    private final String idColumnB;

    public LinkKeyFactory(String idColumnA, String idColumnB) {
      super(" WHERE " + idColumnA + " = ? AND " + idColumnB + " = ? ",
            new String[] {idColumnA,idColumnB},
            " a." + idColumnA + " = b." + idColumnA + " AND a." + idColumnB + " = b." + idColumnB + " ");
      this.idColumnA = idColumnA;
      this.idColumnB = idColumnB;
    }

    @Override
    public DbKey newKey(Record rs) {
      return new LinkKey(rs.get(idColumnA, Long.class), rs.get(idColumnB, Long.class), idColumnA, idColumnB);
    }

    public DbKey newKey(long idA, long idB) {
      return new LinkKey(idA, idB, idColumnA, idColumnB);
    }

    @Override
    public void applySelfJoin(SelectQuery<Record> query, Table<?> queryTable, Table<?> otherTable) {
      query.addConditions(
        queryTable.field(idColumnA, Long.class).eq(
          otherTable.field(idColumnA, Long.class)
        )
      );
      query.addConditions(
        queryTable.field(idColumnB, Long.class).eq(
          otherTable.field(idColumnB, Long.class)
        )
      );
    }
  }

  final class LongKey implements DbKey {

    private final long id;
    private final String idColumn;

    private LongKey(long id, String idColumn) {
      this.id       = id;
      this.idColumn = idColumn;
    }

    @Override
    public long[] getPKValues() {
        return new long[]{id};
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof LongKey && ((LongKey) o).id == id;
    }

    @Override
    public int hashCode() {
      return (int) (id ^ (id >>> 32));
    }

    @Override
    public Collection<Condition> getPKConditions(Table<?> tableClass) {
      ArrayList<Condition> conditions = new ArrayList<>();
      conditions.add(tableClass.field(idColumn, Long.class).eq(id));
      return conditions;
    }
    
  }

  final class LinkKey implements DbKey {

    private final long idA;
    private final long idB;
    private final String idColumnA;
    private final String idColumnB;

    private LinkKey(long idA, long idB, String idColumnA, String idColumnB) {
      this.idA       = idA;
      this.idB       = idB;
      this.idColumnA = idColumnA;
      this.idColumnB = idColumnB;
    }

    @Override
    public long[] getPKValues() {
        return new long[]{idA, idB};
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof LinkKey && ((LinkKey) o).idA == idA && ((LinkKey) o).idB == idB;
    }

    @Override
    public int hashCode() {
      return (int) (idA ^ (idA >>> 32)) ^ (int) (idB ^ (idB >>> 32));
    }

    @Override
    public Collection<Condition> getPKConditions(Table<?> tableClass) {
      ArrayList<Condition> conditions = new ArrayList<>();
      conditions.add(tableClass.field(idColumnA, Long.class).eq(idA));
      conditions.add(tableClass.field(idColumnB, Long.class).eq(idB));
      return conditions;
    }
  }
}
