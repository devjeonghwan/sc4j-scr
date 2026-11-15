package org.fogofwarlabs.sc4j.scr.core.test.golden;

import org.jetbrains.annotations.NotNull;

/**
 * Mixes run values into 64-bit FNV-1a digests, such as the observable state digest and the traces of a replay run.
 */
public final class GoldenDigestUtils {
    /**
     * The FNV-1a 64-bit offset basis.
     */
    public static final long OFFSET_BASIS = 0xcbf29ce484222325L;

    private static final long PRIME = 0x100000001b3L;
    private static final int BYTE_MASK = 0xFF;

    /**
     * Mixes one {@code int} into a digest, least significant byte first.
     *
     * @param digest the digest so far
     * @param value  the value
     * @return the updated digest
     */
    public static long mix(long digest, int value) {
        long result = digest;

        for (int byteIndex = 0; byteIndex < Integer.BYTES; byteIndex++) {
            result ^= (value >>> (byteIndex * Byte.SIZE)) & BYTE_MASK;
            result *= PRIME;
        }

        return result;
    }

    /**
     * Formats a digest as 16 hexadecimal digits.
     *
     * @param digest the digest
     * @return the formatted digest
     */
    public static @NotNull String format(long digest) {
        return String.format("%016x", digest);
    }

    /**
     * Creates a {@link GoldenDigestUtils} instance.
     */
    private GoldenDigestUtils() {
    }
}
