package brs.web.api.http.handler;

import brs.Attachment;
import brs.Blockchain;
import brs.Signum;
import brs.SignumException;
import brs.Constants;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.services.AliasService;
import brs.services.ParameterService;
import brs.web.api.http.common.APITransactionManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import jakarta.servlet.http.HttpServletRequest;

import static brs.TransactionType.Messaging.ALIAS_ASSIGNMENT;
import static brs.web.api.http.common.JSONResponses.*;
import static brs.web.api.http.common.Parameters.ALIAS_NAME_PARAMETER;
import static brs.web.api.http.common.Parameters.ALIAS_URI_PARAMETER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class SetAliasTest extends AbstractTransactionTest {

  private MockedStatic<Signum> signumMock;

  private SetAlias t;

  private ParameterService parameterServiceMock;
  private Blockchain blockchainMock;
  private AliasService aliasServiceMock;
  private APITransactionManager apiTransactionManagerMock;

  @Before
  public void setUp() {
    signumMock = mockStatic(Signum.class);
    parameterServiceMock = mock(ParameterService.class);
    blockchainMock = mock(Blockchain.class);
    aliasServiceMock = mock(AliasService.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    FluxCapacitor mockFluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.PRE_POC2, FluxValues.DIGITAL_GOODS_STORE);
    when(Signum.getFluxCapacitor()).thenReturn(mockFluxCapacitor);

    t = new SetAlias(parameterServiceMock, blockchainMock, aliasServiceMock, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws SignumException {
    final String aliasNameParameter = "aliasNameParameter";
    final String aliasUrl = "aliasUrl";

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ALIAS_NAME_PARAMETER, aliasNameParameter),
        new MockParam(ALIAS_URI_PARAMETER, aliasUrl)
    );
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Signum.getFluxCapacitor()).thenReturn(fluxCapacitor);
    doReturn(Constants.FEE_QUANT_SIP3).when(fluxCapacitor).getValue(eq(FluxValues.FEE_QUANT));

    final Attachment.MessagingAliasAssignment attachment = (Attachment.MessagingAliasAssignment) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(ALIAS_ASSIGNMENT, attachment.getTransactionType());
    assertEquals(aliasNameParameter, attachment.getAliasName());
    assertEquals(aliasUrl, attachment.getAliasUri());
  }

  @Test
  public void processRequest_missingAliasName() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ALIAS_NAME_PARAMETER, null),
        new MockParam(ALIAS_URI_PARAMETER, "aliasUrl")
    );

    assertEquals(MISSING_ALIAS_NAME, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectAliasLength_nameOnlySpaces() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ALIAS_NAME_PARAMETER, "  "),
        new MockParam(ALIAS_URI_PARAMETER, null)
    );

    assertEquals(INCORRECT_ALIAS_LENGTH, t.processRequest(req));
  }


  @Test
  public void processRequest_incorrectAliasLength_incorrectAliasName() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ALIAS_NAME_PARAMETER, "[]"),
        new MockParam(ALIAS_URI_PARAMETER, null)
    );

    assertEquals(INCORRECT_ALIAS_NAME, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectUriLengthWhenOver1000Characters() throws SignumException {
    final StringBuilder uriOver1000Characters = new StringBuilder();

    for (int i = 0; i < 1001; i++) {
      uriOver1000Characters.append("a");
    }

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ALIAS_NAME_PARAMETER, "name"),
        new MockParam(ALIAS_URI_PARAMETER, uriOver1000Characters.toString())
    );

    assertEquals(INCORRECT_URI_LENGTH, t.processRequest(req));
  }

  @After
  public void tearDownSignumMock() {
    signumMock.close();
  }
}
