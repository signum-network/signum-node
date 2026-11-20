package chain;

import static chain.ChainUtils.PASS1;
import static chain.ChainUtils.setupNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.gson.JsonObject;

import brs.util.JSON;
import signumj.Constants;

/** Solo miners should be able to send submitNonce parameters in the POST body, keeping the passphrase out of URLs. */
@RunWith(JUnit4.class)
public class SubmitNonceBodyTest {

    @BeforeClass
    public static void setUp() {
        assertTrue("Mock node did not responded in time", setupNode());
    }

    @Test
    public void submitNonce_GivenParametersInFormBody_Succeeds() throws Exception {
        String body = "requestType=submitNonce&secretPhrase=" + encode(PASS1) + "&nonce=0";

        JsonObject response = post("", body);

        assertEquals(response.toString(), "success", response.get("result").getAsString());
    }

    @Test
    public void submitNonce_GivenParameterInQueryAndBody_BodyWins() throws Exception {
        String body = "secretPhrase=" + encode(PASS1) + "&nonce=0";

        JsonObject response = post("?requestType=submitNonce&nonce=not-a-number", body);

        assertEquals(response.toString(), "success", response.get("result").getAsString());
    }

    private static JsonObject post(String query, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(Constants.HTTP_NODE_LOCAL_TESTNET + "/api" + query))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        return JSON.getAsJsonObject(JSON.parse(response.body()));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
