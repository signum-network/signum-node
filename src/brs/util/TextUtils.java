package brs.util;

import brs.Constants;

import java.util.Locale;

public class TextUtils {
    public static boolean isInAlphabet(String input) {
        if (input == null) return true;
        String normalizedName = input.toLowerCase(Locale.ENGLISH);
        for (int i = 0; i < normalizedName.length(); i++) {
            if (Constants.ALPHABET.indexOf(normalizedName.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}
