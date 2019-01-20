package it.common;

import brs.common.TestInfrastructure;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static brs.http.common.Parameters.*;

public class APISender {

  private final HttpClient httpclient;
  private final JsonParser parser = new JsonParser();

  public APISender() {
    httpclient = HttpClientBuilder.create().build();
  }

  public JsonObject retrieve(String requestType, List<BasicNameValuePair> extraParams) throws IOException {
    final HttpPost post = new HttpPost("/burst");

    final List<NameValuePair> urlParameters = new ArrayList<>();
    urlParameters.add(new BasicNameValuePair("requestType", requestType));
    urlParameters.add(new BasicNameValuePair("random", "0.7113466594385798"));
    urlParameters.addAll(extraParams);

    post.setEntity(new UrlEncodedFormEntity(urlParameters));

    final HttpResponse result = httpclient.execute(new HttpHost("localhost", TestInfrastructure.TEST_API_PORT), post);

    return (JsonObject) parser.parse(EntityUtils.toString(result.getEntity(), "UTF-8"));
  }

  public JsonObject getAccount(String accountName) throws IOException {
    return retrieve("getAccount", Arrays.asList(
        new BasicNameValuePair(ACCOUNT_PARAMETER, accountName),
        new BasicNameValuePair(FIRST_INDEX_PARAMETER, "0"),
        new BasicNameValuePair(LAST_INDEX_PARAMETER, "100")
      )
    );
  }

}
