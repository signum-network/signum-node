package brs.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(JUnit4.class)
public class TextUtilsTest {
    @Test
    public void testIsInAlphabet() {
        assertFalse(TextUtils.isInAlphabet("This string should not be okay"));
        assertTrue(TextUtils.isInAlphabet("ThisStringShouldBeOkay"));
        assertFalse(TextUtils.isInAlphabet("ThisStringHasPunctuation!"));
        assertFalse(TextUtils.isInAlphabet(new String(new byte[]{0x00, 0x01, 0x02})));

        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        assertTrue(TextUtils.isInAlphabet("ThisStringHasAnIInIt"));
    }
}
