package brs.services.impl.chainscan;

import brs.Block;
import brs.Transaction;
import brs.crypto.Crypto;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code BLOCK_PAYLOAD}: the payload hash of a block matches its signed transactions in {@code db_id}
 * order, and the stored totals cover them, as checked when a node accepts the block.
 *
 * <p>{@code BLOCK_PAYLOAD_LENGTH}: the payload length is the size of the transactions plus the AT
 * bytes, as the generator computes it. Nodes only check it against the maximum, so a mismatch is a
 * {@code WARN}.
 */
public class BlockPayloadCheck implements ChainCheck {

    public static final String BLOCK_PAYLOAD = "BLOCK_PAYLOAD";
    public static final String BLOCK_PAYLOAD_LENGTH = "BLOCK_PAYLOAD_LENGTH";

    private final FluxCapacitor fluxCapacitor;
    private final int cashBackFactor;

    public BlockPayloadCheck(FluxCapacitor fluxCapacitor, int cashBackFactor) {
        this.fluxCapacitor = fluxCapacitor;
        this.cashBackFactor = cashBackFactor;
    }

    @Override
    public String group() {
        return "block";
    }

    @Override
    public List<Finding> check(BlockContext context) {
        ScannedBlock stored = context.block();
        Block block = stored.block();
        if (block == null) {
            return List.of();
        }
        List<Transaction> transactions = new ArrayList<>();
        for (ScannedTransaction transaction : context.signedTransactions()) {
            if (transaction.transaction() == null) {
                return List.of(); // TX_LOAD and BLOCK_LOAD report it
            }
            transactions.add(transaction.transaction());
        }
        try {
            List<Finding> findings = new ArrayList<>();
            String problem = problem(block, stored.height(), transactions);
            if (problem != null) {
                findings.add(Finding.ofBlock(BLOCK_PAYLOAD, Severity.ERROR, stored, problem));
            }
            long length = payloadLength(block, transactions);
            if (length != block.getPayloadLength()) {
                findings.add(Finding.ofBlock(BLOCK_PAYLOAD_LENGTH, Severity.WARN, stored, "payload length "
                        + block.getPayloadLength() + " differs from the transaction and AT bytes " + length));
            }
            return findings;
        } catch (RuntimeException e) {
            return List.of(Finding.ofBlock(BLOCK_PAYLOAD, Severity.ERROR, stored, "cannot compute the payload: " + e));
        }
    }

    private static long payloadLength(Block block, List<Transaction> transactions) {
        long length = block.getBlockAts() == null ? 0 : block.getBlockAts().length;
        for (Transaction transaction : transactions) {
            length += transaction.getSize();
        }
        return length;
    }

    private String problem(Block block, int height, List<Transaction> transactions) {
        MessageDigest digest = Crypto.sha256();
        long totalAmount = 0;
        long totalFee = 0;
        long totalCashBack = 0;
        for (Transaction transaction : transactions) {
            digest.update(transaction.getBytes());
            totalAmount = Math.addExact(totalAmount, transaction.getAmountNqt());
            totalFee = Math.addExact(totalFee, transaction.getFeeNqt());
            totalCashBack = Math.addExact(totalCashBack, transaction.getFeeNqt() / cashBackFactor);
        }
        if (!Arrays.equals(digest.digest(), block.getPayloadHash())) {
            return "payload hash doesn't match the " + transactions.size() + " signed transactions";
        }
        if (totalAmount > block.getTotalAmountNqt() || totalFee > block.getTotalFeeNqt()) {
            return "transaction amounts " + totalAmount + " or fees " + totalFee + " exceed the block totals "
                    + block.getTotalAmountNqt() + " and " + block.getTotalFeeNqt();
        }
        if (fluxCapacitor.getValue(FluxValues.SMART_FEES, height)
                && totalCashBack != block.getTotalFeeCashBackNqt()) {
            return "fee cash back " + block.getTotalFeeCashBackNqt() + " differs from the calculated " + totalCashBack;
        }
        return null;
    }
}
