package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Holds the Storm crypt table and computes its string hash and block decryption, as the emulation's archive records use
 * them: a table of five groups of {@code 0x100} DWORDs, the hash of upper-cased ASCII characters and the DWORD
 * decryption.
 */
final class StormCrypt {
    /**
     * The mask of the low WORD.
     */
    private static final int WORD_MASK = 0xFFFF;
    /**
     * The mask of the low BYTE.
     */
    private static final int BYTE_MASK = 0xFF;

    /**
     * The hash type of a table key ({@code "(hash table)"}, {@code "(block table)"}).
     */
    static final int HASH_TYPE_TABLE_KEY = 3;

    private static final int TABLE_SIZE = 0x500;

    private static final int GROUPS = 5;

    private static final int GROUP_SIZE = 0x100;

    private static final int GROUP_SHIFT = 8;

    private static final int SEED = 0x100001;

    private static final int MULTIPLIER = 125;

    private static final int INCREMENT = 3;

    private static final int MODULUS = 0x2AAAAB;

    private static final int HASH_SEED = 0x7FED7FED;

    private static final int KEY_SEED = 0xEEEEEEEE;

    private static final int SEED_SHIFT = 5;

    private static final int SEED_INCREMENT = 3;

    private static final int DECRYPT_OFFSET = 0x400;

    private static final int KEY_SHIFT_LEFT = 21;

    private static final int KEY_INCREMENT = 0x11111111;

    private static final int KEY_SHIFT_RIGHT = 11;

    private final int @NotNull [] table;

    /**
     * Creates a {@link StormCrypt} instance and fills its crypt table.
     */
    StormCrypt() {
        this.table = new int[TABLE_SIZE];

        int seed = SEED;

        for (int index = 0; index < GROUP_SIZE; index++) {
            for (int group = 0; group < GROUPS; group++) {
                seed = (seed * MULTIPLIER + INCREMENT) % MODULUS;

                int high = (seed & WORD_MASK) << Short.SIZE;

                seed                                   = (seed * MULTIPLIER + INCREMENT) % MODULUS;
                this.table[group * GROUP_SIZE + index] = high | seed & WORD_MASK;
            }
        }
    }

    /**
     * Computes the table-key hash of an ASCII string, ignoring case.
     *
     * @param text the ASCII string
     * @return the hash
     */
    int hashTableKey(@NotNull String text) {
        int hashState = HASH_SEED;
        int seedState = KEY_SEED;

        for (byte character : text.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)) {
            hashState = this.table[(HASH_TYPE_TABLE_KEY << GROUP_SHIFT) + character] ^ (hashState + seedState);
            seedState = character + hashState + seedState + (seedState << SEED_SHIFT) + SEED_INCREMENT;
        }

        return hashState;
    }

    /**
     * Decrypts whole DWORDs of a buffer in place.
     *
     * @param data the buffer
     * @param key  the key
     */
    void decrypt(byte @NotNull [] data, int key) {
        ByteBuffer view = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int seed = KEY_SEED;

        for (int offset = 0; offset + Integer.BYTES <= data.length; offset += Integer.BYTES) {
            seed += this.table[DECRYPT_OFFSET + (key & BYTE_MASK)];

            int value = view.getInt(offset) ^ key + seed;

            view.putInt(offset, value);

            key  = (~key << KEY_SHIFT_LEFT) + KEY_INCREMENT | key >>> KEY_SHIFT_RIGHT;
            seed = value + seed + (seed << SEED_SHIFT) + SEED_INCREMENT;
        }
    }
}
