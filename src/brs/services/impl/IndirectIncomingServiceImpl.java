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

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;

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
        indirectIncomingStore.addIndirectIncomings(getIndirectIncomings(transaction).stream()
                .map(account -> new IndirectIncomingStore.IndirectIncoming(account, transaction.getId(), transaction.getHeight()))
                .collect(Collectors.toList()));
    }

    @Override
    public boolean isIndirectlyReceiving(Transaction transaction, long accountId) {
        // It would be confusing to have inconsistent behaviour so even when not loading from database we should disable when told to do so.
        if (disabled) return false;
        return getIndirectIncomings(transaction).contains(accountId);
    }

    private Collection<Long> getIndirectIncomings(Transaction transaction) {
        if (Objects.equals(transaction.getType(), TransactionType.Payment.MULTI_OUT)) {
            return getMultiOutRecipients(transaction);
        } else if (Objects.equals(transaction.getType(), TransactionType.Payment.MULTI_SAME_OUT)) {
            return getMultiOutSameRecipients(transaction);
        } else {
            return Collections.emptyList();
        }
    }

    private Collection<Long> getMultiOutRecipients(Transaction transaction) {
        if (!Objects.equals(transaction.getType(), TransactionType.Payment.MULTI_OUT)
                || !(transaction.getAttachment() instanceof Attachment.PaymentMultiOutCreation))
            throw new IllegalArgumentException("Wrong transaction type");

        Attachment.PaymentMultiOutCreation attachment = (Attachment.PaymentMultiOutCreation) transaction.getAttachment();
        return attachment.getRecipients().stream()
                .map(recipient -> recipient.get(0))
                .collect(Collectors.toList());
    }

    private Collection<Long> getMultiOutSameRecipients(Transaction transaction) {
        if (!Objects.equals(transaction.getType(), TransactionType.Payment.MULTI_SAME_OUT)
                || !(transaction.getAttachment() instanceof Attachment.PaymentMultiSameOutCreation))
            throw new IllegalArgumentException("Wrong transaction type");

        Attachment.PaymentMultiSameOutCreation attachment = (Attachment.PaymentMultiSameOutCreation) transaction.getAttachment();
        return attachment.getRecipients();
    }
}
