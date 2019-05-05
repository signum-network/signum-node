package brs.http;

import brs.Account;
import brs.Block;
import brs.Blockchain;
import brs.BurstException;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.services.BlockService;
import brs.services.ParameterService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;

import static brs.http.common.Parameters.*;
import static brs.http.common.ResultFields.BLOCKS_RESPONSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

;

@SuppressStaticInitializationFor("brs.Block")
public class GetAccountBlocksTest extends AbstractUnitTest {

  private GetAccountBlocks t;

  private Blockchain blockchainMock;
  private ParameterService parameterServiceMock;
  private BlockService blockServiceMock;

  @Before
  public void setUp() {
    blockchainMock = mock(Blockchain.class);
    parameterServiceMock = mock(ParameterService.class);
    blockServiceMock = mock(BlockService.class);

    t = new GetAccountBlocks(blockchainMock, parameterServiceMock, blockServiceMock);
  }

  @Test
  public void processRequest() throws BurstException {
    final int mockTimestamp = 1;
    final int mockFirstIndex = 2;
    final int mockLastIndex = 3;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(FIRST_INDEX_PARAMETER, "" + mockFirstIndex),
        new MockParam(LAST_INDEX_PARAMETER, "" + mockLastIndex),
        new MockParam(TIMESTAMP_PARAMETER, "" + mockTimestamp)
    );

    final Account mockAccount = mock(Account.class);
    final Block mockBlock = mock(Block.class);


    when(parameterServiceMock.getAccount(req)).thenReturn(mockAccount);

    final Collection<Block> mockBlockIterator = mockCollection(mockBlock);
    when(blockchainMock.getBlocks(eq(mockAccount), eq(mockTimestamp), eq(mockFirstIndex), eq(mockLastIndex))).thenReturn(mockBlockIterator);

    final JsonObject result = (JsonObject) t.processRequest(req);

    final JsonArray blocks = (JsonArray) result.get(BLOCKS_RESPONSE);
    assertNotNull(blocks);
    assertEquals(1, blocks.size());

    final JsonObject resultBlock = (JsonObject) blocks.get(0);
    assertNotNull(resultBlock);

    //TODO validate all fields
  }
}