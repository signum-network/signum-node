package brs.db;

import org.jooq.Record;

public interface BurstKey {

  interface Factory<T> {
    BurstKey newKey(T t);

    BurstKey newKey(Record rs);
  }

  long[] getPKValues();

  interface LongKeyFactory<T> extends Factory<T> {
    @Override
    BurstKey newKey(Record rs);

    BurstKey newKey(long id);

  }

  interface LinkKeyFactory<T> extends Factory<T> {
    BurstKey newKey(long idA, long idB);
  }
}
