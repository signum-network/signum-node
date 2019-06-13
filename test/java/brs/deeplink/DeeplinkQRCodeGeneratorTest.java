package brs.deeplink;

import brs.common.TestConstants;
import brs.feesuggestions.FeeSuggestionType;
import com.google.zxing.WriterException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertNotNull;

@RunWith(JUnit4.class)
public class DeeplinkQRCodeGeneratorTest {
    private DeeplinkQRCodeGenerator deeplinkQRCodeGenerator;

    @Before
    public void setUpDeeplinkQrCodeGeneratorTest() {
        deeplinkQRCodeGenerator = new DeeplinkQRCodeGenerator();
    }

    @Test
    public void testDeeplinkQrCodeGenerator() throws WriterException {
        BufferedImage image = deeplinkQRCodeGenerator.generateRequestBurstDeepLinkQRCode(TestConstants.TEST_ACCOUNT_NUMERIC_ID, TestConstants.TEN_BURST, FeeSuggestionType.STANDARD, 0L, "Test!", true);
        assertNotNull(image);
    }
}
