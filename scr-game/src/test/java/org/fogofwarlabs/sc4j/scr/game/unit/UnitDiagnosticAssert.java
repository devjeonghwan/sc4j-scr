package org.fogofwarlabs.sc4j.scr.game.unit;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

/**
 * Compares modeled scalar state independently of opaque image bytes.
 */
public final class UnitDiagnosticAssert {
    private static final int SPIDER_MINE_COUNT_OFFSET = 0x108;
    private static final int BYTE_MASK = 0xFF;

    /**
     * Compares the modeled scalar state with the expected image.
     *
     * @param nativeImage the expected image
     * @param actual      the actual image
     */
    public static void assertDomainEquals(byte @NotNull [] nativeImage, byte @NotNull [] actual) {
        Assertions.assertEquals(UnitMemoryCheck.SIZE, nativeImage.length);
        Assertions.assertEquals(UnitMemoryCheck.SIZE, actual.length);

        for (int offset = 0; offset < nativeImage.length; offset++) {
            if (UnitMemoryCheck.isScalarByte(offset)) {
                Assertions.assertEquals(nativeImage[offset], actual[offset], "Modeled unit byte " + Integer.toHexString(offset));
            }
        }
    }

    /**
     * Compares the modeled scalar state with the expected image.
     *
     * @param nativeImage the expected image
     * @param unit        the actual unit
     */
    public static void assertDomainEquals(byte @NotNull [] nativeImage, @NotNull Unit unit) {
        assertDomainEquals(nativeImage, unit, "Unit domain state");
    }

    /**
     * Compares the modeled scalar state with the expected image.
     *
     * @param nativeImage the expected image
     * @param unit        the actual unit
     * @param message     the assertion message
     */
    public static void assertDomainEquals(byte @NotNull [] nativeImage, @NotNull Unit unit, @NotNull String message) {
        Assertions.assertEquals(UnitMemoryCheck.SIZE, nativeImage.length, message);

        for (int offset = 0; offset < nativeImage.length; offset++) {
            int width = UnitMemoryCheck.getFieldWidth(offset);

            if (width == 0) {
                continue;
            }

            int expected = 0;

            for (int byteIndex = 0; byteIndex < width; byteIndex++) {
                expected |= (nativeImage[offset + byteIndex] & BYTE_MASK) << (byteIndex * Byte.SIZE);
            }

            int actual = UnitMemoryCheck.readField(unit, offset);

            if (width < Integer.BYTES) {
                actual &= (1 << (width * Byte.SIZE)) - 1;
            }

            Assertions.assertEquals(expected, actual, message + " scalar at capture offset " + Integer.toHexString(offset));
        }

        if (UnitMemoryCheck.isVulture(unit.getUnitId())) {
            Assertions.assertEquals(nativeImage[SPIDER_MINE_COUNT_OFFSET] & BYTE_MASK, unit.getSpiderMineCount(), message + " spider mine count");
        }
    }


    private UnitDiagnosticAssert() {
    }
}
