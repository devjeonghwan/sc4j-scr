package org.fogofwarlabs.sc4j.scr.game.movement;

/**
 * Decodes protected comparison values for executable integrity tests.
 */
final class ExecutableIntegrityCheck {
    private static final long DECODE_OFFSET = 0x3DF39462D99C5310L;
    private static final int KEY_ROTATION_BITS = 2;

    /**
     * Compares the decoded context value with the expected value.
     *
     * @param integrityKey        the encoded integrity key
     * @param encodedContextValue the protected context value
     * @param expectedValue       the expected decoded value
     * @return {@code true} if the decoded value matches, {@code false} otherwise
     */
    static boolean matches(long integrityKey, int encodedContextValue, int expectedValue) {
        int decodedContextValue = (int) (DECODE_OFFSET - Long.rotateLeft(integrityKey, KEY_ROTATION_BITS)) ^ encodedContextValue;

        return decodedContextValue == expectedValue;
    }


    /**
     * Creates a {@link ExecutableIntegrityCheck} instance.
     */
    private ExecutableIntegrityCheck() {
    }
}
