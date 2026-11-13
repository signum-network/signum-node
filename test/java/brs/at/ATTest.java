package brs.at;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ATTest {
    @Before
    public void setUp() {
        AtTestHelper.setupMocks();
    }

    @After
    public void tearDown() {
        AtTestHelper.closeMocks();
    }

    @Test
    public void testAddAt() {
        AtTestHelper.clearAddedAts();
        AtomicBoolean helloWorldReceived = new AtomicBoolean(false);
        AtTestHelper.setOnAtAdded(at -> {
            assertEquals("HelloWorld", at.getName());
            helloWorldReceived.set(true);
        });
        AtTestHelper.addHelloWorldAT();
        assertTrue(helloWorldReceived.get());

        AtomicBoolean echoReceived = new AtomicBoolean(false);
        AtTestHelper.setOnAtAdded(at -> {
            assertEquals("Echo", at.getName());
            echoReceived.set(true);
        });
        AtTestHelper.addEchoAT();
        assertTrue(echoReceived.get());

        AtomicBoolean tipThanksReceived = new AtomicBoolean(false);
        AtTestHelper.setOnAtAdded(at -> {
            assertEquals("TipThanks", at.getName());
            tipThanksReceived.set(true);
        });
        AtTestHelper.addTipThanksAT();
        assertTrue(tipThanksReceived.get());
        assertEquals(3, AT.getOrderedATs().size());
    }
}
