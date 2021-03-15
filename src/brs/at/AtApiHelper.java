/*
 * Copyright (c) 2014 CIYAM Developers

 Distributed under the MIT/X11 software license, please refer to the file license.txt
 in the root project directory or http://www.opensource.org/licenses/mit-license.php.

*/

package brs.at;

import burst.kit.crypto.BurstCrypto;
import org.bouncycastle.util.Arrays;

import java.math.BigInteger;
import java.nio.BufferOverflowException;

public class AtApiHelper {
    private AtApiHelper() {
    }

    private static final BurstCrypto burstCrypto = BurstCrypto.getInstance();

    public static int longToHeight(long x) {
        return (int) (x >> 32);
    }

    public static long getLong(byte[] bytes) {
        if (bytes.length > 8) {
            throw new BufferOverflowException();
        }
        return burstCrypto.bytesToLongLE(bytes);
    }

    public static byte[] getByteArray(long l) {
        return burstCrypto.longToBytesLE(l);
    }

    public static int longToNumOfTx(long x) {
        return (int) x;
    }

    static long getLongTimestamp(int height, int numOfTx) {
        return ((long) height) << 32 | numOfTx;
    }

    public static BigInteger getBigInteger(byte[] b1, byte[] b2, byte[] b3, byte[] b4) {
        return new BigInteger(new byte[]{
                b4[7], b4[6], b4[5], b4[4], b4[3], b4[2], b4[1], b4[0],
                b3[7], b3[6], b3[5], b3[4], b3[3], b3[2], b3[1], b3[0],
                b2[7], b2[6], b2[5], b2[4], b2[3], b2[2], b2[1], b2[0],
                b1[7], b1[6], b1[5], b1[4], b1[3], b1[2], b1[1], b1[0],
        });
    }

    public static byte[] getByteArray(BigInteger bigInt) {
        final int resultSize = 32;
        byte[] bigIntBytes = Arrays.reverse(bigInt.toByteArray());
        byte[] result = new byte[resultSize];
        if (bigIntBytes.length < resultSize) {
            byte padding = (byte) (((byte) (bigIntBytes[bigIntBytes.length-1] & ((byte) 0x80))) >> 7);
            for (int i = 0, length=resultSize-bigIntBytes.length; i < length; i++) {
                result[resultSize-1-i] = padding;
            }
        }
        System.arraycopy(bigIntBytes, 0, result, 0, (resultSize >= bigIntBytes.length) ? bigIntBytes.length : resultSize);
        return result;
    }
}
