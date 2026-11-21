package brs.props;

import brs.Signum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PropertyServiceImplTest {

    private static final String KEY = "3f9a1c7e5b2d8f4a6c0e9b1d3f5a7c2e4b6d8f0a";

    private final Logger julLogger = Logger.getLogger(Signum.class.getName());
    private final List<String> logged = new ArrayList<>();
    private final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord logRecord) {
            logged.add(logRecord.getMessage() + " " + List.of(logRecord.getParameters() == null ? new Object[0] : logRecord.getParameters()));
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        previousLevel = julLogger.getLevel();
        julLogger.setLevel(Level.ALL);
        capture.setLevel(Level.ALL);
        julLogger.addHandler(capture);
    }

    @AfterEach
    void tearDown() {
        julLogger.removeHandler(capture);
        julLogger.setLevel(previousLevel);
    }

    @Test
    void getStringList_GivenAdminKeysAndDebugLogging_NeverLogsThem() {
        // operators share debug logs when asking for help; admin keys must not be in them
        CaselessProperties properties = new CaselessProperties();
        properties.setProperty(Props.API_ADMIN_KEY_LIST.getName(), KEY);
        PropertyServiceImpl propertyService = new PropertyServiceImpl(properties);

        List<String> keys = propertyService.getStringList(Props.API_ADMIN_KEY_LIST);

        assertEquals(List.of(KEY), keys);
        for (String line : logged) {
            assertFalse(line.contains(KEY), line);
        }
    }
}
