package brs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import brs.props.PropertyService;
import brs.services.BlockService;
import java.util.HashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class OCLPoCTest {

  @BeforeEach
  void setUp() {
    OCLPoC.resetForTests();
  }

  @AfterEach
  void tearDown() {
    OCLPoC.resetForTests();
  }

  @Test
  void destroy_GivenNeverInitialized_DoesNotInitializeOpenCl() {
    // Arrange
    try (MockedStatic<Signum> signum = Mockito.mockStatic(Signum.class)) {

      // Act
      OCLPoC.destroy();

      // Assert
      signum.verifyNoInteractions();
    }
  }

  @Test
  void getMaxItems_GivenDestroyed_ReturnsZeroWithoutInitializing() {
    // Arrange
    try (MockedStatic<Signum> signum = Mockito.mockStatic(Signum.class)) {
      OCLPoC.destroy();

      // Act
      long maxItems = OCLPoC.getMaxItems();

      // Assert
      assertEquals(0, maxItems);
      signum.verifyNoInteractions();
    }
  }

  @Test
  void validatePoC_GivenInitializationFails_ThrowsCheckerExceptionOnceAndCachesIt() {
    // Arrange
    PropertyService propertyService = mock(PropertyService.class);
    when(propertyService.getBoolean(any())).thenThrow(new IllegalStateException("no GPU"));
    try (MockedStatic<Signum> signum = Mockito.mockStatic(Signum.class)) {
      signum.when(Signum::getPropertyService).thenReturn(propertyService);
      HashMap<Block, Block> blocks = new HashMap<>();
      BlockService blockService = mock(BlockService.class);

      // Act
      long maxItems = OCLPoC.getMaxItems();
      OCLPoC.OCLCheckerException first = assertThrows(OCLPoC.OCLCheckerException.class,
          () -> OCLPoC.validatePoC(blocks, 2, blockService));
      OCLPoC.OCLCheckerException second = assertThrows(OCLPoC.OCLCheckerException.class,
          () -> OCLPoC.validatePoC(blocks, 2, blockService));

      // Assert
      assertEquals(0, maxItems);
      assertSame(first, second);
      signum.verify(Signum::getPropertyService, Mockito.times(1));
    }
  }

  @Test
  void getMaxItems_GivenNativeLibraryMissing_ReturnsZeroAndKeepsCause() {
    // Arrange
    PropertyService propertyService = mock(PropertyService.class);
    when(propertyService.getBoolean(any())).thenThrow(new UnsatisfiedLinkError("no JOCL"));
    try (MockedStatic<Signum> signum = Mockito.mockStatic(Signum.class)) {
      signum.when(Signum::getPropertyService).thenReturn(propertyService);
      HashMap<Block, Block> blocks = new HashMap<>();
      BlockService blockService = mock(BlockService.class);

      // Act
      long maxItems = OCLPoC.getMaxItems();
      OCLPoC.OCLCheckerException failure = assertThrows(OCLPoC.OCLCheckerException.class,
          () -> OCLPoC.validatePoC(blocks, 2, blockService));

      // Assert
      assertEquals(0, maxItems);
      assertInstanceOf(UnsatisfiedLinkError.class, failure.getCause());
    }
  }
}
