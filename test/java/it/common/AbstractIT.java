package it.common;

import static org.powermock.api.mockito.PowerMockito.mockStatic;

import brs.Signum;
import brs.common.TestInfrastructure;
import brs.peer.Peers;
import brs.peer.ProcessBlock;
import brs.props.CaseInsensitiveProperties;
import brs.props.Props;
import com.google.gson.JsonObject;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

// TODO: Remove this and add javadoc and rename type
@SuppressWarnings({
    "checkstyle:MissingJavadocTypeCheck",
    "checkstyle:AbbreviationAsWordInNameCheck" })

@RunWith(PowerMockRunner.class)
@PrepareForTest(Peers.class)
@PowerMockIgnore("javax.net.ssl.*")
public abstract class AbstractIT {

    private ProcessBlock processBlock;

    protected APISender apiSender = new APISender();

    //TODO: Remove suppression and add javadoc
    @SuppressWarnings("checkstyle:MissingJavadocMethodCheck")
    @Before
    public void setUp() {
        mockStatic(Peers.class);
        Signum.init(testProperties());

        processBlock = new ProcessBlock(Signum.getBlockchain(), Signum.getBlockchainProcessor());
    }

    @After
    public void shutdown() {
        Signum.shutdown(true);
    }

    private CaseInsensitiveProperties testProperties() {
        final CaseInsensitiveProperties props = new CaseInsensitiveProperties();

        props.setProperty(Props.DEV_OFFLINE.getName(), "true");
        props.setProperty(Props.NETWORK_NAME.getName(), "Unit tests");
        props.setProperty(Props.DB_URL.getName(), TestInfrastructure.IN_MEMORY_DB_URL);
        props.setProperty(Props.DB_CONNECTIONS.getName(), "1");

        props.setProperty(Props.API_SERVER.getName(), "on");
        props.setProperty(Props.API_LISTEN.getName(), "127.0.0.1");
        props.setProperty(Props.API_PORT.getName(), "" + TestInfrastructure.TEST_API_PORT);
        props.setProperty(Props.API_ALLOWED.getName(), "*");
        props.setProperty(Props.API_UI_DIR.getName(), "html/ui");

        return props;
    }

    public void processBlock(JsonObject jsonFirstBlock) {
        processBlock.processRequest(jsonFirstBlock, null);
    }

    public void rollback(int height) {
        Signum.getBlockchainProcessor().popOffTo(0);
    }
}
