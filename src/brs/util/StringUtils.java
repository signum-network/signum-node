package brs.util;

public class StringUtils {
    private StringUtils() {
    }

    public static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

    public static int countMatches(String str, String sub) {
        if (!isEmpty(str) && !isEmpty(sub)) {
            int count = 0;

            for(int idx = 0; (idx = str.indexOf(sub, idx)) != -1; idx += sub.length()) {
                ++count;
            }

            return count;
        } else {
            return 0;
        }
    }
}
