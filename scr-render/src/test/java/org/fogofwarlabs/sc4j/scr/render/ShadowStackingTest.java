package org.fogofwarlabs.sc4j.scr.render;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrResourceProvider;
import org.fogofwarlabs.sc4j.scr.data.type.constant.ImageIds;
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
import java.util.Map;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Checks shadow accumulation, warp-flash blending and warp-frame dimming in classic and HD rendering.
 */
@RequiresInstallation
final class ShadowStackingTest {
    /**
     * The system property that enables the GPU checks and the message of a skipped check.
     */
    private static final @NotNull String RENDER_SMOKE_PROPERTY = "sc4j.render.smoke";
    private static final @NotNull String RENDER_SMOKE_MESSAGE = "Pass -Psc4j.render.smoke=true to run the GPU check.";
    /**
     * The width, height and title of the hidden window.
     */
    private static final int WIDTH = 96;
    private static final int HEIGHT = 80;
    private static final @NotNull String WINDOW_TITLE = "Shadow stacking verification";
    /**
     * Two screen pixels per world pixel in the HD frames.
     */
    private static final int HD_SCALE = 2;
    /**
     * The probed screen pixel, where the classic images are drawn.
     */
    private static final int PROBE_X = WIDTH / 2;
    private static final int PROBE_Y = HEIGHT / 2;
    /**
     * The world position of the probed pixel at two screen pixels per world pixel.
     */
    private static final int HD_PROBE_X = PROBE_X / HD_SCALE;
    private static final int HD_PROBE_Y = PROBE_Y / HD_SCALE;
    /**
     * The background, black and white as {@code 0xRRGGBB} colors, and the alpha of an opaque {@code 0xRRGGBBAA} color.
     */
    private static final int BACKGROUND = 0xC86432;
    private static final int BLACK = 0x000000;
    private static final int WHITE = 0xFFFFFF;
    private static final int OPAQUE_ALPHA = 0xFF;
    /**
     * The bit shifts of the channels in a {@code 0xRRGGBB} color, the mask of one channel and its largest value as a float divisor.
     */
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int BLUE_SHIFT = 0;
    private static final int BYTE_MASK = 0xFF;
    private static final float CHANNEL_MAXIMUM = 255.0f;

    /**
     * The Jungle tileset, a tile identifier of it (CV5 group {@code 2}, megatile {@code 0}) and the width and height of the map in tiles.
     */
    private static final int JUNGLE_TILESET = 4;
    private static final int TILE = 0x20;
    private static final int MAP_TILES = 4;

    /**
     * Images: the Nuke Target Dot and the Pylon shadow.
     */
    private static final int NUKE_TARGET_DOT = 233;
    private static final int PYLON_SHADOW = 191;
    /**
     * The frame count of the Warp Texture's SD anim.
     */
    private static final int WARP_FRAME_COUNT = 21;

    /**
     * The SD coverage of opaque pixels and of the Psi Field frame.
     */
    private static final int OPAQUE_COVERAGE = 255;
    private static final int PSI_FIELD_COVERAGE = 72;
    /**
     * The darkening of a solid shadow layer at full sprite alpha.
     */
    private static final float SHADOW_ALPHA = 0.5f;
    /**
     * The percentage of a whole.
     */
    private static final int PERCENT = 100;
    /**
     * The tolerated channel difference and the tolerated brighten alpha difference.
     */
    private static final int CHANNEL_TOLERANCE = 1;
    private static final float ALPHA_TOLERANCE = 1e-6f;

    /**
     * The first warp flash data value of the flash range: alpha {@code 1}.
     */
    private static final int FIRST_FLASH_DATA = 48;
    /**
     * The classic checks: the stacked shadow layers, the drawn flash data values and layers, and the Psi Field shadows over a flash.
     */
    private static final int MAXIMUM_SHADOW_LAYERS = 8;
    private static final int @NotNull [] FLASH_DATA_VALUES = {40, 48, 50, 52, 56, 60, 62, 63, 70};
    private static final int MAXIMUM_FLASH_LAYERS = 2;
    private static final int FLASH_SHADOW_LAYERS = 14;
    /**
     * The HD checks: the stacked Warp Texture layers, the stacked Psi Field layers, the minimum ground red, the factor from a darkening to its alpha,
     * the tolerated layer difference, the minimum checked pixels, the typical darkening percent and its tolerance, and the drawn flash data values.
     */
    private static final int MAXIMUM_HD_SHADOW_LAYERS = 6;
    private static final int HD_PSI_FIELD_LAYERS = 8;
    private static final int MINIMUM_GROUND_RED = 64;
    private static final int DARKENING_TO_ALPHA = 2;
    private static final int HD_LAYER_TOLERANCE = 3;
    private static final int MINIMUM_CHECKED_PIXELS = 300;
    private static final int TYPICAL_DARKENING_PERCENT = 5;
    private static final int TYPICAL_DARKENING_TOLERANCE = 1;
    private static final int @NotNull [] HD_FLASH_DATA_VALUES = {50, 56, 60};
    /**
     * The expected brighten alpha of each drawn flash data value: {@code 1 - sqrt(1 - (63 - data) / 15)} from {@code 48} to {@code 63}, and
     * {@code 0} outside that range.
     */
    private static final @NotNull Map<Integer, Float> EXPECTED_BRIGHTEN_ALPHAS = Map.of(40, 0f,
                                                                                        48, 1f,
                                                                                        50, 0.63485163f,
                                                                                        52, 0.48360223f,
                                                                                        56, 0.26970327f,
                                                                                        60, 0.10557282f,
                                                                                        62, 0.03390819f,
                                                                                        63, 0f,
                                                                                        70, 0f);

    /**
     * The warp frame of a sprite without a warping primary image, the {@code images.rel} relation type of shadow images, an {@code images.rel}
     * relation that is not a shadow and a sampled warp frame.
     */
    private static final int NO_WARP_FRAME = -1;
    private static final int SHADOW_RELATION = 8;
    private static final int OTHER_RELATION = 1;
    private static final int SAMPLE_WARP_FRAME = 7;
    /**
     * The classic and HD warp frames of the dimming checks, the minimum HD darkening and its tolerance.
     */
    private static final int @NotNull [] WARP_FRAMES = {-1, 0, 7, 14, 21, 40};
    private static final int @NotNull [] HD_WARP_FRAMES = {0, 7, 14};
    private static final int MINIMUM_HD_DARKENING = 20;
    private static final double HD_DIMMING_TOLERANCE = 1.5;
    /**
     * The expected classic shadow alpha of each unstacked warp frame: the frame over the frame count, at most {@code 1}, and {@code 1} without a
     * warping primary image.
     */
    private static final @NotNull Map<Integer, Float> EXPECTED_WARP_ALPHAS = Map.of(-1, 1f,
                                                                                    0, 0f,
                                                                                    7, 0.33333334f,
                                                                                    14, 0.6666667f,
                                                                                    21, 1f,
                                                                                    40, 1f);

    private interface Drawing {
        void draw() throws IOException;
    }

    /**
     * Calculates the color after {@code layers} shadows of sprite alpha {@code alpha} ({@code 0..255}), each blend stored as {@code 8} bits.
     *
     * @param color    the color under the shadows
     * @param alpha    the sprite alpha
     * @param layers   the number of shadow layers
     * @param stacking whether Shadow Stacking is enabled
     * @return the shadowed color
     */
    private static int calculateExpectedShadow(int color, int alpha, int layers, boolean stacking) {
        float spriteAlpha = alpha / CHANNEL_MAXIMUM;

        if (stacking) {
            int result = color;

            for (int layer = 0; layer < layers; layer++) {
                result = blendColors(result, BLACK, SHADOW_ALPHA * spriteAlpha);
            }

            return result;
        }

        int effect = 0;

        for (int layer = 0; layer < layers; layer++) {
            effect = Math.round(CHANNEL_MAXIMUM * (effect / CHANNEL_MAXIMUM * (1 - spriteAlpha) + spriteAlpha));
        }

        float darkening = 1 - SHADOW_ALPHA * effect / CHANNEL_MAXIMUM;

        return calculateShadowChannel(color, RED_SHIFT, darkening) << RED_SHIFT |
               calculateShadowChannel(color, GREEN_SHIFT, darkening) << GREEN_SHIFT |
               calculateShadowChannel(color, BLUE_SHIFT, darkening);
    }

    private static int calculateExpectedBrighten(int color, int data, int layers) {
        float strength = OPAQUE_COVERAGE / CHANNEL_MAXIMUM * EXPECTED_BRIGHTEN_ALPHAS.get(data);
        int result = color;

        for (int layer = 0; layer < layers; layer++) {
            result = blendColors(result, WHITE, strength);
        }

        return result;
    }

    private static int blendColors(int destination, int source, float alpha) {
        int result = 0;

        for (int shift = RED_SHIFT; shift >= 0; shift -= Byte.SIZE) {
            result |= Math.round((source >>> shift & BYTE_MASK) * alpha + (destination >>> shift & BYTE_MASK) * (1 - alpha)) << shift;
        }

        return result;
    }

    private static int calculateShadowChannel(int color, int shift, float factor) {
        return Math.round((color >>> shift & BYTE_MASK) * factor);
    }

    /**
     * Finds the pixel of a coverage value nearest to the canvas center.
     *
     * @param coverage the frame coverage
     * @param width    the frame width
     * @param height   the frame height
     * @param value    the coverage value
     * @return {@code {x, y}}, or {@code null} if no pixel has the value
     */
    private static int @Nullable [] findCoveragePixel(byte @NotNull [] coverage, int width, int height, int value) {
        int[] best = null;
        long bestDistance = Long.MAX_VALUE;

        for (int pixelY = 0; pixelY < height; pixelY++) {
            for (int pixelX = 0; pixelX < width; pixelX++) {
                if (Byte.toUnsignedInt(coverage[pixelY * width + pixelX]) != value) {
                    continue;
                }

                long distance = (long) (pixelX - width / 2) * (pixelX - width / 2) + (long) (pixelY - height / 2) * (pixelY - height / 2);

                if (distance < bestDistance) {
                    bestDistance = distance;
                    best         = new int[]{pixelX, pixelY};
                }
            }
        }

        return best;
    }

    /**
     * Draws the classic world over the background color and returns the probed screen pixel.
     *
     * @param renderer the world renderer
     * @param drawing  the drawing between the background and the world resolve
     * @param pixels   the read-back buffer of the whole window
     * @param probe    the probed screen pixel {@code {x, y}}
     * @return the probed color as {@code 0xRRGGBB}
     * @throws IOException if an image cannot be loaded
     */
    private static int renderClassicFrame(@NotNull WorldRenderer renderer,
                                          @NotNull Drawing drawing,
                                          @NotNull ByteBuffer pixels,
                                          int @NotNull [] probe) throws IOException {
        renderer.beginFrame(WIDTH, HEIGHT, WIDTH, HEIGHT, 0, 0, 1);
        renderer.fillRectangle(0, 0, WIDTH, HEIGHT, BACKGROUND << Byte.SIZE | OPAQUE_ALPHA);
        drawing.draw();
        renderer.finishWorld();
        renderer.endFrame();
        glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

        int source = ((HEIGHT - 1 - probe[1]) * WIDTH + probe[0]) * OverlayPass.RGBA_BYTES;

        return HiddenContextScenario.readColor(pixels, source);
    }

    /**
     * Draws the HD scene at two screen pixels per world pixel and returns every screen pixel.
     *
     * @param world   the world renderer
     * @param scene   the HD scene backend
     * @param drawing the scene drawing
     * @param pixels  the read-back buffer of the whole window
     * @return the row-major screen colors as {@code 0xRRGGBB}, top row first
     * @throws IOException if an image cannot be loaded
     */
    private static int @NotNull [] renderHdFrame(@NotNull WorldRenderer world, @NotNull HdSceneRenderer scene,
                                                 @NotNull Drawing drawing,
                                                 @NotNull ByteBuffer pixels) throws IOException {
        world.beginFrame(WIDTH, HEIGHT, WIDTH, HEIGHT, 0, 0, HD_SCALE);

        try {
            drawing.draw();
        } finally {
            scene.finishScene();
            world.endFrame();
        }

        return HiddenContextScenario.readColors(pixels, WIDTH, HEIGHT);
    }

    private static void assertColorChannels(int expected, int actual, @NotNull String message) {
        for (int shift = 0; shift <= RED_SHIFT; shift += Byte.SIZE) {
            Assertions.assertEquals(expected >>> shift & BYTE_MASK,
                                    actual >>> shift & BYTE_MASK,
                                    CHANNEL_TOLERANCE,
                                    message + ": expected " + Integer.toHexString(expected) + " actual " + Integer.toHexString(actual));
        }
    }

    @Test
    void classic_shadows_and_flashes_stack_as_sd_graphics() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        HiddenContextScenario.run(WIDTH, HEIGHT, WINDOW_TITLE, () -> {
            try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); WorldRenderer renderer = new WorldRenderer()) {
                renderer.loadMap(ScrResourceProvider.borrow(storage), JUNGLE_TILESET, 1, 1, new int[]{TILE});

                RenderAssets assets = RenderAssets.load(ScrResourceProvider.borrow(storage), JUNGLE_TILESET);
                ByteBuffer pixels = MemoryUtil.memAlloc(WIDTH * HEIGHT * OverlayPass.RGBA_BYTES);

                try {
                    // The canvas pixel under the probed screen pixel for an image drawn at the probe.
                    int[] probe = {PROBE_X, PROBE_Y};
                    byte[] warpCoverage = assets.decodeCoverage(SceneRenderer.WARP_TEXTURE_IMAGE, 0);
                    ClassicFrame warp = assets.decodeImage(SceneRenderer.WARP_TEXTURE_IMAGE, 0);

                    Assertions.assertEquals(OPAQUE_COVERAGE,
                                            Byte.toUnsignedInt(warpCoverage[warp.getHeight() / 2 * warp.getWidth() + warp.getWidth() / 2]));

                    byte[] psiCoverage = assets.decodeCoverage(ImageIds.PSI_FIELD1_RIGHT_UPPER, 0);
                    ClassicFrame psiField = assets.decodeImage(ImageIds.PSI_FIELD1_RIGHT_UPPER, 0);
                    int[] psiFieldPixel = findCoveragePixel(psiCoverage, psiField.getWidth(), psiField.getHeight(), PSI_FIELD_COVERAGE);

                    Assertions.assertNotNull(psiFieldPixel, "the SD Psi Field frame has alpha 72");

                    for (int value : psiCoverage) {
                        Assertions.assertTrue((value & BYTE_MASK) == 0 || (value & BYTE_MASK) == PSI_FIELD_COVERAGE,
                                              "the SD Psi Field alpha is 0 or 72");
                    }

                    float psiFieldX = probe[0] + psiField.getWidth() / 2 - psiFieldPixel[0];
                    float psiFieldY = probe[1] + psiField.getHeight() / 2 - psiFieldPixel[1];

                    for (boolean stacking : new boolean[]{true, false}) {
                        renderer.setShadowStacking(stacking);

                        for (int layers = 0; layers <= MAXIMUM_SHADOW_LAYERS; layers++) {
                            int count = layers;
                            int warpColor = renderClassicFrame(renderer,
                                                               () -> {
                                                                   for (int layer = 0; layer < count; layer++) {
                                                                       renderer.drawShadowImage(SceneRenderer.WARP_TEXTURE_IMAGE,
                                                                                                0,
                                                                                                PROBE_X,
                                                                                                PROBE_Y,
                                                                                                false);
                                                                   }
                                                               },
                                                               pixels,
                                                               probe);

                            assertColorChannels(calculateExpectedShadow(BACKGROUND, OPAQUE_COVERAGE, layers, stacking),
                                                warpColor,
                                                "Warp Texture shadows, stacking " + stacking + ", layers " + layers);

                            int psiColor = renderClassicFrame(renderer,
                                                              () -> {
                                                                  for (int layer = 0; layer < count; layer++) {
                                                                      renderer.drawShadowImage(ImageIds.PSI_FIELD1_RIGHT_UPPER,
                                                                                               0,
                                                                                               psiFieldX,
                                                                                               psiFieldY,
                                                                                               false);
                                                                  }
                                                              },
                                                              pixels,
                                                              probe);

                            assertColorChannels(calculateExpectedShadow(BACKGROUND, PSI_FIELD_COVERAGE, layers, stacking),
                                                psiColor,
                                                "Psi Field shadows, stacking " + stacking + ", layers " + layers);
                        }
                    }

                    // Draw function 17 over the color: the dot's coverage is opaque at its canvas center.
                    ClassicFrame dot = assets.decodeImage(NUKE_TARGET_DOT, 0);
                    byte[] dotCoverage = assets.decodeCoverage(NUKE_TARGET_DOT, 0);

                    Assertions.assertEquals(OPAQUE_COVERAGE,
                                            Byte.toUnsignedInt(dotCoverage[dot.getHeight() / 2 * dot.getWidth() + dot.getWidth() / 2]));

                    for (int data : FLASH_DATA_VALUES) {
                        for (int layers = 1; layers <= MAXIMUM_FLASH_LAYERS; layers++) {
                            int count = layers;
                            int color = renderClassicFrame(renderer,
                                                           () -> {
                                                               for (int layer = 0; layer < count; layer++) {
                                                                   renderer.drawBrightenImage(NUKE_TARGET_DOT, 0, data, PROBE_X, PROBE_Y, false);
                                                               }
                                                           },
                                                           pixels,
                                                           probe);

                            assertColorChannels(calculateExpectedBrighten(BACKGROUND, data, layers),
                                                color,
                                                "warp flash data " + data + ", layers " + layers);
                        }
                    }

                    // A shadow over a warp flash darkens it, as the logo maps stack their dots under Psi Field shadows.
                    renderer.setShadowStacking(true);

                    int shadedFlash = renderClassicFrame(renderer,
                                                         () -> {
                                                             renderer.drawBrightenImage(NUKE_TARGET_DOT,
                                                                                        0,
                                                                                        FIRST_FLASH_DATA,
                                                                                        PROBE_X,
                                                                                        PROBE_Y,
                                                                                        false);

                                                             for (int layer = 0; layer < FLASH_SHADOW_LAYERS; layer++) {
                                                                 renderer.drawShadowImage(ImageIds.PSI_FIELD1_RIGHT_UPPER,
                                                                                          0,
                                                                                          psiFieldX,
                                                                                          psiFieldY,
                                                                                          false);
                                                             }
                                                         },
                                                         pixels,
                                                         probe);

                    assertColorChannels(calculateExpectedShadow(WHITE, PSI_FIELD_COVERAGE, FLASH_SHADOW_LAYERS, true),
                                        shadedFlash,
                                        "fourteen Psi Field shadows over a white dot");
                } finally {
                    MemoryUtil.memFree(pixels);
                }
            }
        });
    }

    @Test
    void hd_shadows_and_flashes_stack_as_hd_graphics() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        HiddenContextScenario.run(WIDTH, HEIGHT, WINDOW_TITLE, () -> {
            int[] tiles = new int[MAP_TILES * MAP_TILES];

            Arrays.fill(tiles, TILE);

            try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); WorldRenderer world = new WorldRenderer()) {
                world.loadMap(ScrResourceProvider.borrow(storage), JUNGLE_TILESET, MAP_TILES, MAP_TILES, tiles);
                world.setRenderScaleLimit(HdRenderAssets.Quality.HD2.getScale());

                HdSceneRenderer scene = new HdSceneRenderer(world, HdRenderAssets.load(ScrResourceProvider.borrow(storage), JUNGLE_TILESET,
                                                                                       HdRenderAssets.Quality.HD2));

                world.setShadowStacking(true);

                ByteBuffer pixels = MemoryUtil.memAlloc(WIDTH * HEIGHT * OverlayPass.RGBA_BYTES);

                try {
                    int probe = PROBE_Y * WIDTH + PROBE_X;
                    int[] ground = renderHdFrame(world, scene, () -> scene.drawTerrain(index -> TILE), pixels);

                    // Warp Texture: HD alpha 255, every layer halves the ground.
                    for (int layers = 1; layers <= MAXIMUM_HD_SHADOW_LAYERS; layers++) {
                        int count = layers;
                        int[] shaded = renderHdFrame(world, scene,
                                                     () -> {
                                                         scene.drawTerrain(index -> TILE);

                                                         for (int layer = 0; layer < count; layer++) {
                                                             scene.drawImage(SceneRenderer.WARP_TEXTURE_IMAGE,
                                                                             0,
                                                                             SceneRenderer.DRAW_FUNCTION_SHADOW,
                                                                             0,
                                                                             0,
                                                                             HD_PROBE_X,
                                                                             HD_PROBE_Y,
                                                                             false);
                                                         }
                                                     },
                                                     pixels);

                        assertColorChannels(calculateExpectedShadow(ground[probe], OPAQUE_COVERAGE, layers, true),
                                            shaded[probe],
                                            "HD Warp Texture shadows, layers " + layers);
                    }

                    // Psi Field: each layer darkens by half the HD alpha; over the frame the typical layer darkens by 5.1%
                    // (alpha 26), against 14.1% in SD.
                    int[] one = renderHdFrame(world, scene,
                                              () -> {
                                                  scene.drawTerrain(index -> TILE);
                                                  scene.drawImage(ImageIds.PSI_FIELD1_RIGHT_UPPER,
                                                                  0,
                                                                  SceneRenderer.DRAW_FUNCTION_SHADOW,
                                                                  0,
                                                                  0,
                                                                  HD_PROBE_X,
                                                                  HD_PROBE_Y,
                                                                  false);
                                              },
                                              pixels);
                    int[] eight = renderHdFrame(world, scene,
                                                () -> {
                                                    scene.drawTerrain(index -> TILE);

                                                    for (int layer = 0; layer < HD_PSI_FIELD_LAYERS; layer++) {
                                                        scene.drawImage(ImageIds.PSI_FIELD1_RIGHT_UPPER,
                                                                        0,
                                                                        SceneRenderer.DRAW_FUNCTION_SHADOW,
                                                                        0,
                                                                        0,
                                                                        HD_PROBE_X,
                                                                        HD_PROBE_Y,
                                                                        false);
                                                    }
                                                },
                                                pixels);
                    int[] ratioCounts = new int[PERCENT + 1];
                    int checked = 0;

                    for (int index = 0; index < WIDTH * HEIGHT; index++) {
                        int base = ground[index] >>> RED_SHIFT & BYTE_MASK;
                        int single = one[index] >>> RED_SHIFT & BYTE_MASK;

                        if (base < MINIMUM_GROUND_RED || single >= base) {
                            continue;
                        }

                        float alpha = DARKENING_TO_ALPHA * (1 - single / (float) base);
                        int shadowAlpha = Math.round(alpha * CHANNEL_MAXIMUM);
                        int expected = calculateExpectedShadow(ground[index], shadowAlpha, HD_PSI_FIELD_LAYERS, true) >>> RED_SHIFT & BYTE_MASK;

                        Assertions.assertEquals(expected,
                                                eight[index] >>> RED_SHIFT & BYTE_MASK,
                                                HD_LAYER_TOLERANCE,
                                                "HD Psi Field layers stack geometrically at pixel " + index);
                        ratioCounts[Math.round(PERCENT * (1 - single / (float) base))]++;
                        checked++;
                    }

                    Assertions.assertTrue(checked > MINIMUM_CHECKED_PIXELS, "the HD Psi Field covers the view: " + checked);

                    int typical = 0;

                    for (int percent = 0; percent < ratioCounts.length; percent++) {
                        if (ratioCounts[percent] > ratioCounts[typical]) {
                            typical = percent;
                        }
                    }

                    Assertions.assertEquals(TYPICAL_DARKENING_PERCENT,
                                            typical,
                                            TYPICAL_DARKENING_TOLERANCE,
                                            "the typical HD Psi Field layer darkens by 5%");

                    // Draw function 17: white at the alpha 1 - sqrt(1 - (63 - data) / 15).
                    int[] full = renderHdFrame(world, scene,
                                               () -> {
                                                   scene.drawTerrain(index -> TILE);
                                                   scene.drawImage(SceneRenderer.WARP_TEXTURE_IMAGE,
                                                                   0,
                                                                   SceneRenderer.DRAW_FUNCTION_WARP_FLASH,
                                                                   FIRST_FLASH_DATA,
                                                                   0,
                                                                   HD_PROBE_X,
                                                                   HD_PROBE_Y,
                                                                   false);
                                               },
                                               pixels);

                    for (int data : HD_FLASH_DATA_VALUES) {
                        int[] flash = renderHdFrame(world, scene,
                                                    () -> {
                                                        scene.drawTerrain(index -> TILE);
                                                        scene.drawImage(SceneRenderer.WARP_TEXTURE_IMAGE,
                                                                        0,
                                                                        SceneRenderer.DRAW_FUNCTION_WARP_FLASH,
                                                                        data,
                                                                        0,
                                                                        HD_PROBE_X,
                                                                        HD_PROBE_Y,
                                                                        false);
                                                    },
                                                    pixels);

                        Assertions.assertEquals(WHITE, full[probe], "data 48 draws white over the opaque warp texture");
                        assertColorChannels(calculateExpectedBrighten(ground[probe], data, 1), flash[probe], "HD warp flash data " + data);
                    }
                } finally {
                    MemoryUtil.memFree(pixels);
                    scene.close();
                }
            }
        });
    }

    @Test
    void unstacked_shadows_of_warping_sprites_dim_with_the_warp_frame() throws IOException {
        Assertions.assertEquals(1.0f, SceneRenderer.getShadowWarpAlpha(SHADOW_RELATION, NO_WARP_FRAME, WARP_FRAME_COUNT), "no warping primary image");
        Assertions.assertEquals(1.0f,
                                SceneRenderer.getShadowWarpAlpha(OTHER_RELATION, SAMPLE_WARP_FRAME, WARP_FRAME_COUNT),
                                "only images.rel shadow images are dimmed");
        Assertions.assertEquals(7 / 21.0f, SceneRenderer.getShadowWarpAlpha(SHADOW_RELATION, SAMPLE_WARP_FRAME, WARP_FRAME_COUNT), ALPHA_TOLERANCE);
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        HiddenContextScenario.run(WIDTH, HEIGHT, WINDOW_TITLE, () -> {
            int[] tiles = new int[MAP_TILES * MAP_TILES];

            Arrays.fill(tiles, TILE);

            try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); WorldRenderer renderer = new WorldRenderer()) {
                renderer.loadMap(ScrResourceProvider.borrow(storage), JUNGLE_TILESET, MAP_TILES, MAP_TILES, tiles);

                RenderAssets assets = RenderAssets.load(ScrResourceProvider.borrow(storage), JUNGLE_TILESET);

                Assertions.assertEquals(SHADOW_RELATION, assets.getImageRelationType(PYLON_SHADOW), "the Pylon shadow is an images.rel shadow image");
                Assertions.assertEquals(WARP_FRAME_COUNT, assets.getSdEntry(SceneRenderer.WARP_TEXTURE_IMAGE).getFrameCount());

                ClassicFrame shadow = assets.decodeImage(PYLON_SHADOW, 0);
                int[] point = findCoveragePixel(assets.decodeCoverage(PYLON_SHADOW, 0), shadow.getWidth(), shadow.getHeight(), OPAQUE_COVERAGE);

                Assertions.assertNotNull(point);

                int shadowX = PROBE_X + shadow.getWidth() / 2 - point[0];
                int shadowY = PROBE_Y + shadow.getHeight() / 2 - point[1];
                int[] probe = {PROBE_X, PROBE_Y};
                ByteBuffer pixels = MemoryUtil.memAlloc(WIDTH * HEIGHT * OverlayPass.RGBA_BYTES);

                try (ClassicSceneRenderer scene = new ClassicSceneRenderer(renderer, ScrResourceProvider.borrow(storage), JUNGLE_TILESET)) {
                    for (boolean stacking : new boolean[]{false, true}) {
                        renderer.setShadowStacking(stacking);

                        for (int warpFrame : WARP_FRAMES) {
                            int color = renderClassicFrame(renderer,
                                                           () -> {
                                                               scene.drawShadowImage(PYLON_SHADOW, 0, warpFrame, shadowX, shadowY, false);
                                                               scene.finishScene();
                                                           },
                                                           pixels,
                                                           probe);
                            float alpha = stacking ? 1 : EXPECTED_WARP_ALPHAS.get(warpFrame);

                            assertColorChannels(stacking ? calculateExpectedShadow(BACKGROUND, OPAQUE_COVERAGE, 1, true)
                                                         : calculateExpectedShadow(BACKGROUND, Math.round(CHANNEL_MAXIMUM * alpha), 1, false),
                                                color,
                                                "classic Pylon shadow, stacking " + stacking + ", warp frame " + warpFrame);
                        }
                    }
                } finally {
                    MemoryUtil.memFree(pixels);
                }

                // HD: the effect-target shadow darkens in proportion to the multiply alpha.
                renderer.setRenderScaleLimit(HdRenderAssets.Quality.HD2.getScale());
                renderer.setShadowStacking(false);

                HdSceneRenderer hdScene = new HdSceneRenderer(renderer, HdRenderAssets.load(ScrResourceProvider.borrow(storage), JUNGLE_TILESET,
                                                                                            HdRenderAssets.Quality.HD2));
                ByteBuffer hdPixels = MemoryUtil.memAlloc(WIDTH * HEIGHT * OverlayPass.RGBA_BYTES);

                try {
                    int[] ground = renderHdFrame(renderer, hdScene, () -> hdScene.drawTerrain(index -> TILE), hdPixels);
                    int[] full = renderHdFrame(renderer, hdScene,
                                               () -> {
                                                   hdScene.drawTerrain(index -> TILE);
                                                   hdScene.drawShadowImage(PYLON_SHADOW, 0, NO_WARP_FRAME, HD_PROBE_X, HD_PROBE_Y, false);
                                               },
                                               hdPixels);
                    int darkest = 0;

                    for (int index = 0; index < WIDTH * HEIGHT; index++) {
                        if ((ground[index] >>> GREEN_SHIFT & BYTE_MASK) - (full[index] >>> GREEN_SHIFT & BYTE_MASK) >
                            (ground[darkest] >>> GREEN_SHIFT & BYTE_MASK) - (full[darkest] >>> GREEN_SHIFT & BYTE_MASK)) {
                            darkest = index;
                        }
                    }

                    int fullDarkening = (ground[darkest] >>> GREEN_SHIFT & BYTE_MASK) - (full[darkest] >>> GREEN_SHIFT & BYTE_MASK);

                    Assertions.assertTrue(fullDarkening > MINIMUM_HD_DARKENING, "the HD Pylon shadow darkens the ground: " + fullDarkening);

                    for (int warpFrame : HD_WARP_FRAMES) {
                        int[] dimmed = renderHdFrame(renderer, hdScene,
                                                     () -> {
                                                         hdScene.drawTerrain(index -> TILE);
                                                         hdScene.drawShadowImage(PYLON_SHADOW, 0, warpFrame, HD_PROBE_X, HD_PROBE_Y, false);
                                                     },
                                                     hdPixels);
                        int darkening = (ground[darkest] >>> GREEN_SHIFT & BYTE_MASK) - (dimmed[darkest] >>> GREEN_SHIFT & BYTE_MASK);

                        Assertions.assertEquals(fullDarkening * warpFrame / (double) WARP_FRAME_COUNT,
                                                darkening,
                                                HD_DIMMING_TOLERANCE,
                                                "HD Pylon shadow at warp frame " + warpFrame);
                    }
                } finally {
                    MemoryUtil.memFree(hdPixels);
                    hdScene.close();
                }
            }
        });
    }
}
