package org.fogofwarlabs.sc4j.scr.data.format.image;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Reads image relation records and resolves shared animation images.
 */
public final class ImageRelations {
    /**
     * The relation type of images that reuse the related image's anim.
     */
    public static final int TYPE_SHARED_ANIMATION = 512;

    private static final int RECORD_SIZE = 8;
    private static final int RELATED_IMAGE_OFFSET = 4;

    /**
     * Creates an {@link ImageRelations} instance from {@code images.rel} content.
     *
     * @param data the file content
     * @return the created {@link ImageRelations} instance
     * @throws IOException if the content is not a whole number of records
     */
    public static @NotNull ImageRelations parse(byte @NotNull [] data) throws IOException {
        if (data.length % RECORD_SIZE != 0) {
            throw Exceptions.newIOException("images.rel length {0} is not a multiple of 8.", data.length);
        }

        int count = data.length / RECORD_SIZE;
        int[] types = new int[count];
        int[] related = new int[count];

        for (int image = 0; image < count; image++) {
            types[image]   = LittleEndianUtils.readInt32(data, image * RECORD_SIZE);
            related[image] = LittleEndianUtils.readInt32(data, image * RECORD_SIZE + RELATED_IMAGE_OFFSET);
        }

        return new ImageRelations(types, related);
    }


    private final int @NotNull [] types;
    private final int @NotNull [] related;

    private ImageRelations(int @NotNull [] types, int @NotNull [] related) {
        this.types   = types;
        this.related = related;
    }

    /**
     * @return the number of records
     */
    public int getImageCount() {
        return this.types.length;
    }

    /**
     * @param image the image identifier
     * @return the relation type, or {@code 0} for an image outside the table (an image a mod adds)
     */
    public int getType(int image) {
        return image >= 0 && image < this.types.length ? this.types[image] : 0;
    }

    /**
     * @param image the image identifier
     * @return the related image, or {@code -1} when the record has none
     */
    public int getRelatedImage(int image) {
        return this.related[image];
    }

    /**
     * @param image the image identifier
     * @return the related image for {@link #TYPE_SHARED_ANIMATION} records, otherwise the image itself
     */
    public int getAnimationImage(int image) {
        if (image < 0 || image >= this.types.length) {
            return image;
        }

        return this.types[image] == TYPE_SHARED_ANIMATION && this.related[image] >= 0 ? this.related[image] : image;
    }
}
