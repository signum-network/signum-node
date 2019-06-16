package brs.util;

import brs.crypto.Crypto;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;

public class MiningPlot {
  private static final int HASH_SIZE = 32;
  private static final int HASHES_PER_SCOOP = 2;
  public static final int SCOOP_SIZE = HASHES_PER_SCOOP * HASH_SIZE;
  public static final int SCOOPS_PER_PLOT = 4096; // original 1MB/plot = 16384
  public static final int PLOT_SIZE = SCOOPS_PER_PLOT * SCOOP_SIZE;

  private static final int HASH_CAP = 4096;

  private final byte[] data = new byte[PLOT_SIZE];

    public MiningPlot(long addr, long nonce, int blockHeight, FluxCapacitor fluxCapacitor) {
      ByteBuffer baseBuffer = ByteBuffer.allocate(16);
    baseBuffer.putLong(addr);
    baseBuffer.putLong(nonce);
    byte[] base = baseBuffer.array();
    MessageDigest shabal256 = Crypto.shabal256();
    byte[] gendata = new byte[PLOT_SIZE + base.length];
    System.arraycopy(base, 0, gendata, PLOT_SIZE, base.length);
    for (int i = PLOT_SIZE; i > 0; i -= HASH_SIZE) {
      int len = PLOT_SIZE + base.length - i;
      if (len > HASH_CAP) {
        len = HASH_CAP;
      }
      shabal256.update(gendata, i, len);
      System.arraycopy(shabal256.digest(), 0, gendata, i - HASH_SIZE, HASH_SIZE);
    }
    byte[] finalhash = shabal256.digest(gendata);
    for (int i = 0; i < PLOT_SIZE; i++) {
      data[i] = (byte) (gendata[i] ^ finalhash[i % HASH_SIZE]);
    }
    //PoC2 Rearrangement
    if (fluxCapacitor.getValue(FluxValues.POC2, blockHeight)) {
      byte[] hashBuffer = new byte[HASH_SIZE];
      int revPos = PLOT_SIZE - HASH_SIZE; //Start at second hash in last scoop
      for (int pos = 32; pos < (PLOT_SIZE / 2); pos += 64) { //Start at second hash in first scoop
        System.arraycopy(data, pos, hashBuffer, 0, HASH_SIZE); //Copy low scoop second hash to buffer
        System.arraycopy(data, revPos, data, pos, HASH_SIZE); //Copy high scoop second hash to low scoop second hash
        System.arraycopy(hashBuffer, 0, data, revPos, HASH_SIZE); //Copy buffer to high scoop second hash
        revPos -= 64; //move backwards
      }
    }
  }

  public byte[] getScoop(int pos) {
    return Arrays.copyOfRange(data, pos * SCOOP_SIZE, (pos + 1) * SCOOP_SIZE);
  }

  public void hashScoop(MessageDigest shabal256, int pos) {
    shabal256.update(data, pos * SCOOP_SIZE, SCOOP_SIZE);
  }
}
