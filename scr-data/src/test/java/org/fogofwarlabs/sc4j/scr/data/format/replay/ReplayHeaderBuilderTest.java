package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests the player records that the replay header builder writes.
 */
final class ReplayHeaderBuilderTest {
    private static final int PLAYERS_OFFSET = 0xA1;
    private static final int PLAYER_NAME_OFFSET = 11;
    private static final int HEX_CHARACTERS_PER_BYTE = 2;
    private static final int HEX_RADIX = 16;
    /**
     * The name buffer of a player record, terminator included.
     */
    private static final int PLAYER_NAME_SIZE = 25;
    /**
     * A player name of Hangul characters and a space.
     */
    private static final @NotNull String HANGUL_NAME = "베나티르 부족";
    /**
     * The name bytes of {@link #HANGUL_NAME}: the single-byte conversion, its terminator and the rest of the UTF-8 bytes.
     */
    private static final @NotNull String EXPECTED_NAME_HEX = "3f3f3f3f203f3f00b0eba5b420ebb680eca1b1000000000000";

    @Test
    void set_player_should_keep_the_utf8_tail_after_the_single_byte_name() {
        byte[] header = new ReplayHeaderBuilder().setPlayer(0, 0, -1, 1, 2, 1, HANGUL_NAME).build();
        byte[] expected = new byte[EXPECTED_NAME_HEX.length() / HEX_CHARACTERS_PER_BYTE];

        for (int index = 0; index < expected.length; index++) {
            expected[index] = (byte) Integer.parseInt(EXPECTED_NAME_HEX.substring(index * HEX_CHARACTERS_PER_BYTE,
                                                                                  index * HEX_CHARACTERS_PER_BYTE + HEX_CHARACTERS_PER_BYTE),
                                                      HEX_RADIX);
        }

        byte[] actual = new byte[PLAYER_NAME_SIZE];

        System.arraycopy(header, PLAYERS_OFFSET + PLAYER_NAME_OFFSET, actual, 0, PLAYER_NAME_SIZE);

        Assertions.assertArrayEquals(expected, actual);
    }
}
