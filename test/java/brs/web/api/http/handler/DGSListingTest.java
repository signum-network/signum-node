package brs.web.api.http.handler;

import brs.*;
import brs.TransactionType.DigitalGoods;
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

import static brs.web.api.http.common.JSONResponses.*;
import static brs.web.api.http.common.Parameters.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class DGSListingTest extends AbstractTransactionTest {

  private MockedStatic<Signum> signumMock;

  private DGSListing t;

  private ParameterService mockParameterService;
  private Blockchain mockBlockchain;
  private APITransactionManager apiTransactionManagerMock;

  @Before
  public void setUp() {
    signumMock = mockStatic(Signum.class);
    mockParameterService = mock(ParameterService.class);
    mockBlockchain = mock(Blockchain.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    t = new DGSListing(mockParameterService, mockBlockchain, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws SignumException {
    final Account mockAccount = mock(Account.class);

    final String dgsName = "dgsName";
    final String dgsDescription = "dgsDescription";
    final String tags = "tags";
    final int priceNqt = 123;
    final int quantity = 5;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PRICE_NQT_PARAMETER, priceNqt),
        new MockParam(QUANTITY_PARAMETER, quantity),
        new MockParam(NAME_PARAMETER, dgsName),
        new MockParam(DESCRIPTION_PARAMETER, dgsDescription),
        new MockParam(TAGS_PARAMETER, tags)
    );

    when(mockParameterService.getSenderAccount(eq(req))).thenReturn(mockAccount);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Signum.getFluxCapacitor()).thenReturn(fluxCapacitor);
    doReturn(Constants.FEE_QUANT_SIP3).when(fluxCapacitor).getValue(eq(FluxValues.FEE_QUANT));

    final Attachment.DigitalGoodsListing attachment = (Attachment.DigitalGoodsListing) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(DigitalGoods.LISTING, attachment.getTransactionType());
    assertEquals(dgsName, attachment.getName());
    assertEquals(dgsDescription, attachment.getDescription());
    assertEquals(tags, attachment.getTags());
    assertEquals(priceNqt, attachment.getPriceNqt());
    assertEquals(quantity, attachment.getQuantity());
  }

  @Test
  public void processRequest_missingName() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PRICE_NQT_PARAMETER, 123),
        new MockParam(QUANTITY_PARAMETER, 1)
    );

    assertEquals(MISSING_NAME, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectDGSListingName() throws SignumException {
    String tooLongName = "";

    for (int i = 0; i < 101; i++) {
      tooLongName += "a";
    }

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PRICE_NQT_PARAMETER, 123),
        new MockParam(QUANTITY_PARAMETER, 1),
        new MockParam(NAME_PARAMETER, tooLongName)
    );

    assertEquals(INCORRECT_DGS_LISTING_NAME, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectDgsListingDescription() throws SignumException {
    String tooLongDescription = "";

    for (int i = 0; i < 1001; i++) {
      tooLongDescription += "a";
    }

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PRICE_NQT_PARAMETER, 123),
        new MockParam(QUANTITY_PARAMETER, 1),
        new MockParam(NAME_PARAMETER, "name"),
        new MockParam(DESCRIPTION_PARAMETER, tooLongDescription)
    );

    assertEquals(INCORRECT_DGS_LISTING_DESCRIPTION, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectDgsListingTags() throws SignumException {
    String tooLongTags = "";

    for (int i = 0; i < 101; i++) {
      tooLongTags += "a";
    }

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PRICE_NQT_PARAMETER, 123),
        new MockParam(QUANTITY_PARAMETER, 1),
        new MockParam(NAME_PARAMETER, "name"),
        new MockParam(DESCRIPTION_PARAMETER, "description"),
        new MockParam(TAGS_PARAMETER, tooLongTags)
    );

    assertEquals(INCORRECT_DGS_LISTING_TAGS, t.processRequest(req));
  }

  @After
  public void tearDownSignumMock() {
    signumMock.close();
  }
}
