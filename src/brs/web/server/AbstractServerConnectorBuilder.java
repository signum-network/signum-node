package brs.web.server;

import brs.props.Props;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
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

    // Handle optional Let's Encrypt Certificates...
    String letsencryptPath = context.getPropertyService().getString(Props.API_SSL_LETSENCRYPT_PATH);
    if (letsencryptPath != null && !letsencryptPath.isEmpty()) {
      try {
        loadLetsEncryptCertsAsPkcs12(letsencryptPath, context.getPropertyService().getString(Props.API_SSL_KEY_STORE_PATH), context.getPropertyService().getString(Props.API_SSL_KEY_STORE_PASSWORD));
      } catch (Exception e) {
        logger.error(e.getMessage());
      }

      // Reload the certificate every week, in case it was renewed
      ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
      Runnable reloadCert = () -> {
        try {
          loadLetsEncryptCertsAsPkcs12(letsencryptPath, context.getPropertyService().getString(Props.API_SSL_KEY_STORE_PATH), context.getPropertyService().getString(Props.API_SSL_KEY_STORE_PASSWORD));
          sslContextFactory.reload(consumer -> logger.info("SSL keystore from letsencrypt reloaded."));
        } catch (Exception e) {
          logger.error(e.getMessage());
        }
      };
      scheduler.scheduleWithFixedDelay(reloadCert, 7, 7, TimeUnit.DAYS);
    }

    String[] strongCiphers = {
      "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
      "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
      // Add more strong ciphers as needed
    };
    sslContextFactory.setIncludeCipherSuites(strongCiphers);
    sslContextFactory.setIncludeProtocols("TLSv1.2", "TLSv1.3");
    sslContextFactory.setExcludeProtocols("SSLv3");
    return new ServerConnector(server, new SslConnectionFactory(sslContextFactory, "http/1.1"),
      new HttpConnectionFactory(httpsConfig));
  }

  public void loadLetsEncryptCertsAsPkcs12(String letsencryptPath, String p12Filename, String password) throws Exception {

    logger.info("Converting Let's Encrypt Certificate to PKCS12...");
    Security.addProvider(new BouncyCastleProvider());

    try (InputStream keyIn = new FileInputStream(letsencryptPath + "/privkey.pem");
         InputStream certIn = new FileInputStream(letsencryptPath + "/fullchain.pem")) {

      // Load PEM files
      PEMParser keyParser = new PEMParser(new InputStreamReader(keyIn));
      PEMParser certParser = new PEMParser(new InputStreamReader(certIn));

      // make keys compatible with java.security.keystore
      PrivateKeyInfo privateKeyInfo = (PrivateKeyInfo) keyParser.readObject();
      KeyFactory keyFactory = KeyFactory.getInstance("RSA", "BC");
      PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyInfo.getEncoded()));

      // Convert all certificates in fullchain.pem
      JcaX509CertificateConverter certConverter = new JcaX509CertificateConverter().setProvider("BC");
      List<X509Certificate> certList = new ArrayList<>();
      Object certObj;
      while ((certObj = certParser.readObject()) != null) {
        if (certObj instanceof X509CertificateHolder) {
          certList.add(certConverter.getCertificate((X509CertificateHolder) certObj));
        }
      }
      X509Certificate[] certificates = certList.toArray(new X509Certificate[0]);

      // Add the private key and certificates to the keystore
      KeyStore keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(null, null);
      keyStore.setKeyEntry("lets-encrypt", privateKey, password.toCharArray(), certificates);

      // Finally, save as PKCS12 file...
      try (OutputStream out = new FileOutputStream(p12Filename)) {
        keyStore.store(out, password.toCharArray());
        logger.info("Let's Encrypt Certificate successfully converted to PKCS12 and saved under: {}", p12Filename);
      }
    }
  }
}
