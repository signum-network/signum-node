package brs.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

import java.util.Collection;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;

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
