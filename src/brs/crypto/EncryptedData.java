package brs.crypto;

import brs.BurstException;
import burst.kit.entity.BurstEncryptedMessage;

import java.nio.ByteBuffer;

// TODO replace this class with the one from burstkit4j
public class EncryptedData {
  private static final EncryptedData EMPTY_DATA = new EncryptedData(new byte[0], new byte[0]);

  public static EncryptedData encrypt(byte[] plaintext, byte[] myPrivateKey, byte[] theirPublicKey) {
    if (plaintext.length == 0) {
      return EMPTY_DATA;
    }
    BurstEncryptedMessage message = Crypto.burstCrypto.encryptBytesMessage(plaintext, myPrivateKey, theirPublicKey);
    return new EncryptedData(message.getData(), message.getNonce());
  }

  public static EncryptedData readEncryptedData(ByteBuffer buffer, int length, int maxLength)
    throws BurstException.NotValidException {
    if (length == 0) {
      return EMPTY_DATA;
    }
    if (length > maxLength) {
      throw new BurstException.NotValidException("Max encrypted data length exceeded: " + length);
    }
    byte[] noteBytes = new byte[length];
    buffer.get(noteBytes);
    byte[] noteNonceBytes = new byte[32];
    buffer.get(noteNonceBytes);
    return new EncryptedData(noteBytes, noteNonceBytes);
  }

  private final byte[] data;
  private final byte[] nonce;

  public EncryptedData(byte[] data, byte[] nonce) {
    this.data = data;
    this.nonce = nonce;
  }

  public byte[] decrypt(byte[] myPrivateKey, byte[] theirPublicKey) {
    if (data.length == 0) {
      return data;
    }
    return Crypto.burstCrypto.decryptMessage(new BurstEncryptedMessage(data, nonce, false), myPrivateKey, theirPublicKey);
  }

  public byte[] getData() {
    return data;
  }

  public byte[] getNonce() {
    return nonce;
  }

  public int getSize() {
    return data.length + nonce.length;
  }
}
