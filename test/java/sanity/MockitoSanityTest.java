package sanity;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import java.util.List;

public class MockitoSanityTest {

    @Test
    public void testMockitoWorks() {
        List<String> mockList = mock(List.class);
        when(mockList.get(0)).thenReturn("Hello");
        assert "Hello".equals(mockList.get(0));
    }
}
