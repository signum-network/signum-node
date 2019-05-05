package brs.common;

import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxEnable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static brs.http.common.Parameters.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class QuickMocker {

  public static FluxCapacitor fluxCapacitorEnabledFunctionalities(FluxEnable... enabledToggles) {
    final FluxCapacitor mockCapacitor = mock(FluxCapacitor.class);
    when(mockCapacitor.getValue(any())).thenReturn(false);
    when(mockCapacitor.getValue(any(), anyInt())).thenReturn(false);
    for (FluxEnable ft : enabledToggles) {
      when(mockCapacitor.getValue(eq(ft))).thenReturn(true);
      when(mockCapacitor.getValue(eq(ft), anyInt())).thenReturn(true);
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

    return httpServletRequest(paramsWithKeys.toArray(new MockParam[0]));
  }

  public static JsonObject jsonObject(JSONParam... parameters) {
    final JsonObject mockedRequest = new JsonObject();

    for (JSONParam mp : parameters) {
      mockedRequest.add(mp.key, mp.value);
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
