package it.java.brs;

import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.common.AbstractIT;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

;
;

public class AlternativeRealityTest extends AbstractIT {

  @Test
  public void normalReality() throws IOException, InterruptedException {
    for(JsonObject jsonObject:getReality("reality1.json")) {
      super.processBlock(jsonObject);
      Thread.sleep(500);
    }
  }

  public List<JsonObject> getReality(String realityName) throws IOException {
    JsonParser parser = new JsonParser();

    Path path = Paths.get("test/resources/alternatereality/" + realityName);

    String inputFileContent = new String(Files.readAllBytes(path));
    JsonArray array = (JsonArray) parser.parse(inputFileContent);

    List<JsonObject> result = new ArrayList<>();

    for(JsonElement obj:array) {
      result.add(JSON.getAsJsonObject(obj));
    }

    return result;
  }
}
