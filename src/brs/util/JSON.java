package brs.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;

import static brs.Constants.PROTOCOL;

public final class JSON {

    private JSON() {} //never

    public static final JsonElement emptyJSON = new JsonObject();

    public static JsonElement prepareRequest(final JsonObject json) {
        json.addProperty(PROTOCOL, "B1");
        return json;
    }

    public static JsonElement parse(String json) {
        return new JsonParser().parse(json);
    }

    public static JsonElement parse(Reader json) {
        return new JsonParser().parse(json);
    }

    public static JsonElement cloneJson(JsonElement json) {
        return parse(toJsonString(json));
    }

    public static void addAll(JsonObject parent, JsonObject objectToAdd) {
        for (Map.Entry<String, JsonElement> entry : objectToAdd.entrySet()) {
            parent.add(entry.getKey(), entry.getValue());
        }
    }

    public static void writeTo(JsonElement jsonElement, Writer writer) throws IOException {
        writer.write(toJsonString(jsonElement));
        writer.flush();
    }

    public static String toJsonString(JsonElement jsonElement) {
        return jsonElement != null ? jsonElement.toString() : "null";
    }

    public static JsonObject getAsJsonObject(JsonElement jsonElement) {
        return jsonElement != null /*&& jsonElement.isJsonPrimitive()*/ ? jsonElement.getAsJsonObject() : new JsonObject();
    }

    public static JsonArray getAsJsonArray(JsonElement jsonElement) {
        return jsonElement != null && jsonElement.isJsonArray() ? jsonElement.getAsJsonArray() : new JsonArray();
    }

    public static String getAsString(JsonElement jsonElement) {
        return jsonElement != null && jsonElement.isJsonPrimitive() ? jsonElement.getAsString() : null;
    }

    public static long getAsLong(JsonElement jsonElement) {
        return jsonElement != null && jsonElement.isJsonPrimitive() ? jsonElement.getAsLong() : 0;
    }

    public static int getAsInt(JsonElement jsonElement) {
        return jsonElement != null && jsonElement.isJsonPrimitive() ? jsonElement.getAsInt() : 0;
    }

    public static short getAsShort(JsonElement jsonElement) {
        return jsonElement != null && jsonElement.isJsonPrimitive() ? jsonElement.getAsShort() : 0;
    }

    public static byte getAsByte(JsonElement jsonElement) {
        return jsonElement != null && jsonElement.isJsonPrimitive() ? jsonElement.getAsByte() : 0;
    }

    public static boolean getAsBoolean(JsonElement jsonElement) {
        return (jsonElement != null && jsonElement.isJsonPrimitive()) && jsonElement.getAsBoolean();
    }
}
