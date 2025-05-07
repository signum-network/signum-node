package signum.net;

import brs.props.Props;

public class MockNetwork extends TestnetNetwork {

  public MockNetwork() {

    setProperty(Props.NETWORK_NAME, "Signum-LOCAL-MOCK");
    setProperty(Props.ICON_LOCATION, "/images/signum_testnet_logo.png");
    setProperty(Props.GENESIS_TIMESTAMP, "1234");
    setProperty(Props.GENESIS_BLOCK_ID, "13202088261921974776");

    setProperty(Props.DEV_OFFLINE, "true");
    setProperty(Props.DEV_MOCK_MINING, "true");

    setProperty(Props.SODIUM_BLOCK_HEIGHT, "0");
    setProperty(Props.SIGNUM_HEIGHT, "0");
    setProperty(Props.POC_PLUS_HEIGHT, "0");
    setProperty(Props.SPEEDWAY_HEIGHT, "0");
    setProperty(Props.SMART_FEES_HEIGHT, "0");
    setProperty(Props.SMART_TOKEN_HEIGHT, "0");
    setProperty(Props.SMART_ATS_HEIGHT, "0");
    setProperty(Props.AT_FIX_BLOCK_5_BLOCK_HEIGHT, "0");
    setProperty(Props.DISTRIBUTION_FIX_BLOCK_HEIGHT, "0");
    setProperty(Props.PK_BLOCK_HEIGHT, "0");
    setProperty(Props.PK2_BLOCK_HEIGHT, "0");
    setProperty(Props.PK_BLOCK_HEIGHT, "0");
    setProperty(Props.PK2_BLOCK_HEIGHT, "0");
    setProperty(Props.SMART_ALIASES_HEIGHT, "0");
    setProperty(Props.DEV_NEXT_FORK_BLOCK_HEIGHT, "0");

    setProperty(Props.BRS_CHECKPOINT_HEIGHT, "-1");
    setProperty(Props.BRS_CHECKPOINT_HASH, "");
    setProperty(Props.DB_URL, "jdbc:sqlite:file:./db/signum.sqlite-mock.db");
  }
}
