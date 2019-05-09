package brs.http;

import brs.*;
import brs.TransactionType.DigitalGoods;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.services.ParameterService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.servlet.http.HttpServletRequest;

import static brs.http.JSONResponses.*;
import static brs.http.common.Parameters.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Burst.class)
public class DGSListingTest extends AbstractTransactionTest {

  private DGSListing t;

  private ParameterService mockParameterService;
  private Blockchain mockBlockchain;
  private APITransactionManager apiTransactionManagerMock;

  @Before
  public void setUp() {
    mockParameterService = mock(ParameterService.class);
    mockBlockchain = mock(Blockchain.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    t = new DGSListing(mockParameterService, mockBlockchain, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws BurstException {
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

    mockStatic(Burst.class);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Burst.getFluxCapacitor()).thenReturn(fluxCapacitor);

    final Attachment.DigitalGoodsListing attachment = (Attachment.DigitalGoodsListing) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(DigitalGoods.LISTING, attachment.getTransactionType());
    assertEquals(dgsName, attachment.getName());
    assertEquals(dgsDescription, attachment.getDescription());
    assertEquals(tags, attachment.getTags());
    assertEquals(priceNqt, attachment.getPriceNQT());
    assertEquals(quantity, attachment.getQuantity());
  }

  @Test
  public void processRequest_missingName() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PRICE_NQT_PARAMETER, 123),
        new MockParam(QUANTITY_PARAMETER, 1)
    );

    assertEquals(MISSING_NAME, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectDGSListingName() throws BurstException {
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
  public void processRequest_incorrectDgsListingDescription() throws BurstException {
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
  public void processRequest_incorrectDgsListingTags() throws BurstException {
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

}
