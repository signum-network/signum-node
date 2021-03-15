package brs.at;

import brs.util.Convert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.math.BigInteger;
import java.nio.BufferOverflowException;

import static org.junit.Assert.assertEquals;

@RunWith(JUnit4.class)
public class AtApiHelperTest {
    @Test
    public void testGetLong() {
        assertEquals(0x0000000000000000L, AtApiHelper.getLong(new byte[8]));
        assertEquals(0x0000000000000001L, AtApiHelper.getLong(Convert.parseHexString("0100000000000000")));
        assertEquals(0x0000000000002301L, AtApiHelper.getLong(Convert.parseHexString("0123000000000000")));
        assertEquals(0x0000000000452301L, AtApiHelper.getLong(Convert.parseHexString("0123450000000000")));
        assertEquals(0x0000000067452301L, AtApiHelper.getLong(Convert.parseHexString("0123456700000000")));
        assertEquals(0x0000008967452301L, AtApiHelper.getLong(Convert.parseHexString("0123456789000000")));
        assertEquals(0x0000ab8967452301L, AtApiHelper.getLong(Convert.parseHexString("0123456789ab0000")));
        assertEquals(0x00cdab8967452301L, AtApiHelper.getLong(Convert.parseHexString("0123456789abcd00")));
        assertEquals(0xefcdab8967452301L, AtApiHelper.getLong(Convert.parseHexString("0123456789abcdef")));
    }

    @Test(expected = NullPointerException.class)
    public void testGetLong_null() {
        //noinspection ConstantConditions,ResultOfMethodCallIgnored
        AtApiHelper.getLong(null);
    }

    @Test(expected = BufferOverflowException.class)
    public void testGetLong_overflow() {
        //noinspection ResultOfMethodCallIgnored
        AtApiHelper.getLong(Convert.parseHexString("0123456789abcdef0123456789abcdef"));
    }
    
    @Test
    public void testGetByteArray_long() {
        assertEquals("0100000000000000", Convert.toHexString(AtApiHelper.getByteArray(0x0000000000000001L)));
        assertEquals("0123000000000000", Convert.toHexString(AtApiHelper.getByteArray(0x0000000000002301L)));
        assertEquals("0123450000000000", Convert.toHexString(AtApiHelper.getByteArray(0x0000000000452301L)));
        assertEquals("0123456700000000", Convert.toHexString(AtApiHelper.getByteArray(0x0000000067452301L)));
        assertEquals("0123456789000000", Convert.toHexString(AtApiHelper.getByteArray(0x0000008967452301L)));
        assertEquals("0123456789ab0000", Convert.toHexString(AtApiHelper.getByteArray(0x0000ab8967452301L)));
        assertEquals("0123456789abcd00", Convert.toHexString(AtApiHelper.getByteArray(0x00cdab8967452301L)));
        assertEquals("0123456789abcdef", Convert.toHexString(AtApiHelper.getByteArray(0xefcdab8967452301L)));
    }
    
    @Test
    public void testGetByteArray_bigInteger() {
        assertEquals("0100000000000000000000000000000000000000000000000000000000000000", Convert.toHexString(AtApiHelper.getByteArray(BigInteger.valueOf(0x0000000000000001L))));
        assertEquals("0123000000000000000000000000000000000000000000000000000000000000", Convert.toHexString(AtApiHelper.getByteArray(BigInteger.valueOf(0x0000000000002301L))));
        assertEquals("0123450000000000000000000000000000000000000000000000000000000000", Convert.toHexString(AtApiHelper.getByteArray(BigInteger.valueOf(0x0000000000452301L))));
        assertEquals("0123456700000000000000000000000000000000000000000000000000000000", Convert.toHexString(AtApiHelper.getByteArray(BigInteger.valueOf(0x0000000067452301L))));
        assertEquals("0123456789000000000000000000000000000000000000000000000000000000", Convert.toHexString(AtApiHelper.getByteArray(BigInteger.valueOf(0x0000008967452301L))));
        assertEquals("0123456789ab0000000000000000000000000000000000000000000000000000", Convert.toHexString(AtApiHelper.getByteArray(BigInteger.valueOf(0x0000ab8967452301L))));
        assertEquals("0123456789abcd00000000000000000000000000000000000000000000000000", Convert.toHexString(AtApiHelper.getByteArray(BigInteger.valueOf(0x00cdab8967452301L))));
        assertEquals("0123456789abcdefffffffffffffffffffffffffffffffffffffffffffffffff", Convert.toHexString(AtApiHelper.getByteArray(BigInteger.valueOf(0xefcdab8967452301L))));
        assertEquals("0123456789abcdef0123456789abcdef00000000000000000000000000000000", Convert.toHexString(AtApiHelper.getByteArray(new BigInteger("efcdab8967452301efcdab8967452301", 16))));
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", Convert.toHexString(AtApiHelper.getByteArray(new BigInteger("efcdab8967452301efcdab8967452301efcdab8967452301efcdab8967452301", 16))));
    }

    @Test(expected = NullPointerException.class)
    public void testGetByteArray_null() {
        //noinspection ConstantConditions
        AtApiHelper.getByteArray(null);
    }
    
    @Test
    public void testLongToHeight() {
        assertEquals(0, AtApiHelper.longToHeight(0x0000000000000001L));
        assertEquals(0, AtApiHelper.longToHeight(0x0000000000002301L));
        assertEquals(0, AtApiHelper.longToHeight(0x0000000000452301L));
        assertEquals(0, AtApiHelper.longToHeight(0x0000000067452301L));
        assertEquals(0x89, AtApiHelper.longToHeight(0x0000008967452301L));
        assertEquals(0xab89, AtApiHelper.longToHeight(0x0000ab8967452301L));
        assertEquals(0xcdab89, AtApiHelper.longToHeight(0x00cdab8967452301L));
        assertEquals(0xefcdab89, AtApiHelper.longToHeight(0xefcdab8967452301L));
    }
    
    @Test
    public void testLongToNumberOfTx() {
        assertEquals(0x01, AtApiHelper.longToNumOfTx(0x0000000000000001L));
        assertEquals(0x2301, AtApiHelper.longToNumOfTx(0x0000000000002301L));
        assertEquals(0x452301, AtApiHelper.longToNumOfTx(0x0000000000452301L));
        assertEquals(0x67452301, AtApiHelper.longToNumOfTx(0x0000000067452301L));
        assertEquals(0x67452301, AtApiHelper.longToNumOfTx(0x0000008967452301L));
        assertEquals(0x67452301, AtApiHelper.longToNumOfTx(0x0000ab8967452301L));
        assertEquals(0x67452301, AtApiHelper.longToNumOfTx(0x00cdab8967452301L));
        assertEquals(0x67452301, AtApiHelper.longToNumOfTx(0xefcdab8967452301L));
    }
    
    @Test
    public void testGetLongTimestamp() {
        assertEquals(0x0000000100000000L, AtApiHelper.getLongTimestamp(0x01, 0x00));
        assertEquals(0x0000230100000000L, AtApiHelper.getLongTimestamp(0x2301, 0x00));
        assertEquals(0x0045230100000000L, AtApiHelper.getLongTimestamp(0x452301, 0x00));
        assertEquals(0x6745230100000000L, AtApiHelper.getLongTimestamp(0x67452301, 0x00));
        assertEquals(0x0000000000000001L, AtApiHelper.getLongTimestamp(0x00, 0x01));
        assertEquals(0x0000000000002301L, AtApiHelper.getLongTimestamp(0x00, 0x2301));
        assertEquals(0x0000000000452301L, AtApiHelper.getLongTimestamp(0x00, 0x452301));
        assertEquals(0x0000000067452301L, AtApiHelper.getLongTimestamp(0x00, 0x67452301));
        assertEquals(0x0000000100000001L, AtApiHelper.getLongTimestamp(0x01, 0x01));
        assertEquals(0x0000230100002301L, AtApiHelper.getLongTimestamp(0x2301, 0x2301));
        assertEquals(0x0045230100452301L, AtApiHelper.getLongTimestamp(0x452301, 0x452301));
        assertEquals(0x6745230167452301L, AtApiHelper.getLongTimestamp(0x67452301, 0x67452301));
    }

    @Test
    public void testGetBigInteger() {
        assertEquals(new BigInteger(Convert.parseHexString("0000000000000000000000000000000000000000000000000000000000000012")), AtApiHelper.getBigInteger(Convert.parseHexString("1200000000000000"), Convert.parseHexString("0000000000000000"), Convert.parseHexString("0000000000000000"), Convert.parseHexString("0000000000000000")));
        assertEquals(new BigInteger(Convert.parseHexString("0000000000000000000000000000000000000000000000120000000000000012")), AtApiHelper.getBigInteger(Convert.parseHexString("1200000000000000"), Convert.parseHexString("1200000000000000"), Convert.parseHexString("0000000000000000"), Convert.parseHexString("0000000000000000")));
        assertEquals(new BigInteger(Convert.parseHexString("0000000000000000000000000000001200000000000000120000000000000012")), AtApiHelper.getBigInteger(Convert.parseHexString("1200000000000000"), Convert.parseHexString("1200000000000000"), Convert.parseHexString("1200000000000000"), Convert.parseHexString("0000000000000000")));
        assertEquals(new BigInteger(Convert.parseHexString("0000000000000012000000000000001200000000000000120000000000000012")), AtApiHelper.getBigInteger(Convert.parseHexString("1200000000000000"), Convert.parseHexString("1200000000000000"), Convert.parseHexString("1200000000000000"), Convert.parseHexString("1200000000000000")));

        assertEquals(new BigInteger(Convert.parseHexString("0000000000000012000000000000001200000000000000120000000000003412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234000000000000"), Convert.parseHexString("1200000000000000"), Convert.parseHexString("1200000000000000"), Convert.parseHexString("1200000000000000")));
        assertEquals(new BigInteger(Convert.parseHexString("0000000000000012000000000000001200000000000034120000000000003412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234000000000000"), Convert.parseHexString("1234000000000000"), Convert.parseHexString("1200000000000000"), Convert.parseHexString("1200000000000000")));
        assertEquals(new BigInteger(Convert.parseHexString("0000000000000012000000000000341200000000000034120000000000003412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234000000000000"), Convert.parseHexString("1234000000000000"), Convert.parseHexString("1234000000000000"), Convert.parseHexString("1200000000000000")));
        assertEquals(new BigInteger(Convert.parseHexString("0000000000003412000000000000341200000000000034120000000000003412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234000000000000"), Convert.parseHexString("1234000000000000"), Convert.parseHexString("1234000000000000"), Convert.parseHexString("1234000000000000")));

        assertEquals(new BigInteger(Convert.parseHexString("0000000000003412000000000000341200000000000034120000000000563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234560000000000"), Convert.parseHexString("1234000000000000"), Convert.parseHexString("1234000000000000"), Convert.parseHexString("1234000000000000")));
        assertEquals(new BigInteger(Convert.parseHexString("0000000000003412000000000000341200000000005634120000000000563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234560000000000"), Convert.parseHexString("1234560000000000"), Convert.parseHexString("1234000000000000"), Convert.parseHexString("1234000000000000")));
        assertEquals(new BigInteger(Convert.parseHexString("0000000000003412000000000056341200000000005634120000000000563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234560000000000"), Convert.parseHexString("1234560000000000"), Convert.parseHexString("1234560000000000"), Convert.parseHexString("1234000000000000")));
        assertEquals(new BigInteger(Convert.parseHexString("0000000000563412000000000056341200000000005634120000000000563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234560000000000"), Convert.parseHexString("1234560000000000"), Convert.parseHexString("1234560000000000"), Convert.parseHexString("1234560000000000")));

        assertEquals(new BigInteger(Convert.parseHexString("0000000000563412000000000056341200000000005634120000000078563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234567800000000"), Convert.parseHexString("1234560000000000"), Convert.parseHexString("1234560000000000"), Convert.parseHexString("1234560000000000")));
        assertEquals(new BigInteger(Convert.parseHexString("0000000000563412000000000056341200000000785634120000000078563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234567800000000"), Convert.parseHexString("1234567800000000"), Convert.parseHexString("1234560000000000"), Convert.parseHexString("1234560000000000")));
        assertEquals(new BigInteger(Convert.parseHexString("0000000000563412000000007856341200000000785634120000000078563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234567800000000"), Convert.parseHexString("1234567800000000"), Convert.parseHexString("1234567800000000"), Convert.parseHexString("1234560000000000")));
        assertEquals(new BigInteger(Convert.parseHexString("0000000078563412000000007856341200000000785634120000000078563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234567800000000"), Convert.parseHexString("1234567800000000"), Convert.parseHexString("1234567800000000"), Convert.parseHexString("1234567800000000")));

        assertEquals(new BigInteger(Convert.parseHexString("0000000078563412000000007856341200000000785634120000009078563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234567890000000"), Convert.parseHexString("1234567800000000"), Convert.parseHexString("1234567800000000"), Convert.parseHexString("1234567800000000")));
        assertEquals(new BigInteger(Convert.parseHexString("0000000078563412000000007856341200000090785634120000009078563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234567890000000"), Convert.parseHexString("1234567890000000"), Convert.parseHexString("1234567800000000"), Convert.parseHexString("1234567800000000")));
        assertEquals(new BigInteger(Convert.parseHexString("0000000078563412000000907856341200000090785634120000009078563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234567890000000"), Convert.parseHexString("1234567890000000"), Convert.parseHexString("1234567890000000"), Convert.parseHexString("1234567800000000")));
        assertEquals(new BigInteger(Convert.parseHexString("0000009078563412000000907856341200000090785634120000009078563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234567890000000"), Convert.parseHexString("1234567890000000"), Convert.parseHexString("1234567890000000"), Convert.parseHexString("1234567890000000")));

        assertEquals(new BigInteger(Convert.parseHexString("0000009078563412000000907856341200000090785634120000ab9078563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234567890ab0000"), Convert.parseHexString("1234567890000000"), Convert.parseHexString("1234567890000000"), Convert.parseHexString("1234567890000000")));
        assertEquals(new BigInteger(Convert.parseHexString("000000907856341200000090785634120000ab90785634120000ab9078563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234567890ab0000"), Convert.parseHexString("1234567890ab0000"), Convert.parseHexString("1234567890000000"), Convert.parseHexString("1234567890000000")));
        assertEquals(new BigInteger(Convert.parseHexString("00000090785634120000ab90785634120000ab90785634120000ab9078563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234567890ab0000"), Convert.parseHexString("1234567890ab0000"), Convert.parseHexString("1234567890ab0000"), Convert.parseHexString("1234567890000000")));
        assertEquals(new BigInteger(Convert.parseHexString("0000ab90785634120000ab90785634120000ab90785634120000ab9078563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234567890ab0000"), Convert.parseHexString("1234567890ab0000"), Convert.parseHexString("1234567890ab0000"), Convert.parseHexString("1234567890ab0000")));

        assertEquals(new BigInteger(Convert.parseHexString("0000ab90785634120000ab90785634120000ab907856341200cdab9078563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234567890abcd00"), Convert.parseHexString("1234567890ab0000"), Convert.parseHexString("1234567890ab0000"), Convert.parseHexString("1234567890ab0000")));
        assertEquals(new BigInteger(Convert.parseHexString("0000ab90785634120000ab907856341200cdab907856341200cdab9078563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234567890abcd00"), Convert.parseHexString("1234567890abcd00"), Convert.parseHexString("1234567890ab0000"), Convert.parseHexString("1234567890ab0000")));
        assertEquals(new BigInteger(Convert.parseHexString("0000ab907856341200cdab907856341200cdab907856341200cdab9078563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234567890abcd00"), Convert.parseHexString("1234567890abcd00"), Convert.parseHexString("1234567890abcd00"), Convert.parseHexString("1234567890ab0000")));
        assertEquals(new BigInteger(Convert.parseHexString("00cdab907856341200cdab907856341200cdab907856341200cdab9078563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234567890abcd00"), Convert.parseHexString("1234567890abcd00"), Convert.parseHexString("1234567890abcd00"), Convert.parseHexString("1234567890abcd00")));

        assertEquals(new BigInteger(Convert.parseHexString("00cdab907856341200cdab907856341200cdab9078563412efcdab9078563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234567890abcdef"), Convert.parseHexString("1234567890abcd00"), Convert.parseHexString("1234567890abcd00"), Convert.parseHexString("1234567890abcd00")));
        assertEquals(new BigInteger(Convert.parseHexString("00cdab907856341200cdab9078563412efcdab9078563412efcdab9078563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234567890abcdef"), Convert.parseHexString("1234567890abcdef"), Convert.parseHexString("1234567890abcd00"), Convert.parseHexString("1234567890abcd00")));
        assertEquals(new BigInteger(Convert.parseHexString("00cdab9078563412efcdab9078563412efcdab9078563412efcdab9078563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234567890abcdef"), Convert.parseHexString("1234567890abcdef"), Convert.parseHexString("1234567890abcdef"), Convert.parseHexString("1234567890abcd00")));
        assertEquals(new BigInteger(Convert.parseHexString("efcdab9078563412efcdab9078563412efcdab9078563412efcdab9078563412")), AtApiHelper.getBigInteger(Convert.parseHexString("1234567890abcdef"), Convert.parseHexString("1234567890abcdef"), Convert.parseHexString("1234567890abcdef"), Convert.parseHexString("1234567890abcdef")));
    }
}
