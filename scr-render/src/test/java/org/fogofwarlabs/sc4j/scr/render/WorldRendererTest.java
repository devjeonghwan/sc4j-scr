package org.fogofwarlabs.sc4j.scr.render;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrResourceProvider;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Tests that {@link WorldRenderer} draws indexed terrain, rectangles, player-colored units, frozen images, shadows, creep edges, lighting remaps and
 * warp effects on a hidden context.
 */
final class WorldRendererTest {
    /**
     * The system property that enables the GPU checks and the message of a skipped check.
     */
    private static final @NotNull String RENDER_SMOKE_PROPERTY = "sc4j.render.smoke";
    private static final @NotNull String RENDER_SMOKE_MESSAGE = "Pass -Psc4j.render.smoke=true to run the GPU check.";
    /**
     * The width and height of the hidden window in pixels.
     */
    private static final int WIDTH = 96;
    private static final int HEIGHT = 80;
    /**
     * The Badlands, Platform and Jungle tilesets and a tile identifier (CV5 group {@code 2}, megatile {@code 0}).
     */
    private static final int BADLANDS_TILESET = 0;
    private static final int PLATFORM_TILESET = 1;
    private static final int JUNGLE_TILESET = 4;
    private static final int TILE = 0x20;
    /**
     * Bit {@code 15} of a tile identifier, which is not part of the CV5 group.
     */
    private static final int EXCLUDED_TILE_BIT = 0x8000;
    /**
     * The bytes of a palette entry and of a palette row of {@code 256} entries, and the columns of a lighting table.
     */
    private static final int PALETTE_ENTRY_BYTES = 4;
    private static final int PALETTE_ROW_BYTES = 1024;
    private static final int LIGHTING_TABLE_COLUMNS = 256;

    /**
     * The Marine shadow image.
     */
    private static final int MARINE_SHADOW = 240;
    /**
     * The player color of the drawn units and its palette row, which follows the screen palette row.
     */
    private static final int PLAYER_COLOR = 1;
    private static final int PLAYER_PALETTE_OFFSET = (1 + PLAYER_COLOR) * PALETTE_ROW_BYTES;
    /**
     * The palette index of transparent frame pixels.
     */
    private static final int TRANSPARENT_INDEX = 0;
    /**
     * The red, green and blue channels precede alpha.
     */
    private static final int COLOR_CHANNELS = OverlayPass.RGBA_ALPHA;
    /**
     * The darkening of a classic shadow.
     */
    private static final float SHADOW_ALPHA = 0.5f;

    /**
     * The rectangle drawn over the terrain, its color, the probed pixel inside it and the probed channel values.
     */
    private static final int RECTANGLE_X = 40;
    private static final int RECTANGLE_Y = 2;
    private static final int RECTANGLE_SIZE = 8;
    private static final int RECTANGLE_COLOR = 0xC81E50FF;
    private static final int RECTANGLE_PROBE_X = 43;
    private static final int RECTANGLE_PROBE_Y = 5;
    private static final int RECTANGLE_RED = 200;
    private static final int RECTANGLE_GREEN = 30;
    private static final int RECTANGLE_BLUE = 80;

    /**
     * The Marine frame compared with frame zero, the frame left edges, the frame top and the view of the frame checks.
     */
    private static final int SELECTED_FRAME = 2;
    private static final int @NotNull [] FRAME_LEFTS = {6, -12};
    private static final int FRAME_TOP = 4;
    private static final int FRAME_VIEW_WIDTH = 57;
    private static final int FRAME_VIEW_HEIGHT = 53;
    /**
     * The background of the frame checks, whose channels are multiples of {@link #BACKGROUND_CHANNEL_STEP}.
     */
    private static final int FRAME_BACKGROUND = 0x112233FF;
    private static final int BACKGROUND_CHANNEL_STEP = 0x11;

    /**
     * The drawn Marine and shadow frames, the stacked shadow counts and the view of the shadow checks.
     */
    private static final int @NotNull [] SHADOW_FRAMES = {0, 68, 85, 93, 204, 212};
    private static final int @NotNull [] SHADOW_COUNTS = {1, 3};
    private static final int SHADOW_VIEW_WIDTH = 69;
    private static final int SHADOW_VIEW_HEIGHT = 61;
    /**
     * The body orders: no body, the body under the shadows and the body over the shadows.
     */
    private static final int NO_BODY = 0;
    private static final int BODY_UNDER_SHADOWS = 1;
    private static final int BODY_OVER_SHADOWS = 2;
    private static final int BODY_ORDER_COUNT = 3;
    /**
     * The background of the shadow checks and its channel values.
     */
    private static final int SHADOW_BACKGROUND = 0xC86432FF;
    private static final int SHADOW_BACKGROUND_RED = 200;
    private static final int SHADOW_BACKGROUND_GREEN = 100;
    private static final int SHADOW_BACKGROUND_BLUE = 50;
    /**
     * The rectangle drawn after the world resolve, its color and its channel values.
     */
    private static final int OVERLAY_X = 44;
    private static final int OVERLAY_Y = 32;
    private static final int OVERLAY_SIZE = 8;
    private static final int OVERLAY_COLOR = 0x2468ACFF;
    private static final int OVERLAY_RED = 0x24;
    private static final int OVERLAY_CHANNEL_STEP = 0x44;
    /**
     * The tolerated channel difference of the shadow checks.
     */
    private static final int CHANNEL_TOLERANCE = 1;

    /**
     * The world position of the rejected frame draws.
     */
    private static final int REJECTED_DRAW_X = 32;
    private static final int REJECTED_DRAW_Y = 32;
    /**
     * The channel value of the opaque white warp flash, and the bytes of a warp texture pixel: palette index and coverage.
     */
    private static final int WHITE_CHANNEL = 255;
    private static final int WARP_PIXEL_BYTES = 2;

    /**
     * The fractional zoom frame: its view, its scale and the Command Center position.
     */
    private static final int ZOOM_VIEW_WIDTH = 70;
    private static final int ZOOM_VIEW_HEIGHT = 60;
    private static final float ZOOM_SCALE = 1.5f;
    private static final int COMMAND_CENTER_POSITION = 48;
    /**
     * The framebuffer and view size of the Platform frame.
     */
    private static final int PLATFORM_FRAMEBUFFER_SIZE = 64;
    private static final int PLATFORM_VIEW_SIZE = 48;

    /**
     * A three by three tile map and the window of the frozen image and creep edge checks, its center tile and the tile column right of the center.
     */
    private static final int MAP_TILES = 3;
    private static final int SIZE = MAP_TILES * TerrainTileset.TILE_SIZE;
    private static final int CENTER_TILE = MAP_TILES * MAP_TILES / 2;
    private static final int RIGHT_TILE_COLUMN = 2;
    /**
     * The player color of the frozen image and its palette row, which follows the screen palette row.
     */
    private static final int FROZEN_PLAYER_COLOR = 0;
    private static final int FROZEN_PLAYER_PALETTE_OFFSET = (1 + FROZEN_PLAYER_COLOR) * PALETTE_ROW_BYTES;
    /**
     * The drawn frames of the Marine image and the frozen frame-rectangle tops drawn for each.
     */
    private static final int @NotNull [] DRAWN_FRAMES = {0, 5, 13};
    private static final int @NotNull [] FROZEN_TOPS = {20, 37};
    /**
     * The frozen top queried before a map is loaded, and a frame index past the Marine frames.
     */
    private static final int UNLOADED_FROZEN_TOP = 10;
    private static final int MISSING_FRAME = 100000;

    /**
     * The neighbor offsets of the creep edge mask bits, bit {@code n} at index {@code n}.
     */
    private static final int @NotNull [] NEIGHBOR_HORIZONTAL = {1, 0, -1, 1, -1, 1, 0, -1};
    private static final int @NotNull [] NEIGHBOR_VERTICAL = {1, 1, 1, 0, 0, -1, -1, -1};
    /**
     * The frames of the tileset creep GRP, and an exclusive bound of the edge frame numbers.
     */
    private static final int CREEP_EDGE_FRAME_COUNT = 37;
    private static final int EDGE_FRAME_LIMIT = 64;
    /**
     * A Jungle grass tile (CV5 group {@code 2}, megatile {@code 0}) and the number of creep variants drawn around it.
     */
    private static final int GRASS_TILE = 0x20;
    private static final int CREEP_VARIANTS = 6;
    /**
     * The mask bits of the neighbors.
     */
    private static final int LOWER_RIGHT_NEIGHBOR = 0x01;
    private static final int LOWER_NEIGHBOR = 0x02;
    private static final int LOWER_LEFT_NEIGHBOR = 0x04;
    private static final int RIGHT_NEIGHBOR = 0x08;
    private static final int LEFT_NEIGHBOR = 0x10;
    private static final int UPPER_NEIGHBOR = 0x40;
    private static final int UPPER_LEFT_NEIGHBOR = 0x80;
    /**
     * The drawn neighbor masks: single neighbors, both sides, all neighbors and the four edge neighbors.
     */
    private static final int @NotNull [] NEIGHBOR_MASKS = {0x01, 0x02, 0x10, 0x40, 0x18, 0xFF, 0x5A};

    /**
     * The lighting table of {@code bfire} and its rows; the mining spark, a glow image of draw function {@code 9}.
     */
    private static final int BFIRE_TABLE = 2;
    private static final int BFIRE_ROWS = 40;
    private static final int MINING_SPARK = 520;
    /**
     * The drawn mining spark frames, the number of glow draws per frame, and an image whose color shift selects no lighting table.
     */
    private static final int @NotNull [] SPARK_FRAMES = {0, 5};
    private static final int GLOW_DRAWS = 2;
    private static final int UNLIT_IMAGE = 0;

    /**
     * The Kakaru image ({@code unit/neutral/Kcritter.grp}), the frame count of its GRP and a frame that an EUD map plays past the frame count.
     */
    private static final int KAKARU = 952;
    private static final int KAKARU_FRAME_COUNT = 212;
    private static final int EUD_KAKARU_FRAME = 425;

    @RequiresInstallation
    @Test
    void world_renderer_draws_indexed_terrain_overlays_and_sprites_on_a_hidden_context() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        HiddenContextScenario.run(WIDTH, HEIGHT, "Renderer verification", () -> {
            try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); WorldRenderer renderer = new WorldRenderer()) {
                int marine = ScrUnitIds.TERRAN_MARINE;

                Assertions.assertThrows(IllegalStateException.class, () -> renderer.getUnitImageIndex(marine));
                Assertions.assertThrows(IllegalStateException.class, () -> renderer.getUnitFrameCount(marine));

                RenderAssets assets = RenderAssets.load(ScrResourceProvider.borrow(storage), BADLANDS_TILESET);
                byte[] tile = assets.decodeTile(TILE);
                byte[] palette = assets.copyPalette();
                byte[] modifiedPalette = assets.copyPalette();

                modifiedPalette[0] ^= 1;
                Assertions.assertArrayEquals(palette, assets.copyPalette());

                renderer.loadMap(ScrResourceProvider.borrow(storage), BADLANDS_TILESET, 1, 1, new int[]{TILE | EXCLUDED_TILE_BIT});
                renderer.beginFrame(WIDTH, HEIGHT, TerrainTileset.TILE_SIZE, TerrainTileset.TILE_SIZE, 0, 0, 1);
                renderer.drawTerrain();
                renderer.fillRectangle(RECTANGLE_X, RECTANGLE_Y, RECTANGLE_SIZE, RECTANGLE_SIZE, RECTANGLE_COLOR);
                renderer.endFrame();

                ByteBuffer pixels = MemoryUtil.memAlloc(WIDTH * HEIGHT * OverlayPass.RGBA_BYTES);

                try {
                    glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

                    for (int pixelY = 0; pixelY < TerrainTileset.TILE_SIZE; pixelY++) {
                        for (int pixelX = 0; pixelX < TerrainTileset.TILE_SIZE; pixelX++) {
                            int paletteOffset = Byte.toUnsignedInt(tile[pixelY * TerrainTileset.TILE_SIZE + pixelX]) * PALETTE_ENTRY_BYTES;
                            int framebufferOffset = ((HEIGHT - 1 - pixelY) * WIDTH + pixelX) * OverlayPass.RGBA_BYTES;

                            for (int channel = 0; channel < COLOR_CHANNELS; channel++) {
                                Assertions.assertEquals(Byte.toUnsignedInt(palette[paletteOffset + channel]),
                                                        Byte.toUnsignedInt(pixels.get(framebufferOffset + channel)));
                            }
                        }
                    }

                    int rectangleOffset = ((HEIGHT - 1 - RECTANGLE_PROBE_Y) * WIDTH + RECTANGLE_PROBE_X) * OverlayPass.RGBA_BYTES;

                    Assertions.assertEquals(RECTANGLE_RED, Byte.toUnsignedInt(pixels.get(rectangleOffset)));
                    Assertions.assertEquals(RECTANGLE_GREEN, Byte.toUnsignedInt(pixels.get(rectangleOffset + OverlayPass.RGBA_GREEN)));
                    Assertions.assertEquals(RECTANGLE_BLUE, Byte.toUnsignedInt(pixels.get(rectangleOffset + OverlayPass.RGBA_BLUE)));
                } finally {
                    MemoryUtil.memFree(pixels);
                }

                ClassicFrame marineFrame = assets.decodeImage(assets.getUnitImage(marine), 0);
                int coloredPixel = -1;

                for (int pixelIndex = 0; pixelIndex < marineFrame.getPixels().length; pixelIndex++) {
                    int colorIndex = Byte.toUnsignedInt(marineFrame.getPixels()[pixelIndex]);

                    if (colorIndex >= RenderAssets.REMAP_FIRST_INDEX &&
                        colorIndex < RenderAssets.REMAP_FIRST_INDEX + RenderAssets.REMAP_INDEX_COUNT) {
                        coloredPixel = pixelIndex;

                        break;
                    }
                }

                Assertions.assertTrue(coloredPixel >= 0, "The Marine frame has no player-color pixel.");

                renderer.beginFrame(WIDTH, HEIGHT, WIDTH, HEIGHT, 0, 0, 1);
                renderer.drawUnit(marine, PLAYER_COLOR, marineFrame.getWidth() / 2.0f, marineFrame.getHeight() / 2.0f);
                renderer.endFrame();

                ByteBuffer coloredSample = MemoryUtil.memAlloc(OverlayPass.RGBA_BYTES);

                try {
                    glReadPixels(coloredPixel % marineFrame.getWidth(),
                                 HEIGHT - 1 - coloredPixel / marineFrame.getWidth(),
                                 1,
                                 1,
                                 GL_RGBA,
                                 GL_UNSIGNED_BYTE,
                                 coloredSample);

                    int paletteOffset = PLAYER_PALETTE_OFFSET + Byte.toUnsignedInt(marineFrame.getPixels()[coloredPixel]) * PALETTE_ENTRY_BYTES;

                    for (int channel = 0; channel < COLOR_CHANNELS; channel++) {
                        Assertions.assertEquals(Byte.toUnsignedInt(palette[paletteOffset + channel]), Byte.toUnsignedInt(coloredSample.get(channel)));
                    }
                } finally {
                    MemoryUtil.memFree(coloredSample);
                }

                int marineImageIndex = assets.getUnitImage(marine);
                int marineFrameCount = assets.getImageFrameCount(marineImageIndex);

                Assertions.assertEquals(marineImageIndex, renderer.getUnitImageIndex(marine));
                Assertions.assertEquals(marineFrameCount, renderer.getUnitFrameCount(marine));
                Assertions.assertTrue(marineFrameCount > SELECTED_FRAME);

                ByteBuffer framePixels = MemoryUtil.memAlloc(WIDTH * HEIGHT * OverlayPass.RGBA_BYTES);

                try {
                    for (int frameIndex : new int[]{SELECTED_FRAME, marineFrameCount - 1}) {
                        ClassicFrame sourceFrame = assets.decodeImage(marineImageIndex, frameIndex);

                        Assertions.assertFalse(Arrays.equals(marineFrame.getPixels(), sourceFrame.getPixels()),
                                               "The selected frame must differ from frame zero.");

                        for (boolean flipped : new boolean[]{false, true}) {
                            for (int left : FRAME_LEFTS) {
                                renderer.beginFrame(WIDTH, HEIGHT, FRAME_VIEW_WIDTH, FRAME_VIEW_HEIGHT, 0, 0, 1);
                                renderer.fillRectangle(0, 0, WIDTH, HEIGHT, FRAME_BACKGROUND);
                                renderer.drawUnit(marine,
                                                  frameIndex,
                                                  PLAYER_COLOR,
                                                  left + sourceFrame.getWidth() / 2.0f,
                                                  FRAME_TOP + sourceFrame.getHeight() / 2.0f,
                                                  flipped);
                                renderer.endFrame();
                                glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, framePixels);

                                for (int pixelY = 0; pixelY < HEIGHT; pixelY++) {
                                    for (int pixelX = 0; pixelX < WIDTH; pixelX++) {
                                        int sourceX = pixelX - left;
                                        int sourceY = pixelY - FRAME_TOP;
                                        int paletteIndex = TRANSPARENT_INDEX;

                                        if (pixelX < FRAME_VIEW_WIDTH && pixelY < FRAME_VIEW_HEIGHT &&
                                            sourceX >= 0 && sourceX < sourceFrame.getWidth() && sourceY >= 0 && sourceY < sourceFrame.getHeight()) {
                                            if (flipped) {
                                                sourceX = sourceFrame.getWidth() - 1 - sourceX;
                                            }

                                            paletteIndex = Byte.toUnsignedInt(sourceFrame.getPixels()[sourceY * sourceFrame.getWidth() + sourceX]);
                                        }

                                        int framebufferOffset = ((HEIGHT - 1 - pixelY) * WIDTH + pixelX) * OverlayPass.RGBA_BYTES;
                                        int paletteOffset = PLAYER_PALETTE_OFFSET + paletteIndex * PALETTE_ENTRY_BYTES;

                                        for (int channel = 0; channel < COLOR_CHANNELS; channel++) {
                                            int expected = paletteIndex == TRANSPARENT_INDEX
                                                           ? (channel + 1) * BACKGROUND_CHANNEL_STEP
                                                           : Byte.toUnsignedInt(palette[paletteOffset + channel]);

                                            Assertions.assertEquals(expected,
                                                                    Byte.toUnsignedInt(framePixels.get(framebufferOffset + channel)),
                                                                    "Frame " + frameIndex + ", flipped=" + flipped + ", pixel=" +
                                                                    pixelX + "," + pixelY);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    MemoryUtil.memFree(framePixels);
                }

                ByteBuffer shadowPixels = MemoryUtil.memAlloc(WIDTH * HEIGHT * OverlayPass.RGBA_BYTES);

                try {
                    int centerX = WIDTH / 2;
                    int centerY = HEIGHT / 2;

                    for (int frameIndex : SHADOW_FRAMES) {
                        ClassicFrame shadow = assets.decodeImage(MARINE_SHADOW, frameIndex);
                        ClassicFrame body = assets.decodeImage(marineImageIndex, frameIndex);

                        Assertions.assertEquals(0, shadow.getWidth() & 1);
                        Assertions.assertEquals(0, shadow.getHeight() & 1);
                        Assertions.assertEquals(0, body.getWidth() & 1);
                        Assertions.assertEquals(0, body.getHeight() & 1);

                        for (boolean flipped : new boolean[]{false, true}) {
                            for (int shadowCount : SHADOW_COUNTS) {
                                for (int bodyOrder = NO_BODY; bodyOrder < BODY_ORDER_COUNT; bodyOrder++) {
                                    renderer.beginFrame(WIDTH, HEIGHT, SHADOW_VIEW_WIDTH, SHADOW_VIEW_HEIGHT, 0, 0, 1);
                                    renderer.fillRectangle(0, 0, WIDTH, HEIGHT, SHADOW_BACKGROUND);

                                    if (bodyOrder == BODY_UNDER_SHADOWS) {
                                        renderer.drawImage(marineImageIndex, frameIndex, PLAYER_COLOR, centerX, centerY, flipped);
                                    }

                                    for (int shadowIndex = 0; shadowIndex < shadowCount; shadowIndex++) {
                                        renderer.drawShadowImage(MARINE_SHADOW, frameIndex, centerX, centerY, flipped);
                                    }

                                    if (bodyOrder == BODY_OVER_SHADOWS) {
                                        renderer.drawImage(marineImageIndex, frameIndex, PLAYER_COLOR, centerX, centerY, flipped);
                                    }

                                    renderer.finishWorld();
                                    renderer.finishWorld();
                                    renderer.fillRectangle(OVERLAY_X, OVERLAY_Y, OVERLAY_SIZE, OVERLAY_SIZE, OVERLAY_COLOR);
                                    renderer.endFrame();
                                    glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, shadowPixels);

                                    for (int pixelY = 0; pixelY < HEIGHT; pixelY++) {
                                        for (int pixelX = 0; pixelX < WIDTH; pixelX++) {
                                            int shadowX = pixelX - centerX + shadow.getWidth() / 2;
                                            int shadowY = pixelY - centerY + shadow.getHeight() / 2;
                                            int bodyX = pixelX - centerX + body.getWidth() / 2;
                                            int bodyY = pixelY - centerY + body.getHeight() / 2;

                                            if (flipped) {
                                                shadowX = shadow.getWidth() - 1 - shadowX;
                                                bodyX   = body.getWidth() - 1 - bodyX;
                                            }

                                            boolean covered = pixelX < SHADOW_VIEW_WIDTH && pixelY < SHADOW_VIEW_HEIGHT &&
                                                              shadowX >= 0 && shadowX < shadow.getWidth() &&
                                                              shadowY >= 0 && shadowY < shadow.getHeight() &&
                                                              shadow.getPixels()[shadowY * shadow.getWidth() + shadowX] != 0;
                                            int bodyPaletteIndex = TRANSPARENT_INDEX;

                                            if (bodyOrder != NO_BODY && pixelX < SHADOW_VIEW_WIDTH && pixelY < SHADOW_VIEW_HEIGHT &&
                                                bodyX >= 0 && bodyX < body.getWidth() &&
                                                bodyY >= 0 && bodyY < body.getHeight()) {
                                                bodyPaletteIndex = Byte.toUnsignedInt(body.getPixels()[bodyY * body.getWidth() + bodyX]);
                                            }

                                            if (bodyOrder == BODY_OVER_SHADOWS && bodyPaletteIndex != TRANSPARENT_INDEX) {
                                                covered = false;
                                            }

                                            int framebufferOffset = ((HEIGHT - 1 - pixelY) * WIDTH + pixelX) * OverlayPass.RGBA_BYTES;
                                            int bodyPaletteOffset = PLAYER_PALETTE_OFFSET + bodyPaletteIndex * PALETTE_ENTRY_BYTES;

                                            for (int channel = 0; channel < COLOR_CHANNELS; channel++) {
                                                int expected = bodyPaletteIndex == TRANSPARENT_INDEX
                                                               ? SHADOW_BACKGROUND_RED >> channel
                                                               : Byte.toUnsignedInt(palette[bodyPaletteOffset + channel]);

                                                if (covered) {
                                                    expected = Math.round(expected * SHADOW_ALPHA);
                                                }

                                                if (pixelX >= OVERLAY_X && pixelX < OVERLAY_X + OVERLAY_SIZE &&
                                                    pixelY >= OVERLAY_Y && pixelY < OVERLAY_Y + OVERLAY_SIZE) {
                                                    expected = OVERLAY_RED + channel * OVERLAY_CHANNEL_STEP;
                                                }

                                                Assertions.assertEquals(expected,
                                                                        Byte.toUnsignedInt(shadowPixels.get(framebufferOffset + channel)),
                                                                        CHANNEL_TOLERANCE,
                                                                        "Shadow frame " + frameIndex + ", flipped=" + flipped +
                                                                        ", count=" + shadowCount + ", bodyOrder=" + bodyOrder +
                                                                        ", pixel=" + pixelX + "," + pixelY);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Assertions.assertThrows(IllegalStateException.class, () -> renderer.drawShadowImage(MARINE_SHADOW, 0, centerX, centerY, false));

                    renderer.beginFrame(WIDTH, HEIGHT, SHADOW_VIEW_WIDTH, SHADOW_VIEW_HEIGHT, 0, 0, 1);
                    renderer.fillRectangle(0, 0, WIDTH, HEIGHT, SHADOW_BACKGROUND);
                    renderer.endFrame();
                    glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, shadowPixels);

                    for (int pixelIndex = 0; pixelIndex < WIDTH * HEIGHT; pixelIndex++) {
                        Assertions.assertEquals(SHADOW_BACKGROUND_RED, Byte.toUnsignedInt(shadowPixels.get(pixelIndex * OverlayPass.RGBA_BYTES)));
                        Assertions.assertEquals(SHADOW_BACKGROUND_GREEN,
                                                Byte.toUnsignedInt(shadowPixels.get(pixelIndex * OverlayPass.RGBA_BYTES + OverlayPass.RGBA_GREEN)));
                        Assertions.assertEquals(SHADOW_BACKGROUND_BLUE,
                                                Byte.toUnsignedInt(shadowPixels.get(pixelIndex * OverlayPass.RGBA_BYTES + OverlayPass.RGBA_BLUE)));
                    }
                } finally {
                    MemoryUtil.memFree(shadowPixels);
                }

                renderer.beginFrame(WIDTH, HEIGHT, WIDTH, HEIGHT, 0, 0, 1);
                Assertions.assertThrows(IOException.class,
                                        () -> renderer.drawUnit(marine, marineFrameCount, PLAYER_COLOR, REJECTED_DRAW_X, REJECTED_DRAW_Y, false));
                Assertions.assertThrows(IOException.class,
                                        () -> renderer.drawUnit(marine, -1, PLAYER_COLOR, REJECTED_DRAW_X, REJECTED_DRAW_Y, false));
                renderer.endFrame();

                ByteBuffer warpPixels = MemoryUtil.memAlloc(WIDTH * HEIGHT * OverlayPass.RGBA_BYTES);
                int center = TerrainTileset.TILE_SIZE / 2;

                try {
                    for (boolean flipped : new boolean[]{false, true}) {
                        // Draw function 17 brightens the marine's coverage toward white; data 48 is opaque white.
                        renderer.beginFrame(WIDTH, HEIGHT, TerrainTileset.TILE_SIZE, TerrainTileset.TILE_SIZE, 0, 0, 1);
                        renderer.drawTerrain();
                        renderer.drawUnit(marine, PLAYER_COLOR, center, center);
                        renderer.drawBrightenImage(marineImageIndex, 0, SceneRenderer.WARP_FLASH_FIRST_DATA, center, center, flipped);
                        renderer.endFrame();
                        glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, warpPixels);

                        for (int vertical = 0; vertical < TerrainTileset.TILE_SIZE; vertical++) {
                            for (int horizontal = 0; horizontal < TerrainTileset.TILE_SIZE; horizontal++) {
                                int sourceX = horizontal - center + marineFrame.getWidth() / 2;
                                int sourceY = vertical - center + marineFrame.getHeight() / 2;

                                if (sourceX < 0 || sourceX >= marineFrame.getWidth() || sourceY < 0 || sourceY >= marineFrame.getHeight()) {
                                    continue;
                                }

                                int maskX = flipped ? marineFrame.getWidth() - 1 - sourceX : sourceX;

                                if (marineFrame.getCoverage()[sourceY * marineFrame.getWidth() + maskX] == 0) {
                                    continue;
                                }

                                int destination = ((HEIGHT - 1 - vertical) * WIDTH + horizontal) * OverlayPass.RGBA_BYTES;

                                for (int channel = 0; channel < COLOR_CHANNELS; channel++) {
                                    Assertions.assertEquals(WHITE_CHANNEL,
                                                            Byte.toUnsignedInt(warpPixels.get(destination + channel)),
                                                            "Warp flash flipped=" + flipped + " at " + horizontal + "," + vertical);
                                }
                            }
                        }

                        // The warp texture pixels hold a palette index and a coverage byte per pixel.
                        byte[] expectedTexture = assets.createWarpPixels(marineImageIndex, 0, 0, flipped);

                        renderer.beginFrame(WIDTH, HEIGHT, TerrainTileset.TILE_SIZE, TerrainTileset.TILE_SIZE, 0, 0, 1);
                        renderer.drawTerrain();
                        renderer.drawWarpTexture(marineImageIndex, 0, 0, center, center, flipped);
                        renderer.endFrame();
                        glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, warpPixels);

                        for (int vertical = 0; vertical < TerrainTileset.TILE_SIZE; vertical++) {
                            for (int horizontal = 0; horizontal < TerrainTileset.TILE_SIZE; horizontal++) {
                                int index = Byte.toUnsignedInt(tile[vertical * TerrainTileset.TILE_SIZE + horizontal]);
                                int sourceX = horizontal - center + marineFrame.getWidth() / 2;
                                int sourceY = vertical - center + marineFrame.getHeight() / 2;

                                if (sourceX >= 0 && sourceX < marineFrame.getWidth() && sourceY >= 0 && sourceY < marineFrame.getHeight()) {
                                    int offset = (sourceY * marineFrame.getWidth() + sourceX) * WARP_PIXEL_BYTES;

                                    if (expectedTexture[offset + 1] != 0) {
                                        index = Byte.toUnsignedInt(expectedTexture[offset]);
                                    }
                                }

                                int destination = ((HEIGHT - 1 - vertical) * WIDTH + horizontal) * OverlayPass.RGBA_BYTES;

                                for (int channel = 0; channel < COLOR_CHANNELS; channel++) {
                                    Assertions.assertEquals(Byte.toUnsignedInt(palette[index * PALETTE_ENTRY_BYTES + channel]),
                                                            Byte.toUnsignedInt(warpPixels.get(destination + channel)));
                                }
                            }
                        }
                    }
                } finally {
                    MemoryUtil.memFree(warpPixels);
                }

                renderer.beginFrame(WIDTH, HEIGHT, ZOOM_VIEW_WIDTH, ZOOM_VIEW_HEIGHT, 0, 0, ZOOM_SCALE);
                renderer.drawTerrain();
                renderer.drawUnit(marine, PLAYER_COLOR, center, center);
                renderer.drawUnit(ScrUnitIds.TERRAN_COMMAND_CENTER, 0, COMMAND_CENTER_POSITION, COMMAND_CENTER_POSITION);
                renderer.endFrame();

                renderer.loadMap(ScrResourceProvider.borrow(storage), PLATFORM_TILESET, 1, 1, new int[]{TILE});
                renderer.beginFrame(PLATFORM_FRAMEBUFFER_SIZE, PLATFORM_FRAMEBUFFER_SIZE, PLATFORM_VIEW_SIZE, PLATFORM_VIEW_SIZE, 0, 0, 1);
                renderer.drawTerrain();
                renderer.endFrame();
                Assertions.assertEquals(GL_NO_ERROR, glGetError(), "The renderer produced an OpenGL error.");
            }

            Assertions.assertEquals(GL_NO_ERROR, glGetError(), "Renderer cleanup produced an OpenGL error.");
        });
    }

    @RequiresInstallation
    @Test
    void frozen_frame_rectangle_top_is_placed_at_the_frozen_coordinate() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        HiddenContextScenario.run(SIZE, SIZE, "Frozen image verification", () -> {
            try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); WorldRenderer renderer = new WorldRenderer()) {
                RenderAssets assets = RenderAssets.load(ScrResourceProvider.borrow(storage), BADLANDS_TILESET);
                int[] tiles = new int[MAP_TILES * MAP_TILES];

                Arrays.fill(tiles, TILE);
                Assertions.assertThrows(IllegalStateException.class, () -> renderer.getFrozenCenterY(0, 0, UNLOADED_FROZEN_TOP));
                renderer.loadMap(ScrResourceProvider.borrow(storage), BADLANDS_TILESET, MAP_TILES, MAP_TILES, tiles);

                int image = assets.getUnitImage(ScrUnitIds.TERRAN_MARINE);
                byte[] palette = assets.copyPalette();
                ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * OverlayPass.RGBA_BYTES);

                try {
                    for (int frameIndex : DRAWN_FRAMES) {
                        ClassicFrame frame = assets.decodeImage(image, frameIndex);

                        for (int frozenTop : FROZEN_TOPS) {
                            int center = renderer.getFrozenCenterY(image, frameIndex, frozenTop);

                            Assertions.assertEquals(frozenTop - frame.getYOffset() + frame.getHeight() / 2, center);

                            renderer.beginFrame(SIZE, SIZE, SIZE, SIZE, 0, 0, 1);
                            renderer.drawImage(image, frameIndex, FROZEN_PLAYER_COLOR, SIZE / 2, center, false);
                            renderer.endFrame();
                            glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

                            int left = SIZE / 2 - frame.getWidth() / 2;
                            int canvasTop = frozenTop - frame.getYOffset();
                            int topmost = Integer.MAX_VALUE;

                            for (int vertical = 0; vertical < SIZE; vertical++) {
                                for (int horizontal = 0; horizontal < SIZE; horizontal++) {
                                    int sourceX = horizontal - left;
                                    int sourceY = vertical - canvasTop;

                                    if (sourceX < 0 || sourceX >= frame.getWidth() || sourceY < 0 || sourceY >= frame.getHeight()) {
                                        continue;
                                    }

                                    int index = Byte.toUnsignedInt(frame.getPixels()[sourceY * frame.getWidth() + sourceX]);

                                    if (index == TRANSPARENT_INDEX) {
                                        continue;
                                    }

                                    topmost = Math.min(topmost, vertical);

                                    int destination = ((SIZE - 1 - vertical) * SIZE + horizontal) * OverlayPass.RGBA_BYTES;
                                    int expected = Byte.toUnsignedInt(palette[FROZEN_PLAYER_PALETTE_OFFSET + index * PALETTE_ENTRY_BYTES]);

                                    Assertions.assertEquals(expected,
                                                            Byte.toUnsignedInt(pixels.get(destination)),
                                                            "Frozen frame " + frameIndex + " top " + frozenTop + " at " +
                                                            horizontal + "," + vertical);
                                }
                            }

                            Assertions.assertEquals(frozenTop, topmost, "The first opaque row is the frozen frame-rectangle top.");
                        }
                    }

                    Assertions.assertThrows(IOException.class, () -> renderer.getFrozenCenterY(image, MISSING_FRAME, 0));
                } finally {
                    MemoryUtil.memFree(pixels);
                }
            }
        });
    }

    @Test
    void get_creep_edge_frame_should_number_frames_consecutively() {
        Assertions.assertEquals(0, WorldRenderer.getCreepEdgeFrame(0));

        boolean[] used = new boolean[EDGE_FRAME_LIMIT];
        int maximum = 0;

        for (int neighbors = 0; neighbors < 1 << NEIGHBOR_HORIZONTAL.length; neighbors++) {
            int frame = WorldRenderer.getCreepEdgeFrame(neighbors);

            used[frame] = true;
            maximum     = Math.max(maximum, frame);
        }

        for (int frame = 0; frame <= maximum; frame++) {
            Assertions.assertTrue(used[frame], "Edge identifiers are consecutive; missing " + frame);
        }

        Assertions.assertEquals(CREEP_EDGE_FRAME_COUNT, maximum, "The neighbor classes select one-based frames of the 37-frame tileset creep GRP.");
        Assertions.assertEquals(WorldRenderer.getCreepEdgeFrame(LEFT_NEIGHBOR),
                                WorldRenderer.getCreepEdgeFrame(LEFT_NEIGHBOR | LOWER_LEFT_NEIGHBOR | UPPER_LEFT_NEIGHBOR),
                                "Diagonal neighbors without an adjacent edge neighbor do not change the left edge.");
        Assertions.assertNotEquals(WorldRenderer.getCreepEdgeFrame(LOWER_NEIGHBOR), WorldRenderer.getCreepEdgeFrame(UPPER_NEIGHBOR));
    }

    @RequiresInstallation
    @Test
    void world_renderer_draws_creep_edges_and_lighting_remaps_on_a_hidden_context() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        HiddenContextScenario.run(SIZE, SIZE, "Renderer effect verification", () -> {
            try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); WorldRenderer renderer = new WorldRenderer()) {
                RenderAssets assets = RenderAssets.load(ScrResourceProvider.borrow(storage), JUNGLE_TILESET);

                Assertions.assertEquals(CREEP_EDGE_FRAME_COUNT, assets.getCreepEdgeFrameCount());

                for (int tableIndex = 0; tableIndex < RenderAssets.LIGHTING_TABLE_NAMES.length; tableIndex++) {
                    Assertions.assertEquals(0, assets.getLightingTable(tableIndex).length % LIGHTING_TABLE_COLUMNS);
                }

                Assertions.assertEquals(BFIRE_ROWS * LIGHTING_TABLE_COLUMNS, assets.getLightingTable(BFIRE_TABLE).length, "bfire has 40 rows.");

                byte[] palette = assets.copyPalette();
                ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * OverlayPass.RGBA_BYTES);

                try {
                    int[] tiles = new int[MAP_TILES * MAP_TILES];
                    int[] grassMap = new int[MAP_TILES * MAP_TILES];

                    Arrays.fill(grassMap, GRASS_TILE);
                    renderer.loadMap(ScrResourceProvider.borrow(storage), JUNGLE_TILESET, MAP_TILES, MAP_TILES, grassMap);

                    for (int neighborMask : NEIGHBOR_MASKS) {
                        for (int direction = 0; direction < NEIGHBOR_HORIZONTAL.length; direction++) {
                            int tileIndex = (1 + NEIGHBOR_VERTICAL[direction]) * MAP_TILES + 1 + NEIGHBOR_HORIZONTAL[direction];

                            tiles[tileIndex] = (neighborMask & 1 << direction) != 0
                                               ? TerrainTileset.CREEP_TILE + direction % CREEP_VARIANTS
                                               : GRASS_TILE;
                        }

                        tiles[CENTER_TILE] = GRASS_TILE;

                        renderer.beginFrame(SIZE, SIZE, SIZE, SIZE, 0, 0, 1);
                        renderer.drawTerrain(tileIndex -> tiles[tileIndex]);
                        renderer.endFrame();
                        glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

                        byte[] base = assets.decodeTile(GRASS_TILE);
                        int frame = WorldRenderer.getCreepEdgeFrame(neighborMask);

                        Assertions.assertEquals(neighborMask == LOWER_RIGHT_NEIGHBOR, frame == 0, "A lone diagonal neighbor draws no edge.");

                        ClassicFrame edge = frame == 0 ? null : assets.decodeCreepEdge(frame - 1);

                        for (int vertical = 0; vertical < TerrainTileset.TILE_SIZE; vertical++) {
                            for (int horizontal = 0; horizontal < TerrainTileset.TILE_SIZE; horizontal++) {
                                int index = Byte.toUnsignedInt(base[vertical * TerrainTileset.TILE_SIZE + horizontal]);

                                if (edge != null && edge.getCoverage()[vertical * TerrainTileset.TILE_SIZE + horizontal] != 0) {
                                    index = Byte.toUnsignedInt(edge.getPixels()[vertical * TerrainTileset.TILE_SIZE + horizontal]);
                                }

                                int destination = ((SIZE - 1 - TerrainTileset.TILE_SIZE - vertical) * SIZE + TerrainTileset.TILE_SIZE + horizontal) *
                                                  OverlayPass.RGBA_BYTES;

                                for (int channel = 0; channel < COLOR_CHANNELS; channel++) {
                                    Assertions.assertEquals(Byte.toUnsignedInt(palette[index * PALETTE_ENTRY_BYTES + channel]),
                                                            Byte.toUnsignedInt(pixels.get(destination + channel)),
                                                            "Creep edge mask " + neighborMask + " at " + horizontal + "," + vertical);
                                }
                            }
                        }

                        if ((neighborMask & RIGHT_NEIGHBOR) != 0) {
                            byte[] creep = assets.decodeTile(tiles[CENTER_TILE + 1]);

                            for (int vertical = 0; vertical < TerrainTileset.TILE_SIZE; vertical++) {
                                for (int horizontal = 0; horizontal < TerrainTileset.TILE_SIZE; horizontal++) {
                                    int destination = ((SIZE - 1 - TerrainTileset.TILE_SIZE - vertical) * SIZE +
                                                       RIGHT_TILE_COLUMN * TerrainTileset.TILE_SIZE + horizontal) *
                                                      OverlayPass.RGBA_BYTES;
                                    int creepIndex = Byte.toUnsignedInt(creep[vertical * TerrainTileset.TILE_SIZE + horizontal]);

                                    Assertions.assertEquals(Byte.toUnsignedInt(palette[creepIndex * PALETTE_ENTRY_BYTES]),
                                                            Byte.toUnsignedInt(pixels.get(destination)),
                                                            "A creep tile draws its current variant without an edge overlay.");
                                }
                            }
                        }
                    }

                    renderer.loadMap(ScrResourceProvider.borrow(storage), JUNGLE_TILESET, 1, 1, new int[]{GRASS_TILE});

                    byte[] tile = assets.decodeTile(GRASS_TILE);
                    byte[] lighting = assets.getLightingTable(assets.getColorShift(MINING_SPARK) - 1);
                    int rows = lighting.length / LIGHTING_TABLE_COLUMNS;
                    int center = TerrainTileset.TILE_SIZE / 2;

                    for (int frameIndex : SPARK_FRAMES) {
                        ClassicFrame spark = assets.decodeImage(MINING_SPARK, frameIndex);

                        for (boolean flipped : new boolean[]{false, true}) {
                            renderer.beginFrame(SIZE, SIZE, TerrainTileset.TILE_SIZE, TerrainTileset.TILE_SIZE, 0, 0, 1);
                            renderer.drawTerrain();
                            renderer.drawGlowImage(MINING_SPARK, frameIndex, center, center, flipped);
                            renderer.drawGlowImage(MINING_SPARK, frameIndex, center, center, flipped);
                            renderer.endFrame();
                            glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

                            int left = flipped ? center + spark.getWidth() / 2 - spark.getWidth() : center - spark.getWidth() / 2;
                            int top = center - spark.getHeight() / 2;

                            for (int vertical = 0; vertical < TerrainTileset.TILE_SIZE; vertical++) {
                                for (int horizontal = 0; horizontal < TerrainTileset.TILE_SIZE; horizontal++) {
                                    int index = Byte.toUnsignedInt(tile[vertical * TerrainTileset.TILE_SIZE + horizontal]);
                                    int sourceX = horizontal - left;
                                    int sourceY = vertical - top;

                                    if (flipped) {
                                        sourceX = spark.getWidth() - 1 - sourceX;
                                    }

                                    if (sourceX >= 0 && sourceX < spark.getWidth() && sourceY >= 0 && sourceY < spark.getHeight() &&
                                        spark.getCoverage()[sourceY * spark.getWidth() + sourceX] != 0) {
                                        int row = Byte.toUnsignedInt(spark.getPixels()[sourceY * spark.getWidth() + sourceX]) - 1;

                                        for (int repeat = 0; repeat < GLOW_DRAWS; repeat++) {
                                            index = row >= 0 && row < rows ? Byte.toUnsignedInt(lighting[row * LIGHTING_TABLE_COLUMNS + index]) : 0;
                                        }
                                    }

                                    int destination = ((SIZE - 1 - vertical) * SIZE + horizontal) * OverlayPass.RGBA_BYTES;

                                    for (int channel = 0; channel < COLOR_CHANNELS; channel++) {
                                        Assertions.assertEquals(Byte.toUnsignedInt(palette[index * PALETTE_ENTRY_BYTES + channel]),
                                                                Byte.toUnsignedInt(pixels.get(destination + channel)),
                                                                "Glow frame " + frameIndex + " flipped=" + flipped + " at " +
                                                                horizontal + "," + vertical);
                                    }
                                }
                            }
                        }
                    }

                    renderer.beginFrame(SIZE, SIZE, TerrainTileset.TILE_SIZE, TerrainTileset.TILE_SIZE, 0, 0, 1);
                    Assertions.assertThrows(IOException.class,
                                            () -> renderer.drawGlowImage(UNLIT_IMAGE, 0, center, center, false),
                                            "An image whose color shift selects no lighting table is rejected.");
                    renderer.endFrame();
                    Assertions.assertThrows(IllegalStateException.class, () -> renderer.drawGlowImage(MINING_SPARK, 0, center, center, false));
                } finally {
                    MemoryUtil.memFree(pixels);
                }

                Assertions.assertEquals(GL_NO_ERROR, glGetError(), "The renderer produced an OpenGL error.");
            }
        });
    }

    @RequiresInstallation
    @Test
    void is_drawn_frame_should_skip_frames_past_the_grp_frame_count() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        HiddenContextScenario.run(WIDTH, HEIGHT, "Shadow stacking verification", () -> {
            try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); WorldRenderer renderer = new WorldRenderer()) {
                renderer.loadMap(ScrResourceProvider.borrow(storage), JUNGLE_TILESET, 1, 1, new int[]{TILE});

                // An EUD map plays frames 425 + 17n on the Kakaru image.
                int frames = renderer.getImageFrameCount(KAKARU);

                Assertions.assertEquals(KAKARU_FRAME_COUNT, frames);
                Assertions.assertTrue(renderer.isDrawnFrame(KAKARU, frames - 1));
                Assertions.assertFalse(renderer.isDrawnFrame(KAKARU, frames), "a frame past the GRP frame count skips the image");
                Assertions.assertFalse(renderer.isDrawnFrame(KAKARU, EUD_KAKARU_FRAME));
            }
        });
    }
}
