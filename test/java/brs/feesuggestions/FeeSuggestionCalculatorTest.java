package brs.feesuggestions;

import brs.Block;
import brs.BlockchainProcessor;
import brs.BlockchainProcessor.Event;
import brs.Transaction;
import brs.common.AbstractUnitTest;
import brs.db.store.BlockchainStore;
import brs.util.Listener;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import static brs.Constants.FEE_QUANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FeeSuggestionCalculatorTest extends AbstractUnitTest {

  private FeeSuggestionCalculator t;

  private BlockchainProcessor blockchainProcessorMock;
  private BlockchainStore blockchainStoreMock;

  private ArgumentCaptor<Listener<Block>> listenerArgumentCaptor;

  @Before
  public void setUp() {
    blockchainProcessorMock = mock(BlockchainProcessor.class);
    blockchainStoreMock = mock(BlockchainStore.class);

    listenerArgumentCaptor = ArgumentCaptor.forClass(Listener.class);
    when(blockchainProcessorMock.addListener(listenerArgumentCaptor.capture(), eq(Event.AFTER_BLOCK_APPLY))).thenReturn(true);

    t = new FeeSuggestionCalculator(blockchainProcessorMock, blockchainStoreMock, 5);
  }

  @Test
  public void getFeeSuggestion() {

    Block mockBlock1 = mock(Block.class);
    when(mockBlock1.getTransactions()).thenReturn(new ArrayList<>());
    Block mockBlock2 = mock(Block.class);
    when(mockBlock2.getTransactions()).thenReturn(Arrays.asList(mock(Transaction.class)));
    Block mockBlock3 = mock(Block.class);
    when(mockBlock3.getTransactions()).thenReturn(Arrays.asList(mock(Transaction.class)));
    Block mockBlock4 = mock(Block.class);
    when(mockBlock4.getTransactions()).thenReturn(Arrays.asList(mock(Transaction.class)));
    Block mockBlock5 = mock(Block.class);
    when(mockBlock5.getTransactions()).thenReturn(Arrays.asList(mock(Transaction.class)));

    Collection<Block> mockBlocksIterator = mockCollection(mockBlock1, mockBlock2, mockBlock3, mockBlock4, mockBlock5);
    when(blockchainStoreMock.getLatestBlocks(eq(5))).thenReturn(mockBlocksIterator);

    listenerArgumentCaptor.getValue().notify(mockBlock1);
    listenerArgumentCaptor.getValue().notify(mockBlock2);
    listenerArgumentCaptor.getValue().notify(mockBlock3);
    listenerArgumentCaptor.getValue().notify(mockBlock4);
    listenerArgumentCaptor.getValue().notify(mockBlock5);

    FeeSuggestion feeSuggestionOne = t.giveFeeSuggestion();
    assertEquals(1 * FEE_QUANT, feeSuggestionOne.getCheapFee());
    assertEquals(2 * FEE_QUANT, feeSuggestionOne.getStandardFee());
    assertEquals(2 * FEE_QUANT, feeSuggestionOne.getPriorityFee());

    Block mockBlock6 = mock(Block.class);
    when(mockBlock6.getTransactions()).thenReturn(Arrays.asList(mock(Transaction.class), mock(Transaction.class), mock(Transaction.class), mock(Transaction.class)));
    Block mockBlock7 = mock(Block.class);
    when(mockBlock7.getTransactions()).thenReturn(Arrays.asList(mock(Transaction.class), mock(Transaction.class), mock(Transaction.class), mock(Transaction.class)));
    Block mockBlock8 = mock(Block.class);
    when(mockBlock8.getTransactions()).thenReturn(Arrays.asList(mock(Transaction.class), mock(Transaction.class), mock(Transaction.class), mock(Transaction.class), mock(Transaction.class)));

    listenerArgumentCaptor.getValue().notify(mockBlock6);
    FeeSuggestion feeSuggestionTwo = t.giveFeeSuggestion();
    assertEquals(2 * FEE_QUANT, feeSuggestionTwo.getCheapFee());
    assertEquals(3 * FEE_QUANT, feeSuggestionTwo.getStandardFee());
    assertEquals(5 * FEE_QUANT, feeSuggestionTwo.getPriorityFee());

    listenerArgumentCaptor.getValue().notify(mockBlock7);
    FeeSuggestion feeSuggestionThree = t.giveFeeSuggestion();
    assertEquals(2 * FEE_QUANT, feeSuggestionThree.getCheapFee());
    assertEquals(4 * FEE_QUANT, feeSuggestionThree.getStandardFee());
    assertEquals(5 * FEE_QUANT, feeSuggestionThree.getPriorityFee());

    listenerArgumentCaptor.getValue().notify(mockBlock8);
    FeeSuggestion feeSuggestionFour = t.giveFeeSuggestion();
    assertEquals(2 * FEE_QUANT, feeSuggestionFour.getCheapFee());
    assertEquals(4 * FEE_QUANT, feeSuggestionFour.getStandardFee());
    assertEquals(6 * FEE_QUANT, feeSuggestionFour.getPriorityFee());
  }
}