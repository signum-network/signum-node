package brs.props;

import java.util.Properties;

/**
 * Overrides {@link java.util.Properties} to make all the keys lowercase,
 * thus making the config file keys case-insensitve.
 * Courtesy Jim Yingst
 * https://coderanch.com/t/277128/java/Properties-ignoring-case
 */
public class CaseInsensitiveProperties extends Properties {
    public CaseInsensitiveProperties() {
        super();
    }

    public CaseInsensitiveProperties(CaseInsensitiveProperties properties) {
        super(properties);
    }

    public Object put(Object key, Object value) {
        String lowercase = ((String) key).toLowerCase();
        return super.put(lowercase, value);
    }

    public String getProperty(String key) {
        String lowercase = key.toLowerCase();
        return super.getProperty(lowercase);
    }

    public String getProperty(String key, String defaultValue) {
        String lowercase = key.toLowerCase();
        return super.getProperty(lowercase, defaultValue);
    }
}
