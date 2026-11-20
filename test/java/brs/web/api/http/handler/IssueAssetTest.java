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
import brs.services.ParameterService;
import brs.web.api.http.common.APITransactionManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import jakarta.servlet.http.HttpServletRequest;

import static brs.Constants.*;
import static brs.TransactionType.ColoredCoins.ASSET_ISSUANCE;
import static brs.web.api.http.common.JSONResponses.*;
import static brs.web.api.http.common.Parameters.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class IssueAssetTest extends AbstractTransactionTest {

  private MockedStatic<Signum> signumMock;

  private IssueAsset t;

  private ParameterService mockParameterService;
  private Blockchain mockBlockchain;
  private APITransactionManager apiTransactionManagerMock;

  @Before
  public void setUp() {
    signumMock = mockStatic(Signum.class);
    mockParameterService = mock(ParameterService.class);
    mockBlockchain = mock(Blockchain.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    t = new IssueAsset(mockParameterService, mockBlockchain, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws SignumException {
    final String nameParameter = stringWithLength(MIN_ASSET_NAME_LENGTH + 1);
    final String descriptionParameter = stringWithLength(MAX_ASSET_DESCRIPTION_LENGTH - 1);
    final int decimalsParameter = 4;
    final int quantityQNTParameter = 5;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(NAME_PARAMETER, nameParameter),
        new MockParam(DESCRIPTION_PARAMETER, descriptionParameter),
        new MockParam(DECIMALS_PARAMETER, decimalsParameter),
        new MockParam(QUANTITY_QNT_PARAMETER, quantityQNTParameter)
    );
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Signum.getFluxCapacitor()).thenReturn(fluxCapacitor);
    doReturn(Constants.FEE_QUANT_SIP3).when(fluxCapacitor).getValue(eq(FluxValues.FEE_QUANT));

    final Attachment.ColoredCoinsAssetIssuance attachment = (Attachment.ColoredCoinsAssetIssuance) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(ASSET_ISSUANCE, attachment.getTransactionType());
    assertEquals(nameParameter, attachment.getName());
    assertEquals(descriptionParameter, attachment.getDescription());
    assertEquals(decimalsParameter, attachment.getDecimals());
    assertEquals(descriptionParameter, attachment.getDescription());
  }

  @Test
  public void processRequest_missingName() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    assertEquals(MISSING_NAME, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectAssetNameLength_smallerThanMin() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(NAME_PARAMETER, stringWithLength(MIN_ASSET_NAME_LENGTH - 1))
    );

    assertEquals(INCORRECT_ASSET_NAME_LENGTH, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectAssetNameLength_largerThanMax() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(NAME_PARAMETER, stringWithLength(MAX_ASSET_NAME_LENGTH + 1))
    );

    assertEquals(INCORRECT_ASSET_NAME_LENGTH, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectAssetName() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(NAME_PARAMETER, stringWithLength(MIN_ASSET_NAME_LENGTH + 1) + "[")
    );

    assertEquals(INCORRECT_ASSET_NAME, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectAssetDescription() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(NAME_PARAMETER, stringWithLength(MIN_ASSET_NAME_LENGTH + 1)),
        new MockParam(DESCRIPTION_PARAMETER, stringWithLength(MAX_ASSET_DESCRIPTION_LENGTH + 1))
    );

    assertEquals(INCORRECT_ASSET_DESCRIPTION, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectDecimals_unParsable() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(NAME_PARAMETER, stringWithLength(MIN_ASSET_NAME_LENGTH + 1)),
        new MockParam(DESCRIPTION_PARAMETER, stringWithLength(MAX_ASSET_DESCRIPTION_LENGTH - 1)),
        new MockParam(DECIMALS_PARAMETER, "unParsable")
    );

    assertEquals(INCORRECT_DECIMALS, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectDecimals_negativeNumber() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(NAME_PARAMETER, stringWithLength(MIN_ASSET_NAME_LENGTH + 1)),
        new MockParam(DESCRIPTION_PARAMETER, stringWithLength(MAX_ASSET_DESCRIPTION_LENGTH - 1)),
        new MockParam(DECIMALS_PARAMETER, -5)
    );

    assertEquals(INCORRECT_DECIMALS, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectDecimals_moreThan8() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(NAME_PARAMETER, stringWithLength(MIN_ASSET_NAME_LENGTH + 1)),
        new MockParam(DESCRIPTION_PARAMETER, stringWithLength(MAX_ASSET_DESCRIPTION_LENGTH - 1)),
        new MockParam(DECIMALS_PARAMETER, 9)
    );

    assertEquals(INCORRECT_DECIMALS, t.processRequest(req));
  }

  @After
  public void tearDownSignumMock() {
    signumMock.close();
  }
}
