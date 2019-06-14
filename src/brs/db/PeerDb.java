package brs.db;

import java.util.Collection;
import java.util.List;

public interface PeerDb extends Table {
  List<String> loadPeers();

  void deletePeers(Collection<String> peers);

  void addPeers(Collection<String> peers);
}
