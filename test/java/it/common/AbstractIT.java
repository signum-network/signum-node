package it.common;

import brs.Burst;
import brs.common.TestInfrastructure;
import brs.peer.Peers;
import brs.peer.ProcessBlock;
import brs.props.Props;
import com.google.gson.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Properties;

import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Peers.class)
@PowerMockIgnore("javax.net.ssl.*")
public abstract class AbstractIT {

  private ProcessBlock processBlock;

  protected APISender apiSender = new APISender();

  @Before
  public void setUp() {
    mockStatic(Peers.class);
    Burst.init(testProperties());

    processBlock = new ProcessBlock(Burst.getBlockchain(), Burst.getBlockchainProcessor());
  }

  @After
  public void shutdown() {
    Burst.shutdown(true);
  }

  private Properties testProperties() {
    final Properties props = new Properties();

    props.setProperty(Props.DEV_TESTNET.getName(), "true");
    props.setProperty(Props.DEV_OFFLINE.getName(), "true");
    props.setProperty(Props.DEV_DB_URL.getName(), TestInfrastructure.IN_MEMORY_DB_URL);
    props.setProperty(Props.DB_MAX_ROLLBACK.getName(), "1440");
    props.setProperty(Props.DB_CONNECTIONS.getName(), "1");

    props.setProperty(Props.API_SERVER.getName(), "on");
    props.setProperty(Props.API_LISTEN.getName(), "127.0.0.1");
    props.setProperty(Props.DEV_API_PORT.getName(),   "" + TestInfrastructure.TEST_API_PORT);
    props.setProperty(Props.API_ALLOWED.getName(),   "*");
    props.setProperty(Props.API_UI_DIR.getName(), "html/ui");

    return props;
  }

  public void processBlock(JsonObject jsonFirstBlock) {
    processBlock.processRequest(jsonFirstBlock, null);
  }

  public void rollback(int height) {
    Burst.getBlockchainProcessor().popOffTo(0);
  }
}
