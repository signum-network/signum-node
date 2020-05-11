package brs;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;

public class BurstLauncher {
    public static void main(String[] args) {
        Logger logger = LoggerFactory.getLogger(BurstLauncher.class);
        boolean canRunGui = true;
        String confFolder = Burst.CONF_FOLDER;
        
        Options options = new Options();
        options.addOption(Option.builder("l")
        		.longOpt("headless")
        		.desc("Run in headless mode")
        		.build());
        options.addOption(Option.builder("c")
        		.longOpt("config")
        		.argName("conf folder")
        		.desc("The configuration folder to use")
        		.build());
        options.addOption(Option.builder("h")
        		.longOpt("help")
        		.build());
        try {
			CommandLine cmd = new DefaultParser().parse(options, args);
			if(cmd.hasOption("h")) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("java -jar burst.jar", "Burst Referece Software (BRS) version " + Burst.VERSION,
						options,
						"Check for updates at https://github.com/burst-apps-team/burstcoin", true);
				return;
			}
			if(cmd.hasOption("l")) {
	            logger.info("Running in headless mode as specified by argument");
	            canRunGui = false;
			}
			if(cmd.hasOption("c")) {
				confFolder = cmd.getOptionValue("c");
	            logger.info("Using config folder {}", confFolder);
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
                Class.forName("brs.BurstGUI")
                        .getDeclaredMethod("main", String[].class)
                        .invoke(null, (Object) new String[] {confFolder});
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                logger.warn("Your build does not seem to include the BurstGUI extension or it cannot be run. Running as headless...");
                Burst.main(new String[] {confFolder});
            }
        } else {
            Burst.main(new String[] {confFolder});
        }
    }    
}
