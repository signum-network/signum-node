package brs.peer;

import brs.*;
import brs.crypto.Crypto;
import brs.props.Props;
import brs.util.Convert;
import brs.util.CountingInputStream;
import brs.util.CountingOutputStream;
import brs.util.JSON;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

final class PeerImpl implements Peer {

  private static final Logger logger = LoggerFactory.getLogger(PeerImpl.class);

  private final String peerAddress;
  private final AtomicReference<String> announcedAddress = new AtomicReference<>();
  private final AtomicInteger port = new AtomicInteger();
  private final AtomicBoolean shareAddress = new AtomicBoolean(false);
  private final AtomicReference<String> platform = new AtomicReference<>();
  private final AtomicReference<String> application = new AtomicReference<>();
  private final AtomicReference<Version> version = new AtomicReference<>();
  private final AtomicBoolean isOldVersion = new AtomicBoolean(false);
  private final AtomicLong blacklistingTime = new AtomicLong();
  private final AtomicReference<State> state = new AtomicReference<>();
  private final AtomicLong downloadedVolume = new AtomicLong();
  private final AtomicLong uploadedVolume = new AtomicLong();
  private final AtomicInteger lastUpdated = new AtomicInteger();
  private byte[] lastDownloadedTransactionsDigest;
  private final Object lastDownloadedTransactionsLock = new Object();

  PeerImpl(String peerAddress, String announcedAddress) {
    this.peerAddress = peerAddress;
    this.announcedAddress.set(announcedAddress);
    try {
      this.port.set(new URL(Constants.HTTP + announcedAddress).getPort());
    } catch (MalformedURLException ignored) {
    }
    this.state.set(State.NON_CONNECTED);
    this.version.set(Version.EMPTY); //not null
    this.shareAddress.set(true);
  }

  @Override
  public String getPeerAddress() {
    return peerAddress;
  }

  @Override
  public State getState() {
    return state.get();
  }

  @Override
  public boolean isState(State cmpState) {
    return state.get() == cmpState;
  }

  @Override
  public void setState(State state) {
    if (this.state.get() == state) {
      return;
    }
    if (this.state.get() == State.NON_CONNECTED) {
      this.state.set(state);
      Peers.notifyListeners(this, Peers.Event.ADDED_ACTIVE_PEER);
    }
    else if (state != State.NON_CONNECTED) {
      this.state.set(state);
      Peers.notifyListeners(this, Peers.Event.CHANGED_ACTIVE_PEER);
    }
  }

  @Override
  public long getDownloadedVolume() {
    return downloadedVolume.get();
  }

  public boolean diffLastDownloadedTransactions( byte[] data ) {
    synchronized (lastDownloadedTransactionsLock) {
      byte[] newDigest = Crypto.sha256().digest(data);
      if (lastDownloadedTransactionsDigest != null && Arrays.equals(newDigest, lastDownloadedTransactionsDigest)) {
        return false;
      }
      lastDownloadedTransactionsDigest = newDigest;
      return true;
    }
  }

  @Override
  public void updateDownloadedVolume(long volume) {
    synchronized (this) {
      downloadedVolume.addAndGet(volume);
    }
    Peers.notifyListeners(this, Peers.Event.DOWNLOADED_VOLUME);
  }

  @Override
  public long getUploadedVolume() {
    return uploadedVolume.get();
  }

  @Override
  public void updateUploadedVolume(long volume) {
    synchronized (this) {
      uploadedVolume.addAndGet(volume);
    }
    Peers.notifyListeners(this, Peers.Event.UPLOADED_VOLUME);
  }

  @Override
  public Version getVersion() {
    return version.get();
  }

  @Override
  public boolean isHigherOrEqualVersionThan(Version ourVersion) {
    return Peer.isHigherOrEqualVersion(ourVersion, version.get());
  }

  public boolean isAtLeastMyVersion() {
    return isHigherOrEqualVersionThan(Burst.VERSION);
  }
  
  void setVersion(String version) {
    this.version.set(Version.EMPTY);
    isOldVersion.set(false);
    if (Burst.APPLICATION.equals(getApplication()) && version != null) {
      try {
        this.version.set(Version.parse(version));
        isOldVersion.set(Constants.MIN_VERSION.isGreaterThan(this.version.get()));
      } catch (IllegalArgumentException e) {
        isOldVersion.set(true);
      }
    }
  }

  @Override
  public String getApplication() {
    return application.get();
  }

  void setApplication(String application) {
    this.application.set(application);
  }

  @Override
  public String getPlatform() {
    return platform.get();
  }

  void setPlatform(String platform) {
    this.platform.set(platform);
  }

  @Override
  public String getSoftware() {
    return Convert.truncate(application.get(), "?", 10, false)
        + " (" + Convert.truncate(version.toString(), "?", 10, false) + ")"
        + " @ " + Convert.truncate(platform.get(), "?", 10, false);
  }

  @Override
  public boolean shareAddress() {
    return shareAddress.get();
  }

  void setShareAddress(boolean shareAddress) {
    this.shareAddress.set(shareAddress);
  }

  @Override
  public String getAnnouncedAddress() {
    return announcedAddress.get();
  }

  void setAnnouncedAddress(String announcedAddress) {
    String announcedPeerAddress = Peers.normalizeHostAndPort(announcedAddress);
    if (announcedPeerAddress != null) {
      this.announcedAddress.set(announcedPeerAddress);
      try {
        this.port.set(new URL(Constants.HTTP + announcedPeerAddress).getPort());
      } catch (MalformedURLException ignored) {
      }
    }
  }

  @Override
  public int getPort() {
    return port.get();
  }

  @Override
  public boolean isWellKnown() {
    return announcedAddress.get() != null && Peers.wellKnownPeers.contains(announcedAddress.get());
  }

  @Override
  public boolean isRebroadcastTarget() {
    return announcedAddress.get() != null && Peers.rebroadcastPeers.contains(announcedAddress.get());
  }

  @Override
  public boolean isBlacklisted() {
    return blacklistingTime.get() > 0 || isOldVersion.get() || Peers.knownBlacklistedPeers.contains(peerAddress);
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
      if (!alreadyBlacklisted) {
        logger.debug("... because of: {}", cause, cause);
      }
    }
  }

  @Override
  public void blacklist(String description) {
    if (! isBlacklisted() ) {
      if (logger.isInfoEnabled()) {
        logger.info("Blacklisting {} ({}) because of: {}", peerAddress, getVersion(), description);
      }
    }
    blacklist();
  }

  @Override
  public void blacklist() {
    blacklistingTime.set(System.currentTimeMillis());
    setState(State.NON_CONNECTED);
    Peers.notifyListeners(this, Peers.Event.BLACKLIST);
  }

  @Override
  public void unBlacklist() {
    setState(State.NON_CONNECTED);
    blacklistingTime.set(0);
    Peers.notifyListeners(this, Peers.Event.UNBLACKLIST);
  }

  @Override
  public void updateBlacklistedStatus(long curTime) {
    if (blacklistingTime.get() > 0 && blacklistingTime.get() + Peers.blacklistingPeriod <= curTime) {
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
    return lastUpdated.get();
  }
  void setLastUpdated(int lastUpdated) {
    this.lastUpdated.set(lastUpdated);
  }

  @Override
  public JsonObject send(final JsonElement request) {

    JsonObject response;

    String log = null;
    boolean showLog = false;
    HttpURLConnection connection = null;

    try {

      String address = announcedAddress.get() != null ? announcedAddress.get() : peerAddress;
      StringBuilder buf = new StringBuilder(Constants.HTTP);
      buf.append(address);
      if (port.get() <= 0) {
        buf.append(':');
        buf.append(Burst.getPropertyService().getBoolean(Props.DEV_TESTNET) ? Peers.TESTNET_PEER_PORT : Peers.DEFAULT_PEER_PORT);
      }
      buf.append("/burst");
      URL url = new URL(buf.toString());

      if (Peers.communicationLoggingMask != 0) {
        StringWriter stringWriter = new StringWriter();
        JSON.writeTo(request, stringWriter);
        log = "\"" + url.toString() + "\": " + stringWriter.toString();
      }

      connection = (HttpURLConnection)url.openConnection();
      connection.setRequestMethod("POST");
      connection.setDoOutput(true);
      connection.setConnectTimeout(Peers.connectTimeout);
      connection.setReadTimeout(Peers.readTimeout);
      connection.addRequestProperty("User-Agent", "BRS/" + Burst.VERSION.toString());
      connection.setRequestProperty("Accept-Encoding", "gzip");
      connection.setRequestProperty("Connection", "close");

      CountingOutputStream cos = new CountingOutputStream(connection.getOutputStream());
      try (Writer writer = new BufferedWriter(new OutputStreamWriter(cos, StandardCharsets.UTF_8))) {
        JSON.writeTo(request, writer);
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
          response = JSON.getAsJsonObject(JSON.parse(responseValue));
        }
        else {
          try (Reader reader = new BufferedReader(new InputStreamReader(responseStream, StandardCharsets.UTF_8))) {
            response = JSON.getAsJsonObject(JSON.parse(reader));
          }
        }
        updateDownloadedVolume(cis.getCount());
      }
      else {

        if ((Peers.communicationLoggingMask & Peers.LOGGING_MASK_NON200_RESPONSES) != 0) {
          log += " >>> Peer responded with HTTP " + connection.getResponseCode() + " code!";
          showLog = true;
        }
        if (state.get() == State.CONNECTED) {
          setState(State.DISCONNECTED);
        } else {
          setState(State.NON_CONNECTED);
        }
        response = null;
      }

    } catch (RuntimeException|IOException e) {
      if (!isConnectionException(e)) {
        logger.debug("Error sending JSON request", e);
      }
      if ((Peers.communicationLoggingMask & Peers.LOGGING_MASK_EXCEPTIONS) != 0) {
        log += " >>> " + e.toString();
        showLog = true;
      }
      if (state.get() == State.CONNECTED) {
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

  private boolean isConnectionException(Throwable e) {
    if (e instanceof UnknownHostException || e instanceof SocketTimeoutException || e instanceof SocketException) return true;
    if (e.getCause() == null) return false;
    return isConnectionException(e.getCause());
  }

  @Override
  public int compareTo(Peer o) {
    return 0;
  }

  @Override
  public void connect(int currentTime) {
    JsonObject response = send(Peers.myPeerInfoRequest);
    if (response != null) {
      application.set(JSON.getAsString(response.get("application")));
      setVersion(JSON.getAsString(response.get("version")));
      platform.set(JSON.getAsString(response.get("platform")));
      shareAddress.set(Boolean.TRUE.equals(JSON.getAsBoolean(response.get("shareAddress"))));
      String newAnnouncedAddress = Convert.emptyToNull(JSON.getAsString(response.get("announcedAddress")));
      if (newAnnouncedAddress != null && ! newAnnouncedAddress.equals(announcedAddress.get())) {
        // force verification of changed announced address
        setState(Peer.State.NON_CONNECTED);
        setAnnouncedAddress(newAnnouncedAddress);
        return;
      }
      if (announcedAddress.get() == null) {
        setAnnouncedAddress(peerAddress);
      }

      setState(State.CONNECTED);
      Peers.updateAddress(this);
      lastUpdated.set(currentTime);
    }
    else {
      setState(State.NON_CONNECTED);
    }
  }

}
