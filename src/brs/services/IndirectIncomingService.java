package brs.services;

import brs.Transaction;

public interface IndirectIncomingService {
    void processTransaction(Transaction transaction);
}
