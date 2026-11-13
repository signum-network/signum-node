package brs.web.api.http.handler;

import brs.Block;
import brs.Blockchain;
import brs.Constants;
import brs.Signum;
import brs.Transaction;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.BlockService;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import jakarta.servlet.http.HttpServletRequest;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static brs.web.api.http.common.Parameters.FIRST_INDEX_PARAMETER;
import static brs.web.api.http.common.Parameters.LAST_INDEX_PARAMETER;
import static brs.web.api.http.common.ResultFields.NEXT_INDEX_RESPONSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetBlocksTest {

  private Blockchain blockchainMock;
  private BlockService blockServiceMock;
  private MockedStatic<Signum> signumMock;

  @BeforeEach
  void setUp() {
    blockchainMock = mock(Blockchain.class);
    blockServiceMock = mock(BlockService.class);

    PropertyService propertyService = mock(PropertyService.class);
    when(propertyService.getInt(Props.ONE_COIN_NQT)).thenReturn((int) Constants.ONE_SIGNA);
    signumMock = mockStatic(Signum.class);
    signumMock.when(Signum::getPropertyService).thenReturn(propertyService);
  }

  @AfterEach
  void tearDown() {
    signumMock.close();
  }

  @Test
  void processRequest_GivenRangeAbove100Blocks_CapsRangeTo100Blocks() {
    when(blockchainMock.getBlocks(anyInt(), anyInt())).thenReturn(List.of());

    handler().processRequest(request(10, 400));

    verify(blockchainMock).getBlocks(10, 109);
  }

  @Test
  void processRequest_GivenNoLastIndex_CapsRangeTo100Blocks() {
    when(blockchainMock.getBlocks(anyInt(), anyInt())).thenReturn(List.of());
    HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(FIRST_INDEX_PARAMETER, "5"));

    handler().processRequest(req);

    verify(blockchainMock).getBlocks(5, 104);
  }

  @Test
  void processRequest_GivenRangeWithin100Blocks_KeepsRange() {
    when(blockchainMock.getBlocks(anyInt(), anyInt())).thenReturn(List.of());

    handler().processRequest(request(3, 102));

    verify(blockchainMock).getBlocks(3, 102);
  }

  @Test
  void processRequest_GivenFullRange_ReturnsNextIndexAfterRange() {
    givenBlocks(4, 5, mockBlock(0), mockBlock(0));

    JsonObject result = (JsonObject) handler().processRequest(request(4, 5));

    assertEquals(2, ((JsonArray) result.get("blocks")).size());
    assertEquals(6, result.get(NEXT_INDEX_RESPONSE).getAsInt());
  }

  @Test
  void processRequest_GivenRangeBeyondEndOfChain_OmitsNextIndex() {
    givenBlocks(4, 6, mockBlock(0));

    JsonObject result = (JsonObject) handler().processRequest(request(4, 6));

    assertEquals(1, ((JsonArray) result.get("blocks")).size());
    assertFalse(result.has(NEXT_INDEX_RESPONSE));
  }

  @Test
  void processRequest_GivenTransactionBudgetExceeded_ReturnsNextIndexAfterLastReturnedBlock() {
    givenBlocks(4, 6, mockBlock(2), mockBlock(2), mockBlock(2));

    JsonObject result = (JsonObject) handler(3).processRequest(request(4, 6));

    assertEquals(2, ((JsonArray) result.get("blocks")).size());
    assertEquals(6, result.get(NEXT_INDEX_RESPONSE).getAsInt());
  }

  private GetBlocks handler() {
    return handler(Props.API_BLOCKS_MAX_TRANSACTIONS.getDefaultValue());
  }

  private GetBlocks handler(int maxTransactions) {
    return new GetBlocks(blockchainMock, blockServiceMock, maxTransactions);
  }

  private void givenBlocks(int firstIndex, int lastIndex, Block... blocks) {
    when(blockchainMock.getBlocks(firstIndex, lastIndex)).thenReturn(List.of(blocks));
  }

  private static HttpServletRequest request(int firstIndex, int lastIndex) {
    return QuickMocker.httpServletRequest(
        new MockParam(FIRST_INDEX_PARAMETER, "" + firstIndex),
        new MockParam(LAST_INDEX_PARAMETER, "" + lastIndex));
  }

  private static Block mockBlock(int transactionCount) {
    Block block = mock(Block.class);
    when(block.getCumulativeDifficulty()).thenReturn(BigInteger.ONE);
    List<Transaction> transactions = new ArrayList<>();
    for (int i = 0; i < transactionCount; i++) {
      transactions.add(mock(Transaction.class));
    }
    when(block.getAllTransactions()).thenReturn(transactions);
    return block;
  }
}
