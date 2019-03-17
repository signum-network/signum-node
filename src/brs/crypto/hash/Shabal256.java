package brs.crypto.hash;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

/**
 * This class implements ShabalProvider for the output size of 256 bits
 * It's meant to be a hard-coded 256bit version for the {@link Shabal256}
 * class;
 *
 * ==========================(LICENSE BEGIN)============================
 *
 * Copyright (c) 2007-2010  Projet RNRT SAPHIR
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * ===========================(LICENSE END)=============================
 *
 * @author    Thomas Pornin &lt;thomas.pornin@cryptolog.com&gt;
 */

public class Shabal256 extends MessageDigest {

    public static final String ALGORITHM = "Shabal-256";
    public static final List<String> ALIASES = Arrays.asList("SHABAL-256", "shabal-256", "Shabal256", "SHABAL256", "shabal256", "Shabal", "SHABAL", "shabal");

    private static int[] IV;

    private final byte[] buf = new byte[64];
    private int ptr;
    private final int[] state = new int[44];
    private long W;


    private Shabal256(boolean reset) {
        super(ALGORITHM);
        if (reset) reset();
    }

    public Shabal256() {
        this(true);
    }

    @Override
    protected void engineReset() {
        System.arraycopy(getIV(), 0, state, 0, 44);
        W = 1;
        ptr = 0;
    }

    @Override
    protected void engineUpdate(byte in) {
        buf[ptr ++] = in;
        if (ptr == 64) {
            core1(buf);
            ptr = 0;
        }
    }

    @Override
    protected void engineUpdate(byte[] inbuf, int off, int len) {
        if (ptr != 0) {
            int rlen = 64 - ptr;
            if (len < rlen) {
                System.arraycopy(inbuf, off, buf, ptr, len);
                ptr += len;
                return;
            }

            System.arraycopy(inbuf, off, buf, ptr, rlen);
            off += rlen;
            len -= rlen;
            core1(buf);
        }
        int num = len >>> 6;
        if (num > 0) {
            core(inbuf, off, num);
            off += num << 6;
            len &= 63;
        }
        System.arraycopy(inbuf, off, buf, 0, len);
        ptr = len;
    }

    @Override
    protected byte[] engineDigest() {
        byte[] out = new byte[32];
        buf[ptr++] = (byte) 0x80;
        for (int i = ptr; i < 64; i++)
            buf[i] = 0;

        core1(buf); W--;
        core1(buf); W--;
        core1(buf); W--;
        core1(buf); W--;

        int j = 36;
        int w = 0;
        for (int i = 0; i < 32; i++) {
            if ((i & 3) == 0) { // 0 4 8 12 16 20 ...
                w = state[j++];
            }
            out[i] = (byte) w;
            w >>>= 8;
        }
        reset();
        return out;
    }

    private static int[] getIV() {
        if (IV == null) {
            Shabal256 sg = new Shabal256(false);

            sg.buf[ 0] =  0; sg.buf[ 1] = 1;
            sg.buf[ 4] =  1; sg.buf[ 5] = 1;
            sg.buf[ 8] =  2; sg.buf[ 9] = 1;
            sg.buf[12] =  3; sg.buf[13] = 1;
            sg.buf[16] =  4; sg.buf[17] = 1;
            sg.buf[20] =  5; sg.buf[21] = 1;
            sg.buf[24] =  6; sg.buf[25] = 1;
            sg.buf[28] =  7; sg.buf[29] = 1;
            sg.buf[32] =  8; sg.buf[33] = 1;
            sg.buf[36] =  9; sg.buf[37] = 1;
            sg.buf[40] = 10; sg.buf[41] = 1;
            sg.buf[44] = 11; sg.buf[45] = 1;
            sg.buf[48] = 12; sg.buf[49] = 1;
            sg.buf[52] = 13; sg.buf[53] = 1;
            sg.buf[56] = 14; sg.buf[57] = 1;
            sg.buf[60] = 15; sg.buf[61] = 1;

            sg.W = -1L;
            sg.core1(sg.buf);

            sg.buf[ 0] = 16;
            sg.buf[ 4] = 17;
            sg.buf[ 8] = 18;
            sg.buf[12] = 19;
            sg.buf[16] = 20;
            sg.buf[20] = 21;
            sg.buf[24] = 22;
            sg.buf[28] = 23;
            sg.buf[32] = 24;
            sg.buf[36] = 25;
            sg.buf[40] = 26;
            sg.buf[44] = 27;
            sg.buf[48] = 28;
            sg.buf[52] = 29;
            sg.buf[56] = 30;
            sg.buf[60] = 31;

            sg.core1(sg.buf);
            IV = new int[sg.state.length];
            System.arraycopy(sg.state, 0, IV, 0, IV.length);
        }
        return IV;
    }

    @Override
    public Shabal256 clone() {
        Shabal256 d = new Shabal256();
        System.arraycopy(buf, 0, d.buf, 0, ptr);
        d.ptr = ptr;
        System.arraycopy(state, 0, d.state, 0, 44);
        d.W = W;
        return d;
    }

    public int getBlockLength() {
        return 64;
    }

    private static int decodeLEInt(byte[] data, int off) {
        return (data[off]     & 0xFF)
                | ((data[off + 1] & 0xFF) << 8)
                | ((data[off + 2] & 0xFF) << 16)
                | ((data[off + 3] & 0xFF) << 24);
    }

    private void core(byte[] data, int off, int num) {
        int A0 = state[ 0];
        int A1 = state[ 1];
        int A2 = state[ 2];
        int A3 = state[ 3];
        int A4 = state[ 4];
        int A5 = state[ 5];
        int A6 = state[ 6];
        int A7 = state[ 7];
        int A8 = state[ 8];
        int A9 = state[ 9];
        int AA = state[10];
        int AB = state[11];

        int B0 = state[12];
        int B1 = state[13];
        int B2 = state[14];
        int B3 = state[15];
        int B4 = state[16];
        int B5 = state[17];
        int B6 = state[18];
        int B7 = state[19];
        int B8 = state[20];
        int B9 = state[21];
        int BA = state[22];
        int BB = state[23];
        int BC = state[24];
        int BD = state[25];
        int BE = state[26];
        int BF = state[27];

        int C0 = state[28];
        int C1 = state[29];
        int C2 = state[30];
        int C3 = state[31];
        int C4 = state[32];
        int C5 = state[33];
        int C6 = state[34];
        int C7 = state[35];
        int C8 = state[36];
        int C9 = state[37];
        int CA = state[38];
        int CB = state[39];
        int CC = state[40];
        int CD = state[41];
        int CE = state[42];
        int CF = state[43];

        while (num-- > 0) {
            int M0 = decodeLEInt(data, off);
            B0 += M0;
            B0 = (B0 << 17) | (B0 >>> 15);
            int M1 = decodeLEInt(data, off +  4);
            B1 += M1;
            B1 = (B1 << 17) | (B1 >>> 15);
            int M2 = decodeLEInt(data, off +  8);
            B2 += M2;
            B2 = (B2 << 17) | (B2 >>> 15);
            int M3 = decodeLEInt(data, off + 12);
            B3 += M3;
            B3 = (B3 << 17) | (B3 >>> 15);
            int M4 = decodeLEInt(data, off + 16);
            B4 += M4;
            B4 = (B4 << 17) | (B4 >>> 15);
            int M5 = decodeLEInt(data, off + 20);
            B5 += M5;
            B5 = (B5 << 17) | (B5 >>> 15);
            int M6 = decodeLEInt(data, off + 24);
            B6 += M6;
            B6 = (B6 << 17) | (B6 >>> 15);
            int M7 = decodeLEInt(data, off + 28);
            B7 += M7;
            B7 = (B7 << 17) | (B7 >>> 15);
            int M8 = decodeLEInt(data, off + 32);
            B8 += M8;
            B8 = (B8 << 17) | (B8 >>> 15);
            int M9 = decodeLEInt(data, off + 36);
            B9 += M9;
            B9 = (B9 << 17) | (B9 >>> 15);
            int MA = decodeLEInt(data, off + 40);
            BA += MA;
            BA = (BA << 17) | (BA >>> 15);
            int MB = decodeLEInt(data, off + 44);
            BB += MB;
            BB = (BB << 17) | (BB >>> 15);
            int MC = decodeLEInt(data, off + 48);
            BC += MC;
            BC = (BC << 17) | (BC >>> 15);
            int MD = decodeLEInt(data, off + 52);
            BD += MD;
            BD = (BD << 17) | (BD >>> 15);
            int ME = decodeLEInt(data, off + 56);
            BE += ME;
            BE = (BE << 17) | (BE >>> 15);
            int MF = decodeLEInt(data, off + 60);
            BF += MF;
            BF = (BF << 17) | (BF >>> 15);

            off += 64;
            A0 ^= (int) W;
            A1 ^= (int) (W >>> 32);
            W++;

            A0 = ((A0 ^ (((AB << 15) | (AB >>> 17)) * 5) ^ C8) * 3)
                    ^ BD ^ (B9 & ~B6) ^ M0;
            B0 = ~((B0 << 1) | (B0 >>> 31)) ^ A0;
            A1 = ((A1 ^ (((A0 << 15) | (A0 >>> 17)) * 5) ^ C7) * 3)
                    ^ BE ^ (BA & ~B7) ^ M1;
            B1 = ~((B1 << 1) | (B1 >>> 31)) ^ A1;
            A2 = ((A2 ^ (((A1 << 15) | (A1 >>> 17)) * 5) ^ C6) * 3)
                    ^ BF ^ (BB & ~B8) ^ M2;
            B2 = ~((B2 << 1) | (B2 >>> 31)) ^ A2;
            A3 = ((A3 ^ (((A2 << 15) | (A2 >>> 17)) * 5) ^ C5) * 3)
                    ^ B0 ^ (BC & ~B9) ^ M3;
            B3 = ~((B3 << 1) | (B3 >>> 31)) ^ A3;
            A4 = ((A4 ^ (((A3 << 15) | (A3 >>> 17)) * 5) ^ C4) * 3)
                    ^ B1 ^ (BD & ~BA) ^ M4;
            B4 = ~((B4 << 1) | (B4 >>> 31)) ^ A4;
            A5 = ((A5 ^ (((A4 << 15) | (A4 >>> 17)) * 5) ^ C3) * 3)
                    ^ B2 ^ (BE & ~BB) ^ M5;
            B5 = ~((B5 << 1) | (B5 >>> 31)) ^ A5;
            A6 = ((A6 ^ (((A5 << 15) | (A5 >>> 17)) * 5) ^ C2) * 3)
                    ^ B3 ^ (BF & ~BC) ^ M6;
            B6 = ~((B6 << 1) | (B6 >>> 31)) ^ A6;
            A7 = ((A7 ^ (((A6 << 15) | (A6 >>> 17)) * 5) ^ C1) * 3)
                    ^ B4 ^ (B0 & ~BD) ^ M7;
            B7 = ~((B7 << 1) | (B7 >>> 31)) ^ A7;
            A8 = ((A8 ^ (((A7 << 15) | (A7 >>> 17)) * 5) ^ C0) * 3) ^ B5 ^ (B1 & ~BE) ^ M8;

            B8 = ~((B8 << 1) | (B8 >>> 31)) ^ A8;
            A9 = ((A9 ^ (((A8 << 15) | (A8 >>> 17)) * 5) ^ CF) * 3)
                    ^ B6 ^ (B2 & ~BF) ^ M9;
            B9 = ~((B9 << 1) | (B9 >>> 31)) ^ A9;
            AA = ((AA ^ (((A9 << 15) | (A9 >>> 17)) * 5) ^ CE) * 3)
                    ^ B7 ^ (B3 & ~B0) ^ MA;
            BA = ~((BA << 1) | (BA >>> 31)) ^ AA;
            AB = ((AB ^ (((AA << 15) | (AA >>> 17)) * 5) ^ CD) * 3)
                    ^ B8 ^ (B4 & ~B1) ^ MB;
            BB = ~((BB << 1) | (BB >>> 31)) ^ AB;
            A0 = ((A0 ^ (((AB << 15) | (AB >>> 17)) * 5) ^ CC) * 3)
                    ^ B9 ^ (B5 & ~B2) ^ MC;
            BC = ~((BC << 1) | (BC >>> 31)) ^ A0;
            A1 = ((A1 ^ (((A0 << 15) | (A0 >>> 17)) * 5) ^ CB) * 3)
                    ^ BA ^ (B6 & ~B3) ^ MD;
            BD = ~((BD << 1) | (BD >>> 31)) ^ A1;
            A2 = ((A2 ^ (((A1 << 15) | (A1 >>> 17)) * 5) ^ CA) * 3)
                    ^ BB ^ (B7 & ~B4) ^ ME;
            BE = ~((BE << 1) | (BE >>> 31)) ^ A2;
            A3 = ((A3 ^ (((A2 << 15) | (A2 >>> 17)) * 5) ^ C9) * 3)
                    ^ BC ^ (B8 & ~B5) ^ MF;
            BF = ~((BF << 1) | (BF >>> 31)) ^ A3;
            A4 = ((A4 ^ (((A3 << 15) | (A3 >>> 17)) * 5) ^ C8) * 3)
                    ^ BD ^ (B9 & ~B6) ^ M0;
            B0 = ~((B0 << 1) | (B0 >>> 31)) ^ A4;
            A5 = ((A5 ^ (((A4 << 15) | (A4 >>> 17)) * 5) ^ C7) * 3)
                    ^ BE ^ (BA & ~B7) ^ M1;
            B1 = ~((B1 << 1) | (B1 >>> 31)) ^ A5;
            A6 = ((A6 ^ (((A5 << 15) | (A5 >>> 17)) * 5) ^ C6) * 3)
                    ^ BF ^ (BB & ~B8) ^ M2;
            B2 = ~((B2 << 1) | (B2 >>> 31)) ^ A6;
            A7 = ((A7 ^ (((A6 << 15) | (A6 >>> 17)) * 5) ^ C5) * 3)
                    ^ B0 ^ (BC & ~B9) ^ M3;
            B3 = ~((B3 << 1) | (B3 >>> 31)) ^ A7;
            A8 = ((A8 ^ (((A7 << 15) | (A7 >>> 17)) * 5) ^ C4) * 3)
                    ^ B1 ^ (BD & ~BA) ^ M4;
            B4 = ~((B4 << 1) | (B4 >>> 31)) ^ A8;
            A9 = ((A9 ^ (((A8 << 15) | (A8 >>> 17)) * 5) ^ C3) * 3)
                    ^ B2 ^ (BE & ~BB) ^ M5;
            B5 = ~((B5 << 1) | (B5 >>> 31)) ^ A9;
            AA = ((AA ^ (((A9 << 15) | (A9 >>> 17)) * 5) ^ C2) * 3)
                    ^ B3 ^ (BF & ~BC) ^ M6;
            B6 = ~((B6 << 1) | (B6 >>> 31)) ^ AA;
            AB = ((AB ^ (((AA << 15) | (AA >>> 17)) * 5) ^ C1) * 3)
                    ^ B4 ^ (B0 & ~BD) ^ M7;
            B7 = ~((B7 << 1) | (B7 >>> 31)) ^ AB;
            A0 = ((A0 ^ (((AB << 15) | (AB >>> 17)) * 5) ^ C0) * 3)
                    ^ B5 ^ (B1 & ~BE) ^ M8;
            B8 = ~((B8 << 1) | (B8 >>> 31)) ^ A0;
            A1 = ((A1 ^ (((A0 << 15) | (A0 >>> 17)) * 5) ^ CF) * 3)
                    ^ B6 ^ (B2 & ~BF) ^ M9;
            B9 = ~((B9 << 1) | (B9 >>> 31)) ^ A1;
            A2 = ((A2 ^ (((A1 << 15) | (A1 >>> 17)) * 5) ^ CE) * 3)
                    ^ B7 ^ (B3 & ~B0) ^ MA;
            BA = ~((BA << 1) | (BA >>> 31)) ^ A2;
            A3 = ((A3 ^ (((A2 << 15) | (A2 >>> 17)) * 5) ^ CD) * 3)
                    ^ B8 ^ (B4 & ~B1) ^ MB;
            BB = ~((BB << 1) | (BB >>> 31)) ^ A3;
            A4 = ((A4 ^ (((A3 << 15) | (A3 >>> 17)) * 5) ^ CC) * 3)
                    ^ B9 ^ (B5 & ~B2) ^ MC;
            BC = ~((BC << 1) | (BC >>> 31)) ^ A4;
            A5 = ((A5 ^ (((A4 << 15) | (A4 >>> 17)) * 5) ^ CB) * 3)
                    ^ BA ^ (B6 & ~B3) ^ MD;
            BD = ~((BD << 1) | (BD >>> 31)) ^ A5;
            A6 = ((A6 ^ (((A5 << 15) | (A5 >>> 17)) * 5) ^ CA) * 3)
                    ^ BB ^ (B7 & ~B4) ^ ME;
            BE = ~((BE << 1) | (BE >>> 31)) ^ A6;
            A7 = ((A7 ^ (((A6 << 15) | (A6 >>> 17)) * 5) ^ C9) * 3)
                    ^ BC ^ (B8 & ~B5) ^ MF;
            BF = ~((BF << 1) | (BF >>> 31)) ^ A7;
            A8 = ((A8 ^ (((A7 << 15) | (A7 >>> 17)) * 5) ^ C8) * 3)
                    ^ BD ^ (B9 & ~B6) ^ M0;
            B0 = ~((B0 << 1) | (B0 >>> 31)) ^ A8;
            A9 = ((A9 ^ (((A8 << 15) | (A8 >>> 17)) * 5) ^ C7) * 3)
                    ^ BE ^ (BA & ~B7) ^ M1;
            B1 = ~((B1 << 1) | (B1 >>> 31)) ^ A9;
            AA = ((AA ^ (((A9 << 15) | (A9 >>> 17)) * 5) ^ C6) * 3)
                    ^ BF ^ (BB & ~B8) ^ M2;
            B2 = ~((B2 << 1) | (B2 >>> 31)) ^ AA;
            AB = ((AB ^ (((AA << 15) | (AA >>> 17)) * 5) ^ C5) * 3)
                    ^ B0 ^ (BC & ~B9) ^ M3;
            B3 = ~((B3 << 1) | (B3 >>> 31)) ^ AB;
            A0 = ((A0 ^ (((AB << 15) | (AB >>> 17)) * 5) ^ C4) * 3)
                    ^ B1 ^ (BD & ~BA) ^ M4;
            B4 = ~((B4 << 1) | (B4 >>> 31)) ^ A0;
            A1 = ((A1 ^ (((A0 << 15) | (A0 >>> 17)) * 5) ^ C3) * 3)
                    ^ B2 ^ (BE & ~BB) ^ M5;
            B5 = ~((B5 << 1) | (B5 >>> 31)) ^ A1;
            A2 = ((A2 ^ (((A1 << 15) | (A1 >>> 17)) * 5) ^ C2) * 3)
                    ^ B3 ^ (BF & ~BC) ^ M6;
            B6 = ~((B6 << 1) | (B6 >>> 31)) ^ A2;
            A3 = ((A3 ^ (((A2 << 15) | (A2 >>> 17)) * 5) ^ C1) * 3)
                    ^ B4 ^ (B0 & ~BD) ^ M7;
            B7 = ~((B7 << 1) | (B7 >>> 31)) ^ A3;
            A4 = ((A4 ^ (((A3 << 15) | (A3 >>> 17)) * 5) ^ C0) * 3)
                    ^ B5 ^ (B1 & ~BE) ^ M8;
            B8 = ~((B8 << 1) | (B8 >>> 31)) ^ A4;
            A5 = ((A5 ^ (((A4 << 15) | (A4 >>> 17)) * 5) ^ CF) * 3)
                    ^ B6 ^ (B2 & ~BF) ^ M9;
            B9 = ~((B9 << 1) | (B9 >>> 31)) ^ A5;
            A6 = ((A6 ^ (((A5 << 15) | (A5 >>> 17)) * 5) ^ CE) * 3)
                    ^ B7 ^ (B3 & ~B0) ^ MA;
            BA = ~((BA << 1) | (BA >>> 31)) ^ A6;
            A7 = ((A7 ^ (((A6 << 15) | (A6 >>> 17)) * 5) ^ CD) * 3)
                    ^ B8 ^ (B4 & ~B1) ^ MB;
            BB = ~((BB << 1) | (BB >>> 31)) ^ A7;
            A8 = ((A8 ^ (((A7 << 15) | (A7 >>> 17)) * 5) ^ CC) * 3)
                    ^ B9 ^ (B5 & ~B2) ^ MC;
            BC = ~((BC << 1) | (BC >>> 31)) ^ A8;
            A9 = ((A9 ^ (((A8 << 15) | (A8 >>> 17)) * 5) ^ CB) * 3)
                    ^ BA ^ (B6 & ~B3) ^ MD;
            BD = ~((BD << 1) | (BD >>> 31)) ^ A9;
            AA = ((AA ^ (((A9 << 15) | (A9 >>> 17)) * 5) ^ CA) * 3)
                    ^ BB ^ (B7 & ~B4) ^ ME;
            BE = ~((BE << 1) | (BE >>> 31)) ^ AA;
            AB = ((AB ^ (((AA << 15) | (AA >>> 17)) * 5) ^ C9) * 3)
                    ^ BC ^ (B8 & ~B5) ^ MF;
            BF = ~((BF << 1) | (BF >>> 31)) ^ AB;

            AB += C6 + CA + CE;
            AA += C5 + C9 + CD;
            A9 += C4 + C8 + CC;
            A8 += C3 + C7 + CB;
            A7 += C2 + C6 + CA;
            A6 += C1 + C5 + C9;
            A5 += C0 + C4 + C8;
            A4 += CF + C3 + C7;
            A3 += CE + C2 + C6;
            A2 += CD + C1 + C5;
            A1 += CC + C0 + C4;
            A0 += CB + CF + C3;

            int tmp;
            tmp = B0; B0 = C0 - M0; C0 = tmp;
            tmp = B1; B1 = C1 - M1; C1 = tmp;
            tmp = B2; B2 = C2 - M2; C2 = tmp;
            tmp = B3; B3 = C3 - M3; C3 = tmp;
            tmp = B4; B4 = C4 - M4; C4 = tmp;
            tmp = B5; B5 = C5 - M5; C5 = tmp;
            tmp = B6; B6 = C6 - M6; C6 = tmp;
            tmp = B7; B7 = C7 - M7; C7 = tmp;
            tmp = B8; B8 = C8 - M8; C8 = tmp;
            tmp = B9; B9 = C9 - M9; C9 = tmp;
            tmp = BA; BA = CA - MA; CA = tmp;
            tmp = BB; BB = CB - MB; CB = tmp;
            tmp = BC; BC = CC - MC; CC = tmp;
            tmp = BD; BD = CD - MD; CD = tmp;
            tmp = BE; BE = CE - ME; CE = tmp;
            tmp = BF; BF = CF - MF; CF = tmp;
        }

        state[ 0] = A0;
        state[ 1] = A1;
        state[ 2] = A2;
        state[ 3] = A3;
        state[ 4] = A4;
        state[ 5] = A5;
        state[ 6] = A6;
        state[ 7] = A7;
        state[ 8] = A8;
        state[ 9] = A9;
        state[10] = AA;
        state[11] = AB;

        state[12] = B0;
        state[13] = B1;
        state[14] = B2;
        state[15] = B3;
        state[16] = B4;
        state[17] = B5;
        state[18] = B6;
        state[19] = B7;
        state[20] = B8;
        state[21] = B9;
        state[22] = BA;
        state[23] = BB;
        state[24] = BC;
        state[25] = BD;
        state[26] = BE;
        state[27] = BF;

        state[28] = C0;
        state[29] = C1;
        state[30] = C2;
        state[31] = C3;
        state[32] = C4;
        state[33] = C5;
        state[34] = C6;
        state[35] = C7;
        state[36] = C8;
        state[37] = C9;
        state[38] = CA;
        state[39] = CB;
        state[40] = CC;
        state[41] = CD;
        state[42] = CE;
        state[43] = CF;
    }

    private void core1(byte[] data) {
        int A0 = state[ 0];
        int A1 = state[ 1];
        int A2 = state[ 2];
        int A3 = state[ 3];
        int A4 = state[ 4];
        int A5 = state[ 5];
        int A6 = state[ 6];
        int A7 = state[ 7];
        int A8 = state[ 8];
        int A9 = state[ 9];
        int AA = state[10];
        int AB = state[11];

        int B0 = state[12];
        int B1 = state[13];
        int B2 = state[14];
        int B3 = state[15];
        int B4 = state[16];
        int B5 = state[17];
        int B6 = state[18];
        int B7 = state[19];
        int B8 = state[20];
        int B9 = state[21];
        int BA = state[22];
        int BB = state[23];
        int BC = state[24];
        int BD = state[25];
        int BE = state[26];
        int BF = state[27];

        int C0 = state[28];
        int C1 = state[29];
        int C2 = state[30];
        int C3 = state[31];
        int C4 = state[32];
        int C5 = state[33];
        int C6 = state[34];
        int C7 = state[35];
        int C8 = state[36];
        int C9 = state[37];
        int CA = state[38];
        int CB = state[39];
        int CC = state[40];
        int CD = state[41];
        int CE = state[42];
        int CF = state[43];

        int M0 = decodeLEInt(data, 0);
        B0 += M0;
        B0 = (B0 << 17) | (B0 >>> 15);
        int M1 = decodeLEInt(data, 4);
        B1 += M1;
        B1 = (B1 << 17) | (B1 >>> 15);
        int M2 = decodeLEInt(data, 8);
        B2 += M2;
        B2 = (B2 << 17) | (B2 >>> 15);
        int M3 = decodeLEInt(data, 12);
        B3 += M3;
        B3 = (B3 << 17) | (B3 >>> 15);
        int M4 = decodeLEInt(data, 16);
        B4 += M4;
        B4 = (B4 << 17) | (B4 >>> 15);
        int M5 = decodeLEInt(data, 20);
        B5 += M5;
        B5 = (B5 << 17) | (B5 >>> 15);
        int M6 = decodeLEInt(data, 24);
        B6 += M6;
        B6 = (B6 << 17) | (B6 >>> 15);
        int M7 = decodeLEInt(data, 28);
        B7 += M7;
        B7 = (B7 << 17) | (B7 >>> 15);
        int M8 = decodeLEInt(data, 32);
        B8 += M8;
        B8 = (B8 << 17) | (B8 >>> 15);
        int M9 = decodeLEInt(data, 36);
        B9 += M9;
        B9 = (B9 << 17) | (B9 >>> 15);
        int MA = decodeLEInt(data, 40);
        BA += MA;
        BA = (BA << 17) | (BA >>> 15);
        int MB = decodeLEInt(data, 44);
        BB += MB;
        BB = (BB << 17) | (BB >>> 15);
        int MC = decodeLEInt(data, 48);
        BC += MC;
        BC = (BC << 17) | (BC >>> 15);
        int MD = decodeLEInt(data, 52);
        BD += MD;
        BD = (BD << 17) | (BD >>> 15);
        int ME = decodeLEInt(data, 56);
        BE += ME;
        BE = (BE << 17) | (BE >>> 15);
        int MF = decodeLEInt(data, 60);
        BF += MF;
        BF = (BF << 17) | (BF >>> 15);

        A0 ^= (int) W;
        A1 ^= (int) (W >>> 32);
        W++;

        A0 = ((A0 ^ (((AB << 15) | (AB >>> 17)) * 5) ^ C8) * 3)
                ^ BD ^ (B9 & ~B6) ^ M0;
        B0 = ~((B0 << 1) | (B0 >>> 31)) ^ A0;
        A1 = ((A1 ^ (((A0 << 15) | (A0 >>> 17)) * 5) ^ C7) * 3)
                ^ BE ^ (BA & ~B7) ^ M1;
        B1 = ~((B1 << 1) | (B1 >>> 31)) ^ A1;
        A2 = ((A2 ^ (((A1 << 15) | (A1 >>> 17)) * 5) ^ C6) * 3)
                ^ BF ^ (BB & ~B8) ^ M2;
        B2 = ~((B2 << 1) | (B2 >>> 31)) ^ A2;
        A3 = ((A3 ^ (((A2 << 15) | (A2 >>> 17)) * 5) ^ C5) * 3)
                ^ B0 ^ (BC & ~B9) ^ M3;
        B3 = ~((B3 << 1) | (B3 >>> 31)) ^ A3;
        A4 = ((A4 ^ (((A3 << 15) | (A3 >>> 17)) * 5) ^ C4) * 3)
                ^ B1 ^ (BD & ~BA) ^ M4;
        B4 = ~((B4 << 1) | (B4 >>> 31)) ^ A4;
        A5 = ((A5 ^ (((A4 << 15) | (A4 >>> 17)) * 5) ^ C3) * 3)
                ^ B2 ^ (BE & ~BB) ^ M5;
        B5 = ~((B5 << 1) | (B5 >>> 31)) ^ A5;
        A6 = ((A6 ^ (((A5 << 15) | (A5 >>> 17)) * 5) ^ C2) * 3)
                ^ B3 ^ (BF & ~BC) ^ M6;
        B6 = ~((B6 << 1) | (B6 >>> 31)) ^ A6;
        A7 = ((A7 ^ (((A6 << 15) | (A6 >>> 17)) * 5) ^ C1) * 3)
                ^ B4 ^ (B0 & ~BD) ^ M7;
        B7 = ~((B7 << 1) | (B7 >>> 31)) ^ A7;
        A8 = ((A8 ^ (((A7 << 15) | (A7 >>> 17)) * 5) ^ C0) * 3) ^ B5 ^ (B1 & ~BE) ^ M8;

        B8 = ~((B8 << 1) | (B8 >>> 31)) ^ A8;
        A9 = ((A9 ^ (((A8 << 15) | (A8 >>> 17)) * 5) ^ CF) * 3)
                ^ B6 ^ (B2 & ~BF) ^ M9;
        B9 = ~((B9 << 1) | (B9 >>> 31)) ^ A9;
        AA = ((AA ^ (((A9 << 15) | (A9 >>> 17)) * 5) ^ CE) * 3)
                ^ B7 ^ (B3 & ~B0) ^ MA;
        BA = ~((BA << 1) | (BA >>> 31)) ^ AA;
        AB = ((AB ^ (((AA << 15) | (AA >>> 17)) * 5) ^ CD) * 3)
                ^ B8 ^ (B4 & ~B1) ^ MB;
        BB = ~((BB << 1) | (BB >>> 31)) ^ AB;
        A0 = ((A0 ^ (((AB << 15) | (AB >>> 17)) * 5) ^ CC) * 3)
                ^ B9 ^ (B5 & ~B2) ^ MC;
        BC = ~((BC << 1) | (BC >>> 31)) ^ A0;
        A1 = ((A1 ^ (((A0 << 15) | (A0 >>> 17)) * 5) ^ CB) * 3)
                ^ BA ^ (B6 & ~B3) ^ MD;
        BD = ~((BD << 1) | (BD >>> 31)) ^ A1;
        A2 = ((A2 ^ (((A1 << 15) | (A1 >>> 17)) * 5) ^ CA) * 3)
                ^ BB ^ (B7 & ~B4) ^ ME;
        BE = ~((BE << 1) | (BE >>> 31)) ^ A2;
        A3 = ((A3 ^ (((A2 << 15) | (A2 >>> 17)) * 5) ^ C9) * 3)
                ^ BC ^ (B8 & ~B5) ^ MF;
        BF = ~((BF << 1) | (BF >>> 31)) ^ A3;
        A4 = ((A4 ^ (((A3 << 15) | (A3 >>> 17)) * 5) ^ C8) * 3)
                ^ BD ^ (B9 & ~B6) ^ M0;
        B0 = ~((B0 << 1) | (B0 >>> 31)) ^ A4;
        A5 = ((A5 ^ (((A4 << 15) | (A4 >>> 17)) * 5) ^ C7) * 3)
                ^ BE ^ (BA & ~B7) ^ M1;
        B1 = ~((B1 << 1) | (B1 >>> 31)) ^ A5;
        A6 = ((A6 ^ (((A5 << 15) | (A5 >>> 17)) * 5) ^ C6) * 3)
                ^ BF ^ (BB & ~B8) ^ M2;
        B2 = ~((B2 << 1) | (B2 >>> 31)) ^ A6;
        A7 = ((A7 ^ (((A6 << 15) | (A6 >>> 17)) * 5) ^ C5) * 3)
                ^ B0 ^ (BC & ~B9) ^ M3;
        B3 = ~((B3 << 1) | (B3 >>> 31)) ^ A7;
        A8 = ((A8 ^ (((A7 << 15) | (A7 >>> 17)) * 5) ^ C4) * 3)
                ^ B1 ^ (BD & ~BA) ^ M4;
        B4 = ~((B4 << 1) | (B4 >>> 31)) ^ A8;
        A9 = ((A9 ^ (((A8 << 15) | (A8 >>> 17)) * 5) ^ C3) * 3)
                ^ B2 ^ (BE & ~BB) ^ M5;
        B5 = ~((B5 << 1) | (B5 >>> 31)) ^ A9;
        AA = ((AA ^ (((A9 << 15) | (A9 >>> 17)) * 5) ^ C2) * 3)
                ^ B3 ^ (BF & ~BC) ^ M6;
        B6 = ~((B6 << 1) | (B6 >>> 31)) ^ AA;
        AB = ((AB ^ (((AA << 15) | (AA >>> 17)) * 5) ^ C1) * 3)
                ^ B4 ^ (B0 & ~BD) ^ M7;
        B7 = ~((B7 << 1) | (B7 >>> 31)) ^ AB;
        A0 = ((A0 ^ (((AB << 15) | (AB >>> 17)) * 5) ^ C0) * 3)
                ^ B5 ^ (B1 & ~BE) ^ M8;
        B8 = ~((B8 << 1) | (B8 >>> 31)) ^ A0;
        A1 = ((A1 ^ (((A0 << 15) | (A0 >>> 17)) * 5) ^ CF) * 3)
                ^ B6 ^ (B2 & ~BF) ^ M9;
        B9 = ~((B9 << 1) | (B9 >>> 31)) ^ A1;
        A2 = ((A2 ^ (((A1 << 15) | (A1 >>> 17)) * 5) ^ CE) * 3)
                ^ B7 ^ (B3 & ~B0) ^ MA;
        BA = ~((BA << 1) | (BA >>> 31)) ^ A2;
        A3 = ((A3 ^ (((A2 << 15) | (A2 >>> 17)) * 5) ^ CD) * 3)
                ^ B8 ^ (B4 & ~B1) ^ MB;
        BB = ~((BB << 1) | (BB >>> 31)) ^ A3;
        A4 = ((A4 ^ (((A3 << 15) | (A3 >>> 17)) * 5) ^ CC) * 3)
                ^ B9 ^ (B5 & ~B2) ^ MC;
        BC = ~((BC << 1) | (BC >>> 31)) ^ A4;
        A5 = ((A5 ^ (((A4 << 15) | (A4 >>> 17)) * 5) ^ CB) * 3)
                ^ BA ^ (B6 & ~B3) ^ MD;
        BD = ~((BD << 1) | (BD >>> 31)) ^ A5;
        A6 = ((A6 ^ (((A5 << 15) | (A5 >>> 17)) * 5) ^ CA) * 3)
                ^ BB ^ (B7 & ~B4) ^ ME;
        BE = ~((BE << 1) | (BE >>> 31)) ^ A6;
        A7 = ((A7 ^ (((A6 << 15) | (A6 >>> 17)) * 5) ^ C9) * 3)
                ^ BC ^ (B8 & ~B5) ^ MF;
        BF = ~((BF << 1) | (BF >>> 31)) ^ A7;
        A8 = ((A8 ^ (((A7 << 15) | (A7 >>> 17)) * 5) ^ C8) * 3)
                ^ BD ^ (B9 & ~B6) ^ M0;
        B0 = ~((B0 << 1) | (B0 >>> 31)) ^ A8;
        A9 = ((A9 ^ (((A8 << 15) | (A8 >>> 17)) * 5) ^ C7) * 3)
                ^ BE ^ (BA & ~B7) ^ M1;
        B1 = ~((B1 << 1) | (B1 >>> 31)) ^ A9;
        AA = ((AA ^ (((A9 << 15) | (A9 >>> 17)) * 5) ^ C6) * 3)
                ^ BF ^ (BB & ~B8) ^ M2;
        B2 = ~((B2 << 1) | (B2 >>> 31)) ^ AA;
        AB = ((AB ^ (((AA << 15) | (AA >>> 17)) * 5) ^ C5) * 3)
                ^ B0 ^ (BC & ~B9) ^ M3;
        B3 = ~((B3 << 1) | (B3 >>> 31)) ^ AB;
        A0 = ((A0 ^ (((AB << 15) | (AB >>> 17)) * 5) ^ C4) * 3)
                ^ B1 ^ (BD & ~BA) ^ M4;
        B4 = ~((B4 << 1) | (B4 >>> 31)) ^ A0;
        A1 = ((A1 ^ (((A0 << 15) | (A0 >>> 17)) * 5) ^ C3) * 3)
                ^ B2 ^ (BE & ~BB) ^ M5;
        B5 = ~((B5 << 1) | (B5 >>> 31)) ^ A1;
        A2 = ((A2 ^ (((A1 << 15) | (A1 >>> 17)) * 5) ^ C2) * 3)
                ^ B3 ^ (BF & ~BC) ^ M6;
        B6 = ~((B6 << 1) | (B6 >>> 31)) ^ A2;
        A3 = ((A3 ^ (((A2 << 15) | (A2 >>> 17)) * 5) ^ C1) * 3)
                ^ B4 ^ (B0 & ~BD) ^ M7;
        B7 = ~((B7 << 1) | (B7 >>> 31)) ^ A3;
        A4 = ((A4 ^ (((A3 << 15) | (A3 >>> 17)) * 5) ^ C0) * 3)
                ^ B5 ^ (B1 & ~BE) ^ M8;
        B8 = ~((B8 << 1) | (B8 >>> 31)) ^ A4;
        A5 = ((A5 ^ (((A4 << 15) | (A4 >>> 17)) * 5) ^ CF) * 3)
                ^ B6 ^ (B2 & ~BF) ^ M9;
        B9 = ~((B9 << 1) | (B9 >>> 31)) ^ A5;
        A6 = ((A6 ^ (((A5 << 15) | (A5 >>> 17)) * 5) ^ CE) * 3)
                ^ B7 ^ (B3 & ~B0) ^ MA;
        BA = ~((BA << 1) | (BA >>> 31)) ^ A6;
        A7 = ((A7 ^ (((A6 << 15) | (A6 >>> 17)) * 5) ^ CD) * 3)
                ^ B8 ^ (B4 & ~B1) ^ MB;
        BB = ~((BB << 1) | (BB >>> 31)) ^ A7;
        A8 = ((A8 ^ (((A7 << 15) | (A7 >>> 17)) * 5) ^ CC) * 3)
                ^ B9 ^ (B5 & ~B2) ^ MC;
        BC = ~((BC << 1) | (BC >>> 31)) ^ A8;
        A9 = ((A9 ^ (((A8 << 15) | (A8 >>> 17)) * 5) ^ CB) * 3)
                ^ BA ^ (B6 & ~B3) ^ MD;
        BD = ~((BD << 1) | (BD >>> 31)) ^ A9;
        AA = ((AA ^ (((A9 << 15) | (A9 >>> 17)) * 5) ^ CA) * 3)
                ^ BB ^ (B7 & ~B4) ^ ME;
        BE = ~((BE << 1) | (BE >>> 31)) ^ AA;
        AB = ((AB ^ (((AA << 15) | (AA >>> 17)) * 5) ^ C9) * 3)
                ^ BC ^ (B8 & ~B5) ^ MF;
        BF = ~((BF << 1) | (BF >>> 31)) ^ AB;

        AB += C6 + CA + CE;
        AA += C5 + C9 + CD;
        A9 += C4 + C8 + CC;
        A8 += C3 + C7 + CB;
        A7 += C2 + C6 + CA;
        A6 += C1 + C5 + C9;
        A5 += C0 + C4 + C8;
        A4 += CF + C3 + C7;
        A3 += CE + C2 + C6;
        A2 += CD + C1 + C5;
        A1 += CC + C0 + C4;
        A0 += CB + CF + C3;

        int tmp;
        tmp = B0; B0 = C0 - M0; C0 = tmp;
        tmp = B1; B1 = C1 - M1; C1 = tmp;
        tmp = B2; B2 = C2 - M2; C2 = tmp;
        tmp = B3; B3 = C3 - M3; C3 = tmp;
        tmp = B4; B4 = C4 - M4; C4 = tmp;
        tmp = B5; B5 = C5 - M5; C5 = tmp;
        tmp = B6; B6 = C6 - M6; C6 = tmp;
        tmp = B7; B7 = C7 - M7; C7 = tmp;
        tmp = B8; B8 = C8 - M8; C8 = tmp;
        tmp = B9; B9 = C9 - M9; C9 = tmp;
        tmp = BA; BA = CA - MA; CA = tmp;
        tmp = BB; BB = CB - MB; CB = tmp;
        tmp = BC; BC = CC - MC; CC = tmp;
        tmp = BD; BD = CD - MD; CD = tmp;
        tmp = BE; BE = CE - ME; CE = tmp;
        tmp = BF; BF = CF - MF; CF = tmp;

        state[ 0] = A0;
        state[ 1] = A1;
        state[ 2] = A2;
        state[ 3] = A3;
        state[ 4] = A4;
        state[ 5] = A5;
        state[ 6] = A6;
        state[ 7] = A7;
        state[ 8] = A8;
        state[ 9] = A9;
        state[10] = AA;
        state[11] = AB;

        state[12] = B0;
        state[13] = B1;
        state[14] = B2;
        state[15] = B3;
        state[16] = B4;
        state[17] = B5;
        state[18] = B6;
        state[19] = B7;
        state[20] = B8;
        state[21] = B9;
        state[22] = BA;
        state[23] = BB;
        state[24] = BC;
        state[25] = BD;
        state[26] = BE;
        state[27] = BF;

        state[28] = C0;
        state[29] = C1;
        state[30] = C2;
        state[31] = C3;
        state[32] = C4;
        state[33] = C5;
        state[34] = C6;
        state[35] = C7;
        state[36] = C8;
        state[37] = C9;
        state[38] = CA;
        state[39] = CB;
        state[40] = CC;
        state[41] = CD;
        state[42] = CE;
        state[43] = CF;
    }

    @Override
    protected int engineGetDigestLength() {
        return 32;
    }
}
