package org.fogofwarlabs.sc4j.scr.game.sprite.script;

import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the frame offsets, flips and modifier data that a sprite heading applies to the images of the sprite.
 */
final class SpriteDirectionTest {
    /**
     * The sprite type of the fixture sprite, which the direction does not depend on.
     */
    private static final int SPRITE_TYPE = 0;
    /**
     * The image type of the fixture image, which the direction does not depend on.
     */
    private static final int IMAGE_TYPE = 0;
    /**
     * The largest unsigned word.
     */
    private static final int UNSIGNED_WORD_MAXIMUM = 65535;
    /**
     * The mask of a word.
     */
    private static final int WORD_MASK = 0xFFFF;
    /**
     * The heading bytes around the rounding edges of the frame directions.
     */
    private static final int @NotNull [] HEADINGS = {0, 3, 4, 123, 124, 131, 132, 251, 252, 255};
    /**
     * The frame offsets of {@link #HEADINGS}, in the same order.
     */
    private static final int @NotNull [] FRAME_OFFSETS = {0, 0, 1, 15, 16, 16, 15, 1, 0, 0};
    /**
     * The index of the first heading of {@link #HEADINGS} whose frame is horizontally flipped.
     */
    private static final int FIRST_FLIPPED_HEADING_INDEX = 6;
    /**
     * A frame offset that no heading produces, so every heading rewrites it.
     */
    private static final int STALE_FRAME_OFFSET = 7;
    /**
     * The image flags after an unflipped heading: directional frames and redraw.
     */
    private static final int UNFLIPPED_FLAGS = 0x09;
    /**
     * The image flags after a flipped heading: directional frames, horizontally flipped and redraw.
     */
    private static final int FLIPPED_FLAGS = 0x0B;
    /**
     * The warp flash data before a heading is applied.
     */
    private static final int WARP_FLASH_DATA = 0xA5A5A5A5;
    /**
     * The warp flash data after a heading: the high word kept and the low word restarted.
     */
    private static final int RESTARTED_WARP_FLASH_DATA = 0xA5A50230;

    @Test
    void apply_sprite_direction_should_round_heading_and_keep_modifier_word_widths() {
        for (int headingIndex = 0; headingIndex < HEADINGS.length; headingIndex++) {
            Sprite sprite = SpriteScenario.createSprite(SPRITE_TYPE, 0, 0, 0);
            SpriteImage image = SpriteScenario.createImage(IMAGE_TYPE, 0, 0);

            image.setFlags(SpriteImage.FLAG_HAS_DIRECTIONAL_FRAMES);
            image.setFrameIndexOffset(STALE_FRAME_OFFSET);
            image.setFrameIndex(UNSIGNED_WORD_MAXIMUM);
            image.setModifier(SpriteImage.MODIFIER_WARP_FLASH);
            image.setModifierData1(WARP_FLASH_DATA);
            sprite.insertTailImage(image);

            IscriptExecutor.applySpriteDirection(sprite, HEADINGS[headingIndex]);

            Assertions.assertEquals(FRAME_OFFSETS[headingIndex], image.getFrameIndexOffset());
            Assertions.assertEquals(headingIndex >= FIRST_FLIPPED_HEADING_INDEX ? FLIPPED_FLAGS : UNFLIPPED_FLAGS, image.getFlags());
            Assertions.assertEquals(RESTARTED_WARP_FLASH_DATA, image.getModifierData1());
            Assertions.assertEquals((UNSIGNED_WORD_MAXIMUM + FRAME_OFFSETS[headingIndex]) & WORD_MASK, image.getDrawnFrameIndex());
        }
    }
}
