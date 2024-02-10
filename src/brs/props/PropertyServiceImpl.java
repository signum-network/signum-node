package brs.props;

import brs.Signum;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import signum.net.NetworkParameters;

// TODO: Remove this and add proper javadoc
@SuppressWarnings("checkstyle:MissingJavadocTypeCheck")
public class PropertyServiceImpl implements PropertyService {

    private final Logger logger = LoggerFactory.getLogger(Signum.class);
    private static final String LOG_UNDEF_NAME_DEFAULT = "{} using default: >{}<";

    private final CaseInsensitiveProperties properties;

    private final List<String> alreadyLoggedProperties = new ArrayList<>();
    private NetworkParameters networkParameters;

    public PropertyServiceImpl(CaseInsensitiveProperties properties) {
        this.properties = properties;
    }

    private String getProperty(String name) {
        String value = properties.getProperty(name);

        // so we have precedence for user set values
        if (value == null && networkParameters != null) {
            value = networkParameters.getProperty(name);
        }
        return value;
    }

    @Override
    public Boolean getBoolean(String name, boolean assume) {
        String value = getProperty(name);

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

        if (logger.isDebugEnabled()) {
            logOnce(name, false, LOG_UNDEF_NAME_DEFAULT, name, assume);
        }
        return assume;
    }

    @Override
    public Boolean getBoolean(Prop<Boolean> prop) {
        return getBoolean(prop.name, prop.defaultValue);
    }

    @Override
    public int getInt(Prop<Integer> prop) {
        String value = getProperty(prop.name);
        if (value != null && !value.isEmpty()) {
            try {
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

        if (logger.isDebugEnabled()) {
            logOnce(prop.name, false, LOG_UNDEF_NAME_DEFAULT, prop.name, prop.defaultValue);
        }
        return prop.defaultValue;

    }

    @Override
    public String getString(Prop<String> prop) {
        String value = getProperty(prop.name);
        if (value != null && !value.isEmpty()) {
            logOnce(prop.name, true, prop.name + " = \"" + value + "\"");
            return value;
        }

        if (logger.isDebugEnabled()) {
            logOnce(prop.name, false, LOG_UNDEF_NAME_DEFAULT, prop.name, prop.defaultValue);
        }

        return prop.defaultValue;
    }

    @Override
    public List<String> getStringList(Prop<String> name) {
        String value = getString(name);
        if (value == null || value.isEmpty()) {
            return new ArrayList<>();
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

    private void logOnce(
            String propertyName,
            boolean debugLevel,
            String logText,
            Object... arguments) {
        if (Objects.equals(propertyName, Props.SOLO_MINING_PASSPHRASES.getName())) {
            return;
        }
        if (!this.alreadyLoggedProperties.contains(propertyName)) {
            if (debugLevel) {
                this.logger.debug(logText, arguments);
            } else {
                this.logger.info(logText, arguments);
            }
            this.alreadyLoggedProperties.add(propertyName);
        }
    }

    @Override
    public void setNetworkParameters(NetworkParameters params) {
        this.networkParameters = params;
    }

}
