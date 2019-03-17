package brs.crypto;

import brs.crypto.ec.Curve25519;
import brs.crypto.ec.Curve25519Impl;
import brs.crypto.hash.Shabal256;
import brs.util.Convert;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

public final class Crypto {

  private static final Logger logger = LoggerFactory.getLogger(Crypto.class);

  private static final ThreadLocal<SecureRandom> secureRandom = ThreadLocal.withInitial(SecureRandom::new);

  private static final Curve25519 curve25519 = new Curve25519Impl();

  private Crypto() {
  } //never

  private static MessageDigest getMessageDigest(String algorithm) {
    try {
      return MessageDigest.getInstance(algorithm);
    } catch (NoSuchAlgorithmException e) {
      logger.info("Missing message digest algorithm: " + algorithm);
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  public static MessageDigest sha256() {
    return getMessageDigest("SHA-256");
  }

  public static MessageDigest shabal256() {
    return getMessageDigest(Shabal256.ALGORITHM);
  }

  public static byte[] getPublicKey(String secretPhrase) {
    return curve25519.getPublicKey(Crypto.sha256().digest(Convert.toBytes(secretPhrase)));
  }

  public static byte[] getPrivateKey(String secretPhrase) {
    byte[] s = Crypto.sha256().digest(Convert.toBytes(secretPhrase));
    curve25519.clampPrivateKey(s);
    return s;
  }

  public static byte[] sign(byte[] message, String secretPhrase) {
      return curve25519.sign(message, getPrivateKey(secretPhrase));
  }

  public static boolean verify(byte[] signature, byte[] message, byte[] publicKey, boolean enforceCanonical) {
      return curve25519.verify(message, signature, publicKey, enforceCanonical);
  }

  public static byte[] aesEncrypt(byte[] plaintext, byte[] myPrivateKey, byte[] theirPublicKey) {
    return aesEncrypt(plaintext, myPrivateKey, theirPublicKey, new byte[32]);
  }

  public static byte[] aesEncrypt(byte[] plaintext, byte[] myPrivateKey, byte[] theirPublicKey, byte[] nonce) {
    try {
      byte[] dhSharedSecret = curve25519.getSharedSecret(myPrivateKey, theirPublicKey);
      for (int i = 0; i < 32; i++) {
        dhSharedSecret[i] ^= nonce[i];
      }
      byte[] key = sha256().digest(dhSharedSecret);
      byte[] iv = new byte[16];
      secureRandom.get().nextBytes(iv);
      PaddedBufferedBlockCipher aes = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()));
      CipherParameters ivAndKey = new ParametersWithIV(new KeyParameter(key), iv);
      aes.init(true, ivAndKey);
      byte[] output = new byte[aes.getOutputSize(plaintext.length)];
      int ciphertextLength = aes.processBytes(plaintext, 0, plaintext.length, output, 0);
      ciphertextLength += aes.doFinal(output, ciphertextLength);
      byte[] result = new byte[iv.length + ciphertextLength];
      System.arraycopy(iv, 0, result, 0, iv.length);
      System.arraycopy(output, 0, result, iv.length, ciphertextLength);
      return result;
    } catch (InvalidCipherTextException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  public static byte[] aesDecrypt(byte[] ivCiphertext, byte[] myPrivateKey, byte[] theirPublicKey) {
    return aesDecrypt(ivCiphertext, myPrivateKey, theirPublicKey, new byte[32]);
  }

  public static byte[] aesDecrypt(byte[] ivCiphertext, byte[] myPrivateKey, byte[] theirPublicKey, byte[] nonce) {
    try {
      if (ivCiphertext.length < 16 || ivCiphertext.length % 16 != 0) {
        throw new InvalidCipherTextException("invalid ciphertext");
      }
      byte[] iv = Arrays.copyOfRange(ivCiphertext, 0, 16);
      byte[] ciphertext = Arrays.copyOfRange(ivCiphertext, 16, ivCiphertext.length);
        byte[] dhSharedSecret = curve25519.getSharedSecret(myPrivateKey, theirPublicKey);
      for (int i = 0; i < 32; i++) {
        dhSharedSecret[i] ^= nonce[i];
      }
      byte[] key = sha256().digest(dhSharedSecret);
      PaddedBufferedBlockCipher aes = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()));
      CipherParameters ivAndKey = new ParametersWithIV(new KeyParameter(key), iv);
      aes.init(false, ivAndKey);
      byte[] output = new byte[aes.getOutputSize(ciphertext.length)];
      int plaintextLength = aes.processBytes(ciphertext, 0, ciphertext.length, output, 0);
      plaintextLength += aes.doFinal(output, plaintextLength);
      byte[] result = new byte[plaintextLength];
      System.arraycopy(output, 0, result, 0, result.length);
      return result;
    } catch (InvalidCipherTextException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  public static byte[] getSharedSecret(byte[] myPrivateKey, byte[] theirPublicKey) {
    try {
      return curve25519.getSharedSecret(myPrivateKey, theirPublicKey);
    } catch (RuntimeException e) {
      logger.info("Error getting shared secret", e);
      throw e;
    }
  }

  public static String rsEncode(long id) {
    return ReedSolomon.encode(id);
  }

  public static long rsDecode(String rsString) {
    rsString = rsString.toUpperCase();
    try {
      long id = ReedSolomon.decode(rsString);
      if (!rsString.equals(ReedSolomon.encode(id))) {
        throw new RuntimeException("ERROR: Reed-Solomon decoding of " + rsString + " not reversible, decoded to " + id);
      }
      return id;
    } catch (ReedSolomon.DecodeException | NumberFormatException e) {
      logger.debug("Reed-Solomon decoding failed for " + rsString + ": " + e.toString());
      throw new RuntimeException(e.toString(), e);
    }
  }

}
