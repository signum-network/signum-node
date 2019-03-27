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
        // TODO check peerState default value, is it unrecognized?
        // TODO Better enum mapper than to string -> from string as this adds coupling
        for (Peer peer : active ? Peers.getActivePeers() : peerState != null ? Peers.getPeers(Peer.State.valueOf(peerState.toString())) : Peers.getAllPeers()) {
            peers.addPeerAddresses(peer.getAnnouncedAddress());
        }
        return peers.build();
    }
}
