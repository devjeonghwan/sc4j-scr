package org.fogofwarlabs.sc4j.scr.client.ui;

import org.fogofwarlabs.sc4j.base.exception.IgnoreException;
import org.fogofwarlabs.sc4j.scr.render.OverlayPass;
import org.fogofwarlabs.sc4j.scr.render.ui.InterfaceImage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Decodes the web interface's image bytes without a disk cache and converts decoded images to interface images.
 */
public final class WebImageUtils {
    /**
     * The shift of the alpha byte in a packed ARGB pixel.
     */
    private static final int ALPHA_SHIFT = 3 * Byte.SIZE;
    /**
     * The shift of the red byte in a packed ARGB pixel.
     */
    private static final int RED_SHIFT = Short.SIZE;

    /**
     * Decodes the first image with the first registered reader that recognizes the supplied bytes.
     *
     * @param contents the encoded image bytes
     * @return the decoded image, or {@code null} if no registered reader recognizes the bytes
     * @throws IOException if a recognized image cannot be decoded
     */
    public static @Nullable BufferedImage read(byte @NotNull [] contents) throws IOException {
        try (MemoryCacheImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(contents))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);

            if (!readers.hasNext()) {
                return null;
            }

            ImageReader reader = readers.next();

            try {
                ImageReadParam parameters = reader.getDefaultReadParam();

                reader.setInput(stream, true, true);

                try {
                    return reader.read(0, parameters);
                } catch (RuntimeException exception) {
                    throw new IIOException(exception.toString(), exception);
                }
            } finally {
                IgnoreException.execute(
                        reader::dispose
                );
            }
        }
    }

    /**
     * Converts a decoded image to the straight RGBA bytes of an interface image.
     *
     * @param image the decoded image
     * @return the interface image of the same size
     */
    public static @NotNull InterfaceImage toInterfaceImage(@NotNull BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] rgba = new byte[width * height * OverlayPass.RGBA_BYTES];

        for (int pixel = 0; pixel < width * height; pixel++) {
            int argb = image.getRGB(pixel % width, pixel / width);
            int offset = pixel * OverlayPass.RGBA_BYTES;

            rgba[offset]                          = (byte) (argb >>> RED_SHIFT);
            rgba[offset + OverlayPass.RGBA_GREEN] = (byte) (argb >>> Byte.SIZE);
            rgba[offset + OverlayPass.RGBA_BLUE]  = (byte) argb;
            rgba[offset + OverlayPass.RGBA_ALPHA] = (byte) (argb >>> ALPHA_SHIFT);
        }

        return new InterfaceImage(width, height, rgba);
    }

    private WebImageUtils() {
    }
}
