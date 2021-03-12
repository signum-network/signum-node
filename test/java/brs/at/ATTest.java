package brs.at;

import brs.Account;
import brs.Burst;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Burst.class, Account.class})
public class ATTest {
    @Before
    public void setUp() {
        AtTestHelper.setupMocks();
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

        AtomicBoolean fundsForFaucetReceived = new AtomicBoolean(false);
        AtTestHelper.setOnAtAdded(at -> {
            assertEquals("FundsForFaucet", at.getName());
            fundsForFaucetReceived.set(true);
        });
        AtTestHelper.addFundsForFaucetAT();
        assertTrue(fundsForFaucetReceived.get());
        assertEquals(4, AT.getOrderedATs().size());


        
    }
}
