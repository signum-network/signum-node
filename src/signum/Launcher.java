package signum;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brs.ChainScanLauncher;
import brs.Signum;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;

public class Launcher {
    public static void main(String[] args) {
        Logger logger = LoggerFactory.getLogger(Launcher.class);
        boolean canRunGui = true;

        // decided on the raw arguments: if parsing failed, falling through would start the node
        // read-write on the database the operator only meant to scan
        if (ChainScanLauncher.isRequested(args)) {
            System.exit(Signum.verifyChain(args));
            return;
        }

        try {
            CommandLine cmd = new DefaultParser().parse(Signum.CLI_OPTIONS, args);
            if (cmd.hasOption("h")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("java -jar signum-node.jar", "Signum Node version " + Signum.VERSION,
                        Signum.CLI_OPTIONS,
                        "Check for updates at https://github.com/signum-network/signum-node", true);
                return;
            }
            if (cmd.hasOption("l")) {
                logger.info("Running in headless mode as specified by argument");
                canRunGui = false;
            }
        } catch (ParseException e) {
            logger.error("Error parsing arguments", e);
        }

        if (canRunGui && GraphicsEnvironment.isHeadless()) {
            logger.error("Cannot start GUI as running in headless environment");
            canRunGui = false;
        }

        if (canRunGui) {
            try {
                Class.forName("brs.gui.SignumGUI")
                        .getDeclaredMethod("main", String[].class)
                        .invoke(null, (Object) args);
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                    | InvocationTargetException e) {
                logger.warn(
                        "Your build does not seem to include the SignumGUI extension or it cannot be run. Running as headless...");
                Signum.main(args);
            }
        } else {
            Signum.main(args);
        }
    }
}
