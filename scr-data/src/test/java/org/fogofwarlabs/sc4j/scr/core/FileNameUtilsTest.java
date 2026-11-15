package org.fogofwarlabs.sc4j.scr.core;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Checks the file name rule of character, saved game and received map file names, and the file name their bytes give.
 */
final class FileNameUtilsTest {
    /**
     * The size of a saved game name buffer, terminator included.
     */
    private static final int SAVED_GAME_SIZE = 0x1C;
    /**
     * The size of a character name buffer, terminator included.
     */
    private static final int CHARACTER_NAME_SIZE = 0x60;
    /**
     * A character of three UTF-8 bytes.
     */
    private static final @NotNull String HANGUL = "\ud55c";
    /**
     * The UTF-8 byte count of {@link #HANGUL}.
     */
    private static final int HANGUL_BYTES = 3;

    private static boolean isValid(@NotNull String name) {
        return FileNameUtils.isValidFileName(name.getBytes(StandardCharsets.UTF_8), SAVED_GAME_SIZE, FileNameUtils.BAD_CHARACTERS);
    }

    private static byte @NotNull [] toBytes(int @NotNull ... values) {
        byte[] bytes = new byte[values.length];

        for (int index = 0; index < values.length; index++) {
            bytes[index] = (byte) values[index];
        }

        return bytes;
    }

    @Test
    void is_valid_file_name_should_refuse_device_names_before_the_first_dot_in_any_ascii_case() {
        for (String device : new String[]{"nul", "CON", "Prn", "aux", "CONIN$", "conout$", "clock$", "com1", "COM9", "lpt1", "Lpt9"}) {
            Assertions.assertFalse(isValid(device), device);
            Assertions.assertFalse(isValid(device + ".rep"), device);
        }

        Assertions.assertTrue(isValid("com0"));
        Assertions.assertTrue(isValid("console"));
        Assertions.assertTrue(isValid(" con"));
        Assertions.assertTrue(isValid("con "));
        Assertions.assertTrue(isValid("\u212aom1"));
        Assertions.assertFalse(isValid("C:con"));
    }

    @Test
    void is_valid_file_name_should_refuse_bad_characters_after_a_drive_prefix() {
        Assertions.assertTrue(isValid("C:game"));
        Assertions.assertTrue(isValid("z:game"));
        Assertions.assertFalse(isValid("1:game"));
        Assertions.assertFalse(isValid("\u00c4:game"));
        Assertions.assertFalse(isValid("a:b:c"));

        for (char character : FileNameUtils.BAD_CHARACTERS.toCharArray()) {
            Assertions.assertFalse(isValid("game" + character), String.valueOf(character));
        }

        Assertions.assertTrue(FileNameUtils.isValidFileName("a*b".getBytes(StandardCharsets.UTF_8), SAVED_GAME_SIZE, "?"));
    }

    @Test
    void is_valid_file_name_should_refuse_names_of_spaces_and_dots_only() {
        Assertions.assertFalse(isValid(""));
        Assertions.assertFalse(isValid(" "));
        Assertions.assertFalse(isValid(". ."));
        Assertions.assertTrue(isValid(". .a"));
        Assertions.assertTrue(isValid(".rep"));
    }

    @Test
    void is_valid_file_name_should_refuse_control_bytes() {
        Assertions.assertFalse(isValid("a\tb"));
        Assertions.assertFalse(isValid("\u001f"));
    }

    @Test
    void is_valid_file_name_should_check_a_name_longer_than_its_buffer_cut() {
        String longest = "x".repeat(SAVED_GAME_SIZE - 1);
        byte[] characterName = HANGUL.repeat(CHARACTER_NAME_SIZE / HANGUL_BYTES).getBytes(StandardCharsets.UTF_8);

        Assertions.assertTrue(isValid(longest));
        Assertions.assertTrue(isValid(longest + "x"));
        Assertions.assertTrue(isValid(longest + "*"));
        Assertions.assertFalse(isValid(longest.substring(1) + "*"));
        Assertions.assertTrue(FileNameUtils.isValidFileName(characterName, CHARACTER_NAME_SIZE, FileNameUtils.BAD_CHARACTERS));
    }

    @Test
    void is_valid_file_name_should_check_a_name_inside_received_bytes() {
        byte[] name = "(2)Bottleneck.scm".getBytes(StandardCharsets.UTF_8);
        byte[] information = new byte[name.length + 3];

        System.arraycopy(name, 0, information, 2, name.length);

        Assertions.assertTrue(FileNameUtils.isValidFileName(information, 2, name.length + 1, FileNameUtils.BAD_CHARACTERS));
        Assertions.assertFalse(FileNameUtils.isValidFileName(information, 2, name.length, FileNameUtils.BAD_CHARACTERS));
    }

    @Test
    void read_file_name_should_read_well_formed_utf8_bytes() {
        Assertions.assertEquals("my game", FileNameUtils.readFileName("my game".getBytes(StandardCharsets.UTF_8)));
        Assertions.assertEquals(HANGUL.repeat(9), FileNameUtils.readFileName(HANGUL.repeat(9).getBytes(StandardCharsets.UTF_8)));
        Assertions.assertEquals("a\u00e9\ud83d\ude00\t", FileNameUtils.readFileName(toBytes('a', 0xC3, 0xA9, 0xF0, 0x9F, 0x98, 0x80, '\t')));
        Assertions.assertEquals("\u0800\ud000\uffff\udbff\udfff",
                                FileNameUtils.readFileName(toBytes(0xE0, 0xA0, 0x80, 0xED, 0x80, 0x80, 0xEF, 0xBF, 0xBF, 0xF4, 0x8F, 0xBF, 0xBF)));
    }

    @Test
    void read_file_name_should_refuse_split_and_malformed_bytes() {
        // A name cut inside its last character: a + 8 Hangul + 2 bytes, and 26 x + 1 byte.
        byte[] split = Arrays.copyOf(("a" + HANGUL.repeat(SAVED_GAME_SIZE / HANGUL_BYTES)).getBytes(StandardCharsets.UTF_8), SAVED_GAME_SIZE - 1);
        byte[] lead = Arrays.copyOf(("x".repeat(SAVED_GAME_SIZE - 2) + HANGUL).getBytes(StandardCharsets.UTF_8), SAVED_GAME_SIZE - 1);

        Assertions.assertNull(FileNameUtils.readFileName(split));
        Assertions.assertNull(FileNameUtils.readFileName(lead));
        Assertions.assertNull(FileNameUtils.readFileName(toBytes('a', 0x80)));
        Assertions.assertNull(FileNameUtils.readFileName(toBytes(0xC1, 0xBF)));
        Assertions.assertNull(FileNameUtils.readFileName(toBytes(0xE0, 0x9F, 0xBF)));
        Assertions.assertNull(FileNameUtils.readFileName(toBytes(0xED, 0xA0, 0x80)));
        Assertions.assertNull(FileNameUtils.readFileName(toBytes(0xF0, 0x8F, 0xBF, 0xBF)));
        Assertions.assertNull(FileNameUtils.readFileName(toBytes(0xF4, 0x90, 0x80, 0x80)));
        Assertions.assertNull(FileNameUtils.readFileName(toBytes(0xF5, 0x80, 0x80, 0x80)));
        Assertions.assertNull(FileNameUtils.readFileName(toBytes('a', 0x7F)));
        Assertions.assertNull(FileNameUtils.readFileName(toBytes('a', 0x01)));
    }
}
