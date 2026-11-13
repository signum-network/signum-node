package brs.web.api.http.handler;

import brs.Account;
import brs.Block;
import brs.Blockchain;
import brs.Signum;
import brs.SignumException;
import brs.Constants;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.BlockService;
import brs.services.ParameterService;
import brs.util.CollectionWithIndex;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;

import jakarta.servlet.http.HttpServletRequest;

import java.math.BigInteger;
import java.util.Collection;

import static brs.web.api.http.common.Parameters.*;
import static brs.web.api.http.common.ResultFields.BLOCKS_RESPONSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

;

@SuppressStaticInitializationFor("brs.Block")
@RunWith(PowerMockRunner.class)
@PrepareForTest(Signum.class)
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

    mockStatic(Signum.class);
    PropertyService propertyService = mock(PropertyService.class);
    when(Signum.getPropertyService()).thenReturn(propertyService);
    doReturn((int)Constants.ONE_SIGNA).when(propertyService).getInt(eq(Props.ONE_COIN_NQT));

    t = new GetAccountBlocks(blockchainMock, parameterServiceMock, blockServiceMock,
        Props.API_BLOCKS_MAX_TRANSACTIONS.getDefaultValue());
  }

  @Test
  public void processRequest() throws SignumException {
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
    when(mockBlock.getCumulativeDifficulty()).thenReturn(BigInteger.valueOf(123456789L));


    when(parameterServiceMock.getAccount(req)).thenReturn(mockAccount);

    final Collection<Block> mockBlockIterator = mockCollection(mockBlock);
    when(blockchainMock.getBlocks(eq(mockAccount), eq(mockTimestamp), eq(mockFirstIndex), eq(mockLastIndex))).thenReturn(new CollectionWithIndex<Block>(
        mockBlockIterator, -1));

    final JsonObject result = (JsonObject) t.processRequest(req);

    final JsonArray blocks = (JsonArray) result.get(BLOCKS_RESPONSE);
    assertNotNull(blocks);
    assertEquals(1, blocks.size());

    final JsonObject resultBlock = (JsonObject) blocks.get(0);
    assertNotNull(resultBlock);

    //TODO validate all fields
  }
}
