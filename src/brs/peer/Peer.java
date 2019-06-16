package brs.peer;

import brs.Version;
import brs.grpc.proto.BrsApi;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public interface Peer extends Comparable<Peer> {

  enum State {
    NON_CONNECTED, CONNECTED, DISCONNECTED;

    public static State fromProtobuf(BrsApi.PeerState peer) {
      switch (peer) {
        case NON_CONNECTED:
          return NON_CONNECTED;
        case CONNECTED:
          return CONNECTED;
        case DISCONNECTED:
          return DISCONNECTED;
        default:
          return null;
      }
    }

    public BrsApi.PeerState toProtobuf() {
      switch (this) {
        case NON_CONNECTED:
          return BrsApi.PeerState.NON_CONNECTED;
        case CONNECTED:
          return BrsApi.PeerState.CONNECTED;
        case DISCONNECTED:
          return BrsApi.PeerState.NON_CONNECTED;
        default:
          return BrsApi.PeerState.UNRECOGNIZED;
      }
    }
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

  JsonObject send(JsonElement request);
}
