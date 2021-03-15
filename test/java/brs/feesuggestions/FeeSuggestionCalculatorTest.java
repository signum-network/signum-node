package brs.feesuggestions;

import brs.Block;
import brs.BlockchainProcessor;
import brs.BlockchainProcessor.Event;
import brs.common.AbstractUnitTest;
import brs.unconfirmedtransactions.UnconfirmedTransactionStore;
import brs.util.Listener;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;

import static brs.Constants.FEE_QUANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FeeSuggestionCalculatorTest extends AbstractUnitTest {

  private FeeSuggestionCalculator t;

  private BlockchainProcessor blockchainProcessorMock;
  private UnconfirmedTransactionStore unconfirmedTransactionStoreMock;

  private ArgumentCaptor<Listener<Block>> listenerArgumentCaptor;

  @Before
  public void setUp() {
    blockchainProcessorMock = mock(BlockchainProcessor.class);
    unconfirmedTransactionStoreMock = mock(UnconfirmedTransactionStore.class);

    listenerArgumentCaptor = ArgumentCaptor.forClass(Listener.class);
    when(blockchainProcessorMock.addListener(listenerArgumentCaptor.capture(), eq(Event.AFTER_BLOCK_APPLY))).thenReturn(true);

    t = new FeeSuggestionCalculator(blockchainProcessorMock, unconfirmedTransactionStoreMock);
  }

  @Test
  public void getFeeSuggestion() {

    Block mockBlock1 = mock(Block.class);
    when(mockBlock1.getTransactions()).thenReturn(new ArrayList<>());

    when(unconfirmedTransactionStoreMock.getFreeSlot(eq(15))).thenReturn(1L);
    when(unconfirmedTransactionStoreMock.getFreeSlot(eq(3))).thenReturn(2L);
    when(unconfirmedTransactionStoreMock.getFreeSlot(eq(1))).thenReturn(10L);

    listenerArgumentCaptor.getValue().notify(mockBlock1);

    FeeSuggestion feeSuggestionOne = t.giveFeeSuggestion();
    assertEquals(1 * FEE_QUANT, feeSuggestionOne.getCheapFee());
    assertEquals(2 * FEE_QUANT, feeSuggestionOne.getStandardFee());
    assertEquals(12 * FEE_QUANT, feeSuggestionOne.getPriorityFee());
  }
}