package brs;

import org.slf4j.LoggerFactory;

public class BurstLauncher {
    public static void main(String[] args) {
        try {
            Class.forName("javafx.application.Application");
            BurstGUI.main(args);
        } catch (ClassNotFoundException e) {
            LoggerFactory.getLogger(BurstLauncher.class).error("Could not start GUI as your JRE does not seem to have JavaFX installed. To install please install the \"openjfx\" package (eg. \"sudo apt install openjfx\")");
            Burst.main(args);
        }
    }
}
