package brs.peer;

import brs.Version;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public interface Peer extends Comparable<Peer> {

  enum State {
    NON_CONNECTED, CONNECTED, DISCONNECTED
  }

  String getPeerAddress();

  String getAnnouncedAddress();

  State getState();

  Version getVersion();

  String getApplication();

  String getPlatform();

  String getSoftware();

  boolean shareAddress();

  boolean isWellKnown();

  boolean isRebroadcastTarget();

  boolean isBlacklisted();

  boolean isAtLeastMyVersion();

  boolean isHigherOrEqualVersionThan(Version version);

  void blacklist(Exception cause, String description);

  void blacklist(String description);

  void blacklist();

  void unBlacklist();

  void remove();

  long getDownloadedVolume();

  long getUploadedVolume();

  int getLastUpdated();

  // Long getLastUnconfirmedTransactionTimestamp();

  // void setLastUnconfirmedTransactionTimestamp(Long lastUnconfirmedTransactionTimestamp);

  JsonObject send(JsonElement request);

}
