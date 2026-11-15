package org.fogofwarlabs.sc4j.scr.render;

import org.fogofwarlabs.sc4j.engine.client.host.ClientHost;
import org.fogofwarlabs.sc4j.engine.client.host.HostWindow;
import org.fogofwarlabs.sc4j.engine.client.host.WindowSettings;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Runs the GPU checks of the render tests on a hidden OpenGL 3.3 core context of the engine host and reads the drawn window back.
 */
public final class HiddenContextScenario {
    /**
     * The bit shifts of the red and green channels in a read-back {@code 0xRRGGBB} color.
     */
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;

    /**
     * Draws and checks on the hidden context.
     */
    public interface Check {
        /**
         * Runs the check while the hidden context is current.
         *
         * @throws IOException if an installed asset cannot be read
         */
        void run() throws IOException;
    }

    /**
     * Runs a check on a hidden window whose OpenGL 3.3 core context is current, then closes the host: the window, the context capabilities and
     * the windowing library.
     *
     * @param width  the window width in pixels
     * @param height the window height in pixels
     * @param title  the window title
     * @param check  the check
     * @throws IOException if the check cannot read an installed asset
     */
    public static void run(int width, int height, @NotNull String title, @NotNull Check check) throws IOException {
        try (ClientHost host = ClientHost.initialize()) {
            HostWindow window = host.createWindow(WindowSettings.builder(title, width, height).build());

            window.makeContextCurrent();
            host.createCapabilities();

            check.run();
        }
    }

    /**
     * Reads the window back as {@code 0xRRGGBB} colors.
     *
     * @param pixels the read-back buffer of the whole window
     * @param width  the window width in pixels
     * @param height the window height in pixels
     * @return the row-major colors, top row first
     */
    public static int @NotNull [] readColors(@NotNull ByteBuffer pixels, int width, int height) {
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

        int[] result = new int[width * height];

        for (int vertical = 0; vertical < height; vertical++) {
            for (int horizontal = 0; horizontal < width; horizontal++) {
                int source = ((height - 1 - vertical) * width + horizontal) * OverlayPass.RGBA_BYTES;

                result[vertical * width + horizontal] = readColor(pixels, source);
            }
        }

        return result;
    }

    /**
     * Reads a read-back RGBA pixel as a {@code 0xRRGGBB} color.
     *
     * @param pixels the read-back buffer
     * @param source the byte offset of the pixel
     * @return the pixel color
     */
    public static int readColor(@NotNull ByteBuffer pixels, int source) {
        return Byte.toUnsignedInt(pixels.get(source)) << RED_SHIFT |
               Byte.toUnsignedInt(pixels.get(source + OverlayPass.RGBA_GREEN)) << GREEN_SHIFT |
               Byte.toUnsignedInt(pixels.get(source + OverlayPass.RGBA_BLUE));
    }

    private HiddenContextScenario() {
    }
}
