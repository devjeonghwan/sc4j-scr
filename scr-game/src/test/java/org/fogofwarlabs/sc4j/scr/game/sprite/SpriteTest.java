package org.fogofwarlabs.sc4j.scr.game.sprite;

import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Checks the field widths of a sprite, the links of its image list and the redraw marks of an owner change.
 */
final class SpriteTest {
    /**
     * The sprite type of the fixture sprites, which the tested fields and links do not depend on.
     */
    private static final int SPRITE_TYPE = 0;
    /**
     * The image type of the fixture images, which the tested links and flags do not depend on.
     */
    private static final int IMAGE_TYPE = 0;
    /**
     * An elevation one below {@code 0}.
     */
    private static final int BELOW_ZERO_ELEVATION = -1;
    /**
     * The byte that an elevation one below {@code 0} wraps to.
     */
    private static final int WRAPPED_ELEVATION = 255;
    /**
     * The number of values of a byte.
     */
    private static final int BYTE_RANGE = 256;
    /**
     * The low byte of {@link #OVERFLOWING_ELEVATION}.
     */
    private static final int LOW_BYTE_ELEVATION = 4;
    /**
     * An elevation past the byte range.
     */
    private static final int OVERFLOWING_ELEVATION = BYTE_RANGE + LOW_BYTE_ELEVATION;
    /**
     * The owner of a new fixture sprite.
     */
    private static final int INITIAL_OWNER = 0;
    /**
     * The neutral player.
     */
    private static final int NEUTRAL_PLAYER = 11;
    /**
     * The redraw image flag.
     */
    private static final int REDRAW_FLAG = 0x01;
    /**
     * The image flags of an image with no flag set.
     */
    private static final int NO_IMAGE_FLAGS = 0;

    @Test
    void set_elevation_level_should_store_a_byte() {
        Sprite sprite = SpriteScenario.createSprite(SPRITE_TYPE, 0, 0, 0);

        sprite.setElevationLevel(BELOW_ZERO_ELEVATION);

        Assertions.assertEquals(WRAPPED_ELEVATION, sprite.getElevationLevel(), "an elevation one below 0 wraps to 255");

        sprite.setElevationLevel(OVERFLOWING_ELEVATION);

        Assertions.assertEquals(LOW_BYTE_ELEVATION, sprite.getElevationLevel(), "only the low 8 bits are kept");
    }

    @Test
    void inserted_images_link_next_to_their_reference_image() {
        Sprite sprite = SpriteScenario.createSprite(SPRITE_TYPE, 0, 0, 0);
        SpriteImage primary = SpriteScenario.createImage(IMAGE_TYPE, 0, 0);
        SpriteImage below = SpriteScenario.createImage(IMAGE_TYPE, 0, 0);
        SpriteImage above = SpriteScenario.createImage(IMAGE_TYPE, 0, 0);
        SpriteImage betweenPrimaryAndBelow = SpriteScenario.createImage(IMAGE_TYPE, 0, 0);
        SpriteImage head = SpriteScenario.createImage(IMAGE_TYPE, 0, 0);
        SpriteImage tail = SpriteScenario.createImage(IMAGE_TYPE, 0, 0);

        sprite.insertHeadImage(primary);
        sprite.insertImageBelow(primary, below);
        sprite.insertImageAbove(primary, above);
        sprite.insertImageBelow(primary, betweenPrimaryAndBelow);
        sprite.insertHeadImage(head);
        sprite.insertTailImage(tail);

        Assertions.assertEquals(List.of(head, above, primary, betweenPrimaryAndBelow, below, tail), sprite.getImages());
        Assertions.assertSame(sprite, betweenPrimaryAndBelow.getSprite());
    }

    @Test
    void update_owner_should_mark_every_image_for_redrawing_only_when_the_owner_changes() {
        Sprite sprite = SpriteScenario.createSprite(SPRITE_TYPE, 0, 0, 0);
        SpriteImage primary = SpriteScenario.createImage(IMAGE_TYPE, 0, 0);
        SpriteImage overlay = SpriteScenario.createImage(IMAGE_TYPE, 0, 0);

        sprite.insertHeadImage(primary);
        sprite.insertTailImage(overlay);
        primary.setFlags(NO_IMAGE_FLAGS);
        overlay.setFlags(NO_IMAGE_FLAGS);

        sprite.updateOwner(INITIAL_OWNER);

        Assertions.assertEquals(NO_IMAGE_FLAGS, primary.getFlags(), "an unchanged owner leaves the images");
        Assertions.assertEquals(NO_IMAGE_FLAGS, overlay.getFlags(), "an unchanged owner leaves the images");

        sprite.updateOwner(PlayerTable.NEUTRAL_PLAYER_ID);

        Assertions.assertEquals(NEUTRAL_PLAYER, sprite.getOwner());
        Assertions.assertEquals(REDRAW_FLAG, primary.getFlags());
        Assertions.assertEquals(REDRAW_FLAG, overlay.getFlags());
    }

    @Test
    void image_insertion_rejects_a_reference_image_of_another_sprite() {
        Sprite sprite = SpriteScenario.createSprite(SPRITE_TYPE, 0, 0, 0);
        Sprite otherSprite = SpriteScenario.createSprite(SPRITE_TYPE, 0, 0, 0);
        SpriteImage reference = SpriteScenario.createImage(IMAGE_TYPE, 0, 0);
        SpriteImage image = SpriteScenario.createImage(IMAGE_TYPE, 0, 0);

        otherSprite.insertTailImage(reference);

        Assertions.assertThrows(IllegalStateException.class, () -> sprite.insertImageAbove(reference, image));
        Assertions.assertThrows(IllegalStateException.class, () -> sprite.insertImageBelow(reference, image));
        Assertions.assertNull(image.getSprite());
    }
}
