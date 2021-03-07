/*
 * Copyright (c) 2014 CIYAM Developers

 Distributed under the MIT/X11 software license, please refer to the file license.txt
 in the root project directory or http://www.opensource.org/licenses/mit-license.php.
*/

package brs.at;

import brs.Burst;
import brs.crypto.Crypto;
import brs.fluxcapacitor.FluxValues;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Arrays;

public class AtApiImpl implements AtApi {
    private final AtApiPlatformImpl platform = AtApiPlatformImpl.getInstance();

    @Override
    public long getA1(AtMachineState state) {
        return AtApiHelper.getLong(state.getA1());
    }

    @Override
    public long getA2(AtMachineState state) {
        return AtApiHelper.getLong(state.getA2());
    }

    @Override
    public long getA3(AtMachineState state) {
        return AtApiHelper.getLong(state.getA3());
    }

    @Override
    public long getA4(AtMachineState state) {
        return AtApiHelper.getLong(state.getA4());
    }

    @Override
    public long getB1(AtMachineState state) {
        return AtApiHelper.getLong(state.getB1());
    }

    @Override
    public long getB2(AtMachineState state) {
        return AtApiHelper.getLong(state.getB2());
    }

    @Override
    public long getB3(AtMachineState state) {
        return AtApiHelper.getLong(state.getB3());
    }

    @Override
    public long getB4(AtMachineState state) {
        return AtApiHelper.getLong(state.getB4());
    }

    @Override
    public void setA1(long val, AtMachineState state) {
        state.setA1(AtApiHelper.getByteArray(val));
    }

    @Override
    public void setA2(long val, AtMachineState state) {
        state.setA2(AtApiHelper.getByteArray(val));
    }

    @Override
    public void setA3(long val, AtMachineState state) {
        state.setA3(AtApiHelper.getByteArray(val));
    }

    @Override
    public void setA4(long val, AtMachineState state) {
        state.setA4(AtApiHelper.getByteArray(val));
    }

    @Override
    public void setA1A2(long val1, long val2, AtMachineState state) {
        state.setA1(AtApiHelper.getByteArray(val1));
        state.setA2(AtApiHelper.getByteArray(val2));
    }

    @Override
    public void setA3A4(long val1, long val2, AtMachineState state) {
        state.setA3(AtApiHelper.getByteArray(val1));
        state.setA4(AtApiHelper.getByteArray(val2));

    }

    @Override
    public void setB1(long val, AtMachineState state) {
        state.setB1(AtApiHelper.getByteArray(val));
    }

    @Override
    public void setB2(long val, AtMachineState state) {
        state.setB2(AtApiHelper.getByteArray(val));
    }

    @Override
    public void setB3(long val, AtMachineState state) {
        state.setB3(AtApiHelper.getByteArray(val));
    }

    @Override
    public void setB4(long val, AtMachineState state) {
        state.setB4(AtApiHelper.getByteArray(val));
    }

    @Override
    public void setB1B2(long val1, long val2, AtMachineState state) {
        state.setB1(AtApiHelper.getByteArray(val1));
        state.setB2(AtApiHelper.getByteArray(val2));
    }

    @Override
    public void setB3B4(long val3, long val4, AtMachineState state) {
        state.setB3(AtApiHelper.getByteArray(val3));
        state.setB4(AtApiHelper.getByteArray(val4));
    }

    @Override
    public void clearA(AtMachineState state) {
        byte[] b = new byte[8];
        state.setA1(b);
        state.setA2(b);
        state.setA3(b);
        state.setA4(b);
    }

    @Override
    public void clearB(AtMachineState state) {
        byte[] b = new byte[8];
        state.setB1(b);
        state.setB2(b);
        state.setB3(b);
        state.setB4(b);
    }

    @Override
    public void copyAFromB(AtMachineState state) {
        state.setA1(state.getB1());
        state.setA2(state.getB2());
        state.setA3(state.getB3());
        state.setA4(state.getB4());
    }

    @Override
    public void copyBFromA(AtMachineState state) {
        state.setB1(state.getA1());
        state.setB2(state.getA2());
        state.setB3(state.getA3());
        state.setB4(state.getA4());
    }

    private boolean isZero(byte[] bytes) {
        if (bytes == null) return false;
        for (int i = 0, bytesLength = bytes.length; i < bytesLength; i++) {
            byte b = bytes[i];
            if (b != 0) return false;
        }
        return true;
    }

    @Override
    public long checkAIsZero(AtMachineState state) {
        return isZero(state.getA1())
                && isZero(state.getA2())
                && isZero(state.getA3())
                && isZero(state.getA4()) ? 0 : 1; // TODO why return long?!
    }

    @Override
    public long checkBIsZero(AtMachineState state) {
        return isZero(state.getB1())
                && isZero(state.getB2())
                && isZero(state.getB3())
                && isZero(state.getB4()) ? 0 : 1; // TODO why return long?!
    }

    public long checkAEqualsB(AtMachineState state) {
        return (Arrays.equals(state.getA1(), state.getB1()) &&
                Arrays.equals(state.getA2(), state.getB2()) &&
                Arrays.equals(state.getA3(), state.getB3()) &&
                Arrays.equals(state.getA4(), state.getB4())) ? 1 : 0;
    }

    @Override
    public void swapAAndB(AtMachineState state) {
        byte[] b;

        b = state.getA1().clone();
        state.setA1(state.getB1());
        state.setB1(b);

        b = state.getA2().clone();
        state.setA2(state.getB2());
        state.setB2(b);

        b = state.getA3().clone();
        state.setA3(state.getB3());
        state.setB3(b);

        b = state.getA4().clone();
        state.setA4(state.getB4());
        state.setB4(b);
    }

    @Override
    public void addAToB(AtMachineState state) {
        BigInteger a = AtApiHelper.getBigInteger(state.getA1(), state.getA2(), state.getA3(), state.getA4());
        BigInteger b = AtApiHelper.getBigInteger(state.getB1(), state.getB2(), state.getB3(), state.getB4());
        BigInteger result = a.add(b);
        ByteBuffer resultBuffer = ByteBuffer.wrap(AtApiHelper.getByteArray(result));
        resultBuffer.order(ByteOrder.LITTLE_ENDIAN);

        byte[] temp = new byte[8];
        resultBuffer.get(temp, 0, 8);
        state.setB1(temp);
        resultBuffer.get(temp, 0, 8);
        state.setB2(temp);
        resultBuffer.get(temp, 0, 8);
        state.setB3(temp);
        resultBuffer.get(temp, 0, 8);
        state.setB4(temp);
    }

    @Override
    public void addBToA(AtMachineState state) {
        BigInteger a = AtApiHelper.getBigInteger(state.getA1(), state.getA2(), state.getA3(), state.getA4());
        BigInteger b = AtApiHelper.getBigInteger(state.getB1(), state.getB2(), state.getB3(), state.getB4());
        BigInteger result = a.add(b);
        ByteBuffer resultBuffer = ByteBuffer.wrap(AtApiHelper.getByteArray(result));
        resultBuffer.order(ByteOrder.LITTLE_ENDIAN);

        byte[] temp = new byte[8];
        resultBuffer.get(temp, 0, 8);
        state.setA1(temp);
        resultBuffer.get(temp, 0, 8);
        state.setA2(temp);
        resultBuffer.get(temp, 0, 8);
        state.setA3(temp);
        resultBuffer.get(temp, 0, 8);
        state.setA4(temp);
    }

    @Override
    public void subAFromB(AtMachineState state) {
        BigInteger a = AtApiHelper.getBigInteger(state.getA1(), state.getA2(), state.getA3(), state.getA4());
        BigInteger b = AtApiHelper.getBigInteger(state.getB1(), state.getB2(), state.getB3(), state.getB4());
        BigInteger result = b.subtract(a);
        ByteBuffer resultBuffer = ByteBuffer.wrap(AtApiHelper.getByteArray(result));
        resultBuffer.order(ByteOrder.LITTLE_ENDIAN);

        byte[] temp = new byte[8];
        resultBuffer.get(temp, 0, 8);
        state.setB1(temp);
        resultBuffer.get(temp, 0, 8);
        state.setB2(temp);
        resultBuffer.get(temp, 0, 8);
        state.setB3(temp);
        resultBuffer.get(temp, 0, 8);
        state.setB4(temp);
    }

    @Override
    public void subBFromA(AtMachineState state) {
        BigInteger a = AtApiHelper.getBigInteger(state.getA1(), state.getA2(), state.getA3(), state.getA4());
        BigInteger b = AtApiHelper.getBigInteger(state.getB1(), state.getB2(), state.getB3(), state.getB4());
        BigInteger result = a.subtract(b);
        ByteBuffer resultBuffer = ByteBuffer.wrap(AtApiHelper.getByteArray(result));
        resultBuffer.order(ByteOrder.LITTLE_ENDIAN);

        byte[] temp = new byte[8];
        resultBuffer.get(temp, 0, 8);
        state.setA1(temp);
        resultBuffer.get(temp, 0, 8);
        state.setA2(temp);
        resultBuffer.get(temp, 0, 8);
        state.setA3(temp);
        resultBuffer.get(temp, 0, 8);
        state.setA4(temp);
    }

    @Override
    public void mulAByB(AtMachineState state) {
        BigInteger a = AtApiHelper.getBigInteger(state.getA1(), state.getA2(), state.getA3(), state.getA4());
        BigInteger b = AtApiHelper.getBigInteger(state.getB1(), state.getB2(), state.getB3(), state.getB4());
        BigInteger result = a.multiply(b);
        ByteBuffer resultBuffer = ByteBuffer.wrap(AtApiHelper.getByteArray(result));
        resultBuffer.order(ByteOrder.LITTLE_ENDIAN);

        byte[] temp = new byte[8];
        resultBuffer.get(temp, 0, 8);
        state.setB1(temp);
        resultBuffer.get(temp, 0, 8);
        state.setB2(temp);
        resultBuffer.get(temp, 0, 8);
        state.setB3(temp);
        resultBuffer.get(temp, 0, 8);
        state.setB4(temp);
    }

    @Override
    public void mulBByA(AtMachineState state) {
        BigInteger a = AtApiHelper.getBigInteger(state.getA1(), state.getA2(), state.getA3(), state.getA4());
        BigInteger b = AtApiHelper.getBigInteger(state.getB1(), state.getB2(), state.getB3(), state.getB4());
        BigInteger result = a.multiply(b);
        ByteBuffer resultBuffer = ByteBuffer.wrap(AtApiHelper.getByteArray(result));
        resultBuffer.order(ByteOrder.LITTLE_ENDIAN);

        byte[] temp = new byte[8];
        resultBuffer.get(temp, 0, 8);
        state.setA1(temp);
        resultBuffer.get(temp, 0, 8);
        state.setA2(temp);
        resultBuffer.get(temp, 0, 8);
        state.setA3(temp);
        resultBuffer.get(temp, 0, 8);
        state.setA4(temp);
    }

    @Override
    public void divAByB(AtMachineState state) {
        BigInteger a = AtApiHelper.getBigInteger(state.getA1(), state.getA2(), state.getA3(), state.getA4());
        BigInteger b = AtApiHelper.getBigInteger(state.getB1(), state.getB2(), state.getB3(), state.getB4());
        if (b.compareTo(BigInteger.ZERO) == 0)
            return;
        BigInteger result = a.divide(b);
        ByteBuffer resultBuffer = ByteBuffer.wrap(AtApiHelper.getByteArray(result));
        resultBuffer.order(ByteOrder.LITTLE_ENDIAN);

        byte[] temp = new byte[8];
        resultBuffer.get(temp, 0, 8);
        state.setB1(temp);
        resultBuffer.get(temp, 0, 8);
        state.setB2(temp);
        resultBuffer.get(temp, 0, 8);
        state.setB3(temp);
        resultBuffer.get(temp, 0, 8);
        state.setB4(temp);
    }

    @Override
    public void divBByA(AtMachineState state) {
        BigInteger a = AtApiHelper.getBigInteger(state.getA1(), state.getA2(), state.getA3(), state.getA4());
        BigInteger b = AtApiHelper.getBigInteger(state.getB1(), state.getB2(), state.getB3(), state.getB4());
        if (a.compareTo(BigInteger.ZERO) == 0)
            return;
        BigInteger result = b.divide(a);
        ByteBuffer resultBuffer = ByteBuffer.wrap(AtApiHelper.getByteArray(result));
        resultBuffer.order(ByteOrder.LITTLE_ENDIAN);

        byte[] temp = new byte[8];
        resultBuffer.get(temp, 0, 8);
        state.setA1(temp);
        resultBuffer.get(temp, 0, 8);
        state.setA2(temp);
        resultBuffer.get(temp, 0, 8);
        state.setA3(temp);
        resultBuffer.get(temp, 0, 8);
        state.setA4(temp);
    }

    @Override
    public void orAWithB(AtMachineState state) {
        ByteBuffer a = ByteBuffer.allocate(32);
        a.order(ByteOrder.LITTLE_ENDIAN);
        a.put(state.getA1());
        a.put(state.getA2());
        a.put(state.getA3());
        a.put(state.getA4());
        a.clear();

        ByteBuffer b = ByteBuffer.allocate(32);
        b.order(ByteOrder.LITTLE_ENDIAN);
        b.put(state.getB1());
        b.put(state.getB2());
        b.put(state.getB3());
        b.put(state.getB4());
        b.clear();

        state.setA1(AtApiHelper.getByteArray(a.getLong(0) | b.getLong(0)));
        state.setA2(AtApiHelper.getByteArray(a.getLong(8) | b.getLong(8)));
        state.setA3(AtApiHelper.getByteArray(a.getLong(16) | b.getLong(16)));
        state.setA4(AtApiHelper.getByteArray(a.getLong(24) | b.getLong(24)));
    }

    @Override
    public void orBWithA(AtMachineState state) {
        ByteBuffer a = ByteBuffer.allocate(32);
        a.order(ByteOrder.LITTLE_ENDIAN);
        a.put(state.getA1());
        a.put(state.getA2());
        a.put(state.getA3());
        a.put(state.getA4());
        a.clear();

        ByteBuffer b = ByteBuffer.allocate(32);
        b.order(ByteOrder.LITTLE_ENDIAN);
        b.put(state.getB1());
        b.put(state.getB2());
        b.put(state.getB3());
        b.put(state.getB4());
        b.clear();

        state.setB1(AtApiHelper.getByteArray(a.getLong(0) | b.getLong(0)));
        state.setB2(AtApiHelper.getByteArray(a.getLong(8) | b.getLong(8)));
        state.setB3(AtApiHelper.getByteArray(a.getLong(16) | b.getLong(16)));
        state.setB4(AtApiHelper.getByteArray(a.getLong(24) | b.getLong(24)));
    }

    @Override
    public void andAWithB(AtMachineState state) {
        ByteBuffer a = ByteBuffer.allocate(32);
        a.order(ByteOrder.LITTLE_ENDIAN);
        a.put(state.getA1());
        a.put(state.getA2());
        a.put(state.getA3());
        a.put(state.getA4());
        a.clear();

        ByteBuffer b = ByteBuffer.allocate(32);
        b.order(ByteOrder.LITTLE_ENDIAN);
        b.put(state.getB1());
        b.put(state.getB2());
        b.put(state.getB3());
        b.put(state.getB4());
        b.clear();

        state.setA1(AtApiHelper.getByteArray(a.getLong(0) & b.getLong(0)));
        state.setA2(AtApiHelper.getByteArray(a.getLong(8) & b.getLong(8)));
        state.setA3(AtApiHelper.getByteArray(a.getLong(16) & b.getLong(16)));
        state.setA4(AtApiHelper.getByteArray(a.getLong(24) & b.getLong(24)));
    }

    @Override
    public void andBWithA(AtMachineState state) {
        ByteBuffer a = ByteBuffer.allocate(32);
        a.order(ByteOrder.LITTLE_ENDIAN);
        a.put(state.getA1());
        a.put(state.getA2());
        a.put(state.getA3());
        a.put(state.getA4());
        a.clear();

        ByteBuffer b = ByteBuffer.allocate(32);
        b.order(ByteOrder.LITTLE_ENDIAN);
        b.put(state.getB1());
        b.put(state.getB2());
        b.put(state.getB3());
        b.put(state.getB4());
        b.clear();

        state.setB1(AtApiHelper.getByteArray(a.getLong(0) & b.getLong(0)));
        state.setB2(AtApiHelper.getByteArray(a.getLong(8) & b.getLong(8)));
        state.setB3(AtApiHelper.getByteArray(a.getLong(16) & b.getLong(16)));
        state.setB4(AtApiHelper.getByteArray(a.getLong(24) & b.getLong(24)));
    }

    @Override
    public void xorAWithB(AtMachineState state) {
        ByteBuffer a = ByteBuffer.allocate(32);
        a.order(ByteOrder.LITTLE_ENDIAN);
        a.put(state.getA1());
        a.put(state.getA2());
        a.put(state.getA3());
        a.put(state.getA4());
        a.clear();

        ByteBuffer b = ByteBuffer.allocate(32);
        b.order(ByteOrder.LITTLE_ENDIAN);
        b.put(state.getB1());
        b.put(state.getB2());
        b.put(state.getB3());
        b.put(state.getB4());
        b.clear();

        state.setA1(AtApiHelper.getByteArray(a.getLong(0) ^ b.getLong(0)));
        state.setA2(AtApiHelper.getByteArray(a.getLong(8) ^ b.getLong(8)));
        state.setA3(AtApiHelper.getByteArray(a.getLong(16) ^ b.getLong(16)));
        state.setA4(AtApiHelper.getByteArray(a.getLong(24) ^ b.getLong(24)));
    }

    @Override
    public void xorBWithA(AtMachineState state) {
        ByteBuffer a = ByteBuffer.allocate(32);
        a.order(ByteOrder.LITTLE_ENDIAN);
        a.put(state.getA1());
        a.put(state.getA2());
        a.put(state.getA3());
        a.put(state.getA4());
        a.clear();

        ByteBuffer b = ByteBuffer.allocate(32);
        b.order(ByteOrder.LITTLE_ENDIAN);
        b.put(state.getB1());
        b.put(state.getB2());
        b.put(state.getB3());
        b.put(state.getB4());
        b.clear();

        state.setB1(AtApiHelper.getByteArray(a.getLong(0) ^ b.getLong(0)));
        state.setB2(AtApiHelper.getByteArray(a.getLong(8) ^ b.getLong(8)));
        state.setB3(AtApiHelper.getByteArray(a.getLong(16) ^ b.getLong(16)));
        state.setB4(AtApiHelper.getByteArray(a.getLong(24) ^ b.getLong(24)));
    }

    @Override
    public void md5Atob(AtMachineState state) {
        ByteBuffer b = ByteBuffer.allocate(16);
        b.order(ByteOrder.LITTLE_ENDIAN);

        b.put(state.getA1());
        b.put(state.getA2());

        MessageDigest md5 = Crypto.md5();
        ByteBuffer mdb = ByteBuffer.wrap(md5.digest(b.array()));
        mdb.order(ByteOrder.LITTLE_ENDIAN);

        state.setB1(AtApiHelper.getByteArray(mdb.getLong(0)));
        if (Burst.getFluxCapacitor().getValue(FluxValues.SODIUM)) {
            state.setB2(AtApiHelper.getByteArray(mdb.getLong(8)));
        } else {
            state.setB1(AtApiHelper.getByteArray(mdb.getLong(8)));
        }
    }


    @Override
    public long checkMd5AWithB(AtMachineState state) {
        if (Burst.getFluxCapacitor().getValue(FluxValues.AT_FIX_BLOCK_3)) {
            ByteBuffer b = ByteBuffer.allocate(16);
            b.order(ByteOrder.LITTLE_ENDIAN);

            b.put(state.getA1());
            b.put(state.getA2());

            MessageDigest md5 = Crypto.md5();
            ByteBuffer mdb = ByteBuffer.wrap(md5.digest(b.array()));
            mdb.order(ByteOrder.LITTLE_ENDIAN);

            return (mdb.getLong(0) == AtApiHelper.getLong(state.getB1()) &&
                    mdb.getLong(8) == AtApiHelper.getLong(state.getB2())) ? 1 : 0;
        } else {
            return (Arrays.equals(state.getA1(), state.getB1()) &&
                    Arrays.equals(state.getA2(), state.getB2())) ? 1 : 0;
        }
    }

    @Override
    public void hash160AToB(AtMachineState state) {
        ByteBuffer b = ByteBuffer.allocate(32);
        b.order(ByteOrder.LITTLE_ENDIAN);

        b.put(state.getA1());
        b.put(state.getA2());
        b.put(state.getA3());
        b.put(state.getA4());

        ByteBuffer ripemdb = ByteBuffer.wrap(Crypto.ripemd160().digest(b.array()));
        ripemdb.order(ByteOrder.LITTLE_ENDIAN);

        state.setB1(AtApiHelper.getByteArray(ripemdb.getLong(0)));
        state.setB2(AtApiHelper.getByteArray(ripemdb.getLong(8)));
        state.setB3(AtApiHelper.getByteArray((long) ripemdb.getInt(16)));
    }

    @Override
    public long checkHash160AWithB(AtMachineState state) {
        if (Burst.getFluxCapacitor().getValue(FluxValues.AT_FIX_BLOCK_3)) {
            ByteBuffer b = ByteBuffer.allocate(32);
            b.order(ByteOrder.LITTLE_ENDIAN);

            b.put(state.getA1());
            b.put(state.getA2());
            b.put(state.getA3());
            b.put(state.getA4());

            ByteBuffer ripemdb = ByteBuffer.wrap(Crypto.ripemd160().digest(b.array()));
            ripemdb.order(ByteOrder.LITTLE_ENDIAN);

            return (ripemdb.getLong(0) == AtApiHelper.getLong(state.getB1()) &&
                    ripemdb.getLong(8) == AtApiHelper.getLong(state.getB2()) &&
                    ripemdb.getInt(16) == ((int) (AtApiHelper.getLong(state.getB3()) & 0x00000000FFFFFFFFL))
            ) ? 1 : 0;
        } else {
            return (Arrays.equals(state.getA1(), state.getB1()) &&
                    Arrays.equals(state.getA2(), state.getB2()) &&
                    (AtApiHelper.getLong(state.getA3()) & 0x00000000FFFFFFFFL) == (AtApiHelper.getLong(state.getB3()) & 0x00000000FFFFFFFFL)) ? 1 : 0;
        }
    }

    @Override
    public void sha256AToB(AtMachineState state) {
        ByteBuffer b = ByteBuffer.allocate(32);
        b.order(ByteOrder.LITTLE_ENDIAN);

        b.put(state.getA1());
        b.put(state.getA2());
        b.put(state.getA3());
        b.put(state.getA4());

        MessageDigest sha256 = Crypto.sha256();
        ByteBuffer shab = ByteBuffer.wrap(sha256.digest(b.array()));
        shab.order(ByteOrder.LITTLE_ENDIAN);

        state.setB1(AtApiHelper.getByteArray(shab.getLong(0)));
        state.setB2(AtApiHelper.getByteArray(shab.getLong(8)));
        state.setB3(AtApiHelper.getByteArray(shab.getLong(16)));
        state.setB4(AtApiHelper.getByteArray(shab.getLong(24)));
    }

    @Override
    public long checkSha256AWithB(AtMachineState state) {
        if (Burst.getFluxCapacitor().getValue(FluxValues.AT_FIX_BLOCK_3)) {
            ByteBuffer b = ByteBuffer.allocate(32);
            b.order(ByteOrder.LITTLE_ENDIAN);

            b.put(state.getA1());
            b.put(state.getA2());
            b.put(state.getA3());
            b.put(state.getA4());

            MessageDigest sha256 = Crypto.sha256();
            ByteBuffer shab = ByteBuffer.wrap(sha256.digest(b.array()));
            shab.order(ByteOrder.LITTLE_ENDIAN);

            return (shab.getLong(0) == AtApiHelper.getLong(state.getB1()) &&
                    shab.getLong(8) == AtApiHelper.getLong(state.getB2()) &&
                    shab.getLong(16) == AtApiHelper.getLong(state.getB3()) &&
                    shab.getLong(24) == AtApiHelper.getLong(state.getB4())) ? 1 : 0;
        } else {
            return (Arrays.equals(state.getA1(), state.getB1()) &&
                    Arrays.equals(state.getA2(), state.getB2()) &&
                    Arrays.equals(state.getA3(), state.getB3()) &&
                    Arrays.equals(state.getA4(), state.getB4())) ? 1 : 0;
        }
    }

    @Override
    public long getBlockTimestamp(AtMachineState state) {
        return platform.getBlockTimestamp(state);

    }

    @Override
    public long getCreationTimestamp(AtMachineState state) {
        return platform.getCreationTimestamp(state);
    }

    @Override
    public long getLastBlockTimestamp(AtMachineState state) {
        return platform.getLastBlockTimestamp(state);
    }

    @Override
    public void putLastBlockHashInA(AtMachineState state) {
        platform.putLastBlockHashInA(state);

    }

    @Override
    public void aToTxAfterTimestamp(long val, AtMachineState state) {
        platform.aToTxAfterTimestamp(val, state);

    }

    @Override
    public long getTypeForTxInA(AtMachineState state) {
        return platform.getTypeForTxInA(state);
    }

    @Override
    public long getAmountForTxInA(AtMachineState state) {
        return platform.getAmountForTxInA(state);
    }

    @Override
    public long GetAssetIdForTxInA(AtMachineState state) {
        return platform.GetAssetIdForTxInA(state);
    }
 
    @Override
    public long GetAssetAmountForTxInA(AtMachineState state) {
        return platform.GetAssetAmountForTxInA(state);
    }

    @Override
    public long Mold(AtMachineState state) {
        return platform.Mold(state);
    }

    @Override
    public long Mint(AtMachineState state) {
        return platform.Mint(state);
    }

    @Override
    public long getTimestampForTxInA(AtMachineState state) {
        return platform.getTimestampForTxInA(state);
    }

    @Override
    public long getRandomIdForTxInA(AtMachineState state) {
        return platform.getRandomIdForTxInA(state);
    }

    @Override
    public void messageFromTxInAToB(AtMachineState state) {
        platform.messageFromTxInAToB(state);
    }

    @Override
    public void bToAddressOfTxInA(AtMachineState state) {

        platform.bToAddressOfTxInA(state);
    }

    @Override
    public void bToAddressOfCreator(AtMachineState state) {
        platform.bToAddressOfCreator(state);

    }

    @Override
    public long getCurrentBalance(AtMachineState state) {
        return platform.getCurrentBalance(state);
    }

    @Override
    public long getPreviousBalance(AtMachineState state) {
        return platform.getPreviousBalance(state);
    }

    @Override
    public void sendToAddressInB(long val, AtMachineState state) {
        platform.sendToAddressInB(val, state);
    }

    @Override
    public void sendAllToAddressInB(AtMachineState state) {
        platform.sendAllToAddressInB(state);
    }

    @Override
    public void sendOldToAddressInB(AtMachineState state) {
        platform.sendOldToAddressInB(state);
    }

    @Override
    public void sendAToAddressInB(AtMachineState state) {
        platform.sendAToAddressInB(state);
    }

    @Override
    public long addMinutesToTimestamp(long val1, long val2, AtMachineState state) {
        return platform.addMinutesToTimestamp(val1, val2, state);
    }

    @Override
    public void setMinActivationAmount(long val, AtMachineState state) {
        state.setMinActivationAmount(val);
    }

    @Override
    public void putLastBlockGenerationSignatureInA(AtMachineState state) {
        platform.putLastBlockGenerationSignatureInA(state);
    }

    @Override
    public void sha256ToB(long val1, long val2, AtMachineState state) {
        if (val1 < 0 || val2 < 0 ||
                (val1 + val2 - 1) < 0 ||
                val1 * 8 + 8 > ((long) Integer.MAX_VALUE) ||
                val1 * 8 + 8 > state.getdSize() ||
                (val1 + val2 - 1) * 8 + 8 > ((long) Integer.MAX_VALUE) ||
                (val1 + val2 - 1) * 8 + 8 > state.getdSize()) {
            return;
        }

        MessageDigest sha256 = Crypto.sha256();
        sha256.update(state.getApData().array(), (int) val1, (int) (val2 > 256 ? 256 : val2));
        ByteBuffer shab = ByteBuffer.wrap(sha256.digest());
        shab.order(ByteOrder.LITTLE_ENDIAN);

        state.setB1(AtApiHelper.getByteArray(shab.getLong(0)));
        state.setB2(AtApiHelper.getByteArray(shab.getLong(8)));
        state.setB3(AtApiHelper.getByteArray(shab.getLong(16)));
        state.setB4(AtApiHelper.getByteArray(shab.getLong(24)));
    }
}
