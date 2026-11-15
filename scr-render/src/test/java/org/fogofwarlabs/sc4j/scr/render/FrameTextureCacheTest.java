package org.fogofwarlabs.sc4j.scr.render;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that {@link FrameTextureCache} returns the cached texture of each frame key and {@code 0} for keys without one.
 */
final class FrameTextureCacheTest {
    /**
     * The positions of the frame, the relation and the flip in a frame key, as the classic scene renderer packs them.
     */
    private static final int KEY_FRAME_SHIFT = 16;
    private static final int KEY_RELATION_SHIFT = 32;
    private static final int KEY_FLIP_SHIFT = 34;
    /**
     * The images, frames and relations of the cached keys: more keys than the initial table holds.
     */
    private static final int IMAGE_COUNT = 40;
    private static final int FRAME_COUNT = 17;
    private static final int RELATION_COUNT = 3;
    /**
     * The first texture name of the cached keys.
     */
    private static final int FIRST_TEXTURE = 1;

    private static long createKey(int image, int frame, int relation, boolean flipped) {
        return (long) image | (long) frame << KEY_FRAME_SHIFT | (long) relation << KEY_RELATION_SHIFT | (flipped ? 1L << KEY_FLIP_SHIFT : 0);
    }

    @Test
    void get_texture_should_return_cached_textures_and_zero_for_missing_keys() {
        FrameTextureCache cache = new FrameTextureCache();
        int texture = FIRST_TEXTURE;

        Assertions.assertEquals(0, cache.getTexture(createKey(0, 0, 0, false)));

        for (int image = 0; image < IMAGE_COUNT; image++) {
            for (int frame = 0; frame < FRAME_COUNT; frame++) {
                for (int relation = 0; relation < RELATION_COUNT; relation++) {
                    cache.putTexture(createKey(image, frame, relation, frame % 2 == 0), texture++);
                }
            }
        }

        texture = FIRST_TEXTURE;

        for (int image = 0; image < IMAGE_COUNT; image++) {
            for (int frame = 0; frame < FRAME_COUNT; frame++) {
                for (int relation = 0; relation < RELATION_COUNT; relation++) {
                    Assertions.assertEquals(texture++, cache.getTexture(createKey(image, frame, relation, frame % 2 == 0)));
                    Assertions.assertEquals(0, cache.getTexture(createKey(image, frame, relation, frame % 2 != 0)));
                }
            }
        }

        Assertions.assertEquals(0, cache.getTexture(createKey(IMAGE_COUNT, 0, 0, true)));
    }
}
