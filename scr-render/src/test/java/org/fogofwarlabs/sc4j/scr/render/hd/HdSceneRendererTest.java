package org.fogofwarlabs.sc4j.scr.render.hd;

import org.fogofwarlabs.sc4j.engine.resource.graphics.TextureFrame;
import org.fogofwarlabs.sc4j.engine.resource.graphics.TextureSheet;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.ImagesDat;
import org.fogofwarlabs.sc4j.scr.data.format.image.AnimFile;
import org.fogofwarlabs.sc4j.scr.data.format.image.DdsFile;
import org.fogofwarlabs.sc4j.scr.data.format.image.GrpFile;
import org.fogofwarlabs.sc4j.scr.data.format.text.TblFile;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrResourceProvider;
import org.fogofwarlabs.sc4j.scr.render.*;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Tests that {@link HdSceneRenderer} places SD-scaled frames by their GRP offset, draws HD2 terrain, sprites, shadows, effects, overlays and
 * preloaded images, keeps creep edges over water out of the water animation, and refuses every draw once closed.
 */
@RequiresInstallation
final class HdSceneRendererTest {
    /**
     * The system property that enables the GPU checks and the message of a skipped check.
     */
    private static final @NotNull String RENDER_SMOKE_PROPERTY = "sc4j.render.smoke";
    private static final @NotNull String RENDER_SMOKE_MESSAGE = "Pass -Psc4j.render.smoke=true to run the GPU check.";
    /**
     * The width and height of the hidden window in pixels, and the width and height of the map in tiles.
     */
    private static final int SIZE = 128;
    private static final int MAP_TILES = 4;
    /**
     * Two screen pixels per world pixel, so each HD2 texel covers one screen pixel.
     */
    private static final int SCREEN_SCALE = 2;
    /**
     * The game pixels of a tile, and the HD2 megatile size in texels and screen pixels.
     */
    private static final int TILE_SIZE = 32;
    private static final int MEGATILE_SIZE = TILE_SIZE * SCREEN_SCALE;
    /**
     * The frame units per game pixel of an HD anim.
     */
    private static final int HD_FRAME_SCALE = 4;
    /**
     * The Jungle tileset and a tile identifier of it (CV5 group {@code 2}, megatile {@code 0}).
     */
    private static final int JUNGLE_TILESET = 4;
    private static final int TILE = 0x20;
    /**
     * The bit shifts of the red and green channels in a {@code 0xRRGGBB} color, and the mask of one channel.
     */
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int BYTE_MASK = 0xFF;

    /**
     * The number of SD frames that the checked count of the SD frame placement must exceed.
     */
    private static final int MINIMUM_CHECKED_FRAMES = 500;
    /**
     * Images: the Zealot and its shadow.
     */
    private static final int ZEALOT = 151;
    private static final int ZEALOT_SHADOW = 152;
    /**
     * The selection circle of {@code sprites.dat} circle {@code 2} ({@code 48} pixels).
     */
    private static final int CIRCLE_IMAGE = OverlayAssets.FIRST_CIRCLE_IMAGE + 2;
    /**
     * The world position of the drawn sprites.
     */
    private static final int SPRITE_X = 32;
    private static final int SPRITE_Y = 32;
    /**
     * The player colors of the red and the blue Zealot, and a frame index past the Zealot frames.
     */
    private static final int RED_PLAYER_COLOR = 0;
    private static final int BLUE_PLAYER_COLOR = 1;
    private static final int MISSING_FRAME = 100000;
    /**
     * The world y coordinate of the health bar center: the place of its health bar image.
     */
    private static final int HEALTH_BAR_Y = 40;
    /**
     * The health bar width and percentages, and the drawn bar height with shields; {@code -1} draws no energy bar.
     */
    private static final int HEALTH_BAR_WIDTH = 30;
    private static final int HIT_POINTS_PERCENT = 50;
    private static final int SHIELD_PERCENT = 100;
    private static final int NO_ENERGY = -1;
    private static final int DRAWN_BAR_HEIGHT = 7;

    /**
     * The sampled megatile texel strides and the tolerated channel difference.
     */
    private static final int TEXEL_ROW_STRIDE = 7;
    private static final int TEXEL_COLUMN_STRIDE = 5;
    private static final int TEXEL_TOLERANCE = 3;
    /**
     * The numbers of changed pixels or channels that the sprite, team color, shadow, bar and circle checks must exceed.
     */
    private static final int MINIMUM_SPRITE_PIXELS = 200;
    private static final int MINIMUM_TEAM_PIXELS = 10;
    private static final int MINIMUM_DARKENED_CHANNELS = 100;
    private static final int MINIMUM_BAR_PIXELS = 500;
    private static final int MINIMUM_RED_PIXELS = 50;
    /**
     * The channel decrease of a darkened channel and the red excess of a reddish pixel.
     */
    private static final int DARKENED_DIFFERENCE = 2;
    private static final int RED_EXCESS = 40;
    /**
     * The red marker drawn after the HD scene, its size and its probed channel values.
     */
    private static final int MARKER_SIZE = 8;
    private static final int MARKER_COLOR = 0xFF0000FF;
    private static final int MARKER_RED = 255;
    private static final int MARKER_GREEN = 0;

    /**
     * The preload step limit, the upload time budget of one step and the minimum resident texture size in megabytes.
     */
    private static final int MAXIMUM_PRELOAD_STEPS = 10_000;
    private static final long PRELOAD_BUDGET_NANOSECONDS = 20_000_000L;
    private static final long MINIMUM_RESIDENT_MEGABYTES = 200L;
    private static final int MEGABYTE_SHIFT = 20;

    /**
     * A Jungle creep tile (CV5 group {@code 1}) and a Jungle tile whose megatile is water everywhere.
     */
    private static final int CREEP_TILE = 0x10;
    private static final int WATER_TILE = 0x60;
    /**
     * The water tile that receives the creep edge of its left creep neighbors, and the tile column of those neighbors.
     */
    private static final int EDGE_COLUMN = 1;
    private static final int EDGE_ROW = 1;
    private static final int CREEP_COLUMN = 0;
    /**
     * The frames drawn between the two compared water frames and the pause after each of them, so the water animation advances.
     */
    private static final int ANIMATION_FRAMES = 20;
    private static final long FRAME_PAUSE_NANOSECONDS = 16_000_000L;
    /**
     * The alpha of an opaque creep-edge texel, and the numbers of opaque edge pixels and animated water pixels the check must exceed.
     */
    private static final int OPAQUE_ALPHA = 255;
    private static final int MINIMUM_EDGE_PIXELS = 100;
    private static final int MINIMUM_ANIMATED_PIXELS = 500;

    private interface Scene {
        void draw(@NotNull HdSceneRenderer scene) throws IOException;
    }

    private static int @NotNull [] renderScene(@NotNull WorldRenderer world, @NotNull HdSceneRenderer scene,
                                               @NotNull Scene drawing,
                                               @NotNull ByteBuffer pixels) throws IOException {
        world.beginFrame(SIZE, SIZE, SIZE, SIZE, 0, 0, SCREEN_SCALE);

        try {
            drawing.draw(scene);
        } finally {
            scene.finishScene();
            world.endFrame();
        }

        return HiddenContextScenario.readColors(pixels, SIZE, SIZE);
    }


    @Test
    void sd_frames_are_stored_with_their_grp_offset() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            DatTable images = ImagesDat.createSchema().parse(storage.readFile("arr/images.dat"));
            TblFile names = TblFile.parse(storage.readFile("arr/images.tbl"));
            int checked = 0;

            for (int imageIdentifier = 0; imageIdentifier < ImagesDat.IMAGE_COUNT; imageIdentifier++) {
                String path = String.format("anim/main_%03d.anim", imageIdentifier);

                if (!storage.hasFile(path)) {
                    continue;
                }

                AnimFile.Entry entry = AnimFile.parse(storage.readFile(path)).getEntry(0);

                if (entry.isReference()) {
                    continue;
                }

                GrpFile spriteFile = GrpFile.parse(
                        storage.readFile("unit/" + names.getString(images.get(ImagesDat.GRAPHICS_FILE, imageIdentifier) - 1).replace('\\', '/'))
                );

                for (int frame = 0; frame < entry.getFrameCount(); frame++) {
                    if ((entry.getFrameFlags(frame) & HdSceneRenderer.FRAME_CANVAS_PLACED) != 0) {
                        continue;
                    }

                    // A frame without the flag is the SD frame scaled by 4, stored with the GRP offset: The renderer draws it from the
                    // image's classic map position.
                    String label = "image " + imageIdentifier + " frame " + frame;

                    Assertions.assertEquals(spriteFile.getFrameXOffset(frame), entry.getFrameXOffset(frame), label);
                    Assertions.assertEquals(spriteFile.getFrameYOffset(frame), entry.getFrameYOffset(frame), label);
                    Assertions.assertEquals(spriteFile.getFrameWidth(frame) * HD_FRAME_SCALE, entry.getFrameWidth(frame), label);
                    Assertions.assertEquals(spriteFile.getFrameHeight(frame) * HD_FRAME_SCALE, entry.getFrameHeight(frame), label);
                    checked++;
                }
            }

            Assertions.assertTrue(checked > MINIMUM_CHECKED_FRAMES, "SD frames checked: " + checked);
        }
    }

    @Test
    void hd_scene_draws_hd2_terrain_sprites_shadows_and_effects() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        HiddenContextScenario.run(SIZE, SIZE, "HD scene verification", () -> {
            int[] tiles = new int[MAP_TILES * MAP_TILES];

            Arrays.fill(tiles, TILE);

            try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); WorldRenderer world = new WorldRenderer()) {
                world.loadMap(ScrResourceProvider.borrow(storage), JUNGLE_TILESET, MAP_TILES, MAP_TILES, tiles);
                world.setRenderScaleLimit(HdRenderAssets.Quality.HD2.getScale());

                HdRenderAssets assets = HdRenderAssets.load(ScrResourceProvider.borrow(storage), JUNGLE_TILESET, HdRenderAssets.Quality.HD2);
                HdSceneRenderer scene = new HdSceneRenderer(world, assets);
                ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * OverlayPass.RGBA_BYTES);

                try {
                    // Terrain: at two screen pixels per world pixel each HD2 megatile texel covers one screen pixel.
                    int[] terrain = renderScene(world, scene, renderer -> renderer.drawTerrain(index -> TILE), pixels);
                    byte[] megatile = assets.getMegatiles().getFrame(assets.getMegatile(TILE)).decodeRgba();

                    for (int vertical = 0; vertical < MEGATILE_SIZE; vertical += TEXEL_ROW_STRIDE) {
                        for (int horizontal = 0; horizontal < MEGATILE_SIZE; horizontal += TEXEL_COLUMN_STRIDE) {
                            int texel = (vertical * MEGATILE_SIZE + horizontal) * OverlayPass.RGBA_BYTES;
                            int expected = Byte.toUnsignedInt(megatile[texel]) << RED_SHIFT |
                                           Byte.toUnsignedInt(megatile[texel + OverlayPass.RGBA_GREEN]) << GREEN_SHIFT |
                                           Byte.toUnsignedInt(megatile[texel + OverlayPass.RGBA_BLUE]);

                            int actual = terrain[vertical * SIZE + horizontal];
                            String message = "megatile texel " + horizontal + "," + vertical;

                            for (int shift = 0; shift <= RED_SHIFT; shift += Byte.SIZE) {
                                Assertions.assertTrue(Math.abs((expected >>> shift & BYTE_MASK) - (actual >>> shift & BYTE_MASK)) <= TEXEL_TOLERANCE,
                                                      message + ": expected " + Integer.toHexString(expected) + " actual " +
                                                      Integer.toHexString(actual));
                            }

                            Assertions.assertEquals(terrain[vertical * SIZE + horizontal],
                                                    terrain[vertical * SIZE + horizontal + MEGATILE_SIZE],
                                                    "repeated tile");
                        }
                    }

                    // Team color changes only masked pixels; the frame lies inside its canvas-centered rectangle.
                    int[] red = renderScene(
                            world, scene,
                            renderer -> {
                                renderer.drawTerrain(index -> TILE);
                                renderer.drawImage(ZEALOT, 0, SceneRenderer.DRAW_FUNCTION_NORMAL, 0, RED_PLAYER_COLOR, SPRITE_X, SPRITE_Y, false);
                            },
                            pixels
                    );
                    int[] blue = renderScene(
                            world, scene,
                            renderer -> {
                                renderer.drawTerrain(index -> TILE);
                                renderer.drawImage(ZEALOT, 0, SceneRenderer.DRAW_FUNCTION_NORMAL, 0, BLUE_PLAYER_COLOR, SPRITE_X, SPRITE_Y, false);
                            },
                            pixels
                    );
                    TextureSheet animation = assets.getAnimations().loadTextureSheet(ZEALOT);

                    Assertions.assertNotNull(animation);

                    TextureFrame frame = animation.getFrame(0);
                    float left = ((SPRITE_X * HD_FRAME_SCALE - animation.getWidth() / 2) + frame.getX()) / (float) SCREEN_SCALE;
                    float top = ((SPRITE_Y * HD_FRAME_SCALE - animation.getHeight() / 2) + frame.getY()) / (float) SCREEN_SCALE;
                    float right = left + frame.getWidth() / (float) SCREEN_SCALE;
                    float bottom = top + frame.getHeight() / (float) SCREEN_SCALE;
                    int changedByTeam = 0;
                    int changedBySprite = 0;

                    for (int index = 0; index < SIZE * SIZE; index++) {
                        int horizontal = index % SIZE;
                        int vertical = index / SIZE;
                        boolean inside = horizontal >= Math.floor(left) - 1 && horizontal <= Math.ceil(right) &&
                                         vertical >= Math.floor(top) - 1 && vertical <= Math.ceil(bottom);

                        if (red[index] != terrain[index]) {
                            changedBySprite++;
                            Assertions.assertTrue(inside, "sprite pixel outside its frame rectangle at " + horizontal + "," + vertical);
                        }

                        if (red[index] != blue[index]) {
                            changedByTeam++;
                            Assertions.assertTrue(inside, "team color outside the frame rectangle");
                        }
                    }

                    Assertions.assertTrue(changedBySprite > MINIMUM_SPRITE_PIXELS, "the zealot frame is drawn: " + changedBySprite);
                    Assertions.assertTrue(changedByTeam > MINIMUM_TEAM_PIXELS && changedByTeam < changedBySprite,
                                          "only team-color pixels differ: " + changedByTeam);

                    // Flipping mirrors the frame rectangle inside the canvas.
                    int[] flipped = renderScene(
                            world, scene,
                            renderer -> {
                                renderer.drawTerrain(index -> TILE);
                                renderer.drawImage(ZEALOT, 0, SceneRenderer.DRAW_FUNCTION_NORMAL, 0, 0, SPRITE_X, SPRITE_Y, true);
                            },
                            pixels
                    );
                    float flippedLeft = ((SPRITE_X * HD_FRAME_SCALE - (animation.getWidth() - animation.getWidth() / 2)) + animation.getWidth() -
                                         frame.getX() - frame.getWidth()) / (float) SCREEN_SCALE;

                    for (int index = 0; index < SIZE * SIZE; index++) {
                        if (flipped[index] != terrain[index]) {
                            Assertions.assertTrue(index % SIZE >= Math.floor(flippedLeft) - 1 &&
                                                  index % SIZE <= Math.ceil(flippedLeft + frame.getWidth() / (float) SCREEN_SCALE),
                                                  "flipped pixel outside the mirrored rectangle");
                        }
                    }

                    // Shadows darken the resolved color by at most half and never brighten it.
                    int[] shadow = renderScene(
                            world, scene,
                            renderer -> {
                                renderer.drawTerrain(index -> TILE);
                                renderer.drawImage(ZEALOT_SHADOW, 0, SceneRenderer.DRAW_FUNCTION_SHADOW, 0, 0, SPRITE_X, SPRITE_Y, false);
                            },
                            pixels
                    );
                    int darkened = 0;

                    for (int index = 0; index < SIZE * SIZE; index++) {
                        for (int shift = 0; shift <= RED_SHIFT; shift += Byte.SIZE) {
                            int before = terrain[index] >>> shift & BYTE_MASK;
                            int after = shadow[index] >>> shift & BYTE_MASK;

                            Assertions.assertTrue(after <= before + 1 && after >= before / 2 - 1, "shadow range at " + index);

                            if (after + DARKENED_DIFFERENCE < before) {
                                darkened++;
                            }
                        }
                    }

                    Assertions.assertTrue(darkened > MINIMUM_DARKENED_CHANNELS, "the shadow darkens terrain: " + darkened);

                    // Hallucination tints the luminance: the tint raises blue above red.
                    int[] hallucinated = renderScene(
                            world, scene,
                            renderer -> {
                                renderer.drawTerrain(index -> TILE);
                                renderer.drawImage(ZEALOT, 0, SceneRenderer.DRAW_FUNCTION_HALLUCINATION, 0, 0, SPRITE_X, SPRITE_Y, false);
                            },
                            pixels
                    );
                    int bluer = 0;

                    for (int index = 0; index < SIZE * SIZE; index++) {
                        if (hallucinated[index] != terrain[index] &&
                            (hallucinated[index] & BYTE_MASK) > (hallucinated[index] >>> RED_SHIFT & BYTE_MASK)) {
                            bluer++;
                        }
                    }

                    Assertions.assertTrue(bluer > changedBySprite / 2, "hallucinated pixels are tinted blue: " + bluer);
                    Assertions.assertThrows(IOException.class,
                                            () -> renderScene(world, scene,
                                                              renderer -> renderer.drawImage(ZEALOT,
                                                                                             0,
                                                                                             SceneRenderer.DRAW_FUNCTION_SELECTION_CIRCLE,
                                                                                             0,
                                                                                             0,
                                                                                             SPRITE_X,
                                                                                             SPRITE_Y,
                                                                                             false),
                                                              pixels));
                    Assertions.assertThrows(IOException.class,
                                            () -> renderScene(world, scene,
                                                              renderer -> renderer.drawImage(ZEALOT,
                                                                                             MISSING_FRAME,
                                                                                             SceneRenderer.DRAW_FUNCTION_NORMAL,
                                                                                             0,
                                                                                             0,
                                                                                             SPRITE_X,
                                                                                             SPRITE_Y,
                                                                                             false),
                                                              pixels));

                    // Checks the health-bar pixel extent at the view scale.
                    int[] bar = renderScene(
                            world, scene,
                            renderer -> {
                                renderer.drawTerrain(index -> TILE);
                                renderer.drawHealthBar(SPRITE_X, HEALTH_BAR_Y, HEALTH_BAR_WIDTH, HIT_POINTS_PERCENT, SHIELD_PERCENT, NO_ENERGY);
                            },
                            pixels
                    );
                    int barLeft = (SPRITE_X - HEALTH_BAR_WIDTH / 2) * SCREEN_SCALE;
                    int barTop = (HEALTH_BAR_Y - DRAWN_BAR_HEIGHT / 2) * SCREEN_SCALE;
                    int barPixels = 0;

                    for (int index = 0; index < SIZE * SIZE; index++) {
                        if (bar[index] == terrain[index]) {
                            continue;
                        }

                        int horizontal = index % SIZE;
                        int vertical = index / SIZE;

                        barPixels++;
                        Assertions.assertTrue(horizontal >= barLeft && horizontal < barLeft + HEALTH_BAR_WIDTH * SCREEN_SCALE &&
                                              vertical >= barTop && vertical < barTop + DRAWN_BAR_HEIGHT * SCREEN_SCALE,
                                              "bar pixel outside its rectangle at " + horizontal + "," + vertical);
                    }

                    Assertions.assertTrue(barPixels > MINIMUM_BAR_PIXELS, "the bar is drawn: " + barPixels);

                    // Selection circles: the white HD ring tinted by the relation color.
                    int[] circle = renderScene(
                            world, scene,
                            renderer -> {
                                renderer.drawTerrain(index -> TILE);
                                renderer.drawSelectionCircle(CIRCLE_IMAGE, SceneRenderer.RELATION_ENEMY, SPRITE_X, SPRITE_Y);
                            },
                            pixels
                    );
                    int reddish = 0;

                    for (int index = 0; index < SIZE * SIZE; index++) {
                        if (circle[index] != terrain[index] &&
                            (circle[index] >>> RED_SHIFT & BYTE_MASK) > (circle[index] >>> GREEN_SHIFT & BYTE_MASK) + RED_EXCESS) {
                            reddish++;
                        }
                    }

                    Assertions.assertTrue(reddish > MINIMUM_RED_PIXELS, "the enemy circle is red: " + reddish);

                    // The world renderer's state is restored: classic overlays still draw after the HD scene.
                    world.beginFrame(SIZE, SIZE, SIZE, SIZE, 0, 0, SCREEN_SCALE);
                    scene.drawTerrain(index -> TILE);
                    scene.finishScene();
                    world.finishWorld();
                    world.fillRectangle(0, 0, MARKER_SIZE, MARKER_SIZE, MARKER_COLOR);
                    world.endFrame();
                    glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

                    int topLeft = (SIZE - 1) * SIZE * OverlayPass.RGBA_BYTES;

                    Assertions.assertEquals(MARKER_RED, Byte.toUnsignedInt(pixels.get(topLeft)));
                    Assertions.assertEquals(MARKER_GREEN, Byte.toUnsignedInt(pixels.get(topLeft + OverlayPass.RGBA_GREEN)));
                    Assertions.assertTrue(scene.getTextureBytes() > 0);
                } finally {
                    MemoryUtil.memFree(pixels);
                    scene.close();
                }
            }
        });
    }

    @Test
    void creep_edges_over_water_stay_still_while_the_water_animates() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        HiddenContextScenario.run(SIZE, SIZE, "HD creep over water verification", () -> {
            int[] tiles = new int[MAP_TILES * MAP_TILES];

            for (int index = 0; index < tiles.length; index++) {
                tiles[index] = index % MAP_TILES == CREEP_COLUMN ? CREEP_TILE : WATER_TILE;
            }

            try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); WorldRenderer world = new WorldRenderer()) {
                world.loadMap(ScrResourceProvider.borrow(storage), JUNGLE_TILESET, MAP_TILES, MAP_TILES, tiles);
                world.setRenderScaleLimit(HdRenderAssets.Quality.HD2.getScale());

                HdRenderAssets assets = HdRenderAssets.load(ScrResourceProvider.borrow(storage), JUNGLE_TILESET, HdRenderAssets.Quality.HD2);
                HdSceneRenderer scene = new HdSceneRenderer(world, assets);
                ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * OverlayPass.RGBA_BYTES);

                try {
                    int[] first = renderScene(world, scene, renderer -> renderer.drawTerrain(index -> tiles[index]), pixels);

                    for (int frame = 0; frame < ANIMATION_FRAMES; frame++) {
                        LockSupport.parkNanos(FRAME_PAUSE_NANOSECONDS);
                        renderScene(world, scene, renderer -> renderer.drawTerrain(index -> tiles[index]), pixels);
                    }

                    int[] second = renderScene(world, scene, renderer -> renderer.drawTerrain(index -> tiles[index]), pixels);
                    int animated = 0;

                    for (int index = 0; index < SIZE * SIZE; index++) {
                        if (first[index] != second[index]) {
                            animated++;
                        }
                    }

                    Assertions.assertTrue(animated > MINIMUM_ANIMATED_PIXELS, "the water animates: " + animated);

                    // The water tile's left neighbors hold creep, so it receives that creep edge; its opaque texels cover one screen pixel each.
                    int neighborMask = 0;

                    for (int direction = 0; direction < WorldRenderer.getCreepNeighborCount(); direction++) {
                        if (EDGE_COLUMN + WorldRenderer.getCreepNeighborHorizontal(direction) == CREEP_COLUMN) {
                            neighborMask |= 1 << direction;
                        }
                    }

                    int edgeFrame = WorldRenderer.getCreepEdgeFrame(neighborMask) - 1;
                    DdsFile edge = assets.getCreepEdges().getFrame(edgeFrame);
                    byte[] edgeTexels = edge.decodeRgba();
                    int edgeLeft = EDGE_COLUMN * MEGATILE_SIZE + assets.getCreepEdgeXOffset(edgeFrame);
                    int edgeTop = EDGE_ROW * MEGATILE_SIZE + assets.getCreepEdgeYOffset(edgeFrame);
                    int edgePixels = 0;

                    for (int vertical = 0; vertical < edge.getHeight(); vertical++) {
                        for (int horizontal = 0; horizontal < edge.getWidth(); horizontal++) {
                            int texel = (vertical * edge.getWidth() + horizontal) * OverlayPass.RGBA_BYTES;
                            int screenHorizontal = edgeLeft + horizontal;
                            int screenVertical = edgeTop + vertical;

                            if (Byte.toUnsignedInt(edgeTexels[texel + OverlayPass.RGBA_ALPHA]) != OPAQUE_ALPHA ||
                                screenHorizontal >= SIZE || screenVertical >= SIZE) {
                                continue;
                            }

                            int index = screenVertical * SIZE + screenHorizontal;

                            Assertions.assertEquals(first[index], second[index],
                                                    "creep edge pixel animated at " + screenHorizontal + "," + screenVertical);
                            edgePixels++;
                        }
                    }

                    Assertions.assertTrue(edgePixels > MINIMUM_EDGE_PIXELS, "opaque creep-edge pixels checked: " + edgePixels);
                } finally {
                    MemoryUtil.memFree(pixels);
                    scene.close();
                }
            }
        });
    }

    @Test
    void preloaded_images_appear_on_their_first_streamed_draw() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        HiddenContextScenario.run(SIZE, SIZE, "HD preload verification", () -> {
            int[] tiles = new int[MAP_TILES * MAP_TILES];

            Arrays.fill(tiles, TILE);

            try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); WorldRenderer world = new WorldRenderer()) {
                world.loadMap(ScrResourceProvider.borrow(storage), JUNGLE_TILESET, MAP_TILES, MAP_TILES, tiles);
                world.setRenderScaleLimit(HdRenderAssets.Quality.HD2.getScale());

                HdSceneRenderer scene = new HdSceneRenderer(world, HdRenderAssets.load(ScrResourceProvider.borrow(storage), JUNGLE_TILESET,
                                                                                       HdRenderAssets.Quality.HD2));
                ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * OverlayPass.RGBA_BYTES);

                try {
                    scene.setStreaming(true);
                    scene.startPreload(index -> TILE);
                    Assertions.assertTrue(scene.isPreloading());

                    float progress = 0;

                    for (int step = 0; step < MAXIMUM_PRELOAD_STEPS && scene.isPreloading(); step++) {
                        float next = scene.continuePreload(PRELOAD_BUDGET_NANOSECONDS);

                        Assertions.assertTrue(next >= progress, "the progress never goes back");
                        progress = next;
                    }

                    Assertions.assertFalse(scene.isPreloading(), "the preload completes");
                    Assertions.assertEquals(1, progress);
                    Assertions.assertTrue(scene.getTextureBytes() > MINIMUM_RESIDENT_MEGABYTES << MEGABYTE_SHIFT,
                                          "all images are resident: " + (scene.getTextureBytes() >> MEGABYTE_SHIFT) + " MB");

                    // A streamed first draw skips images that are not uploaded yet; preloaded ones are drawn at once.
                    int[] terrain = renderScene(world, scene, renderer -> renderer.drawTerrain(index -> TILE), pixels);
                    int[] zealot = renderScene(
                            world, scene,
                            renderer -> {
                                renderer.drawTerrain(index -> TILE);
                                renderer.drawImage(ZEALOT, 0, SceneRenderer.DRAW_FUNCTION_NORMAL, 0, 0, SPRITE_X, SPRITE_Y, false);
                            },
                            pixels
                    );

                    Assertions.assertFalse(Arrays.equals(terrain, zealot), "the zealot is drawn on its first frame");
                } finally {
                    MemoryUtil.memFree(pixels);
                    scene.close();
                }
            }
        });
    }

    @Test
    void draw_shadow_image_should_reject_a_closed_renderer_like_the_other_draws() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        HiddenContextScenario.run(SIZE, SIZE, "HD closed scene verification", () -> {
            int[] tiles = new int[MAP_TILES * MAP_TILES];

            Arrays.fill(tiles, TILE);

            try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); WorldRenderer world = new WorldRenderer()) {
                world.loadMap(ScrResourceProvider.borrow(storage), JUNGLE_TILESET, MAP_TILES, MAP_TILES, tiles);
                world.setRenderScaleLimit(HdRenderAssets.Quality.HD2.getScale());

                HdSceneRenderer scene = new HdSceneRenderer(world, HdRenderAssets.load(ScrResourceProvider.borrow(storage), JUNGLE_TILESET,
                                                                                       HdRenderAssets.Quality.HD2));

                scene.close();

                // A shadow that is the first draw of a frame begins the scene, which a closed renderer refuses.
                Assertions.assertThrows(IllegalStateException.class,
                                        () -> scene.drawImage(ZEALOT, 0, SceneRenderer.DRAW_FUNCTION_NORMAL, 0, 0, SPRITE_X, SPRITE_Y, false));
                Assertions.assertThrows(IllegalStateException.class, () -> scene.drawShadowImage(ZEALOT_SHADOW, 0, -1, SPRITE_X, SPRITE_Y, false));
            }
        });
    }
}
