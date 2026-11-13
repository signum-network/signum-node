package brs.web.api.http.handler;

import brs.Account;
import brs.Block;
import brs.Blockchain;
import brs.Constants;
import brs.Signum;
import brs.SignumException;
import brs.Transaction;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.BlockService;
import brs.services.ParameterService;
import brs.util.CollectionWithIndex;

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
import static brs.web.api.http.common.Parameters.TIMESTAMP_PARAMETER;
import static brs.web.api.http.common.ResultFields.BLOCKS_RESPONSE;
import static brs.web.api.http.common.ResultFields.NEXT_INDEX_RESPONSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GetAccountBlocksTest {

  private static final int TIMESTAMP = 1;

  private Blockchain blockchainMock;
  private ParameterService parameterServiceMock;
  private BlockService blockServiceMock;
  private Account accountMock;
  private MockedStatic<Signum> signumMock;

  @BeforeEach
  void setUp() {
    blockchainMock = mock(Blockchain.class);
    parameterServiceMock = mock(ParameterService.class);
    blockServiceMock = mock(BlockService.class);
    accountMock = mock(Account.class);

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
  void processRequest_GivenRangeBeyondLastBlock_ReturnsBlocksWithoutNextIndex() throws SignumException {
    HttpServletRequest req = request(2, 3);
    givenBlocks(req, 2, 3, mockBlock(0));

    JsonObject result = (JsonObject) handler().processRequest(req);

    JsonArray blocks = (JsonArray) result.get(BLOCKS_RESPONSE);
    assertEquals(1, blocks.size());
    assertNotNull(blocks.get(0));
    assertFalse(result.has(NEXT_INDEX_RESPONSE));
  }

  @Test
  void processRequest_GivenFullRange_ReturnsNextIndexAfterRange() throws SignumException {
    HttpServletRequest req = request(2, 3);
    givenBlocks(req, 2, 3, mockBlock(0), mockBlock(0));

    JsonObject result = (JsonObject) handler().processRequest(req);

    assertEquals(2, ((JsonArray) result.get(BLOCKS_RESPONSE)).size());
    assertEquals(4, result.get(NEXT_INDEX_RESPONSE).getAsInt());
  }

  @Test
  void processRequest_GivenTransactionBudgetExceeded_ReturnsNextIndexAfterLastReturnedBlock()
      throws SignumException {
    HttpServletRequest req = request(2, 4);
    givenBlocks(req, 2, 4, mockBlock(2), mockBlock(2), mockBlock(2));

    JsonObject result = (JsonObject) handler(3).processRequest(req);

    assertEquals(2, ((JsonArray) result.get(BLOCKS_RESPONSE)).size());
    assertEquals(4, result.get(NEXT_INDEX_RESPONSE).getAsInt());
  }

  private GetAccountBlocks handler() {
    return handler(Props.API_BLOCKS_MAX_TRANSACTIONS.getDefaultValue());
  }

  private GetAccountBlocks handler(int maxTransactions) {
    return new GetAccountBlocks(blockchainMock, parameterServiceMock, blockServiceMock, maxTransactions);
  }

  private void givenBlocks(HttpServletRequest req, int firstIndex, int lastIndex, Block... blocks)
      throws SignumException {
    when(parameterServiceMock.getAccount(req)).thenReturn(accountMock);
    when(blockchainMock.getBlocks(accountMock, TIMESTAMP, firstIndex, lastIndex))
        .thenReturn(new CollectionWithIndex<>(List.of(blocks), -1));
  }

  private static HttpServletRequest request(int firstIndex, int lastIndex) {
    return QuickMocker.httpServletRequest(
        new MockParam(FIRST_INDEX_PARAMETER, "" + firstIndex),
        new MockParam(LAST_INDEX_PARAMETER, "" + lastIndex),
        new MockParam(TIMESTAMP_PARAMETER, "" + TIMESTAMP));
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
