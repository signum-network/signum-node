/*
 * Copyright (c) 2014 CIYAM Developers

 Distributed under the MIT/X11 software license, please refer to the file license.txt
 in the root project directory or http://www.opensource.org/licenses/mit-license.php.
*/

package brs.at;

import brs.Burst;
import brs.fluxcapacitor.FluxValues;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.TreeSet;


public class AtMachineState {

    private final int creationBlockHeight;
    private final int sleepBetween;
    private final ByteBuffer apCode;
    private final LinkedHashMap<ByteBuffer, AtTransaction> transactions;
    private short version;
    private long gBalance;
    private long pBalance;
    private MachineState machineState;
    private int cSize;
    private int dSize;
    private int cUserStackBytes;
    private int cCallStackBytes;
    private byte[] atID;
    private byte[] creator;
    private int waitForNumberOfBlocks;
    private boolean freezeWhenSameBalance;
    private long minActivationAmount;
    private ByteBuffer apData;
    private int height;

    protected AtMachineState(byte[] atId, byte[] creator, short version,
                             int height,
                             byte[] stateBytes, int cSize, int dSize, int cUserStackBytes, int cCallStackBytes,
                             int creationBlockHeight, int sleepBetween,
                             boolean freezeWhenSameBalance, long minActivationAmount, byte[] apCode) {
        this.atID = atId;
        this.creator = creator;
        this.version = version;
        this.height = height;
        this.machineState = new MachineState();
        this.setState(stateBytes);
        this.cSize = cSize;
        this.dSize = dSize;
        this.cUserStackBytes = cUserStackBytes;
        this.cCallStackBytes = cCallStackBytes;
        this.creationBlockHeight = creationBlockHeight;
        this.sleepBetween = sleepBetween;
        this.freezeWhenSameBalance = freezeWhenSameBalance;
        this.minActivationAmount = minActivationAmount;

        this.apCode = ByteBuffer.allocate(apCode.length);
        this.apCode.order(ByteOrder.LITTLE_ENDIAN);
        this.apCode.put(apCode);
        this.apCode.clear();

        transactions = new LinkedHashMap<>();
    }

    protected AtMachineState(byte[] atId, byte[] creator, byte[] creationBytes, int height) {
        this.version = AtConstants.getInstance().atVersion(height);
        this.atID = atId;
        this.creator = creator;

        ByteBuffer b = ByteBuffer.allocate(creationBytes.length);
        b.order(ByteOrder.LITTLE_ENDIAN);

        b.put(creationBytes);
        b.clear();

        this.version = b.getShort();

        b.getShort(); //future: reserved for future needs

        int pageSize = (int) AtConstants.getInstance().pageSize(height);
        short codePages = b.getShort();
        short dataPages = b.getShort();
        short callStackPages = b.getShort();
        short userStackPages = b.getShort();

        this.cSize = codePages * pageSize;
        this.dSize = dataPages * pageSize;
        this.cCallStackBytes = callStackPages * pageSize;
        this.cUserStackBytes = userStackPages * pageSize;

        this.minActivationAmount = b.getLong();

        int codeLen;
        if (codePages * pageSize < pageSize + 1) {
            codeLen = b.get();
            if (codeLen < 0)
                codeLen += (Byte.MAX_VALUE + 1) * 2;
        } else if (codePages * pageSize < Short.MAX_VALUE + 1) {
            codeLen = b.getShort();
            if (codeLen < 0)
                codeLen += (Short.MAX_VALUE + 1) * 2;
        } else {
            codeLen = b.getInt();
        }
        byte[] code = new byte[codeLen];
        b.get(code, 0, codeLen);

        this.apCode = ByteBuffer.allocate(cSize);
        this.apCode.order(ByteOrder.LITTLE_ENDIAN);
        this.apCode.put(code);
        this.apCode.clear();

        int dataLen;
        if (dataPages * pageSize < 257) {
            dataLen = b.get();
            if (dataLen < 0)
                dataLen += (Byte.MAX_VALUE + 1) * 2;
        } else if (dataPages * pageSize < Short.MAX_VALUE + 1) {
            dataLen = b.getShort();
            if (dataLen < 0)
                dataLen += (Short.MAX_VALUE + 1) * 2;
        } else {
            dataLen = b.getInt();
        }
        byte[] data = new byte[dataLen];
        b.get(data, 0, dataLen);

        this.apData = ByteBuffer.allocate(this.dSize + this.cCallStackBytes + this.cUserStackBytes);
        this.apData.order(ByteOrder.LITTLE_ENDIAN);
        this.apData.put(data);
        this.apData.clear();

        this.height = height;
        this.creationBlockHeight = height;
        this.waitForNumberOfBlocks = 0;
        this.sleepBetween = 0;
        this.freezeWhenSameBalance = false;
        this.transactions = new LinkedHashMap<>();
        this.gBalance = 0;
        this.pBalance = 0;
        this.machineState = new MachineState();
    }

    byte[] getA1() {
        return machineState.a1;
    }

    void setA1(byte[] a1) {
        this.machineState.a1 = a1.clone();
    }

    byte[] getA2() {
        return machineState.a2;
    }

    void setA2(byte[] a2) {
        this.machineState.a2 = a2.clone();
    }

    byte[] getA3() {
        return machineState.a3;
    }

    void setA3(byte[] a3) {
        this.machineState.a3 = a3.clone();
    }

    byte[] getA4() {
        return machineState.a4;
    }

    void setA4(byte[] a4) {
        this.machineState.a4 = a4.clone();
    }

    byte[] getB1() {
        return machineState.b1;
    }

    void setB1(byte[] b1) {
        this.machineState.b1 = b1.clone();
    }

    byte[] getB2() {
        return machineState.b2;
    }

    void setB2(byte[] b2) {
        this.machineState.b2 = b2.clone();
    }

    byte[] getB3() {
        return machineState.b3;
    }

    void setB3(byte[] b3) {
        this.machineState.b3 = b3.clone();
    }

    byte[] getB4() {
        return machineState.b4;
    }

    void setB4(byte[] b4) {
        this.machineState.b4 = b4.clone();
    }

    void addTransaction(AtTransaction tx) {

        ByteBuffer keyBuf = ByteBuffer.allocate(16);
        keyBuf.put(tx.getRecipientId());
        keyBuf.putLong(tx.getAssetId()); //add asset id to the key which might be 0L
        keyBuf.position(0);

        AtTransaction oldTx = transactions.get(keyBuf);
        if (oldTx == null || tx.getAsset() != null) {  //a duplicated mold call will replace the previous mold with the same asset
            transactions.put(keyBuf, tx);
        } else {
            AtTransaction newTx = new AtTransaction(tx.getSenderId(),
                tx.getRecipientId(),
                oldTx.getAmount() + tx.getAmount(),
                tx.getMessage() != null ? tx.getMessage() : oldTx.getMessage());

            if(tx.getAssetId() != 0L ){
                newTx.setAssetId(tx.getAssetId());
                newTx.setAssetAmount(oldTx.getAssetAmount() + tx.getAssetAmount());
            }

            transactions.put(keyBuf, newTx);
        }
    }

    protected void clearTransactions() {
        transactions.clear();
    }

    public Collection<AtTransaction> getTransactions() {
        return transactions.values();
    }

    public ByteBuffer getApCode() {
        return apCode;
    }

    public ByteBuffer getApData() {
        return apData;
    }

    public byte[] getApCodeBytes() {
        return apCode.array();
    }

    public byte[] getApDataBytes() {
        return apData.array();
    }

    public int getcCallStackBytes() {
        return cCallStackBytes;
    }

    protected void setcCallStackBytes(int cCallStackBytes) {
        this.cCallStackBytes = cCallStackBytes;
    }

    public int getcUserStackBytes() {
        return cUserStackBytes;
    }

    protected void setcUserStackBytes(int cUserStackBytes) {
        this.cUserStackBytes = cUserStackBytes;
    }

    public int getcSize() {
        return cSize;
    }

    protected void setcSize(int cSize) {
        this.cSize = cSize;
    }

    public int getdSize() {
        return dSize;
    }

    protected void setdSize(int dSize) {
        this.dSize = dSize;
    }

    public Long getgBalance() {
        return gBalance;
    }

    public void setgBalance(Long gBalance) {
        this.gBalance = gBalance;
    }

    public Long getpBalance() {
        return pBalance;
    }

    public void setpBalance(Long pBalance) {
        this.pBalance = pBalance;
    }

    public byte[] getId() {
        return atID;
    }

    public MachineState getMachineState() {
        return machineState;
    }

    public void setMachineState(MachineState machineState) {
        this.machineState = machineState;
    }

    protected int getWaitForNumberOfBlocks() {
        return this.waitForNumberOfBlocks;
    }

    public void setWaitForNumberOfBlocks(int waitForNumberOfBlocks) {
        this.waitForNumberOfBlocks = waitForNumberOfBlocks;
    }

    public byte[] getCreator() {
        return this.creator;
    }

    public int getCreationBlockHeight() {
        return this.creationBlockHeight;
    }

    public boolean freezeOnSameBalance() {
        return this.freezeWhenSameBalance;
    }

    public long minActivationAmount() {
        return this.minActivationAmount;
    }

    public void setMinActivationAmount(long minActivationAmount) {
        this.minActivationAmount = minActivationAmount;
    }

    public short getVersion() {
        return version;
    }

    public int getSleepBetween() {
        return sleepBetween;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    private byte[] getTransactionBytes() {
        ByteBuffer b = ByteBuffer.allocate((creator.length + 8) * transactions.size());
        b.order(ByteOrder.LITTLE_ENDIAN);
        for (AtTransaction tx : transactions.values()) {
            b.put(tx.getRecipientId());
            b.putLong(tx.getAmount());
        }
        return b.array();
    }

    protected byte[] getState() {
        byte[] stateBytes = machineState.getMachineStateBytes();
        byte[] dataBytes = apData.array();

        ByteBuffer b = ByteBuffer.allocate(getStateSize());
        b.order(ByteOrder.LITTLE_ENDIAN);

        b.put(stateBytes);
        b.putLong(gBalance);
        b.putLong(pBalance);
        b.putInt(waitForNumberOfBlocks);
        b.put(dataBytes);

        return b.array();
    }

    private void setState(byte[] state) {
        ByteBuffer b = ByteBuffer.allocate(state.length);
        b.order(ByteOrder.LITTLE_ENDIAN);
        b.put(state);
        b.flip();

        int stateSize = this.machineState.getSize();
        byte[] newMachineState = new byte[stateSize];
        b.get(newMachineState, 0, stateSize);
        this.machineState.setMachineState(newMachineState);

        gBalance = b.getLong();
        pBalance = b.getLong();
        waitForNumberOfBlocks = b.getInt();

        byte[] newApData = new byte[b.capacity() - b.position()];
        b.get(newApData);
        this.apData = ByteBuffer.allocate(newApData.length);
        this.apData.order(ByteOrder.LITTLE_ENDIAN);
        this.apData.put(newApData);
        this.apData.clear();
    }

    private int getStateSize() {
        return (this.machineState.getSize() + 8 + 8 + 4 + apData.capacity());
    }

    //these bytes are digested with MD5
    public byte[] getBytes() {
        byte[] txBytes = getTransactionBytes();
        byte[] stateBytes = machineState.getMachineStateBytes();
        byte[] dataBytes = apData.array();

        ByteBuffer b = ByteBuffer.allocate(atID.length + txBytes.length + stateBytes.length + dataBytes.length);
        b.order(ByteOrder.LITTLE_ENDIAN);

        b.put(atID);
        b.put(stateBytes);
        b.put(dataBytes);
        b.put(txBytes);

        return b.array();
    }

    public void setFreeze(boolean freeze) {
        this.freezeWhenSameBalance = freeze;
    }

    public class MachineState {
        final byte[] flags = new byte[2];
        final TreeSet<Integer> jumps = new TreeSet<>();
        boolean running;
        boolean stopped;
        boolean finished;
        boolean dead;
        int pc;
        int pcs;
        int opc;
        int cs;
        int us;
        int err;
        int steps;
        private byte[] a1 = new byte[8];
        private byte[] a2 = new byte[8];
        private byte[] a3 = new byte[8];
        private byte[] a4 = new byte[8];
        private byte[] b1 = new byte[8];
        private byte[] b2 = new byte[8];
        private byte[] b3 = new byte[8];
        private byte[] b4 = new byte[8];

        MachineState() {
            pcs = 0;
            reset();
        }

        public boolean isRunning() {
            return running;
        }

        public boolean isStopped() {
            return stopped;
        }

        public boolean isFinished() {
            return finished;
        }

        public boolean isDead() {
            return dead;
        }

        void reset() {
            pc = pcs;
            opc = 0;
            cs = 0;
            us = 0;
            err = -1;
            steps = 0;
            if (!jumps.isEmpty())
                jumps.clear();
            flags[0] = 0;
            flags[1] = 0;
            running = false;
            stopped = true;
            finished = false;
            dead = false;
        }

        byte[] getMachineStateBytes() {
            ByteBuffer bytes = ByteBuffer.allocate(getSize());
            bytes.order(ByteOrder.LITTLE_ENDIAN);

            if (Burst.getFluxCapacitor().getValue(FluxValues.AT_FIX_BLOCK_2)) {
                flags[0] = (byte) ((running ? 1 : 0)
                        | (stopped ? 1 : 0) << 1
                        | (finished ? 1 : 0) << 2
                        | (dead ? 1 : 0) << 3);
                flags[1] = 0;
            }

            bytes.put(flags);

            bytes.putInt(machineState.pc);
            bytes.putInt(machineState.pcs);
            bytes.putInt(machineState.cs);
            bytes.putInt(machineState.us);
            bytes.putInt(machineState.err);

            bytes.put(a1);
            bytes.put(a2);
            bytes.put(a3);
            bytes.put(a4);
            bytes.put(b1);
            bytes.put(b2);
            bytes.put(b3);
            bytes.put(b4);

            return bytes.array();
        }

        private void setMachineState(byte[] machineState) {
            ByteBuffer bf = ByteBuffer.allocate(getSize());
            bf.order(ByteOrder.LITTLE_ENDIAN);
            bf.put(machineState);
            bf.flip();

            bf.get(flags, 0, 2);
            running = (flags[0] & 1) == 1;
            stopped = (flags[0] >>> 1 & 1) == 1;
            finished = (flags[0] >>> 2 & 1) == 1;
            dead = (flags[0] >>> 3 & 1) == 1;

            pc = bf.getInt();
            pcs = bf.getInt();
            cs = bf.getInt();
            us = bf.getInt();
            err = bf.getInt();
            bf.get(a1, 0, 8);
            bf.get(a2, 0, 8);
            bf.get(a3, 0, 8);
            bf.get(a4, 0, 8);
            bf.get(b1, 0, 8);
            bf.get(b2, 0, 8);
            bf.get(b3, 0, 8);
            bf.get(b4, 0, 8);
        }

        int getSize() {
            return 2 + 4 + 4 + 4 + 4 + 4 + 4 * 8 + 4 * 8;
        }

        public long getSteps() {
            return steps;
        }
    }
}
