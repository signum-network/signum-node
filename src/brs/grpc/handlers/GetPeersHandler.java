package brs.grpc.handlers;

import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.peer.Peer;
import brs.peer.Peers;

public class GetPeersHandler implements GrpcApiHandler<BrsApi.GetPeersRequest, BrsApi.Peers> {
    @Override
    public BrsApi.Peers handleRequest(BrsApi.GetPeersRequest getPeersRequest) throws Exception {
        boolean active = getPeersRequest.getActive();
        BrsApi.PeerState peerState = getPeersRequest.getState();
        BrsApi.Peers.Builder peers = BrsApi.Peers.newBuilder();
        for (Peer peer : active ? Peers.getActivePeers() : peerState == BrsApi.PeerState.PeerState_UNSET ? Peers.getAllPeers() : Peers.getPeers(Peer.State.fromProtobuf(peerState))) {
            peers.addPeerAddresses(peer.getAnnouncedAddress());
        }
        return peers.build();
    }
}
