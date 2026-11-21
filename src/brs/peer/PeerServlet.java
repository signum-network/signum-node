package brs.peer;

import static brs.Constants.PROTOCOL;

import brs.Blockchain;
import brs.BlockchainProcessor;
import brs.TransactionProcessor;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.AccountService;
import brs.services.TimeService;
import brs.util.CountingInputStream;
import brs.util.CountingOutputStream;
import brs.util.JSON;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO: Create JavaDocs and remove this
@SuppressWarnings({ "checkstyle:MissingJavadocTypeCheck", "checkstyle:MissingJavadocMethodCheck" })
public final class PeerServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(PeerServlet.class);

    interface PeerRequestHandler {
        JsonElement processRequest(JsonObject request, Peer peer);
    }

    abstract static class ExtendedPeerRequestHandler implements PeerRequestHandler {
        @Override
        public JsonElement processRequest(JsonObject request, Peer peer) {
            return null;
        }

        abstract ExtendedProcessRequest extendedProcessRequest(JsonObject request, Peer peer);
    }

    static class ExtendedProcessRequest {
        final JsonElement response;
        final RequestLifecycleHook afterRequestHook;

        public ExtendedProcessRequest(JsonElement response, RequestLifecycleHook afterRequestHook) {
            this.response = response;
            this.afterRequestHook = afterRequestHook;
        }
    }

    interface RequestLifecycleHook {
        void run();
    }

    private final Map<String, PeerRequestHandler> peerRequestHandlers;

    public PeerServlet(TimeService timeService, AccountService accountService,
            Blockchain blockchain,
            TransactionProcessor transactionProcessor,
            BlockchainProcessor blockchainProcessor,
            PropertyService propertyService) {
        final Map<String, PeerRequestHandler> map = new HashMap<>();
        map.put("addPeers", AddPeers.instance);
        map.put("getCumulativeDifficulty", new GetCumulativeDifficulty(blockchain));
        map.put("getInfo", new GetInfo(timeService, propertyService.getBoolean(Props.P2P_SHARE_CLOCK)));
        map.put("getMilestoneBlockIds", new GetMilestoneBlockIds(blockchain));
        map.put("getNextBlockIds", new GetNextBlockIds(blockchain));
        map.put("getBlocksFromHeight", new GetBlocksFromHeight(blockchain));
        map.put("getNextBlocks", new GetNextBlocks(blockchain, propertyService));
        map.put("getPeers", GetPeers.instance);
        map.put("getUnconfirmedTransactions", new GetUnconfirmedTransactions(transactionProcessor));
        map.put("processBlock", new ProcessBlock(blockchain, blockchainProcessor));
        map.put("processTransactions", new ProcessTransactions(transactionProcessor));
        peerRequestHandlers = Collections.unmodifiableMap(map);
    }

    private static final JsonElement UNSUPPORTED_REQUEST_TYPE;

    static {
        final JsonObject response = new JsonObject();
        response.addProperty("error", "Unsupported request type!");
        UNSUPPORTED_REQUEST_TYPE = response;
    }

    private static final JsonElement UNSUPPORTED_PROTOCOL;

    static {
        final JsonObject response = new JsonObject();
        response.addProperty("error", "Unsupported protocol!");
        UNSUPPORTED_PROTOCOL = response;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        try {
            if (!Peers.isSupportedUserAgent(req.getHeader("User-Agent"))) {
                return;
            }
            process(req, resp);
        } catch (Exception e) { // We don't want to send exception information to client...
            resp.setStatus(500);
            logger.warn("Error handling peer request", e);
        }
    }

    private void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Peer peer = null;
        JsonElement response;

        ExtendedProcessRequest extendedProcessRequest = null;

        String requestType = "unknown";
        try {
            peer = Peers.addPeer(req.getRemoteAddr(), null);
            if (peer == null || peer.isBlacklisted()) {
                return;
            }
            Peers.getInboundContacts().record(req.getRemoteAddr(), System.currentTimeMillis());

            JsonObject request;
            CountingInputStream cis = new CountingInputStream(req.getInputStream());
            try (Reader reader = new InputStreamReader(cis, StandardCharsets.UTF_8)) {
                request = JSON.getAsJsonObject(JSON.parse(reader));
            }
            if (request == null) {
                return;
            }

            if (peer.isState(Peer.State.DISCONNECTED)) {
                peer.setState(Peer.State.CONNECTED);
                if (peer.getAnnouncedAddress() != null) {
                    Peers.updateAddress(peer);
                }
            }
            peer.updateDownloadedVolume(cis.getCount());

            if (request.get(PROTOCOL) != null
                    && JSON.getAsString(request.get(PROTOCOL)).equals("B1")) {
                requestType = "" + JSON.getAsString(request.get("requestType"));
                PeerRequestHandler peerRequestHandler = peerRequestHandlers
                        .get(JSON.getAsString(request.get("requestType")));
                if (peerRequestHandler != null) {
                    if (peerRequestHandler instanceof ExtendedPeerRequestHandler) {
                        extendedProcessRequest = ((ExtendedPeerRequestHandler) peerRequestHandler)
                                .extendedProcessRequest(request, peer);
                        response = extendedProcessRequest.response;
                    } else {
                        response = peerRequestHandler.processRequest(request, peer);
                    }
                } else {
                    response = UNSUPPORTED_REQUEST_TYPE;
                }
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug(
                            "Unsupported protocol {}",
                            JSON.getAsString(request.get(PROTOCOL)));
                }
                response = UNSUPPORTED_PROTOCOL;
            }

        } catch (RuntimeException e) {
            logger.debug("Error processing POST request", e);
            JsonObject json = new JsonObject();
            json.addProperty("error", e.toString());
            response = json;
        }

        resp.setContentType("text/plain; charset=UTF-8");
        try {
            long byteCount;

            CountingOutputStream cos = new CountingOutputStream(resp.getOutputStream());
            try (Writer writer = new OutputStreamWriter(cos, StandardCharsets.UTF_8)) {
                JSON.writeTo(response, writer);
            }
            byteCount = cos.getCount();
            if (peer != null) {
                peer.updateUploadedVolume(byteCount);
            }
        } catch (Exception e) {
            if (peer != null) {
                peer.blacklist(e, "can't respond to requestType=" + requestType);
            }
            return;
        }

        if (extendedProcessRequest != null) {
            extendedProcessRequest.afterRequestHook.run();
        }
    }

}
