package brs.web.server;

import brs.props.Props;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class AbstractServerConnectorBuilder {

  private static final Logger logger = LoggerFactory.getLogger(AbstractServerConnectorBuilder.class);
  protected final WebServerContext context;

  public AbstractServerConnectorBuilder(WebServerContext context) {
    this.context = context;
  }


  abstract ServerConnector build(Server server);


  protected ServerConnector createSSLConnector(Server server) {
    logger.info("Creating SSL Connector");
    HttpConfiguration httpsConfig = new HttpConfiguration();
    httpsConfig.setSecureScheme("https");
    httpsConfig.setSecurePort(context.getPropertyService().getInt(Props.API_PORT));
    httpsConfig.addCustomizer(new SecureRequestCustomizer());
    SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();

    sslContextFactory.setKeyStorePath(context.getPropertyService().getString(Props.API_SSL_KEY_STORE_PATH));
    sslContextFactory.setKeyStorePassword(context.getPropertyService().getString(Props.API_SSL_KEY_STORE_PASSWORD));

    String letsencryptPath = context.getPropertyService().getString(Props.API_SSL_LETSENCRYPT_PATH);
    if (letsencryptPath != null && !letsencryptPath.isEmpty()) {
      try {
        letsencryptToPkcs12(letsencryptPath, context.getPropertyService().getString(Props.API_SSL_KEY_STORE_PATH), context.getPropertyService().getString(Props.API_SSL_KEY_STORE_PASSWORD));
      } catch (Exception e) {
        logger.error(e.getMessage());
      }

      // Reload the certificate every week, in case it was renewed
      ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
      Runnable reloadCert = () -> {
        try {
          letsencryptToPkcs12(letsencryptPath, context.getPropertyService().getString(Props.API_SSL_KEY_STORE_PATH), context.getPropertyService().getString(Props.API_SSL_KEY_STORE_PASSWORD));
          sslContextFactory.reload(consumer -> logger.info("SSL keystore from letsencrypt reloaded."));
        } catch (Exception e) {
          logger.error(e.getMessage());
        }
      };
      scheduler.scheduleWithFixedDelay(reloadCert, 7, 7, TimeUnit.DAYS);
    }

    sslContextFactory.setExcludeCipherSuites("SSL_RSA_WITH_DES_CBC_SHA",
      "SSL_DHE_RSA_WITH_DES_CBC_SHA",
      "SSL_DHE_DSS_WITH_DES_CBC_SHA",
      "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
      "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
      "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
      "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");
    sslContextFactory.setExcludeProtocols("SSLv3");
    return new ServerConnector(server, new SslConnectionFactory(sslContextFactory, "http/1.1"),
      new HttpConnectionFactory(httpsConfig));
  }


  private void letsencryptToPkcs12(String letsencryptPath, String p12File, String password) throws Exception {
    // TODO: check if there is a way for us to use directly the PEM files and not need to convert this way
    logger.info("Generating {} from {}", p12File, letsencryptPath);
    String cmd = "openssl pkcs12 -export -in " + letsencryptPath + "/fullchain.pem "
      + "-inkey " + letsencryptPath + "/privkey.pem -out " + p12File + " -password pass:" + password;

    Process process = Runtime.getRuntime().exec(cmd);
    process.waitFor();
  }
}
