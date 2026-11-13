package it.common;

import static org.mockito.Mockito.mockStatic;

import brs.Signum;
import brs.common.TestInfrastructure;
import brs.peer.Peers;
import brs.peer.ProcessBlock;
import brs.props.CaselessProperties;
import brs.props.Props;
import com.google.gson.JsonObject;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.mockito.MockedStatic;

// TODO: Remove this and add javadoc and rename type
@SuppressWarnings({
    "checkstyle:MissingJavadocTypeCheck",
    "checkstyle:AbbreviationAsWordInNameCheck" })

public abstract class AbstractIT {

    private ProcessBlock processBlock;
    private MockedStatic<Peers> peersMock;

    protected APISender apiSender = new APISender();

    //TODO: Remove suppression and add javadoc
    @SuppressWarnings("checkstyle:MissingJavadocMethodCheck")
    @Before
    public void setUp() {
        peersMock = mockStatic(Peers.class);
        Signum.init(testProperties());

        processBlock = new ProcessBlock(Signum.getBlockchain(), Signum.getBlockchainProcessor());
    }

    @After
    public void shutdown() {
        Signum.shutdown(true);
        peersMock.close();
    }

    private CaselessProperties testProperties() {
        final CaselessProperties props = new CaselessProperties();

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
