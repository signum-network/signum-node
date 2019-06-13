package brs.services.impl;

import brs.*;
import brs.db.store.IndirectIncomingStore;
import brs.props.PropertyService;
import brs.props.Props;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public class IndirectIncomingServiceImplTest {
    private final AtomicReference<Consumer<List<IndirectIncomingStore.IndirectIncoming>>> addIndirectIncomingsRunnable = new AtomicReference<>();
    private IndirectIncomingServiceImpl indirectIncomingService;

    @Before
    public void setUpIndirectIncomingServiceImplTest() {
        PropertyService propertyService = mock(PropertyService.class);
        IndirectIncomingStore indirectIncomingStore = mock(IndirectIncomingStore.class);
        when(propertyService.getBoolean(Props.INDIRECT_INCOMING_SERVICE_ENABLE)).thenReturn(true);
        doAnswer(invocation -> {
            addIndirectIncomingsRunnable.get().accept(invocation.getArgument(0));
            return null;
        }).when(indirectIncomingStore).addIndirectIncomings(any());
        indirectIncomingService = new IndirectIncomingServiceImpl(indirectIncomingStore, propertyService);
    }

    @Test
    public void testIndirectIncomingServiceImplTestMultiOutTransaction() throws BurstException.NotValidException {
        addIndirectIncomingsRunnable.set(indirectIncomings -> {
            assertEquals(4, indirectIncomings.size());
            assertEquals(new HashSet<>(indirectIncomings).size(), indirectIncomings.size()); // Assert that there are no duplicates
        });
        List<List<Long>> recipients = new ArrayList<>();
        recipients.add(Arrays.asList(1L, Constants.ONE_BURST));
        recipients.add(Arrays.asList(2L, Constants.ONE_BURST));
        recipients.add(Arrays.asList(3L, Constants.ONE_BURST));
        recipients.add(Arrays.asList(4L, Constants.ONE_BURST));
        Attachment.PaymentMultiOutCreation attachment = mock(Attachment.PaymentMultiOutCreation.class);
        when(attachment.getRecipients()).thenReturn(recipients);
        Transaction multiOut = mock(Transaction.class);
        when(multiOut.getType()).thenReturn(TransactionType.Payment.MULTI_OUT);
        when(multiOut.getAttachment()).thenReturn(attachment);
        indirectIncomingService.processTransaction(multiOut);
    }

    @Test
    public void testIndirectIncomingServiceImplTestMultiOutSameTransaction() throws BurstException.NotValidException {
        addIndirectIncomingsRunnable.set(indirectIncomings -> {
            assertEquals(4, indirectIncomings.size());
            assertEquals(new HashSet<>(indirectIncomings).size(), indirectIncomings.size()); // Assert that there are no duplicates
        });
        List<Long> recipients = new ArrayList<>();
        recipients.add(1L);
        recipients.add(2L);
        recipients.add(3L);
        recipients.add(4L);
        Attachment.PaymentMultiSameOutCreation attachment = mock(Attachment.PaymentMultiSameOutCreation.class);
        when(attachment.getRecipients()).thenReturn(recipients);
        Transaction multiOutSame = mock(Transaction.class);
        when(multiOutSame.getType()).thenReturn(TransactionType.Payment.MULTI_SAME_OUT);
        when(multiOutSame.getAttachment()).thenReturn(attachment);
        indirectIncomingService.processTransaction(multiOutSame);
    }

    @Test
    public void testIndirectIncomingServiceImplTestOrdinaryTransaction() {
        addIndirectIncomingsRunnable.set(indirectIncomings -> assertEquals(0, indirectIncomings.size()));
        Transaction ordinaryTransaction = mock(Transaction.class);
        when(ordinaryTransaction.getAttachment()).thenReturn(Attachment.ORDINARY_PAYMENT);
        when(ordinaryTransaction.getType()).thenReturn(TransactionType.Payment.ORDINARY);
        indirectIncomingService.processTransaction(ordinaryTransaction);
    }
}
