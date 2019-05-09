package brs.common;

import brs.crypto.Crypto;

import static brs.Constants.ONE_BURST;

public class TestConstants {

  public static final String TEST_ACCOUNT_ID = "BURST-D95D-67CQ-8VDN-5EVAR";

  public static final long TEST_ACCOUNT_NUMERIC_ID_PARSED = 4297397359864028267L;

  public static final String TEST_SECRET_PHRASE =  "ach wie gut dass niemand weiss dass ich Rumpelstilzchen heiss";

  public static final String TEST_PUBLIC_KEY = "6b223e427b2d44ef8fe2dcb64845d7d9790045167202f1849facef10398bd529";

  public static final byte[] TEST_PUBLIC_KEY_BYTES = Crypto.getPublicKey(TEST_SECRET_PHRASE);

  public static final String TEST_ACCOUNT_NUMERIC_ID = "4297397359864028267";

  public static final String DEADLINE = "400";

  public static final String FEE = "" + ONE_BURST;

  public static final long TEN_BURST = ONE_BURST * 10;
}
