package org.fogofwarlabs.sc4j.scr.data.format.text;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Covers legacy-text decoding: UTF-8 first, then CP949 for Korean, else Windows-1252.
 */
final class LegacyTextUtilsTest {
    private static final @NotNull Charset KOREAN = Charset.forName("x-windows-949");
    /**
     * A Korean map name that both UTF-8 and CP949 encode.
     */
    private static final @NotNull String KOREAN_TEXT = "신 백두대간 2.4";
    /**
     * The size of a force name field in the LAN lobby's force name message.
     */
    private static final int FORCE_NAME_SIZE = 30;

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
    void decode_should_read_a_korean_force_name_alike_from_utf8_and_cp949() {
        String name = "고수팀 1";
        byte[] utf8 = name.getBytes(StandardCharsets.UTF_8);
        byte[] legacy = name.getBytes(KOREAN);

        Assertions.assertEquals(name, LegacyTextUtils.decode(utf8, 0, utf8.length));
        Assertions.assertEquals(name, LegacyTextUtils.decode(legacy, 0, legacy.length));
        Assertions.assertEquals(name, LegacyTextUtils.decodeTerminated(Arrays.copyOf(legacy, FORCE_NAME_SIZE), 0, FORCE_NAME_SIZE));
    }

    @Test
    void decode_should_read_ascii_mixed_with_cp949_as_korean() {
        byte[] bytes = "Team 한국".getBytes(KOREAN);

        Assertions.assertTrue(LegacyTextUtils.isKorean(bytes, 0, bytes.length));
        Assertions.assertEquals("Team 한국", LegacyTextUtils.decode(bytes, 0, bytes.length));
    }

    @Test
    void decode_should_prefer_utf8_for_cp949_bytes_that_are_also_valid_utf8() {
        // CP949 C8 AB is also the UTF-8 sequence of U+022B; valid UTF-8 wins, per string.
        byte[] bytes = "Team 홍".getBytes(KOREAN);

        Assertions.assertTrue(LegacyTextUtils.isUtf8(bytes, 0, bytes.length));
        Assertions.assertEquals("Team \u022b", LegacyTextUtils.decode(bytes, 0, bytes.length));
    }

    @Test
    void decode_should_fall_back_to_windows_1252_for_an_invalid_cp949_pair() {
        // 0xB0 leads a CP949 pair, but a space cannot trail it.
        byte[] bytes = {'A', (byte) 0xB0, ' ', 'B'};

        Assertions.assertFalse(LegacyTextUtils.isUtf8(bytes, 0, bytes.length));
        Assertions.assertFalse(LegacyTextUtils.isKorean(bytes, 0, bytes.length));
        Assertions.assertEquals("A\u00b0 B", LegacyTextUtils.decode(bytes, 0, bytes.length));
    }

    @Test
    void decode_should_keep_ascii_unchanged() {
        byte[] bytes = "| iCCup | Python 1.3".getBytes(StandardCharsets.US_ASCII);

        Assertions.assertEquals("| iCCup | Python 1.3", LegacyTextUtils.decode(bytes, 0, bytes.length));
    }
}
