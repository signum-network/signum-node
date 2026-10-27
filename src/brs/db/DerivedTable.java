package brs.db;

public interface DerivedTable {
  String getTable();
  
  void rollback(int height);

  void truncate();

  void trim(int height);

  void finish();
}
