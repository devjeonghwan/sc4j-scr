package org.fogofwarlabs.sc4j.scr.client.glue.popup;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;

/**
 * Checks save-dialog file ordering, which typed characters the name box takes and how the dialog reads the typed name.
 */
final class GlueSavePopupTest {
    /**
     * The longest saved game name in bytes.
     */
    private static final int LONGEST_NAME = 27;
    /**
     * A character of three UTF-8 bytes.
     */
    private static final @NotNull String HANGUL = "\ud55c";
    /**
     * The Hangul characters that fill the longest name: {@code 27} bytes.
     */
    private static final int HANGUL_NAME_LENGTH = 9;
    /**
     * The spaces typed between the name and the character the cut drops.
     */
    private static final int TRAILING_SPACES = 2;
    private static final int FILE_SIZE = 1;
    private static final long OLDER_MODIFIED_MILLISECONDS = 1_000_000L;
    private static final long NEWER_MODIFIED_MILLISECONDS = 2_000_000L;
    private static final @NotNull String EXTENSION = ".rep";

    private static byte @NotNull [] encode(@NotNull String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void list_names_should_list_matching_files_oldest_first(@TempDir @NotNull Path directory) throws IOException {
        Path oldest = directory.resolve("zerg rush.rep");
        Path upperCase = directory.resolve("Alpha.rep");
        Path lowerCase = directory.resolve("beta.rep");

        Files.write(oldest, new byte[FILE_SIZE]);
        Files.write(upperCase, new byte[FILE_SIZE]);
        Files.write(lowerCase, new byte[FILE_SIZE]);
        Files.write(directory.resolve("notes.txt"), new byte[FILE_SIZE]);
        Files.createDirectory(directory.resolve("folder.rep"));
        Files.setLastModifiedTime(oldest, FileTime.fromMillis(OLDER_MODIFIED_MILLISECONDS));
        Files.setLastModifiedTime(upperCase, FileTime.fromMillis(NEWER_MODIFIED_MILLISECONDS));
        Files.setLastModifiedTime(lowerCase, FileTime.fromMillis(NEWER_MODIFIED_MILLISECONDS));

        Assertions.assertEquals(List.of("zerg rush", "Alpha", "beta"), GlueSavePopup.listNames(directory, EXTENSION));
        Assertions.assertTrue(GlueSavePopup.listNames(directory.resolve("missing"), EXTENSION).isEmpty());
    }

    @Test
    void read_name_should_cut_the_text_bytes_to_the_name_buffer_before_trimming_spaces() {
        String longest = "x".repeat(LONGEST_NAME);
        String shortened = longest.substring(TRAILING_SPACES);

        Assertions.assertArrayEquals(encode(longest), GlueSavePopup.readName(longest + "y"));
        Assertions.assertArrayEquals(encode(shortened), GlueSavePopup.readName(shortened + "  y"));
        Assertions.assertArrayEquals(encode("  my game"), GlueSavePopup.readName("  my game   "));
        Assertions.assertArrayEquals(new byte[0], GlueSavePopup.readName("   "));
        // Only space bytes are cut: an ideographic space stays.
        Assertions.assertArrayEquals(encode("game\u3000"), GlueSavePopup.readName("game\u3000"));
    }

    @Test
    void read_name_should_keep_the_first_bytes_of_a_character_split_by_the_cut() {
        byte[] mixed = encode("a" + HANGUL.repeat(HANGUL_NAME_LENGTH));
        byte[] single = encode("x".repeat(LONGEST_NAME - 1) + HANGUL);

        // The 28-byte name keeps its first 27 bytes: a, 8 Hangul and the first two bytes of the ninth.
        Assertions.assertArrayEquals(Arrays.copyOf(mixed, LONGEST_NAME), GlueSavePopup.readName("a" + HANGUL.repeat(HANGUL_NAME_LENGTH)));
        Assertions.assertArrayEquals(Arrays.copyOf(single, LONGEST_NAME), GlueSavePopup.readName("x".repeat(LONGEST_NAME - 1) + HANGUL));
        Assertions.assertArrayEquals(encode(HANGUL.repeat(HANGUL_NAME_LENGTH)), GlueSavePopup.readName(HANGUL.repeat(HANGUL_NAME_LENGTH + 1)));
    }

    @Test
    void is_typable_should_refuse_a_character_once_the_text_holds_the_name_bytes() {
        String almost = "x".repeat(LONGEST_NAME - 1);
        String full = "x".repeat(LONGEST_NAME);
        String hangulFull = HANGUL.repeat(HANGUL_NAME_LENGTH);
        String hangulAlmost = HANGUL.repeat(HANGUL_NAME_LENGTH - 1) + "xx";

        Assertions.assertTrue(GlueSavePopup.isTypable(almost, almost + "y"));
        Assertions.assertFalse(GlueSavePopup.isTypable(full, full + "y"), "The 28th character is refused.");
        // Bytes count, not characters: 9 Hangul hold 27 bytes.
        Assertions.assertFalse(GlueSavePopup.isTypable(hangulFull, hangulFull + "y"));
        Assertions.assertTrue(GlueSavePopup.isTypable(hangulAlmost, hangulAlmost + HANGUL));
    }

    @Test
    void is_typable_should_refuse_a_space_into_an_empty_box_only() {
        Assertions.assertFalse(GlueSavePopup.isTypable("", " "));
        Assertions.assertTrue(GlueSavePopup.isTypable("a", "a "));
        Assertions.assertTrue(GlueSavePopup.isTypable("", "a"));
    }
}
