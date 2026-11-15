package org.fogofwarlabs.sc4j.scr.render;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrResourceProvider;
import org.fogofwarlabs.sc4j.scr.render.hd.HdRenderAssets;
import org.fogofwarlabs.sc4j.scr.render.hd.HdSceneRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Checks that the view-aligned fog texture of the classic and HD backends survives extreme zoom-out with a view far larger than the map, zoom-in with
 * a fractional camera, a camera off the map, window resizes, a view that grows after a smaller one without writing past the previous upload's texel
 * count and a window at the client's minimum zoom {@code 1/32} within the context's target size.
 */
@RequiresInstallation
final class FogViewTest {
    /**
     * The width and height of the hidden window and of the read-back view in pixels.
     */
    private static final int WINDOW_SIZE = 128;
    /**
     * The Jungle tileset and a tile identifier of it (CV5 group {@code 2}, megatile {@code 0}).
     */
    private static final int JUNGLE_TILESET = 4;
    private static final int TILE = 0x20;
    private static final int MAP_TILES = 4;
    /**
     * The unexplored tile column; the rest is visible.
     */
    private static final int UNEXPLORED_COLUMN = 3;
    /**
     * Tile flags of a tile neither visible nor explored by any player.
     */
    private static final int UNEXPLORED_FLAGS = 0xFFFF;
    private static final int FIRST_PLAYER_VISION = 1;

    /**
     * The {@link #VIEWS} columns.
     */
    private static final int VIEW_FRAMEBUFFER_WIDTH = 0;
    private static final int VIEW_FRAMEBUFFER_HEIGHT = 1;
    private static final int VIEW_CAMERA_X = 2;
    private static final int VIEW_CAMERA_Y = 3;
    private static final int VIEW_SCALE = 4;
    /**
     * The divisor of the {@link #VIEW_SCALE} column.
     */
    private static final float VIEW_SCALE_DIVISOR = 1000.0f;
    /**
     * {@code {framebuffer width, framebuffer height, camera x, camera y, scale * 1000}}; {@code 32} is about the client minimum {@code 1/32}.
     */
    private static final int @NotNull [] @NotNull [] VIEWS = {
            {WINDOW_SIZE, WINDOW_SIZE, 0, 0, 1000},
            {512, 512, -2000, -2000, 50},
            {1024, 256, 0, 0, 500},
            {64, 64, 60, 33, 8000},
            {256, 256, -4000, -4000, 32},
            {1280, 800, -12000, -8000, 31},
            {WINDOW_SIZE, WINDOW_SIZE, 5000, 5000, 1000},
            {300, 700, -17, 9, 250},
            };

    /**
     * The half extent of the square sampled by {@link #calculateGreenAverage}.
     */
    private static final int SAMPLE_RADIUS = 4;
    private static final int SAMPLE_COUNT = (2 * SAMPLE_RADIUS) * (2 * SAMPLE_RADIUS);
    /**
     * The green byte of a read-back pixel in its native-order integer.
     */
    private static final int GREEN_SHIFT = 8;
    private static final int BYTE_MASK = 0xFF;
    /**
     * The sampled centers of visible tile {@code 0} and unexplored tile {@code 3} in the first view, and the brightness margin between them.
     */
    private static final int VISIBLE_SAMPLE_X = 16;
    private static final int UNEXPLORED_SAMPLE_X = 112;
    private static final int SAMPLE_Y = 64;
    private static final int VISIBILITY_MARGIN = 4;
    /**
     * The passes over {@link #VIEWS}.
     */
    private static final int VIEW_ROUNDS = 2;

    /**
     * The system property that enables the GPU check and the message of a skipped check.
     */
    private static final @NotNull String RENDER_SMOKE_PROPERTY = "sc4j.render.smoke";
    private static final @NotNull String RENDER_SMOKE_MESSAGE = "Pass -Psc4j.render.smoke=true to run the GPU check.";

    private static int calculateGreenAverage(int @NotNull [] frame, int positionX, int positionY) {
        int sum = 0;

        for (int offsetX = -SAMPLE_RADIUS; offsetX < SAMPLE_RADIUS; offsetX++) {
            for (int offsetY = -SAMPLE_RADIUS; offsetY < SAMPLE_RADIUS; offsetY++) {
                sum += frame[(WINDOW_SIZE - 1 - (positionY + offsetY)) * WINDOW_SIZE + positionX + offsetX] >>> GREEN_SHIFT & BYTE_MASK;
            }
        }

        return sum / SAMPLE_COUNT;
    }

    private static int @Nullable [] renderView(@NotNull WorldRenderer world, @NotNull SceneRenderer scene,
                                               int @NotNull [] view,
                                               @NotNull ByteBuffer pixels) throws IOException {
        world.beginFrame(view[VIEW_FRAMEBUFFER_WIDTH],
                         view[VIEW_FRAMEBUFFER_HEIGHT],
                         view[VIEW_FRAMEBUFFER_WIDTH],
                         view[VIEW_FRAMEBUFFER_HEIGHT],
                         view[VIEW_CAMERA_X],
                         view[VIEW_CAMERA_Y],
                         view[VIEW_SCALE] / VIEW_SCALE_DIVISOR);
        scene.drawTerrain(index -> TILE);
        scene.drawFog(index -> index % MAP_TILES == UNEXPLORED_COLUMN ? UNEXPLORED_FLAGS : 0, FIRST_PLAYER_VISION);
        scene.finishScene();
        world.endFrame();

        if (view[VIEW_FRAMEBUFFER_WIDTH] != WINDOW_SIZE || view[VIEW_FRAMEBUFFER_HEIGHT] != WINDOW_SIZE) {
            return null;
        }

        glReadPixels(0, 0, WINDOW_SIZE, WINDOW_SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

        int[] result = new int[WINDOW_SIZE * WINDOW_SIZE];

        for (int index = 0; index < result.length; index++) {
            result[index] = pixels.getInt(index * OverlayPass.RGBA_BYTES);
        }

        return result;
    }


    @Test
    void fog_survives_zoom_and_resize() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        HiddenContextScenario.run(WINDOW_SIZE, WINDOW_SIZE, "Fog view verification", () -> {
            int[] tiles = new int[MAP_TILES * MAP_TILES];

            Arrays.fill(tiles, TILE);

            try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); WorldRenderer world = new WorldRenderer();
                 ClassicSceneRenderer classic = new ClassicSceneRenderer(world, ScrResourceProvider.borrow(storage), JUNGLE_TILESET)) {
                world.loadMap(ScrResourceProvider.borrow(storage), JUNGLE_TILESET, MAP_TILES, MAP_TILES, tiles);

                ByteBuffer pixels = MemoryUtil.memAlloc(WINDOW_SIZE * WINDOW_SIZE * OverlayPass.RGBA_BYTES);
                HdSceneRenderer hdScene = new HdSceneRenderer(world, HdRenderAssets.load(ScrResourceProvider.borrow(storage), JUNGLE_TILESET,
                                                                                         HdRenderAssets.Quality.HD2));

                try {
                    for (SceneRenderer scene : new SceneRenderer[]{classic, hdScene}) {
                        int[] first = null;

                        for (int round = 0; round < VIEW_ROUNDS; round++) {
                            for (int[] view : VIEWS) {
                                int[] frame = renderView(world, scene, view, pixels);

                                if (frame != null && first == null) {
                                    first = frame;
                                }
                            }
                        }

                        int[] again = renderView(world, scene, VIEWS[0], pixels);

                        Assertions.assertNotNull(first);
                        Assertions.assertNotNull(again);

                        Assertions.assertArrayEquals(first, again, scene.getClass().getSimpleName() + ": the first view after zoom and resize");
                        // Visible tile 0 is brighter than unexplored tile 3 on the same terrain.
                        Assertions.assertTrue(calculateGreenAverage(again, VISIBLE_SAMPLE_X, SAMPLE_Y) >
                                              calculateGreenAverage(again, UNEXPLORED_SAMPLE_X, SAMPLE_Y) + VISIBILITY_MARGIN,
                                              scene.getClass().getSimpleName() + ": visible " +
                                              calculateGreenAverage(again, VISIBLE_SAMPLE_X, SAMPLE_Y) + ", unexplored " +
                                              calculateGreenAverage(again, UNEXPLORED_SAMPLE_X, SAMPLE_Y));
                    }

                    Assertions.assertEquals(GL_NO_ERROR, glGetError());
                } finally {
                    hdScene.close();
                    MemoryUtil.memFree(pixels);
                }
            }
        });
    }
}
