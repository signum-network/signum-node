package brs.peer;

import brs.*;
import brs.crypto.Crypto;
import brs.props.Props;
import brs.util.Convert;
import brs.util.CountingInputStream;
import brs.util.CountingOutputStream;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import org.json.simple.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

final class PeerImpl implements Peer {

  private static final Logger logger = LoggerFactory.getLogger(PeerImpl.class);

  private final String peerAddress;
  private volatile String announcedAddress;
  private volatile int port;
  private volatile boolean shareAddress;
  private volatile String platform;
  private volatile String application;
  private volatile Version version;
  private volatile boolean isOldVersion;
  private volatile long blacklistingTime;
  private volatile State state;
  private volatile long downloadedVolume;
  private volatile long uploadedVolume;
  private volatile int lastUpdated;
  private volatile Long lastUnconfirmedTransactionTimestamp = null;
  private volatile byte[] lastDownloadedTransactionsDigest;

  PeerImpl(String peerAddress, String announcedAddress) {
    this.peerAddress = peerAddress;
    this.announcedAddress = announcedAddress;
    try {
      this.port = new URL("http://" + announcedAddress).getPort();
    } catch (MalformedURLException ignore) {}
    this.state = State.NON_CONNECTED;
    this.version = Version.EMPTY; //not null
    this.shareAddress = true;
  }

  @Override
  public String getPeerAddress() {
    return peerAddress;
  }

  @Override
  public State getState() {
    return state;
  }

  public boolean isState(State cmp_state) {
    return state == cmp_state;
  }

  void setState(State state) {
    if (this.state == state) {
      return;
    }
    if (this.state == State.NON_CONNECTED) {
      this.state = state;
      Peers.notifyListeners(this, Peers.Event.ADDED_ACTIVE_PEER);
    }
    else if (state != State.NON_CONNECTED) {
      this.state = state;
      Peers.notifyListeners(this, Peers.Event.CHANGED_ACTIVE_PEER);
    }
  }

  @Override
  public long getDownloadedVolume() {
    return downloadedVolume;
  }

  public boolean diffLastDownloadedTransactions( byte[] data ) {
    byte[] newDigest = Crypto.sha256().digest(data);
    if ( lastDownloadedTransactionsDigest != null && Arrays.equals(newDigest, lastDownloadedTransactionsDigest) ) {
      return false;
    }
    lastDownloadedTransactionsDigest = newDigest;
    return true;
  }

  void updateDownloadedVolume(long volume) {
    synchronized (this) {
      downloadedVolume += volume;
    }
    Peers.notifyListeners(this, Peers.Event.DOWNLOADED_VOLUME);
  }

  @Override
  public long getUploadedVolume() {
    return uploadedVolume;
  }

  void updateUploadedVolume(long volume) {
    synchronized (this) {
      uploadedVolume += volume;
    }
    Peers.notifyListeners(this, Peers.Event.UPLOADED_VOLUME);
  }

  @Override
  public Version getVersion() {
    return version;
  }

  // semantic versioning for peer versions. here: ">=" negate it for "<"
  public boolean isHigherOrEqualVersionThan(Version ourVersion) {
    return isHigherOrEqualVersion(ourVersion, version);
  }

  public static boolean isHigherOrEqualVersion(Version ourVersion, Version possiblyLowerVersion) {
    if (ourVersion == null || possiblyLowerVersion == null) {
      return false;
    }

    return possiblyLowerVersion.isGreaterThanOrEqualTo(ourVersion);
  }

  public boolean isAtLeastMyVersion() {
    return isHigherOrEqualVersionThan(Burst.VERSION);
  }
  
  void setVersion(String version) {
    this.version = Version.EMPTY;
    isOldVersion = false;
    if (Burst.APPLICATION.equals(application) && version != null) {
      try {
        this.version = Version.parse(version);
        isOldVersion = Constants.MIN_VERSION.isGreaterThan(this.version);
      } catch (IllegalArgumentException e) {
        isOldVersion = true;
      }
    }
  }

  @Override
  public String getApplication() {
    return application;
  }

  void setApplication(String application) {
    this.application = application;
  }

  @Override
  public String getPlatform() {
    return platform;
  }

  void setPlatform(String platform) {
    this.platform = platform;
  }

  @Override
  public String getSoftware() {
    return Convert.truncate(application, "?", 10, false)
        + " (" + Convert.truncate(version.toString(), "?", 10, false) + ")"
        + " @ " + Convert.truncate(platform, "?", 10, false);
  }

  @Override
  public boolean shareAddress() {
    return shareAddress;
  }

  void setShareAddress(boolean shareAddress) {
    this.shareAddress = shareAddress;
  }

  @Override
  public String getAnnouncedAddress() {
    return announcedAddress;
  }

  void setAnnouncedAddress(String announcedAddress) {
    String announcedPeerAddress = Peers.normalizeHostAndPort(announcedAddress);
    if (announcedPeerAddress != null) {
      this.announcedAddress = announcedPeerAddress;
      try {
        this.port = new URL("http://" + announcedPeerAddress).getPort();
      } catch (MalformedURLException ignore) {}
    }
  }

  int getPort() {
    return port;
  }

  @Override
  public boolean isWellKnown() {
    return announcedAddress != null && Peers.wellKnownPeers.contains(announcedAddress);
  }

  @Override
  public boolean isRebroadcastTarget() {
    return announcedAddress != null && Peers.rebroadcastPeers.contains(announcedAddress);
  }

  @Override
  public boolean isBlacklisted() {
    // logger.debug("isBlacklisted - BL time: " + blacklistingTime + " Oldvers: " + isOldVersion + " PeerAddr: " + peerAddress);
    return blacklistingTime > 0 || isOldVersion || Peers.knownBlacklistedPeers.contains(peerAddress);
  }

  @Override
  public void blacklist(Exception cause, String description) {
    if (cause instanceof BurstException.NotCurrentlyValidException || cause instanceof BlockchainProcessor.BlockOutOfOrderException
        || cause instanceof SQLException || cause.getCause() instanceof SQLException) {
      // don't blacklist peers just because a feature is not yet enabled, or because of database timeouts
      // prevents erroneous blacklisting during loading of blockchain from scratch
      return;
    }
    if ( (cause instanceof IOException) ) {
      // don't trigger verbose logging, if we had an IO Exception (eg. network stuff)
      blacklist();
    }
    else {
      boolean alreadyBlacklisted = isBlacklisted();
      logger.error("Reason for following blacklist: " + cause.getMessage(), cause);
      blacklist(description); // refresh blacklist expiry
      if ( ! alreadyBlacklisted ) {
        logger.debug("... because of: " + cause.toString(), cause);
      }
    }
  }

  @Override
  public void blacklist(String description) {
    if (! isBlacklisted() ) {
      logger.info("Blacklisting " + peerAddress + " (" + getVersion() + ") because of: " + description);
    }
    blacklist();
  }

  @Override
  public void blacklist() {
    blacklistingTime = System.currentTimeMillis();
    setState(State.NON_CONNECTED);
    Peers.notifyListeners(this, Peers.Event.BLACKLIST);
  }

  @Override
  public void unBlacklist() {
    setState(State.NON_CONNECTED);
    blacklistingTime = 0;
    Peers.notifyListeners(this, Peers.Event.UNBLACKLIST);
  }

  void updateBlacklistedStatus(long curTime) {
    if (blacklistingTime > 0 && blacklistingTime + Peers.blacklistingPeriod <= curTime) {
      unBlacklist();
    }
  }

  @Override
  public void remove() {
    Peers.removePeer(this);
    Peers.notifyListeners(this, Peers.Event.REMOVE);
  }

  @Override
  public int getLastUpdated() {
    return lastUpdated;
  }
/*
  @Override
  public Long getLastUnconfirmedTransactionTimestamp() {
    return this.lastUnconfirmedTransactionTimestamp;
  }

  @Override
  public void setLastUnconfirmedTransactionTimestamp(Long lastUnconfirmedTransactionTimestamp) {
    this.lastUnconfirmedTransactionTimestamp = lastUnconfirmedTransactionTimestamp;
  }
*/

  void setLastUpdated(int lastUpdated) {
    this.lastUpdated = lastUpdated;
  }

  @Override
  public JSONObject send(final JSONStreamAware request) {

    JSONObject response;

    String log = null;
    boolean showLog = false;
    HttpURLConnection connection = null;

    try {

      String address = announcedAddress != null ? announcedAddress : peerAddress;
      StringBuilder buf = new StringBuilder("http://");
      buf.append(address);
      if (port <= 0) {
        buf.append(':');
        buf.append(Burst.getPropertyService().getBoolean(Props.DEV_TESTNET) ? Peers.TESTNET_PEER_PORT : Peers.DEFAULT_PEER_PORT);
      }
      buf.append("/burst");
      URL url = new URL(buf.toString());

      if (Peers.communicationLoggingMask != 0) {
        StringWriter stringWriter = new StringWriter();
        request.writeJSONString(stringWriter);
        log = "\"" + url.toString() + "\": " + stringWriter.toString();
      }

      connection = (HttpURLConnection)url.openConnection();
      connection.setRequestMethod("POST");
      connection.setDoOutput(true);
      connection.setConnectTimeout(Peers.connectTimeout);
      connection.setReadTimeout(Peers.readTimeout);
      connection.addRequestProperty("User-Agent", "BRS/" + Burst.VERSION.toBackwardsCompatibleStringIfNeeded(this.version));
      connection.setRequestProperty("Accept-Encoding", "gzip");
      connection.setRequestProperty("Connection", "close");

      CountingOutputStream cos = new CountingOutputStream(connection.getOutputStream());
      try (Writer writer = new BufferedWriter(new OutputStreamWriter(cos, StandardCharsets.UTF_8))) {
        request.writeJSONString(writer);
      } // rico666: no catch?
      updateUploadedVolume(cos.getCount());

      if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
        CountingInputStream cis = new CountingInputStream(connection.getInputStream());
        InputStream responseStream = cis;
        if ("gzip".equals(connection.getHeaderField("Content-Encoding"))) {
          responseStream = new GZIPInputStream(cis);
        }
        if ((Peers.communicationLoggingMask & Peers.LOGGING_MASK_200_RESPONSES) != 0) {
          ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
          byte[] buffer = new byte[1024];
          int numberOfBytes;
          try (InputStream inputStream = responseStream) {
            while ((numberOfBytes = inputStream.read(buffer, 0, buffer.length)) > 0) {
              byteArrayOutputStream.write(buffer, 0, numberOfBytes);
            }
          }
          String responseValue = byteArrayOutputStream.toString("UTF-8");
          if (! responseValue.isEmpty() && responseStream instanceof GZIPInputStream) {
            log += String.format("[length: %d, compression ratio: %.2f]", cis.getCount(), (double)cis.getCount() / (double)responseValue.length());
          }
          log += " >>> " + responseValue;
          showLog = true;
          response = (JSONObject) JSONValue.parse(responseValue);
        }
        else {
          try (Reader reader = new BufferedReader(new InputStreamReader(responseStream, StandardCharsets.UTF_8))) {
            response = (JSONObject)JSONValue.parse(reader);
          }
        }
        updateDownloadedVolume(cis.getCount());
      }
      else {

        if ((Peers.communicationLoggingMask & Peers.LOGGING_MASK_NON200_RESPONSES) != 0) {
          log += " >>> Peer responded with HTTP " + connection.getResponseCode() + " code!";
          showLog = true;
        }
        if (state == State.CONNECTED) {
          setState(State.DISCONNECTED);
        } else {
          setState(State.NON_CONNECTED);
        }
        response = null;
      }

    } catch (RuntimeException|IOException e) {
      if (! (e instanceof UnknownHostException || e instanceof SocketTimeoutException || e instanceof SocketException)) {
        logger.debug("Error sending JSON request", e);
      }
      if ((Peers.communicationLoggingMask & Peers.LOGGING_MASK_EXCEPTIONS) != 0) {
        log += " >>> " + e.toString();
        showLog = true;
      }
      if (state == State.CONNECTED) {
        setState(State.DISCONNECTED);
      }
      response = null;
    }

    if (showLog) {
      logger.info(log);
    }

    if (connection != null) {
      connection.disconnect();
    }

    return response;

  }

  @Override
  public int compareTo(Peer o) {
    return 0;
  }

  void connect(int currentTime) {
    JSONObject response = send(version.backwardsCompatibilityNeeded() ? Peers.myPeerInfoRequestBackwardsCompatible : Peers.myPeerInfoRequest);
    if (response != null) {
      application = (String)response.get("application");
      setVersion((String) response.get("version"));
      platform = (String)response.get("platform");
      shareAddress = Boolean.TRUE.equals(response.get("shareAddress"));
      String newAnnouncedAddress = Convert.emptyToNull((String)response.get("announcedAddress"));
      if (newAnnouncedAddress != null && ! newAnnouncedAddress.equals(announcedAddress)) {
        // force verification of changed announced address
        setState(Peer.State.NON_CONNECTED);
        setAnnouncedAddress(newAnnouncedAddress);
        return;
      }
      if (announcedAddress == null) {
        setAnnouncedAddress(peerAddress);
        //logger.debug("Connected to peer without announced address, setting to " + peerAddress);
      }

      setState(State.CONNECTED);
      Peers.updateAddress(this);
      lastUpdated = currentTime;
    }
    else {
      setState(State.NON_CONNECTED);
    }
  }

}
