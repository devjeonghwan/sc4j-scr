package org.fogofwarlabs.sc4j.scr.client.ui.dialog;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Locale;

/**
 * Checks the input-language mapping of keyboard layout locales, the language marks and the unbound input method.
 */
final class InputMethodTest {

    private static final int NO_LANGUAGE = 0;
    private static final int JAPANESE = 1;
    private static final int KOREAN = 2;
    private static final int TRADITIONAL_CHINESE = 3;
    private static final int OTHER_CHINESE = 4;
    private static final int ENGLISH = 5;
    private static final int POLISH = 6;
    private static final int RUSSIAN = 7;

    @Test
    void get_input_language_should_map_keyboard_layout_locales_to_input_languages() {
        Assertions.assertEquals(KOREAN, InputMethod.getInputLanguage(Locale.forLanguageTag("ko-KR")));
        Assertions.assertEquals(JAPANESE, InputMethod.getInputLanguage(Locale.forLanguageTag("ja-JP")));
        Assertions.assertEquals(ENGLISH, InputMethod.getInputLanguage(Locale.forLanguageTag("en-US")));
        Assertions.assertEquals(ENGLISH, InputMethod.getInputLanguage(Locale.forLanguageTag("en-GB")));
        Assertions.assertEquals(POLISH, InputMethod.getInputLanguage(Locale.forLanguageTag("pl-PL")));
        Assertions.assertEquals(RUSSIAN, InputMethod.getInputLanguage(Locale.forLanguageTag("ru-RU")));

        // Chinese: Taiwan (zh-TW) gives 3, the others 4.
        Assertions.assertEquals(TRADITIONAL_CHINESE, InputMethod.getInputLanguage(Locale.forLanguageTag("zh-TW")));
        Assertions.assertEquals(OTHER_CHINESE, InputMethod.getInputLanguage(Locale.forLanguageTag("zh-CN")));
        Assertions.assertEquals(OTHER_CHINESE, InputMethod.getInputLanguage(Locale.forLanguageTag("zh-HK")));

        // Other languages have no input language.
        Assertions.assertEquals(NO_LANGUAGE, InputMethod.getInputLanguage(Locale.forLanguageTag("de-DE")));
    }

    @Test
    void get_input_language_mark_should_map_each_language_to_its_mark() {
        Assertions.assertEquals("| EN", ControlPainterUtils.getInputLanguageMark(NO_LANGUAGE));
        Assertions.assertEquals("| 日本語", ControlPainterUtils.getInputLanguageMark(JAPANESE));
        Assertions.assertEquals("| 가", ControlPainterUtils.getInputLanguageMark(KOREAN));
        Assertions.assertEquals("| 中", ControlPainterUtils.getInputLanguageMark(TRADITIONAL_CHINESE));
        Assertions.assertEquals("| 中", ControlPainterUtils.getInputLanguageMark(OTHER_CHINESE));
        Assertions.assertEquals("| EN", ControlPainterUtils.getInputLanguageMark(ENGLISH));
        Assertions.assertEquals("| PL", ControlPainterUtils.getInputLanguageMark(POLISH));
        Assertions.assertEquals("| RU", ControlPainterUtils.getInputLanguageMark(RUSSIAN));
    }

    @Test
    void text_input_stays_off_and_language_stays_zero_without_bound_window() {
        InputMethod.setTextInput(true);

        Assertions.assertEquals(NO_LANGUAGE, InputMethod.getInputLanguage());
        Assertions.assertNull(InputMethod.getCompositionClause());

        // A refusal outside a composition update changes nothing.
        InputMethod.rejectInsertion();

        Assertions.assertNull(InputMethod.getCompositionClause());

        InputMethod.setTextInput(false);
    }
}
