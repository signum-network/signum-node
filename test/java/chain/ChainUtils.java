package chain;

import brs.Signum;
import java.io.File;
import signumj.Constants;
import signumj.crypto.SignumCrypto;
import signumj.entity.SignumAddress;
import signumj.entity.response.Transaction;
import signumj.entity.response.TransactionBroadcast;
import signumj.service.NodeService;
import signumj.service.TransactionBuilder;
import signumj.service.impl.HttpNodeService;

public class ChainUtils {

    public static NodeService nodeService;
    public static SignumCrypto crypto;
    static final String PASS1 = "a test passphrase 1";
    static final String PASS2 = "a test passphrase 2";
    static final String PASS3 = "a test passphrase 3";
    static final String PASS4 = "a test passphrase 4";
    
    static final SignumAddress ACCOUNT1 = SignumCrypto.getInstance().getAddressFromPassphrase(PASS1);
    static final SignumAddress ACCOUNT2 = SignumCrypto.getInstance().getAddressFromPassphrase(PASS2);
    static final SignumAddress ACCOUNT3 = SignumCrypto.getInstance().getAddressFromPassphrase(PASS3);
    static final SignumAddress ACCOUNT4 = SignumCrypto.getInstance().getAddressFromPassphrase(PASS4);
    
    public static boolean setupNode() {
        if(nodeService != null) {
            return true;
        }

        // Remove any chain left over from a previous run so every JVM starts at genesis.
        // The junit DB is a file (conf/junit -> ./db/signum-mock.sqlite.db); without this
        // the chain accumulates across runs, making height-dependent tests order-/run-dependent.
        deleteMockDb();

        // a mock node with memory DB
        String[] args = {"-l", "-c", "conf/junit"};
        Signum.main(args);

        crypto = SignumCrypto.getInstance();
        nodeService = new HttpNodeService(Constants.HTTP_NODE_LOCAL_TESTNET, "mock-node-testing");
        long startupTime = System.currentTimeMillis();
        while(System.currentTimeMillis() - startupTime < 10000) {
            // we wait for the node to boot
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
            try{
                nodeService.getBlockChainStatus().blockingGet();

                // make the accounts to have some balance
                forgeBlock(PASS1);
                forgeBlock(PASS2);
                forgeBlock(PASS3);
                forgeBlock(PASS4);

                return true;
            }
            catch (Exception e) {
            }
        }
        return false;
    }

    private static void deleteMockDb() {
        for (String suffix : new String[] {"", "-shm", "-wal"}) {
            File dbFile = new File("db/signum-mock.sqlite.db" + suffix);
            if (dbFile.exists()) {
                dbFile.delete();
            }
        }
    }

    public static void forgeBlock(String pass, TransactionBroadcast ... txs) {
        for (int i = 0; i < 4; i++) {
            // retries
            boolean allFound = true;
            for(TransactionBroadcast tx : txs){
                try {
                    Transaction txConfirmed = nodeService.getTransaction(tx.getTransactionId()).blockingGet();
                    if (txConfirmed.getBlockHeight() == Integer.MAX_VALUE) {
                        allFound = false;
                        break;
                    }
                }
                catch (Exception e){
                    allFound = false;
                }
            }
            if (allFound)
                break;

            forgeBlock(pass);
        }
    }
    
    public static void forgeBlock() {
        forgeBlock(PASS1);
    }
    
    /**
     * Forge block by mock mining for the given passphrase with the given millis timeout.
     *
     * Just for testing purposes.
     */
    public static void forgeBlock(String pass) {
        int timeout = 2000;
        try {
            Thread.sleep(200);
            long height = nodeService.getMiningInfoSingle().blockingGet().getHeight();
            long startTimer = System.currentTimeMillis();
            nodeService.submitNonce(pass, "0", null).blockingGet();
            while(true) {
                Thread.sleep(50);
                long newHeight = nodeService.getMiningInfoSingle().blockingGet().getHeight();
                long timeElapsed = System.currentTimeMillis() - startTimer;
                if(newHeight > height || timeElapsed > timeout) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Get the transaction bytes, verify, sign, broadcast, and confirm the tx in a block
     * @param pass
     * @param tb
     */
    public static TransactionBroadcast confirm(String pass, TransactionBuilder tb) {
        byte []utx = nodeService.generateTransaction(tb).blockingGet();
        if(!tb.verify(utx)) {
            throw new IllegalArgumentException("transaction bytes do not match");
        }
        byte[] stx = crypto.signTransaction(pass, utx);
        TransactionBroadcast tx = nodeService.broadcastTransaction(stx).blockingGet();
        forgeBlock(PASS1, tx);
        return tx;
    }
}
