package brs;

import brs.crypto.Crypto;
import brs.util.Convert;

public final class Token {

  public static String generateToken(String secretPhrase, String websiteString, int timestamp) {

    byte[] website = Convert.toBytes(websiteString);
    byte[] data = new byte[website.length + 32 + 4];
    System.arraycopy(website, 0, data, 0, website.length);
    System.arraycopy(Crypto.getPublicKey(secretPhrase), 0, data, website.length, 32);

    data[website.length + 32] = (byte)timestamp;
    data[website.length + 32 + 1] = (byte)(timestamp >> 8);
    data[website.length + 32 + 2] = (byte)(timestamp >> 16);
    data[website.length + 32 + 3] = (byte)(timestamp >> 24);

    byte[] token = new byte[100];
    System.arraycopy(data, website.length, token, 0, 32 + 4);
    System.arraycopy(Crypto.sign(data, secretPhrase), 0, token, 32 + 4, 64);

    StringBuilder buf = new StringBuilder();
    for (int ptr = 0; ptr < 100; ptr += 5) {

      long number = ((long)(token[ptr] & 0xFF)) | (((long)(token[ptr + 1] & 0xFF)) << 8) | (((long)(token[ptr + 2] & 0xFF)) << 16)
          | (((long)(token[ptr + 3] & 0xFF)) << 24) | (((long)(token[ptr + 4] & 0xFF)) << 32);

      if (number < 32) {
        buf.append("0000000");
      } else if (number < 1024) {
        buf.append("000000");
      } else if (number < 32768) {
        buf.append("00000");
      } else if (number < 1048576) {
        buf.append("0000");
      } else if (number < 33554432) {
        buf.append("000");
      } else if (number < 1073741824) {
        buf.append("00");
      } else if (number < 34359738368L) {
        buf.append("0");
      }
      buf.append(Long.toString(number, 32));

    }

    return buf.toString();

  }

  public static Token parseToken(String tokenString, String website) {

    byte[] websiteBytes = Convert.toBytes(website);
    byte[] tokenBytes = new byte[100];
    int i = 0;
    int j = 0;

    for (; i < tokenString.length(); i += 8, j += 5) {

      long number = Long.parseLong(tokenString.substring(i, i + 8), 32);
      tokenBytes[j] = (byte)number;
      tokenBytes[j + 1] = (byte)(number >> 8);
      tokenBytes[j + 2] = (byte)(number >> 16);
      tokenBytes[j + 3] = (byte)(number >> 24);
      tokenBytes[j + 4] = (byte)(number >> 32);

    }

    if (i != 160) {
      throw new IllegalArgumentException("Invalid token string: " + tokenString);
    }
    byte[] publicKey = new byte[32];
    System.arraycopy(tokenBytes, 0, publicKey, 0, 32);
    int timestamp = (tokenBytes[32] & 0xFF) | ((tokenBytes[33] & 0xFF) << 8) | ((tokenBytes[34] & 0xFF) << 16) | ((tokenBytes[35] & 0xFF) << 24);
    byte[] signature = new byte[64];
    System.arraycopy(tokenBytes, 36, signature, 0, 64);

    byte[] data = new byte[websiteBytes.length + 36];
    System.arraycopy(websiteBytes, 0, data, 0, websiteBytes.length);
    System.arraycopy(tokenBytes, 0, data, websiteBytes.length, 36);
    boolean isValid = Crypto.verify(signature, data, publicKey, true);

    return new Token(publicKey, timestamp, isValid);

  }

  private final byte[] publicKey;
  private final int timestamp;
  private final boolean isValid;

  private Token(byte[] publicKey, int timestamp, boolean isValid) {
    this.publicKey = publicKey;
    this.timestamp = timestamp;
    this.isValid = isValid;
  }

  public byte[] getPublicKey() {
    return publicKey;
  }

  public int getTimestamp() {
    return timestamp;
  }

  public boolean isValid() {
    return isValid;
  }

}
