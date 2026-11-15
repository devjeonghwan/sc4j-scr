package org.fogofwarlabs.sc4j.scr.render;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrResourceProvider;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.IntUnaryOperator;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Checks that the fog brightness falloff across a visible/unexplored edge spans the expected {@code 162} screen pixels from {@code 10%} to
 * {@code 90%} at a scale of {@code 2.25} screen pixels per game pixel.
 */
@RequiresInstallation
final class FogProfileTest {
    private static final int WIDTH = 1152;
    private static final int HEIGHT = 128;
    private static final float SCALE = 2.25f;
    /**
     * Classic graphics render the world at one target pixel per game pixel and resize it to the window. The profile is read from the world target and
     * converted to screen pixels; {@link WorldRenderer#setRenderScaleLimit(int)} caps the render scale at the asset scale.
     */
    private static final int TARGET_WIDTH = Math.round(WIDTH / SCALE);
    private static final int TARGET_HEIGHT = Math.round(HEIGHT / SCALE);

    /**
     * The Jungle tileset and a tile identifier of it (CV5 group {@code 2}, megatile {@code 0}).
     */
    private static final int JUNGLE_TILESET = 4;
    private static final int TILE = 0x20;
    /**
     * The width of a tile in game pixels.
     */
    private static final int TILE_SIZE = 32;
    private static final int MAP_COLUMNS = 16;
    private static final int MAP_ROWS = 4;
    /**
     * Tiles left of this column are unexplored, the rest visible.
     */
    private static final int FOG_EDGE_COLUMN = 8;
    /**
     * Tile flags of a tile neither visible nor explored by any player.
     */
    private static final int UNEXPLORED_FLAGS = 0xFFFF;
    private static final int FIRST_PLAYER_VISION = 1;

    /**
     * The Rec. 601 luma weights of the red, green and blue channels.
     */
    private static final double LUMA_RED = 0.299;
    private static final double LUMA_GREEN = 0.587;
    private static final double LUMA_BLUE = 0.114;
    /**
     * Keeps the per-column ratio defined over black columns.
     */
    private static final double BRIGHTNESS_EPSILON = 1e-6;
    /**
     * The fogged-to-visible brightness ratios whose crossings bound the falloff width.
     */
    private static final double LOW_BRIGHTNESS_LEVEL = 0.1;
    private static final double HIGH_BRIGHTNESS_LEVEL = 0.9;
    /**
     * The expected falloff width in screen pixels and its tolerance, and the tolerance of the falloff center around the tile edge.
     */
    private static final int FALLOFF_WIDTH = 162;
    private static final int FALLOFF_WIDTH_TOLERANCE = 16;
    private static final int FALLOFF_CENTER_TOLERANCE = 24;

    /**
     * The system property that enables the GPU check and the message of a skipped check.
     */
    private static final @NotNull String RENDER_SMOKE_PROPERTY = "sc4j.render.smoke";
    private static final @NotNull String RENDER_SMOKE_MESSAGE = "Pass -Psc4j.render.smoke=true to run the GPU check.";

    private static double findBrightnessCrossing(double @NotNull [] fogged, double @NotNull [] visible, double level) {
        for (int column = 1; column < fogged.length; column++) {
            double previous = fogged[column - 1] / visible[column - 1];
            double current = fogged[column] / visible[column];

            if (previous < level && current >= level) {
                return column - 1 + (level - previous) / (current - previous);
            }
        }

        return Double.NaN;
    }

    private static double @NotNull [] readBrightnessProfile(@NotNull WorldRenderer world, @NotNull ClassicSceneRenderer scene,
                                                            @NotNull IntUnaryOperator flags,
                                                            @NotNull ByteBuffer pixels) throws IOException {
        world.beginFrame(WIDTH, HEIGHT, WIDTH, HEIGHT, 0, 0, SCALE);
        scene.drawTerrain(index -> TILE);
        scene.drawFog(flags, FIRST_PLAYER_VISION);
        scene.finishScene();
        world.endFrame();

        // Read the world framebuffer instead of the window: the operating system may clamp a hidden window to the screen
        // work area (seen at 604 pixels wide), which would cut the 1152-pixel profile.
        glBindFramebuffer(GL_READ_FRAMEBUFFER, world.getWorldFramebuffer());
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        glReadPixels(0, 0, TARGET_WIDTH, TARGET_HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);

        double[] result = new double[TARGET_WIDTH];

        for (int column = 0; column < TARGET_WIDTH; column++) {
            double sum = 0;

            for (int row = 0; row < TARGET_HEIGHT; row++) {
                int offset = (row * TARGET_WIDTH + column) * OverlayPass.RGBA_BYTES;

                sum += LUMA_RED * Byte.toUnsignedInt(pixels.get(offset)) +
                       LUMA_GREEN * Byte.toUnsignedInt(pixels.get(offset + OverlayPass.RGBA_GREEN)) +
                       LUMA_BLUE * Byte.toUnsignedInt(pixels.get(offset + OverlayPass.RGBA_BLUE));
            }

            // The same terrain is rendered with and without fog, so the per-column ratio removes the terrain texture.
            result[column] = sum / TARGET_HEIGHT + BRIGHTNESS_EPSILON;
        }

        return result;
    }


    @Test
    void fog_falloff_matches_the_measured_width_centered_on_the_tile_edge() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        HiddenContextScenario.run(WIDTH, HEIGHT, "Fog profile verification", () -> {
            int[] tiles = new int[MAP_COLUMNS * MAP_ROWS];

            Arrays.fill(tiles, TILE);

            try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); WorldRenderer world = new WorldRenderer();
                 ClassicSceneRenderer classic = new ClassicSceneRenderer(world, ScrResourceProvider.borrow(storage), JUNGLE_TILESET)) {
                world.loadMap(ScrResourceProvider.borrow(storage), JUNGLE_TILESET, MAP_COLUMNS, MAP_ROWS, tiles);

                ByteBuffer pixels = MemoryUtil.memAlloc(WIDTH * HEIGHT * OverlayPass.RGBA_BYTES);

                try {
                    double[] visible = readBrightnessProfile(world, classic, index -> 0, pixels);
                    double[] fogged = readBrightnessProfile(world, classic,
                                                            index -> index % MAP_COLUMNS < FOG_EDGE_COLUMN ? UNEXPLORED_FLAGS : 0,
                                                            pixels);
                    double low = findBrightnessCrossing(fogged, visible, LOW_BRIGHTNESS_LEVEL) * WIDTH / TARGET_WIDTH;
                    double high = findBrightnessCrossing(fogged, visible, HIGH_BRIGHTNESS_LEVEL) * WIDTH / TARGET_WIDTH;
                    double width = high - low;
                    double edge = FOG_EDGE_COLUMN * TILE_SIZE * SCALE;

                    System.out.println("FOG PROFILE: 10% at " + low + ", 90% at " + high + ", width " + width);
                    Assertions.assertEquals(FALLOFF_WIDTH,
                                            width,
                                            FALLOFF_WIDTH_TOLERANCE,
                                            "10%..90% falloff width in screen pixels (low " + low + ", high " + high + ")");
                    Assertions.assertEquals(edge, (low + high) / 2, FALLOFF_CENTER_TOLERANCE, "the falloff is centered on the tile edge");
                } finally {
                    MemoryUtil.memFree(pixels);
                }
            }
        });
    }
}
