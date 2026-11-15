package org.fogofwarlabs.sc4j.scr.game.save;

import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Checks the bytes of the saved-game banner and the description parsed back from them.
 */
final class SaveGameDescriptionTest {
    /**
     * A character of three UTF-8 bytes.
     */
    private static final @NotNull String HANGUL = "\ud55c";
    /**
     * The Hangul characters after the first name character; together they exceed the name buffer.
     */
    private static final int NAME_HANGUL_COUNT = 9;
    /**
     * The name bytes that the name buffer holds before its terminator.
     */
    private static final int NAME_TEXT_SIZE = ReplayFile.SAVED_GAME_NAME_SIZE - 1;

    private static final @NotNull List<String> PLAYERS = List.of("first", HANGUL);
    private static final @NotNull String SCENARIO_TITLE = "title";
    private static final @NotNull String MAP_PATH = "maps\\(2)map.scx";
    private static final int EUD_FLAGS = 3;
    /**
     * The client version and the game type that a written description carries.
     */
    private static final @NotNull String CLIENT_VERSION = "1.23.10.13515";
    private static final @NotNull String CUSTOM_GAME_TYPE = "Custom";

    /**
     * The banner bytes around the file name: the line end before its label, the label, and the line end after the name.
     */
    private static final byte @NotNull [] FILENAME_LABEL = "\r\n  Filename:  ".getBytes(StandardCharsets.US_ASCII);
    private static final byte @NotNull [] LINE_END = "\r\n".getBytes(StandardCharsets.US_ASCII);

    private static @NotNull SaveGameDescription createDescription(byte @NotNull [] name) {
        return new SaveGameDescription(SaveGameDescription.CLIENT_VERSION, true, name, PLAYERS, SaveGameDescription.CUSTOM_GAME_TYPE,
                                       SCENARIO_TITLE, MAP_PATH, EUD_FLAGS);
    }

    private static int indexOf(byte @NotNull [] bytes, byte @NotNull [] part) {
        for (int index = 0; index + part.length <= bytes.length; index++) {
            if (Arrays.equals(bytes, index, index + part.length, part, 0, part.length)) {
                return index;
            }
        }

        return -1;
    }

    @Test
    void to_banner_bytes_should_write_the_save_name_bytes_as_the_command_carried_them() {
        // The name a + 9 Hangul cut to the name buffer: its last character keeps two of its three bytes.
        byte[] name = Arrays.copyOf(("a" + HANGUL.repeat(NAME_HANGUL_COUNT)).getBytes(StandardCharsets.UTF_8), NAME_TEXT_SIZE);
        byte[] banner = createDescription(name).toBannerBytes();
        int start = indexOf(banner, FILENAME_LABEL) + FILENAME_LABEL.length;
        int end = start + name.length;

        Assertions.assertArrayEquals(name, Arrays.copyOfRange(banner, start, end));
        Assertions.assertArrayEquals(LINE_END, Arrays.copyOfRange(banner, end, end + LINE_END.length));
    }

    @Test
    void parse_should_read_back_the_save_name_bytes_and_the_text_lines() {
        // The name buffer keeps only the first of the last character's three bytes.
        byte[] text = ("x".repeat(NAME_TEXT_SIZE - 1) + HANGUL).getBytes(StandardCharsets.UTF_8);
        byte[] name = Arrays.copyOf(text, NAME_TEXT_SIZE);
        SaveGameDescription parsed = SaveGameDescription.parse(createDescription(name).toBannerBytes());

        Assertions.assertEquals(CLIENT_VERSION, parsed.getClientVersion());
        Assertions.assertTrue(parsed.isMultiplayer());
        Assertions.assertArrayEquals(name, parsed.getFilename());
        Assertions.assertEquals(PLAYERS, parsed.getPlayers());
        Assertions.assertEquals(CUSTOM_GAME_TYPE, parsed.getGameType());
        Assertions.assertEquals(SCENARIO_TITLE, parsed.getScenarioTitle());
        Assertions.assertEquals(MAP_PATH, parsed.getMapPath());
        Assertions.assertEquals(EUD_FLAGS, parsed.getEudFlags());
    }
}
