package org.fogofwarlabs.sc4j.scr.game.sprite;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks exclusive image ownership and immutable image views.
 */
final class SpriteImageOwnershipTest {
    /**
     * The sprite type of the fixture sprites, which the ownership does not depend on.
     */
    private static final int SPRITE_TYPE = 0;
    /**
     * The image type of the fixture image, which the ownership does not depend on.
     */
    private static final int IMAGE_TYPE = 0;

    @Test
    void image_attachment_has_one_owner_and_the_image_list_cannot_bypass_it() {
        Sprite first = SpriteScenario.createSprite(SPRITE_TYPE, 0, 0, 0);
        Sprite second = SpriteScenario.createSprite(SPRITE_TYPE, 0, 0, 0);
        SpriteImage image = SpriteScenario.createImage(IMAGE_TYPE, 0, 0);

        first.insertHeadImage(image);
        first.setMainImage(image);

        Assertions.assertSame(first, image.getSprite());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> first.getImages().clear());
        Assertions.assertThrows(IllegalStateException.class, () -> second.insertHeadImage(image));

        Assertions.assertTrue(first.removeImage(image));

        Assertions.assertNull(image.getSprite());
        Assertions.assertSame(image, first.getMainImage());

        second.insertHeadImage(image);

        Assertions.assertSame(second, image.getSprite());

        second.clearImages();

        Assertions.assertNull(image.getSprite());
    }
}
