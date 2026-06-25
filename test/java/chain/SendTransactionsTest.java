package chain;

import static chain.ChainUtils.ACCOUNT1;
import static chain.ChainUtils.ACCOUNT2;
import static chain.ChainUtils.ACCOUNT3;
import static chain.ChainUtils.PASS1;
import static chain.ChainUtils.PASS2;
import static chain.ChainUtils.confirm;
import static chain.ChainUtils.crypto;
import static chain.ChainUtils.forgeBlock;
import static chain.ChainUtils.nodeService;
import static chain.ChainUtils.setupNode;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bouncycastle.util.encoders.Hex;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import signumj.crypto.SignumCrypto;
import signumj.entity.SignumAddress;
import signumj.entity.SignumValue;
import signumj.entity.response.AT;
import signumj.entity.response.Account;
import signumj.entity.response.Transaction;
import signumj.entity.response.TransactionBroadcast;
import signumj.response.appendix.PlaintextMessageAppendix;
import signumj.service.TransactionBuilder;

@RunWith(JUnit4.class)
public class SendTransactionsTest {

    @BeforeClass
    public static void setUpTest() {
        assertTrue("Mock node did not responded in time", setupNode());
    }

    @Test
    public void testSendMoney() {
        SignumValue amount = SignumValue.fromSigna(1);
        TransactionBuilder tb = new TransactionBuilder(TransactionBuilder.SEND_MONEY,
                ACCOUNT1.getPublicKey(), SignumValue.fromSigna(0.01), 1440)
                .recipient(ACCOUNT2)
                .amount(amount);

        byte []utx = nodeService.generateTransaction(tb).blockingGet();
        assertTrue(tb.verify(utx));

        SignumValue startBalance = nodeService.getAccount(ACCOUNT2).blockingGet().getBalance();

        byte[] stx = crypto.signTransaction(PASS1, utx);
        TransactionBroadcast tx = nodeService.broadcastTransaction(stx).blockingGet();
        forgeBlock(PASS1, tx);

        SignumValue newBalance  = nodeService.getAccount(ACCOUNT2).blockingGet().getBalance();

        assertEquals(amount, newBalance.subtract(startBalance));
    }
    
    @Test
    public void testBurn() {
        SignumValue amount = SignumValue.fromSigna(1);
        SignumAddress recipient = SignumAddress.fromId(0L);
        TransactionBuilder tb = new TransactionBuilder(TransactionBuilder.SEND_MONEY,
                ACCOUNT1.getPublicKey(), SignumValue.fromSigna(0.01), 1440)
                .recipient(recipient)
                .amount(amount);
        TransactionBroadcast tx = confirm(PASS1, tb);

        Transaction confirmedTx = nodeService.getTransaction(tx.getTransactionId()).blockingGet();
        assertEquals(amount, confirmedTx.getAmount());
        // when burning, the JSON object omits the recipient
        assertNull(confirmedTx.getRecipient());
    }
    
    @Test
    public void testBlockInactiveAccounts() {
        SignumValue amount = SignumValue.fromSigna(1);
        SignumAddress recipient = SignumAddress.fromId(123456789L);
        TransactionBuilder tb = new TransactionBuilder(TransactionBuilder.SEND_MONEY,
                ACCOUNT1.getPublicKey(), SignumValue.fromSigna(0.01), 1440)
                .recipient(recipient)
                .amount(amount);
        
        Exception ex = null;
        try {
            confirm(PASS1, tb);
        }
        catch (Exception e) {
            ex = e;
        }
        assertNotNull(ex);
    }
    
    @Test
    public void testSendMoneyBadSignature() {
        SignumValue amount = SignumValue.fromSigna(1);
        TransactionBuilder tb = new TransactionBuilder(TransactionBuilder.SEND_MONEY,
                ACCOUNT1.getPublicKey(), SignumValue.fromSigna(0.01), 1440)
                .recipient(ACCOUNT2)
                .amount(amount);
        Exception ex = null;
        try {
            // try to sign with PASS2 for ACCOUNT1, should fail
            confirm(PASS2, tb);
        }
        catch (Exception e) {
            ex = e;
        }
        assertNotNull(ex);
    }

    @Test
    public void testSendMessage() {
        String message = "Test message";
        TransactionBuilder tb = new TransactionBuilder(TransactionBuilder.SEND_MESSAGE,
                ACCOUNT1.getPublicKey(), SignumValue.fromSigna(0.01), 1440)
                .recipient(ACCOUNT2)
                .message(message);

        byte []utx = nodeService.generateTransaction(tb).blockingGet();
        assertTrue(tb.verify(utx));

        byte[] stx = crypto.signTransaction(PASS1, utx);
        TransactionBroadcast tx = nodeService.broadcastTransaction(stx).blockingGet();
        forgeBlock(PASS1, tx);

        Transaction confirmedTx = nodeService.getTransaction(tx.getTransactionId()).blockingGet();
        assertNotNull(confirmedTx);

        assertEquals(message, ((PlaintextMessageAppendix)confirmedTx.getAppendages()[0]).getMessage());
    }

    @Test
    public void testSendMessageNotText() {
        byte []message = ACCOUNT2.getPublicKey();
        TransactionBuilder tb = new TransactionBuilder(TransactionBuilder.SEND_MESSAGE,
                ACCOUNT1.getPublicKey(), SignumValue.fromSigna(0.01), 1440)
                .recipient(ACCOUNT2)
                .message(message);

        byte []utx = nodeService.generateTransaction(tb).blockingGet();
        assertTrue(tb.verify(utx));

        byte[] stx = crypto.signTransaction(PASS1, utx);
        TransactionBroadcast tx = nodeService.broadcastTransaction(stx).blockingGet();
        forgeBlock(PASS1, tx);

        Transaction confirmedTx = nodeService.getTransaction(tx.getTransactionId()).blockingGet();
        assertNotNull(confirmedTx);

        PlaintextMessageAppendix confirmedMessage = (PlaintextMessageAppendix)confirmedTx.getAppendages()[0];
        assertFalse(confirmedMessage.isText());
        assertEquals(Hex.toHexString(message), confirmedMessage.getMessage());
    }

    @Test
    public void testSendMessageAnnouncePublicKey() {
        SignumAddress recipient = crypto.getAddressFromPassphrase(Long.toString(new Random().nextLong()));
        TransactionBuilder tb = new TransactionBuilder(TransactionBuilder.SEND_MESSAGE,
                ACCOUNT1.getPublicKey(), SignumValue.fromSigna(0.01), 1440)
                .recipient(recipient)
                .message("Account activation");

        byte []utx = nodeService.generateTransaction(tb).blockingGet();
        assertTrue(tb.verify(utx));

        byte[] stx = crypto.signTransaction(PASS1, utx);
        TransactionBroadcast tx = nodeService.broadcastTransaction(stx).blockingGet();
        forgeBlock(PASS1, tx);

        Account accountConfirmed = nodeService.getAccount(recipient).blockingGet();
        assertArrayEquals(recipient.getPublicKey(), accountConfirmed.getPublicKey());
    }

    @Test
    public void testSendMoneyMessage() {
        String message = "Test message";
        SignumValue amount = SignumValue.fromSigna(1);
        TransactionBuilder tb = new TransactionBuilder(TransactionBuilder.SEND_MONEY,
                ACCOUNT1.getPublicKey(), SignumValue.fromSigna(0.01), 1440)
                .recipient(ACCOUNT2)
                .amount(amount)
                .message(message);

        byte []utx = nodeService.generateTransaction(tb).blockingGet();
        assertTrue(tb.verify(utx));

        SignumValue startBalance = nodeService.getAccount(ACCOUNT2).blockingGet().getBalance();

        byte[] stx = crypto.signTransaction(PASS1, utx);
        TransactionBroadcast tx = nodeService.broadcastTransaction(stx).blockingGet();
        forgeBlock(PASS1, tx);

        Transaction confirmedTx = nodeService.getTransaction(tx.getTransactionId()).blockingGet();
        assertNotNull(confirmedTx);

        assertEquals(message, ((PlaintextMessageAppendix)confirmedTx.getAppendages()[0]).getMessage());

        SignumValue newBalance  = nodeService.getAccount(ACCOUNT2).blockingGet().getBalance();

        assertEquals(amount, newBalance.subtract(startBalance));
    }

    @Test
    public void testSetRewardRecipient() {
        TransactionBuilder tb = new TransactionBuilder(TransactionBuilder.SET_REWARD_RECIPIENT,
                ACCOUNT2.getPublicKey(), SignumValue.fromSigna(0.01), 1440)
                .recipient(ACCOUNT1);

        byte []utx = nodeService.generateTransaction(tb).blockingGet();
        assertTrue(tb.verify(utx));

        byte[] stx = crypto.signTransaction(PASS2, utx);
        TransactionBroadcast tx = nodeService.broadcastTransaction(stx).blockingGet();
        forgeBlock(PASS1, tx);
        forgeBlock(PASS1);
        forgeBlock(PASS1);
        forgeBlock(PASS1);
        forgeBlock(PASS1);
        forgeBlock(PASS1);

        SignumAddress rewardConfirmed = nodeService.getRewardRecipient(ACCOUNT2).blockingGet();
        assertEquals(ACCOUNT1, rewardConfirmed);
    }

    @Test
    public void testAddCommitment() {
        SignumValue amount = SignumValue.fromSigna(1);

        // Reserved = balance - unconfirmedBalance. Other tests share the @BeforeClass
        // chain state and may have left reservations on ACCOUNT1, so assert the delta
        // caused by this commitment rather than an absolute value (order-independent).
        Account before = nodeService.getAccount(ACCOUNT1, null, true, true).blockingGet();
        SignumValue reservedBefore = before.getBalance().subtract(before.getUnconfirmedBalance());

        TransactionBuilder tb = new TransactionBuilder(TransactionBuilder.ADD_COMMITMENT,
                ACCOUNT1.getPublicKey(), SignumValue.fromSigna(0.01), 1440)
                .amount(amount);

        byte []utx = nodeService.generateTransaction(tb).blockingGet();
        assertTrue(tb.verify(utx));

        byte[] stx = crypto.signTransaction(PASS1, utx);
        TransactionBroadcast tx = nodeService.broadcastTransaction(stx).blockingGet();
        forgeBlock(PASS1, tx);

        Account account1 = nodeService.getAccount(ACCOUNT1, null, true, true).blockingGet();
        SignumValue reservedAfter = account1.getBalance().subtract(account1.getUnconfirmedBalance());
        assertEquals(amount, reservedAfter.subtract(reservedBefore));
    }

    @Test
    public void testSetAlias() {
        String aliasName = "aNewAlias";
        String uri = "pointing to something";
        TransactionBuilder tb = new TransactionBuilder(TransactionBuilder.SET_ALIAS,
                ACCOUNT1.getPublicKey(), SignumValue.fromSigna(1), 1440)
                .alias(aliasName, null)
                .aliasURI(uri);

        byte []utx = nodeService.generateTransaction(tb).blockingGet();
        assertTrue(tb.verify(utx));

        byte[] stx = crypto.signTransaction(PASS1, utx);
        TransactionBroadcast tx = nodeService.broadcastTransaction(stx).blockingGet();
        forgeBlock(PASS1, tx);

        // TODO: confirm the alias results here
    }

    @Test
    public void testMultiOut() {
        SignumValue amount2 = SignumValue.fromSigna(2);
        SignumValue amount3 = SignumValue.fromSigna(3);
        Map<SignumAddress, SignumValue> recipients = new HashMap<>();
        recipients.put(ACCOUNT2, amount2);
        recipients.put(ACCOUNT3, amount3);

        TransactionBuilder tb = new TransactionBuilder(TransactionBuilder.SEND_MONEY_MULTI,
                ACCOUNT1.getPublicKey(), SignumValue.fromSigna(0.01), 1440)
                .recipients(recipients);

        byte []utx = nodeService.generateTransaction(tb).blockingGet();
        assertTrue(tb.verify(utx));

        SignumValue account2Start = nodeService.getAccount(ACCOUNT2).blockingGet().getBalance();
        SignumValue account3Start = nodeService.getAccount(ACCOUNT3).blockingGet().getBalance();

        byte[] stx = crypto.signTransaction(PASS1, utx);
        TransactionBroadcast tx = nodeService.broadcastTransaction(stx).blockingGet();
        forgeBlock(PASS1, tx);

        SignumValue account2Confirmed = nodeService.getAccount(ACCOUNT2).blockingGet().getBalance();
        SignumValue account3Confirmed = nodeService.getAccount(ACCOUNT3).blockingGet().getBalance();

        assertEquals(amount2, account2Confirmed.subtract(account2Start));
        assertEquals(amount3, account3Confirmed.subtract(account3Start));
    }

    @Test
    public void testMultiOutSame() {
        SignumValue amount = SignumValue.fromSigna(2);
        Set<SignumAddress> recipients = new HashSet<>();
        recipients.add(ACCOUNT2);
        recipients.add(ACCOUNT3);

        TransactionBuilder tb = new TransactionBuilder(TransactionBuilder.SEND_MONEY_MULTI_SAME,
                ACCOUNT1.getPublicKey(), SignumValue.fromSigna(0.01), 1440)
                .amount(amount)
                .recipients(recipients);

        byte []utx = nodeService.generateTransaction(tb).blockingGet();
        assertTrue(tb.verify(utx));

        SignumValue account2Start = nodeService.getAccount(ACCOUNT2).blockingGet().getBalance();
        SignumValue account3Start = nodeService.getAccount(ACCOUNT3).blockingGet().getBalance();

        byte[] stx = crypto.signTransaction(PASS1, utx);
        TransactionBroadcast tx = nodeService.broadcastTransaction(stx).blockingGet();
        forgeBlock(PASS1, tx);

        SignumValue account2Confirmed = nodeService.getAccount(ACCOUNT2).blockingGet().getBalance();
        SignumValue account3Confirmed = nodeService.getAccount(ACCOUNT3).blockingGet().getBalance();

        assertEquals(amount, account2Confirmed.subtract(account2Start));
        assertEquals(amount, account3Confirmed.subtract(account3Start));
    }

    @Test
    public void testCreateATTransaction() {
        SignumValue activation = SignumValue.fromSigna(2);
        byte[] lotteryAtCode = Hex.decode("1e000000003901090000006400000000000000351400000000000201000000000000000104000000803a0900000000000601000000040000003615000200000000000000260200000036160003000000020000001f030000000100000072361b0008000000020000002308000000090000000f1af3000000361c0004000000020000001e0400000035361700040000000200000026040000007f2004000000050000001e02050000000400000036180006000000020000000200000000030000001a39000000352000070000001b07000000181b0500000012332100060000001a310100000200000000030000001a1a0000003618000a0000000200000020080000000900000023070800000009000000341f00080000000a0000001a78000000341f00080000000a0000001ab800000002000000000400000003050000001a1a000000");
        byte[] lotteryAtCreationBytes = SignumCrypto.getInstance().getATCreationBytes((short) 2, lotteryAtCode, new byte[0], (short) 1, (short) 1, (short) 1, activation);
        assertEquals("02000000020001000100010000c2eb0b0000000044011e000000003901090000006400000000000000351400000000000201000000000000000104000000803a0900000000000601000000040000003615000200000000000000260200000036160003000000020000001f030000000100000072361b0008000000020000002308000000090000000f1af3000000361c0004000000020000001e0400000035361700040000000200000026040000007f2004000000050000001e02050000000400000036180006000000020000000200000000030000001a39000000352000070000001b07000000181b0500000012332100060000001a310100000200000000030000001a1a0000003618000a0000000200000020080000000900000023070800000009000000341f00080000000a0000001a78000000341f00080000000a0000001ab800000002000000000400000003050000001a1a00000000", Hex.toHexString(lotteryAtCreationBytes));

        String name = "TestAT";
        String description = "An AT For Testing";
        TransactionBuilder tb = new TransactionBuilder(TransactionBuilder.CREATE_AT,
                ACCOUNT1.getPublicKey(), SignumValue.fromSigna(2), 1440)
                .name(name)
                .description(description)
                .creationBytes(lotteryAtCreationBytes);

        byte []utx = nodeService.generateTransaction(tb).blockingGet();
        assertTrue(tb.verify(utx));
        
        byte[] stx = crypto.signTransaction(PASS1, utx);
        TransactionBroadcast tx = nodeService.broadcastTransaction(stx).blockingGet();
        forgeBlock(PASS1, tx);
        
        AT at = nodeService.getAt(SignumAddress.fromId(tx.getTransactionId().getSignedLongId())).blockingGet();
        assertEquals(name, at.getName());
        assertEquals(description, at.getDescription());
        assertEquals(activation, at.getMinimumActivation());
        
        byte[] ourCode = new byte[at.getMachineCode().length];
        System.arraycopy(lotteryAtCode, 0, ourCode, 0, lotteryAtCode.length);
        assertArrayEquals(ourCode, at.getMachineCode());
        
        // now create a green contract, without the machine code
        byte[] greenBytes = SignumCrypto.getInstance().getATCreationBytes((short) 2, new byte[0], new byte[0], (short) 1, (short) 1, (short) 1, activation);
        Transaction confirmedTx = nodeService.getTransaction(tx.getTransactionId()).blockingGet();
        tb = new TransactionBuilder(TransactionBuilder.CREATE_AT,
                ACCOUNT1.getPublicKey(), SignumValue.fromSigna(2), 1440)
                .name(name)
                .description(description)
                .creationBytes(greenBytes)
                .referencedTransactionFullHash(Hex.toHexString(confirmedTx.getFullHash()));
        
        utx = nodeService.generateTransaction(tb).blockingGet();
        assertTrue(tb.verify(utx));
        
        stx = crypto.signTransaction(PASS1, utx);
        tx = nodeService.broadcastTransaction(stx).blockingGet();
        forgeBlock(PASS1, tx);
        
        AT atGreen = nodeService.getAt(SignumAddress.fromId(tx.getTransactionId().getSignedLongId())).blockingGet();
        assertEquals(name, atGreen.getName());
        assertEquals(description, atGreen.getDescription());
        assertEquals(activation, atGreen.getMinimumActivation());
        // still the machine code should be the same
        assertArrayEquals(ourCode, atGreen.getMachineCode());
    }

    @Test
    public void testCreateATTransactionWithMessage() {
        SignumValue activation = SignumValue.fromSigna(2);
        byte[] lotteryAtCode = Hex.decode("1e000000003901090000006400000000000000351400000000000201000000000000000104000000803a0900000000000601000000040000003615000200000000000000260200000036160003000000020000001f030000000100000072361b0008000000020000002308000000090000000f1af3000000361c0004000000020000001e0400000035361700040000000200000026040000007f2004000000050000001e02050000000400000036180006000000020000000200000000030000001a39000000352000070000001b07000000181b0500000012332100060000001a310100000200000000030000001a1a0000003618000a0000000200000020080000000900000023070800000009000000341f00080000000a0000001a78000000341f00080000000a0000001ab800000002000000000400000003050000001a1a000000");
        byte[] lotteryAtCreationBytes = SignumCrypto.getInstance().getATCreationBytes((short) 2, lotteryAtCode, new byte[0], (short) 1, (short) 1, (short) 1, activation);
        assertEquals("02000000020001000100010000c2eb0b0000000044011e000000003901090000006400000000000000351400000000000201000000000000000104000000803a0900000000000601000000040000003615000200000000000000260200000036160003000000020000001f030000000100000072361b0008000000020000002308000000090000000f1af3000000361c0004000000020000001e0400000035361700040000000200000026040000007f2004000000050000001e02050000000400000036180006000000020000000200000000030000001a39000000352000070000001b07000000181b0500000012332100060000001a310100000200000000030000001a1a0000003618000a0000000200000020080000000900000023070800000009000000341f00080000000a0000001a78000000341f00080000000a0000001ab800000002000000000400000003050000001a1a00000000", Hex.toHexString(lotteryAtCreationBytes));

        String name = "TestAT";
        String description = "An AT For Testing";
        String message = "Test extra AT message";
        TransactionBuilder tb = new TransactionBuilder(TransactionBuilder.CREATE_AT,
                ACCOUNT1.getPublicKey(), SignumValue.fromSigna(2), 1440)
                .name(name)
                .description(description)
                .creationBytes(lotteryAtCreationBytes)
                .message(message);

        byte []utx = nodeService.generateTransaction(tb).blockingGet();
        assertTrue(tb.verify(utx));
        
        byte[] stx = crypto.signTransaction(PASS1, utx);
        TransactionBroadcast tx = nodeService.broadcastTransaction(stx).blockingGet();
        forgeBlock(PASS1, tx);
        
        AT at = nodeService.getAt(SignumAddress.fromId(tx.getTransactionId().getSignedLongId())).blockingGet();
        assertEquals(name, at.getName());
        assertEquals(description, at.getDescription());
        assertEquals(activation, at.getMinimumActivation());
        
        byte[] ourCode = new byte[at.getMachineCode().length];
        System.arraycopy(lotteryAtCode, 0, ourCode, 0, lotteryAtCode.length);
        assertArrayEquals(ourCode, at.getMachineCode());
        
        Transaction confirmedTx = nodeService.getTransaction(tx.getTransactionId()).blockingGet();
        
        assertEquals(message, ((PlaintextMessageAppendix)confirmedTx.getAppendages()[0]).getMessage());
    }

    @Test
    public void testCreateATTransactionInitiateContract() {
        SignumValue activation = SignumValue.fromSigna(2);
        byte[] lotteryAtCode = Hex.decode("1e000000003901090000006400000000000000351400000000000201000000000000000104000000803a0900000000000601000000040000003615000200000000000000260200000036160003000000020000001f030000000100000072361b0008000000020000002308000000090000000f1af3000000361c0004000000020000001e0400000035361700040000000200000026040000007f2004000000050000001e02050000000400000036180006000000020000000200000000030000001a39000000352000070000001b07000000181b0500000012332100060000001a310100000200000000030000001a1a0000003618000a0000000200000020080000000900000023070800000009000000341f00080000000a0000001a78000000341f00080000000a0000001ab800000002000000000400000003050000001a1a000000");
        byte[] lotteryAtCreationBytes = SignumCrypto.getInstance().getATCreationBytes((short) 2, lotteryAtCode, new byte[0], (short) 1, (short) 1, (short) 1, activation);
        assertEquals("02000000020001000100010000c2eb0b0000000044011e000000003901090000006400000000000000351400000000000201000000000000000104000000803a0900000000000601000000040000003615000200000000000000260200000036160003000000020000001f030000000100000072361b0008000000020000002308000000090000000f1af3000000361c0004000000020000001e0400000035361700040000000200000026040000007f2004000000050000001e02050000000400000036180006000000020000000200000000030000001a39000000352000070000001b07000000181b0500000012332100060000001a310100000200000000030000001a1a0000003618000a0000000200000020080000000900000023070800000009000000341f00080000000a0000001a78000000341f00080000000a0000001ab800000002000000000400000003050000001a1a00000000", Hex.toHexString(lotteryAtCreationBytes));

        String name = "TestAT";
        String description = "An AT For Testing";
        TransactionBuilder tb = new TransactionBuilder(TransactionBuilder.CREATE_AT,
                ACCOUNT1.getPublicKey(), SignumValue.fromSigna(2), 1440)
                .name(name)
                .description(description)
                .creationBytes(lotteryAtCreationBytes);

        byte []utx = nodeService.generateTransaction(tb).blockingGet();
        assertTrue(tb.verify(utx));
        
        byte[] stx = crypto.signTransaction(PASS1, utx);
        TransactionBroadcast tx = nodeService.broadcastTransaction(stx).blockingGet();
        
        tb = new TransactionBuilder(TransactionBuilder.SEND_MONEY,
                ACCOUNT1.getPublicKey(), SignumValue.fromSigna(0.01), 1440)
                .recipient(SignumAddress.fromId(tx.getTransactionId()))
                .amount(activation)
                .referencedTransactionFullHash(tx.getFullHash());
        confirm(PASS1, tb);
    }

}
