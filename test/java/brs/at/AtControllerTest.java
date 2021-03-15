package brs.at;

import brs.Account;
import brs.Burst;
import brs.util.Convert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Burst.class, Account.class})
public class AtControllerTest {
    @Before
    public void setUp() {
        AtTestHelper.setupMocks();
    }

    @Test
    public void testCheckCreationBytes() throws AtException {
        AtTestHelper.clearAddedAts();
        assertEquals(4, AtController.checkCreationBytes(AtTestHelper.HELLO_WORLD_CREATION_BYTES, Integer.MAX_VALUE));
        assertEquals(4, AtController.checkCreationBytes(AtTestHelper.ECHO_CREATION_BYTES, Integer.MAX_VALUE));
        assertEquals(5, AtController.checkCreationBytes(AtTestHelper.TIP_THANKS_CREATION_BYTES, Integer.MAX_VALUE));
    }

    @Test
    public void testRunSteps() {
        AtTestHelper.clearAddedAts();
        AtTestHelper.addHelloWorldAT();
        AtTestHelper.addEchoAT();
        AtTestHelper.addTipThanksAT();
        assertEquals(3, AT.getOrderedATs().size());
        AtBlock atBlock = AtController.getCurrentBlockATs(Integer.MAX_VALUE, Integer.MAX_VALUE, 0L);
        assertNotNull(atBlock);
        assertNotNull(atBlock.getBytesForBlock());
        assertEquals("010000000000000097c1d1e5b25c1d109f2ba522d1dda248020000000000000014ea12712c274caebc49ccd7fff0b0b703000000000000009f1af5443c8d1e7b492f848e91fccb1f", Convert.toHexString(atBlock.getBytesForBlock()));
    }

    @Test
    public void testValidateAts() throws AtException {
        AtTestHelper.clearAddedAts();
        AtTestHelper.addHelloWorldAT();
        AtTestHelper.addEchoAT();
        AtTestHelper.addTipThanksAT();
        assertEquals(3, AT.getOrderedATs().size());
        AtBlock atBlock = AtController.validateATs(Convert.parseHexString("010000000000000097c1d1e5b25c1d109f2ba522d1dda248020000000000000014ea12712c274caebc49ccd7fff0b0b703000000000000009f1af5443c8d1e7b492f848e91fccb1f"), Integer.MAX_VALUE, 0L);
        assertNotNull(atBlock);
        assertEquals(0, atBlock.getTotalAmount());
        assertEquals(5439000, atBlock.getTotalFees());
    }
}
