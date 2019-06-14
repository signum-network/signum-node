package brs.db;

public interface DerivedTable extends Table {
  void rollback(int height);

  void truncate();

  void trim(int height);

  void finish();
}
