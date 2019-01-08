package brs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;

public class BurstLauncher {
    public static void main(String[] args) {
        Logger logger = LoggerFactory.getLogger(BurstLauncher.class);
        boolean canRunGui = true;

        addToClasspath(logger, "./conf");

        try {
            Class.forName("javafx.application.Application");
        } catch (ClassNotFoundException e) {
            logger.error("Could not start GUI as your JRE does not seem to have JavaFX installed. To install please install the \"openjfx\" package (eg. \"sudo apt install openjfx\")");
            canRunGui = false;
        }

        if (GraphicsEnvironment.isHeadless()) {
            logger.error("Cannot start GUI as running in headless environment");
            canRunGui = false;
        }

        if (canRunGui) BurstGUI.main(args); else Burst.main(args);
    }

    public static void addToClasspath(Logger logger, String path) {
        try {
            File f = new File(path);
            URI u = f.toURI();
            URLClassLoader urlClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            Class<URLClassLoader> urlClass = URLClassLoader.class;
            Method method = urlClass.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(urlClassLoader, u.toURL());
        } catch (Exception e) {
            logger.error("Could not add path \"" + path + "\" to classpath", e);
        }
    }
}
