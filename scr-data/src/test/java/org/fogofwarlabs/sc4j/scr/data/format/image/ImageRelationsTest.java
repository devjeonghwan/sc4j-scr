package org.fogofwarlabs.sc4j.scr.data.format.image;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Verifies that image relations resolve related and animation images and reject short content.
 */
final class ImageRelationsTest {
    @Test
    void image_relations_resolve_related_and_animation_images_and_reject_short_input() throws IOException {
        ByteBuffer contents = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);

        contents.putInt(1).putInt(-1).putInt(8).putInt(0).putInt(512).putInt(0);

        ImageRelations relations = ImageRelations.parse(contents.array());

        Assertions.assertEquals(3, relations.getImageCount());
        Assertions.assertEquals(-1, relations.getRelatedImage(0));
        Assertions.assertEquals(1, relations.getAnimationImage(1), "Shadow relations keep their own anim.");
        Assertions.assertEquals(0, relations.getAnimationImage(2), "Shared-animation relations use the related image's anim.");
        Assertions.assertThrows(IOException.class, () -> ImageRelations.parse(new byte[7]));
    }
}
