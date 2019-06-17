package brs.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Handle logging for the Burst node server
 */

public final class LoggerConfigurator {
  private static final Logger logger = Logger.getLogger(LoggerConfigurator.class.getSimpleName());

  /**
   * No constructor
   */
  private LoggerConfigurator() {
  }

  /**
   * LoggerConfigurator initialization
   *
   * The existing Java logging configuration will be used if the Java logger has already
   * been initialized.  Otherwise, we will configure our own log manager and log handlers.
   * The conf/logging-default.properties and conf/logging.properties configuration
   * files will be used.  Entries in logging.properties will override entries in
   * logging-default.properties.
   */
  public static void init() {
    final String managerPackage = "java.util.logging.manager";
    String oldManager = System.getProperty(managerPackage);
    System.setProperty(managerPackage, "brs.util.BurstLogManager");
    if (!(LogManager.getLogManager() instanceof BurstLogManager)) {
      System.setProperty(managerPackage,
                         (oldManager != null ? oldManager : "java.util.logging.LogManager"));
    }
    if (!Boolean.getBoolean("brs.doNotConfigureLogging")) {
      try {
        boolean foundProperties = false;
        Properties loggingProperties = new Properties();
        try (InputStream is = ClassLoader.getSystemResourceAsStream("logging-default.properties")) {
          if (is != null) {
            loggingProperties.load(is);
            foundProperties = true;
          }
        }
        try (InputStream is = ClassLoader.getSystemResourceAsStream("logging.properties")) {
          if (is != null) {
            loggingProperties.load(is);
            foundProperties = true;
          }
        }
        if (foundProperties) {
          ByteArrayOutputStream outStream = new ByteArrayOutputStream();
          loggingProperties.store(outStream, "logging properties");
          ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
          java.util.logging.LogManager.getLogManager().readConfiguration(inStream);
          inStream.close();
          outStream.close();
        }
        BriefLogFormatter.init();
      }
      catch (IOException e) {
        throw new RuntimeException("Error loading logging properties", e);
      }
    }

    logger.info("logging enabled");
  }

  /**
   * LoggerConfigurator shutdown
   */
  public static void shutdown() {
    if (LogManager.getLogManager() instanceof BurstLogManager) {
      ((BurstLogManager) LogManager.getLogManager()).burstShutdown();
    }
  }
}
