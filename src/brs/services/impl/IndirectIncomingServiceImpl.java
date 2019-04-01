package brs.services.impl;

import brs.Attachment;
import brs.Transaction;
import brs.TransactionType;
import brs.db.store.IndirectIncomingStore;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.IndirectIncomingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class IndirectIncomingServiceImpl implements IndirectIncomingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndirectIncomingServiceImpl.class);

    private final IndirectIncomingStore indirectIncomingStore;
    private final boolean disabled;

    public IndirectIncomingServiceImpl(IndirectIncomingStore indirectIncomingStore, PropertyService propertyService) {
        this.indirectIncomingStore = indirectIncomingStore;
        this.disabled = !propertyService.getBoolean(Props.INDIRECT_INCOMING_SERVICE_ENABLE);
        if (disabled) {
            LOGGER.warn("Indirect Incoming Service Disabled!");
        }
    }

    @Override
    public void processTransaction(Transaction transaction) {
        if (disabled) return;
        indirectIncomingStore.addIndirectIncomings(getIndirectIncomings(transaction));
    }

    @Override
    public boolean isIndirectlyReceiving(Transaction transaction, long accountId) {
        // It would be confusing to have inconsistent behaviour so even when not loading from database we should disable when told to do so.
        if (disabled) return false;
        List<IndirectIncomingStore.IndirectIncoming> indirectIncomings = getIndirectIncomings(transaction);
        for (IndirectIncomingStore.IndirectIncoming indirectIncoming : indirectIncomings) {
            if (indirectIncoming.getAccountId() == accountId) {
                return true;
            }
        }
        return false;
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
