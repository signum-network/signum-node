package brs.crypto.ec;

public interface Curve25519 {
    void clampPrivateKey(byte[] privateKey);
    byte[] getPublicKey(byte[] privateKey);
    byte[] getSharedSecret(byte[] privateKey, byte[] publicKey);

    byte[] sign(byte[] message, byte[] privateKey);
    boolean verify(byte[] message, byte[] signature, byte[] publicKey, boolean enforceCanonical);
}
