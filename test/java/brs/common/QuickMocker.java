package brs.common;

import brs.fluxcapacitor.FeatureToggle;
import brs.fluxcapacitor.FluxCapacitor;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static brs.http.common.Parameters.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class QuickMocker {

  public static FluxCapacitor fluxCapacitorEnabledFunctionalities(FeatureToggle... enabledToggles) {
    final FluxCapacitor mockCapacitor = mock(FluxCapacitor.class);
    for (FeatureToggle ft : enabledToggles) {
      when(mockCapacitor.isActive(eq(ft))).thenReturn(true);
    }
    return mockCapacitor;
  }

  public static HttpServletRequest httpServletRequest(MockParam... parameters) {
    final HttpServletRequest mockedRequest = mock(HttpServletRequest.class);

    for (MockParam mp : parameters) {
      when(mockedRequest.getParameter(mp.key)).thenReturn(mp.value);
    }

    return mockedRequest;
  }

  public static HttpServletRequest httpServletRequestDefaultKeys(MockParam... parameters) {
    final List<MockParam> paramsWithKeys = new ArrayList<>(Arrays.asList(
        new MockParam(SECRET_PHRASE_PARAMETER, TestConstants.TEST_SECRET_PHRASE),
        new MockParam(PUBLIC_KEY_PARAMETER, TestConstants.TEST_PUBLIC_KEY),
        new MockParam(DEADLINE_PARAMETER, TestConstants.DEADLINE),
        new MockParam(FEE_NQT_PARAMETER, TestConstants.FEE)
    ));

    paramsWithKeys.addAll(Arrays.asList(parameters));

    return httpServletRequest(paramsWithKeys.toArray(new MockParam[paramsWithKeys.size()]));
  }

  public static JsonObject jsonObject(JSONParam... parameters) {
    final JsonObject mockedRequest = mock(JsonObject.class);

    for (JSONParam mp : parameters) {
      when(mockedRequest.get(mp.key)).thenReturn(mp.value);
    }

    return mockedRequest;
  }

  public static class MockParam {

    private final String key;
    private final String value;

    public MockParam(String key, String value) {
      this.key = key;
      this.value = value;
    }

    public MockParam(String key, int value) {
      this(key, "" + value);
    }

    public MockParam(String key, long value) {
      this(key, "" + value);
    }

    public MockParam(String key, boolean value) {
      this(key, "" + value);
    }
  }

  public static class JSONParam {

    private final String key;
    private final JsonElement value;

    public JSONParam(String key, JsonElement value) {
      this.key = key;
      this.value = value;
    }

  }

}
