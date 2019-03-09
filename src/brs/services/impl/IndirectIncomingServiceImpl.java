package brs.services.impl;

import brs.Attachment;
import brs.Transaction;
import brs.TransactionType;
import brs.db.store.IndirectIncomingStore;
import brs.services.IndirectIncomingService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class IndirectIncomingServiceImpl implements IndirectIncomingService {
    private final IndirectIncomingStore indirectIncomingStore;

    public IndirectIncomingServiceImpl(IndirectIncomingStore indirectIncomingStore) {
        this.indirectIncomingStore = indirectIncomingStore;
    }

    @Override
    public void processTransaction(Transaction transaction) {
        indirectIncomingStore.addIndirectIncomings(getIndirectIncomings(transaction));
    }

    private List<IndirectIncomingStore.IndirectIncoming> getIndirectIncomings(Transaction transaction) {
        List<IndirectIncomingStore.IndirectIncoming> indirectIncomings = new ArrayList<>();
        if (Objects.equals(transaction.getType(), TransactionType.Payment.MULTI_OUT)) {
            indirectIncomings.addAll(getMultiOutRecipients(transaction));
        } else if (Objects.equals(transaction.getType(), TransactionType.Payment.MULTI_SAME_OUT)) {
            indirectIncomings.addAll(getMultiOutSameRecipients(transaction));
        }
        return indirectIncomings;
    }

    private List<IndirectIncomingStore.IndirectIncoming> getMultiOutRecipients(Transaction transaction) {
        if (!Objects.equals(transaction.getType(), TransactionType.Payment.MULTI_OUT)
                || !(transaction.getAttachment() instanceof Attachment.PaymentMultiOutCreation))
            throw new IllegalArgumentException("Wrong transaction type");

        List<IndirectIncomingStore.IndirectIncoming> indirectIncomings = new ArrayList<>();
        Attachment.PaymentMultiOutCreation attachment = (Attachment.PaymentMultiOutCreation) transaction.getAttachment();
        attachment.getRecipients().forEach(recipient -> indirectIncomings.add(new IndirectIncomingStore.IndirectIncoming(recipient.get(0), transaction.getId(), transaction.getHeight())));
        return indirectIncomings;
    }

    private List<IndirectIncomingStore.IndirectIncoming> getMultiOutSameRecipients(Transaction transaction) {
        if (!Objects.equals(transaction.getType(), TransactionType.Payment.MULTI_SAME_OUT)
                || !(transaction.getAttachment() instanceof Attachment.PaymentMultiSameOutCreation))
            throw new IllegalArgumentException("Wrong transaction type");

        List<IndirectIncomingStore.IndirectIncoming> indirectIncomings = new ArrayList<>();
        Attachment.PaymentMultiSameOutCreation attachment = (Attachment.PaymentMultiSameOutCreation) transaction.getAttachment();
        attachment.getRecipients().forEach(recipient -> indirectIncomings.add(new IndirectIncomingStore.IndirectIncoming(recipient, transaction.getId(), transaction.getHeight())));
        return indirectIncomings;
    }
}
