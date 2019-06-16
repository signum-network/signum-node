package brs.at;

import brs.Appendix;
import brs.Burst;
import brs.Transaction;
import brs.crypto.Crypto;
import brs.fluxcapacitor.FluxValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Arrays;

public class AtApiPlatformImpl extends AtApiImpl {

    private static final Logger logger = LoggerFactory.getLogger(AtApiPlatformImpl.class);

    private static final AtApiPlatformImpl instance = new AtApiPlatformImpl();


    private AtApiPlatformImpl() {
    }

    public static AtApiPlatformImpl getInstance() {
        return instance;
    }

    private static Long findTransaction(int startHeight, int endHeight, Long atID, int numOfTx, long minAmount) {
        return Burst.getStores().getAtStore().findTransaction(startHeight, endHeight, atID, numOfTx, minAmount);
    }

    private static int findTransactionHeight(Long transactionId, int height, Long atID, long minAmount) {
        return Burst.getStores().getAtStore().findTransactionHeight(transactionId, height, atID, minAmount);
    }

    @Override
    public long getBlockTimestamp(AtMachineState state) {
        int height = state.getHeight();
        return AtApiHelper.getLongTimestamp(height, 0);
    }

    @Override
    public long getCreationTimestamp(AtMachineState state) {
        return AtApiHelper.getLongTimestamp(state.getCreationBlockHeight(), 0);
    }

    @Override
    public long getLastBlockTimestamp(AtMachineState state) {
        int height = state.getHeight() - 1;
        return AtApiHelper.getLongTimestamp(height, 0);
    }

    @Override
    public void putLastBlockHashInA(AtMachineState state) {
        ByteBuffer b = ByteBuffer.allocate(state.getA1().length * 4);
        b.order(ByteOrder.LITTLE_ENDIAN);

        b.put(Burst.getBlockchain().getBlockAtHeight(state.getHeight() - 1).getBlockHash());

        b.clear();

        byte[] temp = new byte[8];

        b.get(temp, 0, 8);
        state.setA1(temp);

        b.get(temp, 0, 8);
        state.setA2(temp);

        b.get(temp, 0, 8);
        state.setA3(temp);

        b.get(temp, 0, 8);
        state.setA4(temp);
    }

    @Override
    public void aToTxAfterTimestamp(long val, AtMachineState state) {

        int height = AtApiHelper.longToHeight(val);
        int numOfTx = AtApiHelper.longToNumOfTx(val);

        byte[] b = state.getId();

        long tx = findTransaction(height, state.getHeight(), AtApiHelper.getLong(b), numOfTx, state.minActivationAmount());
        logger.debug("tx with id {} found", tx);
        clearA(state);
        state.setA1(AtApiHelper.getByteArray(tx));

    }

    @Override
    public long getTypeForTxInA(AtMachineState state) {
        long txid = AtApiHelper.getLong(state.getA1());

        Transaction tx = Burst.getBlockchain().getTransaction(txid);

        if (tx == null || (tx.getHeight() >= state.getHeight())) {
            return -1;
        }

        if (tx.getMessage() != null) {
            return 1;
        }

        return 0;
    }

    @Override
    public long getAmountForTxInA(AtMachineState state) {
        long txId = AtApiHelper.getLong(state.getA1());

        Transaction tx = Burst.getBlockchain().getTransaction(txId);

        if (tx == null || (tx.getHeight() >= state.getHeight())) {
            return -1;
        }

        if ((tx.getMessage() == null || Burst.getFluxCapacitor().getValue(FluxValues.AT_FIX_BLOCK_2, state.getHeight())) && state.minActivationAmount() <= tx.getAmountNQT()) {
            return tx.getAmountNQT() - state.minActivationAmount();
        }

        return 0;
    }

    @Override
    public long getTimestampForTxInA(AtMachineState state) {
        long txId = AtApiHelper.getLong(state.getA1());
        logger.debug("get timestamp for tx with id {} found", txId);
        Transaction tx = Burst.getBlockchain().getTransaction(txId);

        if (tx == null || (tx.getHeight() >= state.getHeight())) {
            return -1;
        }

        byte[] b = state.getId();
        int blockHeight = tx.getHeight();
        int txHeight = findTransactionHeight(txId, blockHeight, AtApiHelper.getLong(b), state.minActivationAmount());

        return AtApiHelper.getLongTimestamp(blockHeight, txHeight);
    }

    @Override
    public long getRandomIdForTxInA(AtMachineState state) {
        long txId = AtApiHelper.getLong(state.getA1());

        Transaction tx = Burst.getBlockchain().getTransaction(txId);

        if (tx == null || (tx.getHeight() >= state.getHeight())) {
            return -1;
        }

        int txBlockHeight = tx.getHeight();
        int blockHeight = state.getHeight();

        if (blockHeight - txBlockHeight < AtConstants.getInstance().blocksForRandom(blockHeight)) { //for tests - for real case 1440
            state.setWaitForNumberOfBlocks((int) AtConstants.getInstance().blocksForRandom(blockHeight) - (blockHeight - txBlockHeight));
            state.getMachineState().pc -= 7;
            state.getMachineState().stopped = true;
            return 0;
        }

        MessageDigest digest = Crypto.sha256();

        byte[] senderPublicKey = tx.getSenderPublicKey();

        ByteBuffer bf = ByteBuffer.allocate(32 + Long.SIZE + senderPublicKey.length);
        bf.order(ByteOrder.LITTLE_ENDIAN);
        bf.put(Burst.getBlockchain().getBlockAtHeight(blockHeight - 1).getGenerationSignature());
        bf.putLong(tx.getId());
        bf.put(senderPublicKey);

        digest.update(bf.array());
        byte[] byteRandom = digest.digest();

        return Math.abs(AtApiHelper.getLong(Arrays.copyOfRange(byteRandom, 0, 8)));
    }

    @Override
    public void messageFromTxInAToB(AtMachineState state) {
        long txid = AtApiHelper.getLong(state.getA1());

        Transaction tx = Burst.getBlockchain().getTransaction(txid);
        if (tx != null && tx.getHeight() >= state.getHeight()) {
            tx = null;
        }

        ByteBuffer b = ByteBuffer.allocate(state.getA1().length * 4);
        b.order(ByteOrder.LITTLE_ENDIAN);
        if (tx != null) {
            Appendix.Message txMessage = tx.getMessage();
            if (txMessage != null) {
                byte[] message = txMessage.getMessageBytes();
                if (message.length <= state.getA1().length * 4) {
                    b.put(message);
                }
            }
        }

        b.clear();

        byte[] temp = new byte[8];

        b.get(temp, 0, 8);
        state.setB1(temp);

        b.get(temp, 0, 8);
        state.setB2(temp);

        b.get(temp, 0, 8);
        state.setB3(temp);

        b.get(temp, 0, 8);
        state.setB4(temp);

    }

    @Override
    public void bToAddressOfTxInA(AtMachineState state) {
        long txId = AtApiHelper.getLong(state.getA1());

        clearB(state);

        Transaction tx = Burst.getBlockchain().getTransaction(txId);
        if (tx != null && tx.getHeight() >= state.getHeight()) {
            tx = null;
        }
        if (tx != null) {
            long address = tx.getSenderId();
            state.setB1(AtApiHelper.getByteArray(address));
        }
    }

    @Override
    public void bToAddressOfCreator(AtMachineState state) {
        long creator = AtApiHelper.getLong(state.getCreator());

        clearB(state);

        state.setB1(AtApiHelper.getByteArray(creator));

    }

    @Override
    public void putLastBlockGenerationSignatureInA(AtMachineState state) {
        ByteBuffer b = ByteBuffer.allocate(state.getA1().length * 4);
        b.order(ByteOrder.LITTLE_ENDIAN);

        b.put(Burst.getBlockchain().getBlockAtHeight(state.getHeight() - 1).getGenerationSignature());

        byte[] temp = new byte[8];

        b.get(temp, 0, 8);
        state.setA1(temp);

        b.get(temp, 0, 8);
        state.setA2(temp);

        b.get(temp, 0, 8);
        state.setA3(temp);

        b.get(temp, 0, 8);
        state.setA4(temp);
    }

    @Override
    public long getCurrentBalance(AtMachineState state) {
        if (!Burst.getFluxCapacitor().getValue(FluxValues.AT_FIX_BLOCK_2, state.getHeight())) {
            return 0;
        }

        return state.getgBalance();
    }

    @Override
    public long getPreviousBalance(AtMachineState state) {
        if (!Burst.getFluxCapacitor().getValue(FluxValues.AT_FIX_BLOCK_2, state.getHeight())) {
            return 0;
        }

        return state.getpBalance();
    }

    @Override
    public void sendToAddressInB(long val, AtMachineState state) {
        if (val < 1)
            return;

        if (val < state.getgBalance()) {
            AtTransaction tx = new AtTransaction(state.getId(), state.getB1().clone(), val, null);
            state.addTransaction(tx);

            state.setgBalance(state.getgBalance() - val);
        } else {
            AtTransaction tx = new AtTransaction(state.getId(), state.getB1().clone(), state.getgBalance(), null);
            state.addTransaction(tx);

            state.setgBalance(0L);
        }
    }

    @Override
    public void sendAllToAddressInB(AtMachineState state) {
        AtTransaction tx = new AtTransaction(state.getId(), state.getB1().clone(), state.getgBalance(), null);
        state.addTransaction(tx);
        state.setgBalance(0L);
    }

    @Override
    public void sendOldToAddressInB(AtMachineState state) {
        if (state.getpBalance() > state.getgBalance()) {
            AtTransaction tx = new AtTransaction(state.getId(), state.getB1(), state.getgBalance(), null);
            state.addTransaction(tx);

            state.setgBalance(0L);
            state.setpBalance(0L);
        } else {
            AtTransaction tx = new AtTransaction(state.getId(), state.getB1(), state.getpBalance(), null);
            state.addTransaction(tx);

            state.setgBalance(state.getgBalance() - state.getpBalance());
            state.setpBalance(0L);
        }
    }

    @Override
    public void sendAToAddressInB(AtMachineState state) {
        ByteBuffer b = ByteBuffer.allocate(32);
        b.order(ByteOrder.LITTLE_ENDIAN);
        b.put(state.getA1());
        b.put(state.getA2());
        b.put(state.getA3());
        b.put(state.getA4());
        b.clear();

        AtTransaction tx = new AtTransaction(state.getId(), state.getB1(), 0L, b.array());
        state.addTransaction(tx);
    }

    @Override
    public long addMinutesToTimestamp(long val1, long val2, AtMachineState state) {
        int height = AtApiHelper.longToHeight(val1);
        int numOfTx = AtApiHelper.longToNumOfTx(val1);
        int addHeight = height + (int) (val2 / AtConstants.getInstance().averageBlockMinutes(state.getHeight()));

        return AtApiHelper.getLongTimestamp(addHeight, numOfTx);
    }
}
