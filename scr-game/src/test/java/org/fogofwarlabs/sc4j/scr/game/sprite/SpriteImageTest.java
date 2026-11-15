package org.fogofwarlabs.sc4j.scr.game.sprite;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks byte and word truncation in image storage and the unsigned byte wait.
 */
final class SpriteImageTest {
    /**
     * The image type of the fixture image, which the field widths do not depend on.
     */
    private static final int IMAGE_TYPE = 0;
    /**
     * An int with every bit set, which a signed byte field also reads back.
     */
    private static final int ALL_BITS = -1;
    /**
     * The largest unsigned byte.
     */
    private static final int UNSIGNED_BYTE_MAXIMUM = 255;
    /**
     * The unsigned byte whose signed value is the smallest signed byte.
     */
    private static final int SIGNED_BYTE_MINIMUM_BITS = 128;
    /**
     * The smallest signed byte.
     */
    private static final int SIGNED_BYTE_MINIMUM = -128;
    /**
     * One below the smallest signed byte.
     */
    private static final int BELOW_SIGNED_BYTE_MINIMUM = -129;
    /**
     * The largest signed byte.
     */
    private static final int SIGNED_BYTE_MAXIMUM = 127;
    /**
     * A value past the byte range whose low byte is {@code 1}.
     */
    private static final int BYTE_OVERFLOW = 257;
    /**
     * The largest unsigned word.
     */
    private static final int UNSIGNED_WORD_MAXIMUM = 65535;
    /**
     * A value past the word range whose low word is {@code 1}.
     */
    private static final int WORD_OVERFLOW = 65537;
    /**
     * The low byte of {@link #BYTE_OVERFLOW} and the low word of {@link #WORD_OVERFLOW}.
     */
    private static final int OVERFLOW_LOW_BITS = 1;
    /**
     * The frame index of the largest unsigned word base plus offset {@code 1}, which wraps to a word.
     */
    private static final int WRAPPED_FRAME_INDEX = 0;
    /**
     * The largest wait byte.
     */
    private static final int MAXIMUM_WAIT = 255;
    /**
     * The first wait past the byte range, which wraps to {@code 0}.
     */
    private static final int WRAPPING_WAIT = 256;
    /**
     * A wait whose low byte is {@link #MAXIMUM_WAIT}.
     */
    private static final int WRAPPED_MAXIMUM_WAIT = 511;
    /**
     * A negative wait, whose low byte is {@link #MAXIMUM_WAIT}.
     */
    private static final int NEGATIVE_WAIT = -1;
    /**
     * The wait that a value with a zero low byte stores.
     */
    private static final int ZERO_WAIT = 0;

    @Test
    void image_fields_truncate_to_their_byte_and_word_widths() {
        SpriteImage image = SpriteScenario.createImage(IMAGE_TYPE, UNSIGNED_BYTE_MAXIMUM, SIGNED_BYTE_MINIMUM_BITS);

        Assertions.assertEquals(ALL_BITS, image.getOffsetX());
        Assertions.assertEquals(SIGNED_BYTE_MINIMUM, image.getOffsetY());

        image.setOffsetX(BYTE_OVERFLOW);
        image.setOffsetY(BELOW_SIGNED_BYTE_MINIMUM);

        Assertions.assertEquals(OVERFLOW_LOW_BITS, image.getOffsetX());
        Assertions.assertEquals(SIGNED_BYTE_MAXIMUM, image.getOffsetY());

        image.setFrameIndex(UNSIGNED_WORD_MAXIMUM);
        image.setFrameIndexOffset(BYTE_OVERFLOW);

        Assertions.assertEquals(UNSIGNED_WORD_MAXIMUM, image.getFrameIndex());
        Assertions.assertEquals(OVERFLOW_LOW_BITS, image.getFrameIndexOffset());
        Assertions.assertEquals(WRAPPED_FRAME_INDEX, image.getDrawnFrameIndex());

        image.setAnimationType(ALL_BITS);
        image.setProgramCounter(WORD_OVERFLOW);
        image.setReturnAddress(ALL_BITS);
        image.setFlags(ALL_BITS);

        Assertions.assertEquals(UNSIGNED_BYTE_MAXIMUM, image.getAnimationType());
        Assertions.assertEquals(OVERFLOW_LOW_BITS, image.getProgramCounter());
        Assertions.assertEquals(UNSIGNED_WORD_MAXIMUM, image.getReturnAddress());
        Assertions.assertEquals(UNSIGNED_WORD_MAXIMUM, image.getFlags());
    }

    @Test
    void set_wait_should_store_only_the_unsigned_low_byte() {
        SpriteImage image = SpriteScenario.createImage(IMAGE_TYPE, 0, 0);

        image.setWait(NEGATIVE_WAIT);

        Assertions.assertEquals(MAXIMUM_WAIT, image.getWait());

        image.setWait(WRAPPING_WAIT);

        Assertions.assertEquals(ZERO_WAIT, image.getWait());

        image.setWait(WRAPPED_MAXIMUM_WAIT);

        Assertions.assertEquals(MAXIMUM_WAIT, image.getWait());

        image.setWait(Integer.MIN_VALUE);

        Assertions.assertEquals(ZERO_WAIT, image.getWait());
    }
}
