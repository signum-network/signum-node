package brs.common;

import java.util.Arrays;
import java.util.Collection;

public abstract class AbstractUnitTest {
  @SafeVarargs
  protected final <T> Collection<T> mockCollection(T... items) {
    return Arrays.asList(items);
  }

  protected String stringWithLength(int length) {
    StringBuilder result = new StringBuilder();

    for(int i = 0; i < length; i++) {
      result.append("a");
    }

    return result.toString();
  }

}
