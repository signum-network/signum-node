package brs.crypto;

import brs.common.TestConstants;
import brs.util.Convert;
import burst.kit.crypto.BurstCrypto;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnit4.class)
public class CryptoTest {
    
    private byte[] stringToBytes(String string) {
        return string.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void testCryptoSha256() {
        MessageDigest sha256 = Crypto.sha256();
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Convert.toHexString(sha256.digest(stringToBytes(""))));
        assertEquals("e806a291cfc3e61f83b98d344ee57e3e8933cccece4fb45e1481f1f560e70eb1", Convert.toHexString(sha256.digest(stringToBytes("Testing"))));
        assertEquals("6de732f18e99e18ac25c609d6942f06f6ed7ab3f261ca46668d3a0e19fbc9e80", Convert.toHexString(sha256.digest(stringToBytes("Burstcoin!"))));
        assertEquals("d059c5e6b6715f1e1dd83295e804d4f5fbc560cd10befde400434d19afdf4cfe", Convert.toHexString(sha256.digest(stringToBytes("Burst Apps Team"))));
    }

    @Test
    public void testCryptoShabal256() {
        MessageDigest shabal256 = Crypto.shabal256();
        assertEquals("aec750d11feee9f16271922fbaf5a9be142f62019ef8d720f858940070889014", Convert.toHexString(shabal256.digest(stringToBytes(""))));
        assertEquals("10e237979a7233aa6a9377ff6a4b2541f890f67107fe0c89008fdd2c48e4cfe5", Convert.toHexString(shabal256.digest(stringToBytes("Testing"))));
        assertEquals("9beec9e237da7542a045b89c709b5d423b22faa99d5f01abab67261e1a9de6b8", Convert.toHexString(shabal256.digest(stringToBytes("Burstcoin!"))));
        assertEquals("4d92fb90793baaefabf4691cdcf4f1332ccd51c4a74f509a4b9a338eddb39e09", Convert.toHexString(shabal256.digest(stringToBytes("Burst Apps Team"))));
    }

    @Test
    public void testCryptoRipemd160() {
        MessageDigest ripemd160 = Crypto.ripemd160();
        assertEquals("9c1185a5c5e9fc54612808977ee8f548b2258d31", Convert.toHexString(ripemd160.digest(stringToBytes(""))));
        assertEquals("01743c6e71742ed72d6c51537f1790a462b82c82", Convert.toHexString(ripemd160.digest(stringToBytes("Testing"))));
        assertEquals("9b7e20c53c6e77ed8d9768d8a5a813d02c0a0d6a", Convert.toHexString(ripemd160.digest(stringToBytes("Burstcoin!"))));
        assertEquals("b089c88c2f81e87326c22b2df66dca6857f690a0", Convert.toHexString(ripemd160.digest(stringToBytes("Burst Apps Team"))));
    }

    @Test
    public void testCryptoGetPublicKey() {
        assertEquals("18656ba6d7862cb11d995afe20bf8761edee05ec28aa29bfbcf31ebd19dede71", Convert.toHexString(Crypto.getPublicKey("")));
        assertEquals("d28b42565bf16008158d2750b686722343a9a4d58b22c45deae3bb89135c3d66", Convert.toHexString(Crypto.getPublicKey("Testing")));
        assertEquals("cea5e981451125a7b7675af9154ce56a973d62f817bd25fa250820b32349c32e", Convert.toHexString(Crypto.getPublicKey("Burstcoin!")));
        assertEquals("25fa4a7c542f042ad86102addfd45272bbec1b350e94a483c4dc93f9d123f408", Convert.toHexString(Crypto.getPublicKey("Burst Apps Team")));
    }

    @Test
    public void testCryptoGetPrivateKey() {
        assertEquals("e0b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Convert.toHexString(Crypto.getPrivateKey("")));
        assertEquals("e806a291cfc3e61f83b98d344ee57e3e8933cccece4fb45e1481f1f560e70e71", Convert.toHexString(Crypto.getPrivateKey("Testing")));
        assertEquals("68e732f18e99e18ac25c609d6942f06f6ed7ab3f261ca46668d3a0e19fbc9e40", Convert.toHexString(Crypto.getPrivateKey("Burstcoin!")));
        assertEquals("d059c5e6b6715f1e1dd83295e804d4f5fbc560cd10befde400434d19afdf4c7e", Convert.toHexString(Crypto.getPrivateKey("Burst Apps Team")));
    }
    
    @Test
    public void testCryptoSign() {
        assertEquals("f6c3cace87e022565c1d547c4a13d216d765cb4aec098ed36ef758a33b11b3008573b8d81155939dc5677f4b222a3d3943c6e2f139cba3f82f5137d61b9a79fd", Convert.toHexString(Crypto.sign(stringToBytes(""), TestConstants.TEST_SECRET_PHRASE)));
        assertEquals("aa140db8d96a058b6d9488aa4d3771b0da3ad8d2bcfba64d8b3b117c1a73910efec6917aa986027784c46acfe645b400edfd04a77ad50af77e10d169b470d64b", Convert.toHexString(Crypto.sign(stringToBytes("Testing"), TestConstants.TEST_SECRET_PHRASE)));
        assertEquals("e4b7bcd76ebccbf6bf3d4ad1c72c6e0b3e902f041a38e37f8aac09ea5a43310d78a90061eb9c12f6d32174ce19bc4105223df38035f2cfc0ebb069591e1e1a01", Convert.toHexString(Crypto.sign(stringToBytes("Burstcoin!"), TestConstants.TEST_SECRET_PHRASE)));
        assertEquals("46ab525630fd4f2266d78309a04153dd7d69c8d3c77765956eff1b86cc4e5a0d8d0d8df1cfe8300617551361b99f54b5db7afbd8ffa0a21ddcdac2cfdec57b71", Convert.toHexString(Crypto.sign(stringToBytes("Burst Apps Team"), TestConstants.TEST_SECRET_PHRASE)));
    }
    
    @Test
    public void testCryptoVerify() {
        byte[] publicKey = Crypto.getPublicKey(TestConstants.TEST_SECRET_PHRASE);
        assertTrue(Crypto.verify(Convert.parseHexString("f6c3cace87e022565c1d547c4a13d216d765cb4aec098ed36ef758a33b11b3008573b8d81155939dc5677f4b222a3d3943c6e2f139cba3f82f5137d61b9a79fd"), stringToBytes(""), publicKey, true));
        assertTrue(Crypto.verify(Convert.parseHexString("aa140db8d96a058b6d9488aa4d3771b0da3ad8d2bcfba64d8b3b117c1a73910efec6917aa986027784c46acfe645b400edfd04a77ad50af77e10d169b470d64b"), stringToBytes("Testing"), publicKey, true));
        assertTrue(Crypto.verify(Convert.parseHexString("e4b7bcd76ebccbf6bf3d4ad1c72c6e0b3e902f041a38e37f8aac09ea5a43310d78a90061eb9c12f6d32174ce19bc4105223df38035f2cfc0ebb069591e1e1a01"), stringToBytes("Burstcoin!"), publicKey, true));
        assertTrue(Crypto.verify(Convert.parseHexString("46ab525630fd4f2266d78309a04153dd7d69c8d3c77765956eff1b86cc4e5a0d8d0d8df1cfe8300617551361b99f54b5db7afbd8ffa0a21ddcdac2cfdec57b71"), stringToBytes("Burst Apps Team"), publicKey, true));
    }

    // TODO test AES encrypt / decrypt & getSharedSecret

    @Test
    public void testCryptoRsEncode() {
        BurstCrypto burstCrypto = BurstCrypto.getInstance();
        assertEquals("23YP-M8H9-FA5W-5CX9B", Crypto.rsEncode(burstCrypto.getBurstAddressFromPassphrase("").getBurstID().getSignedLongId()));
        assertEquals("BTKQ-5ST6-6HAL-HKVYW", Crypto.rsEncode(burstCrypto.getBurstAddressFromPassphrase("Testing").getBurstID().getSignedLongId()));
        assertEquals("4KFW-N4LS-7UVW-8AUZJ", Crypto.rsEncode(burstCrypto.getBurstAddressFromPassphrase("Burstcoin!").getBurstID().getSignedLongId()));
        assertEquals("T7XD-7M3X-MB9F-38DU8", Crypto.rsEncode(burstCrypto.getBurstAddressFromPassphrase("Burst Apps Team").getBurstID().getSignedLongId()));
    }

    @Test
    public void testCryptoRsDecode() {
        BurstCrypto burstCrypto = BurstCrypto.getInstance();
        assertEquals(burstCrypto.getBurstAddressFromPassphrase("").getBurstID().getSignedLongId(), Crypto.rsDecode("23YP-M8H9-FA5W-5CX9B"));
        assertEquals(burstCrypto.getBurstAddressFromPassphrase("Testing").getBurstID().getSignedLongId(), Crypto.rsDecode("BTKQ-5ST6-6HAL-HKVYW"));
        assertEquals(burstCrypto.getBurstAddressFromPassphrase("Burstcoin!").getBurstID().getSignedLongId(), Crypto.rsDecode("4KFW-N4LS-7UVW-8AUZJ"));
        assertEquals(burstCrypto.getBurstAddressFromPassphrase("Burst Apps Team").getBurstID().getSignedLongId(), Crypto.rsDecode("T7XD-7M3X-MB9F-38DU8"));
    }
}
