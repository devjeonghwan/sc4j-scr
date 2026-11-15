package org.fogofwarlabs.sc4j.scr.data.format.mpq;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Writes the encrypted tables and little-endian fields of the synthetic MPQ archives of the archive tests.
 */
final class MpqArchiveScenario {
    /**
     * Creates a hash table entry of a file name.
     *
     * @param fileName   the file name the entry hashes
     * @param locale     the locale WORD
     * @param blockIndex the block table index
     * @return the four entry DWORDs
     */
    static int @NotNull [] createHashEntry(@NotNull String fileName, int locale, int blockIndex) {
        return new int[]{MpqCryptoUtils.hashNameA(fileName), MpqCryptoUtils.hashNameB(fileName), locale, blockIndex};
    }

    /**
     * Encrypts by solving the keystream one DWORD at a time with {@link MpqCryptoUtils#decrypt}, whose keystream depends only
     * on the key and the preceding plain DWORDs.
     *
     * @param plain the plain DWORDs
     * @param key   the encryption key
     * @return the encrypted DWORDs
     */
    static int @NotNull [] encrypt(int @NotNull [] plain, int key) {
        int[] encrypted = new int[plain.length];

        for (int valueIndex = 0; valueIndex < plain.length; valueIndex++) {
            int[] probe = Arrays.copyOf(encrypted, valueIndex + 1);

            probe[valueIndex] = 0;
            MpqCryptoUtils.decrypt(probe, key);
            encrypted[valueIndex] = plain[valueIndex] ^ probe[valueIndex];
        }

        return encrypted;
    }

    /**
     * Writes little-endian DWORDs.
     *
     * @param archive  the archive bytes
     * @param position the position of the first DWORD
     * @param values   the DWORDs
     */
    static void putInts(byte @NotNull [] archive, int position, int @NotNull [] values) {
        for (int valueIndex = 0; valueIndex < values.length; valueIndex++) {
            LittleEndianUtils.writeInt32(archive, position + valueIndex * Integer.BYTES, values[valueIndex]);
        }
    }

    /**
     * Creates a {@link MpqArchiveScenario} instance.
     */
    private MpqArchiveScenario() {
    }
}
