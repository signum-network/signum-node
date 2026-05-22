# Peers API — Signum Node

## Public HTTP API Endpoints

All endpoints follow the pattern: `GET /api?requestType=<type>`

---

### `getPeers`

Returns a list of peer addresses known to the node.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `active` | boolean | No | If `true`, returns only active peers (state != NON_CONNECTED) |
| `state` | string | No | Filter by state: `NON_CONNECTED`, `CONNECTED`, `DISCONNECTED` |

**Response:**
```json
{
  "peers": ["138.2.137.23", "71.87.114.2"],
  "requestProcessingTime": 1
}
```

---

### `getPeer`

Returns detailed info about a specific peer.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `peer` | string | Yes | Peer IP address |

**Response:**
```json
{
  "state": 1,
  "announcedAddress": "example.com:8123",
  "shareAddress": true,
  "downloadedVolume": 23845866,
  "uploadedVolume": 51546404,
  "application": "BRS",
  "version": "v3.4.1",
  "platform": "PC",
  "networkName": "signum",
  "blacklisted": false,
  "lastUpdated": 251548859,
  "requestProcessingTime": 1
}
```

**State values:** `0` = NON_CONNECTED, `1` = CONNECTED, `2` = DISCONNECTED

**Errors:** `MISSING_PEER`, `UNKNOWN_PEER`

---

### `getMyPeerInfo`

Returns info about the local node's peer status.

**Parameters:** none

**Response:**
```json
{
  "utsInStore": 42,
  "requestProcessingTime": 1
}
```

---

## P2P Protocol (Internal)

The node runs a separate Jetty server for peer-to-peer communication. Peers POST JSON to `/burst`.

| Port | Network |
|------|---------|
| **8123** | Mainnet |
| **7123** | Testnet |

**P2P request types:**

| Request | Description |
|---------|-------------|
| `getInfo` | Handshake — exchange node metadata |
| `getPeers` | Request peer list from peer |
| `addPeers` | Share peer addresses with peer |
| `getCumulativeDifficulty` | Get chain difficulty |
| `getNextBlockIds` | Block sync |
| `getNextBlocks` / `getBlocksFromHeight` | Block download |
| `getMilestoneBlockIds` | Sync checkpoint |
| `processBlock` | Push new block |
| `processTransactions` | Push transactions |
| `getUnconfirmedTransactions` | Fetch mempool |

---

## Key Configuration (`node.properties`)

| Property | Default | Description |
|----------|---------|-------------|
| `P2P.Port` | `8123` / `7123` | P2P server port |
| `P2P.myAddress` | `""` | Announced address |
| `P2P.myPlatform` | `"PC"` | Platform identifier |
| `P2P.shareMyAddress` | `true` | Advertise to peers |
| `P2P.BootstrapPeers` | (list) | Comma-separated bootstrap peers |
| `P2P.MaxConnections` | `20` | Max connected peers |
| `P2P.TimeoutConnect_ms` | `4000` | Connection timeout |
| `P2P.TimeoutRead_ms` | `8000` | Read timeout |
| `P2P.BlacklistingTime_ms` | `6000000` | Blacklist duration (~100 min) |
| `P2P.getMorePeers` | `true` | Auto-discover peers |
| `P2P.sendToLimit` | `10` | Max peers for block broadcast |
| `P2P.enableTxRebroadcast` | `true` | Transaction rebroadcasting |
| `P2P.rebroadcastTo` | `""` | Specific rebroadcast targets |

---

## Peer States

| Value | Name | Description |
|-------|------|-------------|
| `0` | `NON_CONNECTED` | Not connected, not attempted |
| `1` | `CONNECTED` | Successfully connected |
| `2` | `DISCONNECTED` | Was connected but now disconnected |

---

## Source Locations

| Component | Path |
|-----------|------|
| HTTP handlers | `src/brs/web/api/http/handler/GetPeer.java`, `GetPeers.java`, `GetMyPeerInfo.java` |
| Peer interface | `src/brs/peer/Peer.java` |
| Peer implementation | `src/brs/peer/PeerImpl.java` |
| Peer manager | `src/brs/peer/Peers.java` |
| P2P servlet | `src/brs/peer/PeerServlet.java` |
| JSON formatting | `src/brs/web/api/http/common/JSONData.java` (`peer()` method) |
| DB interface | `src/brs/db/PeerDb.java` |
| Config props | `src/brs/props/Props.java` (P2P.* section) |
