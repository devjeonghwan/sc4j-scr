package org.fogofwarlabs.sc4j.scr.core;

import org.fogofwarlabs.sc4j.base.text.AsciiCaseUtils;
import org.fogofwarlabs.sc4j.base.text.TerminatedStringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Checks names that become file names: character names, saved game names and received map file names.
 * <p>
 * The check works on the UTF-8 bytes of a name, which end with a zero terminator within a limit
 * ({@link TerminatedStringUtils#getTerminatedLength}). A valid name has no control byte, holds no bad character after an
 * optional drive prefix ({@code C:}), is not made of spaces and dots only, and the part before its first dot is not a
 * Windows device name; the device names compare with {@link AsciiCaseUtils}.
 * <p>
 * A file is named from the bytes of a valid name only if they convert to a path ({@link #readFileName}): a name whose bytes
 * are not well-formed UTF-8, such as a name cut inside a character, opens no file.
 */
public final class FileNameUtils {
    /**
     * The network text key of the characters a file name must not hold.
     */
    public static final @NotNull String BAD_CHARACTERS_KEY = "strBADFILECHARS";
    /**
     * The characters a file name must not hold: the English {@link #BAD_CHARACTERS_KEY} network text.
     */
    public static final @NotNull String BAD_CHARACTERS = "*\\\\/:><|&+^?\"";

    /**
     * The Windows device names, in lower case; a name whose part before the first dot is one of them is refused.
     */
    private static final @NotNull String @NotNull [] DEVICE_NAMES = {"nul", "con", "prn", "aux", "conin$", "conout$", "clock$",
                                                                     "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
                                                                     "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"};
    /**
     * The drive letters in lower case: the ASCII letters only.
     */
    private static final int FIRST_DRIVE_LETTER = 'a';
    private static final int LAST_DRIVE_LETTER = 'z';
    /**
     * The length of a drive prefix ({@code C:}), which the bad character and device name checks skip.
     */
    private static final int DRIVE_PREFIX_LENGTH = 2;
    private static final byte DRIVE_SEPARATOR = ':';
    private static final byte SPACE = ' ';
    private static final byte DOT = '.';
    /**
     * The control bytes a path may hold: tab, line feed and carriage return.
     */
    private static final int TAB = '\t';
    private static final int LINE_FEED = '\n';
    private static final int CARRIAGE_RETURN = '\r';
    /**
     * The first byte that is not a control byte, and the delete byte, which a path may not hold.
     */
    private static final int FIRST_PRINTABLE = 0x20;
    private static final int DELETE = 0x7F;
    /**
     * The first lead bytes of two-, three- and four-byte UTF-8 sequences, and the last lead byte.
     */
    private static final int FIRST_TWO_BYTE_LEAD = 0xC2;
    private static final int FIRST_THREE_BYTE_LEAD = 0xE0;
    private static final int FIRST_FOUR_BYTE_LEAD = 0xF0;
    private static final int LAST_FOUR_BYTE_LEAD = 0xF4;
    /**
     * The three-byte lead whose second byte excludes the surrogates: {@code 0x80}-{@code 0x9f} only.
     */
    private static final int SURROGATE_LEAD = 0xED;
    private static final int LAST_SURROGATE_FREE_SECOND = 0x9F;
    /**
     * The first second bytes of the leads {@code 0xe0} and {@code 0xf0}, which exclude overlong forms.
     */
    private static final int FIRST_THREE_BYTE_SECOND = 0xA0;
    private static final int FIRST_FOUR_BYTE_SECOND = 0x90;
    /**
     * The last second byte of the lead {@code 0xf4}, which excludes values over U+10FFFF.
     */
    private static final int LAST_FOUR_BYTE_SECOND = 0x8F;
    private static final int FIRST_CONTINUATION = 0x80;
    private static final int LAST_CONTINUATION = 0xBF;
    private static final int TWO_BYTES = 2;
    private static final int THREE_BYTES = 3;
    private static final int FOUR_BYTES = 4;

    /**
     * Checks a name as a buffer of a size holds it: {@link #isValidFileName(byte[], int, int, String)} on the bytes that
     * {@link TerminatedStringUtils#cutText(byte[], int)} keeps and the terminator, so a name longer than the buffer is checked
     * cut and never refused for its length.
     *
     * @param name          the bytes of the name, without terminator
     * @param size          the buffer size, terminator included
     * @param badCharacters the characters the name must not hold
     * @return {@code true} if the name is a valid file name, {@code false} otherwise
     */
    public static boolean isValidFileName(byte @NotNull [] name, int size, @NotNull String badCharacters) {
        byte[] cut = TerminatedStringUtils.cutText(name, size);

        return isValidFileName(Arrays.copyOf(cut, cut.length + 1), 0, size, badCharacters);
    }

    /**
     * Checks a zero-terminated name: it ends within the limit without a control byte, holds none of the bad characters after
     * an optional drive prefix, is not only spaces and dots, and the part before its first dot is not a device name.
     *
     * @param text          the bytes
     * @param offset        the offset of the name
     * @param limit         the most bytes of the name with its terminator
     * @param badCharacters the characters the name must not hold; each UTF-8 byte of them is refused
     * @return {@code true} if the name is a valid file name, {@code false} otherwise
     */
    public static boolean isValidFileName(byte @NotNull [] text, int offset, int limit, @NotNull String badCharacters) {
        int length = TerminatedStringUtils.getTerminatedLength(text, offset, limit);

        if (length == 0) {
            return false;
        }

        int start = offset;
        int end = offset + length - 1;

        // The terminator lies at the end, so a one-byte name reads it as its second byte.
        if (isDriveLetter(text[start]) && text[start + 1] == DRIVE_SEPARATOR) {
            start += DRIVE_PREFIX_LENGTH;
        }

        byte[] refused = badCharacters.getBytes(StandardCharsets.UTF_8);

        for (int index = start; index < end; index++) {
            for (byte character : refused) {
                if (text[index] == character) {
                    return false;
                }
            }
        }

        int first = start;

        while (first < end && (text[first] == SPACE || text[first] == DOT)) {
            first++;
        }

        if (first == end) {
            return false;
        }

        int dot = start;

        while (dot < end && text[dot] != DOT) {
            dot++;
        }

        for (String device : DEVICE_NAMES) {
            if (isNamed(text, start, dot, device)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Reads the file name of a name: its text if its bytes convert to a path, that is if they are well-formed UTF-8 (no
     * overlong form, surrogate or value over U+10FFFF) and hold no control byte but tab, line feed and carriage return and no
     * delete byte.
     *
     * @param name the bytes of the name, without terminator
     * @return the file name, or {@code null} if the bytes do not convert, so that no file of the name can be opened
     */
    public static @Nullable String readFileName(byte @NotNull [] name) {
        int index = 0;

        while (index < name.length) {
            int length = readSequenceLength(name, index);

            if (length == 0) {
                return null;
            }

            index += length;
        }

        return new String(name, StandardCharsets.UTF_8);
    }

    /**
     * Reads the length of the convertible UTF-8 sequence at an index.
     *
     * @param name  the bytes of the name
     * @param index the index of the sequence's lead byte
     * @return the bytes of the sequence, or {@code 0} if it does not convert
     */
    private static int readSequenceLength(byte @NotNull [] name, int index) {
        int lead = Byte.toUnsignedInt(name[index]);

        if (lead < FIRST_PRINTABLE) {
            return lead == TAB || lead == LINE_FEED || lead == CARRIAGE_RETURN ? 1 : 0;
        }

        if (lead < DELETE) {
            return 1;
        }

        int length;
        int firstSecond = FIRST_CONTINUATION;
        int lastSecond = LAST_CONTINUATION;

        if (lead >= FIRST_TWO_BYTE_LEAD && lead < FIRST_THREE_BYTE_LEAD) {
            length = TWO_BYTES;
        } else if (lead >= FIRST_THREE_BYTE_LEAD && lead < FIRST_FOUR_BYTE_LEAD) {
            length = THREE_BYTES;

            if (lead == FIRST_THREE_BYTE_LEAD) {
                firstSecond = FIRST_THREE_BYTE_SECOND;
            } else if (lead == SURROGATE_LEAD) {
                lastSecond = LAST_SURROGATE_FREE_SECOND;
            }
        } else if (lead >= FIRST_FOUR_BYTE_LEAD && lead <= LAST_FOUR_BYTE_LEAD) {
            length = FOUR_BYTES;

            if (lead == FIRST_FOUR_BYTE_LEAD) {
                firstSecond = FIRST_FOUR_BYTE_SECOND;
            } else if (lead == LAST_FOUR_BYTE_LEAD) {
                lastSecond = LAST_FOUR_BYTE_SECOND;
            }
        } else {
            return 0;
        }

        if (!isInRange(name, index + 1, firstSecond, lastSecond)) {
            return 0;
        }

        for (int next = index + TWO_BYTES; next < index + length; next++) {
            if (!isInRange(name, next, FIRST_CONTINUATION, LAST_CONTINUATION)) {
                return 0;
            }
        }

        return length;
    }

    private static boolean isInRange(byte @NotNull [] name, int index, int first, int last) {
        if (index >= name.length) {
            return false;
        }

        int value = Byte.toUnsignedInt(name[index]);

        return value >= first && value <= last;
    }

    private static boolean isDriveLetter(byte value) {
        int letter = AsciiCaseUtils.toLowerCase(Byte.toUnsignedInt(value));

        return letter >= FIRST_DRIVE_LETTER && letter <= LAST_DRIVE_LETTER;
    }

    private static boolean isNamed(byte @NotNull [] text, int start, int end, @NotNull String name) {
        if (end - start != name.length()) {
            return false;
        }

        for (int index = 0; index < name.length(); index++) {
            if (AsciiCaseUtils.toLowerCase(Byte.toUnsignedInt(text[start + index])) != AsciiCaseUtils.toLowerCase(name.charAt(index))) {
                return false;
            }
        }

        return true;
    }

    private FileNameUtils() {
    }
}
