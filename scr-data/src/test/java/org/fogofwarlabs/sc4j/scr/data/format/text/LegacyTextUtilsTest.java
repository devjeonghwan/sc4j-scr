package org.fogofwarlabs.sc4j.scr.data.format.text;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Covers legacy-text decoding: UTF-8 first, then CP949 for Korean, else Windows-1252.
 */
final class LegacyTextUtilsTest {
    private static final @NotNull Charset KOREAN = Charset.forName("x-windows-949");
    /**
     * A Korean map name that both UTF-8 and CP949 encode.
     */
    private static final @NotNull String KOREAN_TEXT = "신 백두대간 2.4";

    @Test
    void decode_should_keep_valid_utf8() {
        byte[] bytes = KOREAN_TEXT.getBytes(StandardCharsets.UTF_8);

        Assertions.assertEquals(KOREAN_TEXT, LegacyTextUtils.decode(bytes, 0, bytes.length));
    }

    @Test
    void decode_should_read_korean_from_cp949() {
        byte[] bytes = KOREAN_TEXT.getBytes(KOREAN);

        Assertions.assertFalse(LegacyTextUtils.isUtf8(bytes, 0, bytes.length));
        Assertions.assertTrue(LegacyTextUtils.isKorean(bytes, 0, bytes.length));
        Assertions.assertEquals(KOREAN_TEXT, LegacyTextUtils.decode(bytes, 0, bytes.length));
    }

    @Test
    void decode_should_read_western_latin_from_windows_1252() {
        byte[] bytes = "Caf\u00e9 \u00c9t\u00e9".getBytes(Charset.forName("windows-1252"));

        Assertions.assertFalse(LegacyTextUtils.isKorean(bytes, 0, bytes.length));
        Assertions.assertEquals("Caf\u00e9 \u00c9t\u00e9", LegacyTextUtils.decode(bytes, 0, bytes.length));
    }

    @Test
    void is_korean_should_count_every_byte_against_apostrophes() {
        byte[] mixed = {'D', 'o', 'n', (byte) 0x92, 't'};

        Assertions.assertTrue(LegacyTextUtils.isKorean(mixed, 0, mixed.length));
        Assertions.assertEquals("Don" + new String(new byte[]{(byte) 0x92, 't'}, KOREAN), LegacyTextUtils.decode(mixed, 0, mixed.length));

        byte[] apostrophe = {(byte) 0x92};

        Assertions.assertFalse(LegacyTextUtils.isKorean(apostrophe, 0, apostrophe.length));
        Assertions.assertEquals("’", LegacyTextUtils.decode(apostrophe, 0, apostrophe.length));
    }

    @Test
    void decode_should_honor_offset_and_terminator() {
        byte[] field = {'x', 'M', 'a', 'p', 0, 'z', 'z'};

        Assertions.assertEquals("Map", LegacyTextUtils.decodeTerminated(field, 1, 6));
        Assertions.assertEquals("ap", LegacyTextUtils.decode(field, 2, 2));
    }

    @Test
    void decode_should_keep_ascii_unchanged() {
        byte[] bytes = "| iCCup | Python 1.3".getBytes(StandardCharsets.US_ASCII);

        Assertions.assertEquals("| iCCup | Python 1.3", LegacyTextUtils.decode(bytes, 0, bytes.length));
    }
}
