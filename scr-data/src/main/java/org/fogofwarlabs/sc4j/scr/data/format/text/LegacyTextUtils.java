package org.fogofwarlabs.sc4j.scr.data.format.text;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;

/**
 * Decodes game text as UTF-8, detected CP949 or Windows-1252.
 * <p>
 * Valid UTF-8 takes priority. Legacy detection checks CP949 byte pairs and distinguishes isolated
 * Windows-1252 apostrophes before selecting the fallback encoding.
 */
public final class LegacyTextUtils {
    private static final @NotNull Charset KOREAN = Charset.forName("x-windows-949");
    private static final @NotNull Charset WESTERN = Charset.forName("windows-1252");

    private static final int BYTE_MASK = 0xFF;
    private static final int FIRST_CONTROL_BYTE = 0x01;
    private static final int LAST_CONTROL_BYTE = 0x1F;
    private static final int FIRST_NON_ASCII_BYTE = 0x80;
    /**
     * The Windows-1252 apostrophe, counted apart from the other bytes.
     */
    private static final int APOSTROPHE = 0x92;
    private static final int FIRST_LEAD_BYTE = 0x81;
    private static final int LAST_LEAD_BYTE = 0xFE;
    private static final int FIRST_TRAIL_BYTE = 0x41;
    private static final int LAST_TRAIL_BYTE = 0xFE;
    private static final int PAIR_SIZE = 2;

    /**
     * Decodes game text bytes using UTF-8, detected CP949 or Windows-1252.
     *
     * @param data   the source bytes
     * @param offset the first byte
     * @param length the number of bytes, excluding any terminator
     * @return the decoded text
     */
    public static @NotNull String decode(byte @NotNull [] data, int offset, int length) {
        if (isUtf8(data, offset, length)) {
            return new String(data, offset, length, StandardCharsets.UTF_8);
        }

        if (isKorean(data, offset, length)) {
            try {
                return createStrictDecoder(KOREAN).decode(ByteBuffer.wrap(data, offset, length)).toString();
            } catch (CharacterCodingException ignored) {
                // A pair that passes the byte range test but has no CP949 mapping falls through to Windows-1252.
            }
        }

        return new String(data, offset, length, WESTERN);
    }

    /**
     * Decodes a zero-terminated field.
     *
     * @param data   the source bytes
     * @param offset the first byte of the field
     * @param length the field size
     * @return the decoded text up to the first zero byte
     */
    public static @NotNull String decodeTerminated(byte @NotNull [] data, int offset, int length) {
        int end = offset;

        while (end < offset + length && end < data.length && data[end] != 0) {
            end++;
        }

        return decode(data, offset, end - offset);
    }

    /**
     * Checks whether the bytes are valid UTF-8.
     *
     * @param data   the source bytes
     * @param offset the first byte
     * @param length the number of bytes
     * @return {@code true} if the bytes are valid UTF-8, {@code false} otherwise
     */
    static boolean isUtf8(byte @NotNull [] data, int offset, int length) {
        try {
            createStrictDecoder(StandardCharsets.UTF_8).decode(ByteBuffer.wrap(data, offset, length));

            return true;
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    /**
     * Checks whether the bytes satisfy CP949 detection.
     *
     * @param data   the source bytes
     * @param offset the first byte
     * @param length the number of bytes
     * @return {@code true} if the bytes are read as CP949, {@code false} otherwise
     */
    static boolean isKorean(byte @NotNull [] data, int offset, int length) {
        int characters = 0;
        int apostrophes = 0;
        int asciiApostrophes = 0;
        CharsetDecoder decoder = createStrictDecoder(KOREAN);

        for (int index = offset; index < offset + length; index++) {
            int value = data[index] & BYTE_MASK;

            if (value >= FIRST_CONTROL_BYTE && value <= LAST_CONTROL_BYTE) {
                continue;
            }

            characters++;

            if (value < FIRST_NON_ASCII_BYTE) {
                continue;
            }

            if (value == APOSTROPHE) {
                apostrophes++;

                // The follower may be the terminator, which counts as an ASCII byte.
                if (index + 1 >= offset + length || (data[index + 1] & BYTE_MASK) < FIRST_NON_ASCII_BYTE) {
                    asciiApostrophes++;
                }

                continue;
            }

            if (value < FIRST_LEAD_BYTE || value > LAST_LEAD_BYTE || index + 1 >= offset + length) {
                return false;
            }

            int trail = data[index + 1] & BYTE_MASK;

            if (trail < FIRST_TRAIL_BYTE || trail > LAST_TRAIL_BYTE) {
                return false;
            }

            try {
                CharBuffer pair = decoder.decode(ByteBuffer.wrap(data, index, PAIR_SIZE));

                if (pair.length() != 1) {
                    return false;
                }
            } catch (CharacterCodingException exception) {
                return false;
            }

            index++;
        }

        return characters != apostrophes || apostrophes != asciiApostrophes;
    }

    private static @NotNull CharsetDecoder createStrictDecoder(@NotNull Charset charset) {
        return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
    }


    private LegacyTextUtils() {
    }
}
