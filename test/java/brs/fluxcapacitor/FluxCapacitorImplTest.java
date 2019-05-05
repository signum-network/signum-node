package brs.fluxcapacitor;

import brs.Blockchain;
import brs.props.PropertyService;
import brs.props.Props;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class FluxCapacitorImplTest {

  private Blockchain blockchainMock;
  private PropertyService propertyServiceMock;

  private FluxCapacitorImpl t;

  @BeforeEach
  public void setUp() {
    blockchainMock = mock(Blockchain.class);
    propertyServiceMock = mock(PropertyService.class);
    reset(blockchainMock, propertyServiceMock);
  }

  @DisplayName("Feature is active on ProdNet")
  @Test
  public void featureIsActiveOnProdNet() {
    when(propertyServiceMock.getBoolean(eq(Props.DEV_TESTNET))).thenReturn(false);
    when(blockchainMock.getHeight()).thenReturn(500000);

    t = new FluxCapacitorImpl(blockchainMock, propertyServiceMock);

    assertTrue(t.getValue(FluxValues.PRE_DYMAXION));
  }

  @DisplayName("Feature is not active on ProdNet")
  @Test
  public void featureIsInactiveProdNet() {
    when(propertyServiceMock.getBoolean(eq(Props.DEV_TESTNET))).thenReturn(false);
    when(blockchainMock.getHeight()).thenReturn(499999);

    t = new FluxCapacitorImpl(blockchainMock, propertyServiceMock);

    assertFalse(t.getValue(FluxValues.POC2));
  }

  @DisplayName("Feature is active on TestNet")
  @Test
  public void featureIsActiveTestNet() {
    when(propertyServiceMock.getBoolean(eq(Props.DEV_TESTNET))).thenReturn(true);
    when(blockchainMock.getHeight()).thenReturn(88999);
    when(propertyServiceMock.getInt(any())).thenReturn(-1);

    t = new FluxCapacitorImpl(blockchainMock, propertyServiceMock);

    assertTrue(t.getValue(FluxValues.POC2));

    when(blockchainMock.getHeight()).thenReturn(30000);

    assertFalse(t.getValue(FluxValues.POC2));
  }

  @DisplayName("FluxInt gives its default value when no historical moments changed it yet")
  @Test
  public void fluxIntDefaultValue() {
    when(propertyServiceMock.getBoolean(eq(Props.DEV_TESTNET))).thenReturn(false);
    when(blockchainMock.getHeight()).thenReturn(88000);

    t = new FluxCapacitorImpl(blockchainMock, propertyServiceMock);

    assertEquals((Integer) 255, t.getValue(FluxValues.MAX_NUMBER_TRANSACTIONS));
  }

  @DisplayName("FluxInt gives a new value when a historical moment has passed")
  @Test
  public void fluxIntHistoricalValue() {
    when(propertyServiceMock.getBoolean(eq(Props.DEV_TESTNET))).thenReturn(false);
    when(blockchainMock.getHeight()).thenReturn(500000);

    t = new FluxCapacitorImpl(blockchainMock, propertyServiceMock);

    assertEquals((Integer) 1020, t.getValue(FluxValues.MAX_NUMBER_TRANSACTIONS));
  }

  @DisplayName("FluxInt on TestNet gives its default value when no historical moments changed it yet")
  @Test
  public void fluxIntTestNetDefaultValue() {
    when(propertyServiceMock.getBoolean(eq(Props.DEV_TESTNET))).thenReturn(true);
    when(propertyServiceMock.getInt(any())).thenReturn(-1);

    t = new FluxCapacitorImpl(blockchainMock, propertyServiceMock);

    when(blockchainMock.getHeight()).thenReturn(5);

    assertEquals((Integer) 255, t.getValue(FluxValues.MAX_NUMBER_TRANSACTIONS));
  }

  @DisplayName("FluxInt on TestNet gives a new value when a historical moment has passed")
  @Test
  public void fluxIntTestNetHistoricalValue() {
    when(propertyServiceMock.getBoolean(eq(Props.DEV_TESTNET))).thenReturn(true);
    when(propertyServiceMock.getInt(any())).thenReturn(-1);

    t = new FluxCapacitorImpl(blockchainMock, propertyServiceMock);

    when(blockchainMock.getHeight()).thenReturn(88000);

    assertEquals((Integer) 1020, t.getValue(FluxValues.MAX_NUMBER_TRANSACTIONS));
  }

  @DisplayName("FluxInt on TestNet gives a different value because the historical moment configuration is different")
  @Test
  public void fluxIntTestNetHistoricalMomentChangedThroughProperty() {
    when(propertyServiceMock.getBoolean(eq(Props.DEV_TESTNET))).thenReturn(true);
    when(propertyServiceMock.getInt(eq(Props.DEV_PRE_DYMAXION_BLOCK_HEIGHT))).thenReturn(12345);

    t = new FluxCapacitorImpl(blockchainMock, propertyServiceMock);

    assertEquals((Integer) 255, t.getValue(FluxValues.MAX_NUMBER_TRANSACTIONS, 12344));
    assertEquals((Integer) 1020, t.getValue(FluxValues.MAX_NUMBER_TRANSACTIONS, 12345));
  }
}
