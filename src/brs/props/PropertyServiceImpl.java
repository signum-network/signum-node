package brs.props;

import brs.Burst;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class PropertyServiceImpl implements PropertyService {

  private final Logger logger = LoggerFactory.getLogger(Burst.class);
  private static final String LOG_UNDEF_NAME_DEFAULT = "{} undefined. Default: >{}<";

  private final Properties properties;

  private final List<String> alreadyLoggedProperties = new ArrayList<>();

  public PropertyServiceImpl(Properties properties) {
    this.properties = properties;
  }

  @Override
  public Boolean getBoolean(String name, boolean assume) {
    String value = properties.getProperty(name);

    if (value != null) {
      if (value.matches("(?i)^1|active|true|yes|on$")) {
        logOnce(name, true, "{} = 'true'", name);
        return true;
      }

      if (value.matches("(?i)^0|false|no|off$")) {
        logOnce(name, true, "{} = 'false'", name);
        return false;
      }
    }

    logOnce(name, false, LOG_UNDEF_NAME_DEFAULT, name, assume);
    return assume;
  }

  @Override
  public Boolean getBoolean(Prop<Boolean> prop) {
    return getBoolean(prop.name, prop.defaultValue);
  }

  @Override
  public int getInt(Prop<Integer> prop) {
    try {
      String value = properties.getProperty(prop.name);
      int radix = 10;

      if (value != null && value.matches("(?i)^0x.+$")) {
        value = value.replaceFirst("^0x", "");
        radix = 16;
      } else if (value != null && value.matches("(?i)^0b[01]+$")) {
        value = value.replaceFirst("^0b", "");
        radix = 2;
      }

      int result = Integer.parseInt(value, radix);
      logOnce(prop.name, true, "{} = '{}'", prop.name, result);
      return result;
    } catch (NumberFormatException e) {
      logOnce(prop.name, false, LOG_UNDEF_NAME_DEFAULT, prop.name, prop.defaultValue);
      return prop.defaultValue;
    }
  }

  @Override
  public String getString(Prop<String> prop) {
    String value = properties.getProperty(prop.name);
    if (value != null && ! value.isEmpty()) {
      logOnce(prop.name, true, prop.name + " = \"" + value + "\"");
      return value;
    }

    logOnce(prop.name, false, LOG_UNDEF_NAME_DEFAULT, prop.name, prop.defaultValue);

    return prop.defaultValue;
  }

  @Override
  public List<String> getStringList(Prop<String> name) {
    String value = getString(name);
    if (value == null || value.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<>();
    for (String s : value.split(";")) {
      s = s.trim();
      if (!s.isEmpty()) {
        result.add(s);
      }
    }
    return result;
  }

  private void logOnce(String propertyName, boolean debugLevel, String logText, Object... arguments) {
    if (Objects.equals(propertyName, Props.SOLO_MINING_PASSPHRASES.getName())) return;
    if (!this.alreadyLoggedProperties.contains(propertyName)) {
      if (debugLevel) {
        this.logger.debug(logText, arguments);
      } else {
        this.logger.info(logText, arguments);
      }
      this.alreadyLoggedProperties.add(propertyName);
    }
  }

}
