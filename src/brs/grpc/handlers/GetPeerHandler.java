package brs.grpc.handlers;

import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.BrsApi;
import brs.peer.Peer;
import brs.peer.Peers;

public class GetPeerHandler implements GrpcApiHandler<BrsApi.GetPeerRequest, BrsApi.Peer> {
    @Override
    public BrsApi.Peer handleRequest(BrsApi.GetPeerRequest getPeerRequest) throws Exception {
        Peer peer = Peers.getPeer(getPeerRequest.getPeerAddress());
        if (peer == null) throw new ApiException("Could not find peer");
        return BrsApi.Peer.newBuilder()
                .setState(peer.getState().toProtobuf())
                .setAnnouncedAddress(peer.getAnnouncedAddress())
                .setShareAddress(peer.shareAddress())
                .setDownloadedVolume(peer.getDownloadedVolume())
                .setUploadedVolume(peer.getUploadedVolume())
                .setApplication(peer.getApplication())
                .setVersion(peer.getVersion().toStringIfNotEmpty())
                .setPlatform(peer.getPlatform())
                .setBlacklisted(peer.isBlacklisted())
                .setLastUpdated(peer.getLastUpdated())
                .build();
    }
}
