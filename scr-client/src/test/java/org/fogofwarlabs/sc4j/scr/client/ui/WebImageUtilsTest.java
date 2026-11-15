package org.fogofwarlabs.sc4j.scr.client.ui;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Verifies memory-only image decoding independently of the process-wide ImageIO cache preference.
 */
final class WebImageUtilsTest {
    private static final int IMAGE_SIZE = 2;
    private static final int TRUNCATED_LENGTH = 16;
    private static final int CACHE_EVENT_TIMEOUT_SECONDS = 1;
    private static final int @NotNull [] PIXELS = {0xFF11AA22, 0x80445566, 0x00778899, 0xFFABCDEF};
    private static final byte @NotNull [] UNRECOGNIZED_CONTENTS = {1, 2, 3, 4};

    @Test
    void read_should_preserve_pixels_without_cache_files_and_reject_truncated_images(@TempDir @NotNull Path directory) throws Exception {
        BufferedImage original = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);

        original.setRGB(0, 0, IMAGE_SIZE, IMAGE_SIZE, PIXELS, 0, IMAGE_SIZE);

        ByteArrayOutputStream contents = new ByteArrayOutputStream();

        try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(contents)) {
            Assertions.assertTrue(ImageIO.write(original, "png", output));
        }

        byte[] encoded = contents.toByteArray();
        boolean previousUseCache = ImageIO.getUseCache();
        File previousDirectory = ImageIO.getCacheDirectory();

        try (WatchService events = directory.getFileSystem().newWatchService()) {
            directory.register(events, StandardWatchEventKinds.ENTRY_CREATE);
            ImageIO.setCacheDirectory(directory.toFile());
            ImageIO.setUseCache(true);

            try {
                BufferedImage decoded = WebImageUtils.read(encoded);

                Assertions.assertNotNull(decoded);
                Assertions.assertEquals(IMAGE_SIZE, decoded.getWidth());
                Assertions.assertEquals(IMAGE_SIZE, decoded.getHeight());
                Assertions.assertArrayEquals(PIXELS, decoded.getRGB(0, 0, IMAGE_SIZE, IMAGE_SIZE, null, 0, IMAGE_SIZE));
                Assertions.assertNull(WebImageUtils.read(UNRECOGNIZED_CONTENTS));
                Assertions.assertThrows(IOException.class, () -> WebImageUtils.read(Arrays.copyOf(encoded, TRUNCATED_LENGTH)));
                Assertions.assertNull(events.poll(CACHE_EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            } finally {
                ImageIO.setUseCache(previousUseCache);
                ImageIO.setCacheDirectory(previousDirectory);
            }
        }
    }
}
