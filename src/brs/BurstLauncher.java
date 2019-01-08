package brs;

import org.slf4j.LoggerFactory;

public class BurstLauncher {
    public static void main(String[] args) {
        addToClasspath("./conf");
        try {
            Class.forName("javafx.application.Application");
            BurstGUI.main(args);
        } catch (ClassNotFoundException e) {
            LoggerFactory.getLogger(BurstLauncher.class).error("Could not start GUI as your JRE does not seem to have JavaFX installed. To install please install the \"openjfx\" package (eg. \"sudo apt install openjfx\")");
            Burst.main(args);
        }
    }

    public static void addToClasspath(String path) {
        try {
            File f = new File(path);
            URI u = f.toURI();
            URLClassLoader urlClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            Class<URLClassLoader> urlClass = URLClassLoader.class;
            Method method = urlClass.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(urlClassLoader, u.toURL());
        } catch (Exception e) {
            LOGGER.error("Could not add path \"" + path + "\" to classpath", e);
        }
    }
}
