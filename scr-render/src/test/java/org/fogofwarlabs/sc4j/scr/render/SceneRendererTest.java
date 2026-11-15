package org.fogofwarlabs.sc4j.scr.render;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrResourceProvider;
import org.fogofwarlabs.sc4j.scr.render.hd.HdRenderAssets;
import org.fogofwarlabs.sc4j.scr.render.hd.HdSceneRenderer;
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
 * Tests that the classic and HD {@link SceneRenderer} backends draw selection circles, health bars, fog and deferred lighting, that switching between
 * them on one context leaves no residue in the classic frame, and the warp flash alpha of the scene contract.
 */
final class SceneRendererTest {
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
     * The view scale of the HD scenes.
     */
    private static final float HD_SCALE = 2;
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
     * Images: the Zealot, a frame flagged without lighting data, the Probe shadow and the Bunker flash.
     */
    private static final int ZEALOT = 151;
    private static final int UNLIT_FRAME_IMAGE = 8;
    private static final int UNLIT_FRAME = 232;
    private static final int PROBE_SHADOW = 138;
    private static final int BUNKER_FLASH = 306;
    /**
     * The selection circle of {@code sprites.dat} circle {@code 2} ({@code 48} pixels), its classic world position and the minimum number of drawn
     * circle pixels.
     */
    private static final int CIRCLE_IMAGE = OverlayAssets.FIRST_CIRCLE_IMAGE + 2;
    private static final int CIRCLE_X = 64;
    private static final int CIRCLE_Y = 64;
    private static final int MINIMUM_CIRCLE_PIXELS = 50;
    /**
     * The number of {@code tselect} palette indices of one relation group.
     */
    private static final int SELECTION_GROUP_SIZE = 8;
    /**
     * The world coordinates of the classic health bar center: the place of its health bar image.
     */
    private static final int HEALTH_BAR_X = 64;
    private static final int HEALTH_BAR_Y = 60;
    /**
     * The requested health bar width, full percentages and the percentage of a missing bar.
     */
    private static final int HEALTH_BAR_WIDTH = 30;
    private static final int FULL_PERCENT = 100;
    private static final int NO_BAR = -1;
    /**
     * The drawn classic bar without shields: five rows high and {@code 28} pixels wide ({@code 30} reduced to {@code 3n + 1}).
     */
    private static final int DRAWN_BAR_HEIGHT = 5;
    private static final int DRAWN_BAR_WIDTH = 28;
    /**
     * The {@code thpbar} color of the bar border and the separators, and the {@code thpbar} color of the first health row.
     */
    private static final int BORDER_COLOR = 18;
    private static final int FIRST_HEALTH_ROW_COLOR = 0;
    /**
     * The sampled separator pixel inside the drawn bar.
     */
    private static final int SEPARATOR_ROW = 2;
    private static final int SEPARATOR_COLUMN = 3;

    /**
     * Tile flags of a tile explored but not visible, and of a tile neither visible nor explored, by any player.
     */
    private static final int EXPLORED_FLAGS = 0xFF;
    private static final int UNEXPLORED_FLAGS = 0xFFFF;
    private static final int FIRST_PLAYER_VISION = 1;
    /**
     * The fog states: visible, explored and unexplored.
     */
    private static final int VISIBLE_STATE = 0;
    private static final int EXPLORED_STATE = 1;
    private static final int FOG_STATE_COUNT = 3;
    /**
     * The brightness of explored, not visible tiles at the default fog level: {@code 1 - 170 / 255}.
     */
    private static final float EXPLORED_BRIGHTNESS = 1.0f / 3.0f;
    /**
     * The pixel stride of the fog samples and the tolerated green difference.
     */
    private static final int FOG_SAMPLE_STRIDE = 97;
    private static final int FOG_TOLERANCE = 2;

    /**
     * The world position of the HD sprites, the HD selection circle and the HD health bar, and the hit point percentage of the HD health bar.
     */
    private static final int SPRITE_X = 32;
    private static final int SPRITE_Y = 32;
    private static final int HD_CIRCLE_Y = 40;
    private static final int HD_HEALTH_BAR_Y = 52;
    private static final int HD_HIT_POINTS_PERCENT = 60;
    /**
     * The minimum numbers of pixels changed by lighting and drawn by the burrow frame.
     */
    private static final int MINIMUM_LIT_CHANGES = 1000;
    private static final int MINIMUM_BURROW_PIXELS = 100;
    /**
     * The expected ratios of the lit to the forward channel sums and their tolerances.
     */
    private static final double DIFFUSE_RATIO = 1.0;
    private static final double DIFFUSE_RATIO_TOLERANCE = 0.1;
    private static final double LAMBERT_RATIO = 0.9245;
    private static final double LAMBERT_RATIO_TOLERANCE = 0.01;
    /**
     * The minimum channel sum darkening of the Probe shadow.
     */
    private static final int MINIMUM_SHADOW_DARKENING = 1000;
    /**
     * The Bunker flashes drawn before the last light, their position, the last light position and the probed pixel beside it.
     */
    private static final int CROWDED_LIGHTS = 64;
    private static final int CROWD_X = 16;
    private static final int CROWD_Y = 16;
    private static final int LAST_LIGHT_X = 100;
    private static final int LAST_LIGHT_Y = 100;
    private static final int BESIDE_ROW = 120;
    private static final int BESIDE_COLUMN = 100;

    /**
     * Images of the backend switch scene: the Zealot shadow and the mining spark (lighting table on unit {@code 3}).
     */
    private static final int ZEALOT_SHADOW = 152;
    private static final int MINING_SPARK = 520;
    /**
     * The world positions of the shadowed Zealot, the mining spark and the selection circle of the backend switch scene.
     */
    private static final int ZEALOT_X = 40;
    private static final int ZEALOT_Y = 40;
    private static final int MINING_SPARK_X = 80;
    private static final int MINING_SPARK_Y = 50;
    private static final int SWITCH_CIRCLE_X = 40;
    private static final int SWITCH_CIRCLE_Y = 48;
    /**
     * The frame, player color and world position of the cloaked Zealot.
     */
    private static final int CLOAKED_FRAME = 3;
    private static final int CLOAKED_PLAYER_COLOR = 1;
    private static final int CLOAKED_X = 90;
    private static final int CLOAKED_Y = 90;
    /**
     * The health bar center and percentages of the backend switch scene ({@link #HEALTH_BAR_WIDTH} wide); {@code -1} draws no energy bar.
     */
    private static final int SWITCH_HEALTH_BAR_X = 40;
    private static final int SWITCH_HEALTH_BAR_Y = 60;
    private static final int HIT_POINTS_PERCENT = 70;
    private static final int SHIELD_PERCENT = 50;
    private static final int NO_ENERGY = -1;

    /**
     * The explored but not visible tile column of the backend switch scene.
     */
    private static final int EXPLORED_COLUMN = 3;

    /**
     * Warp flash data values: the first (alpha {@code 1}) and the last (alpha {@code 0}) of the flash range, inside the range, below and above it,
     * and with bits above the data byte, and the tolerated brighten alpha difference.
     */
    private static final int FIRST_FLASH_DATA = 48;
    private static final int LAST_FLASH_DATA = 63;
    private static final int MIDDLE_FLASH_DATA = 56;
    private static final int BELOW_FLASH_DATA = 47;
    private static final int ABOVE_FLASH_DATA = 64;
    private static final int HIGH_BITS_FLASH_DATA = 0x230;
    private static final float ALPHA_TOLERANCE = 1e-6f;

    private interface Scene {
        void draw() throws IOException;
    }

    private interface BackendDrawing {
        void draw(@NotNull SceneRenderer scene) throws IOException;
    }

    private static int @NotNull [] renderSceneFrame(@NotNull WorldRenderer world, @NotNull ByteBuffer pixels, float scale, @NotNull Scene drawing) throws IOException {
        world.beginFrame(SIZE, SIZE, SIZE, SIZE, 0, 0, scale);
        drawing.draw();
        world.endFrame();

        return HiddenContextScenario.readColors(pixels, SIZE, SIZE);
    }

    private static int @NotNull [] renderBackendFrame(@NotNull WorldRenderer world, @NotNull SceneRenderer scene,
                                                      @NotNull BackendDrawing drawing,
                                                      @NotNull ByteBuffer pixels) throws IOException {
        world.beginFrame(SIZE, SIZE, SIZE, SIZE, 0, 0, 1);
        drawing.draw(scene);
        world.endFrame();
        glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

        int[] result = new int[SIZE * SIZE];

        for (int index = 0; index < result.length; index++) {
            result[index] = pixels.getInt(index * OverlayPass.RGBA_BYTES);
        }

        return result;
    }

    @RequiresInstallation
    @Test
    void scene_overlay_draws_circles_bars_fog_and_lighting() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        HiddenContextScenario.run(SIZE, SIZE, "Overlay verification", () -> {
            int[] tiles = new int[MAP_TILES * MAP_TILES];

            Arrays.fill(tiles, TILE);

            try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); WorldRenderer world = new WorldRenderer();
                 ClassicSceneRenderer classic = new ClassicSceneRenderer(world, ScrResourceProvider.borrow(storage), JUNGLE_TILESET)) {
                world.loadMap(ScrResourceProvider.borrow(storage), JUNGLE_TILESET, MAP_TILES, MAP_TILES, tiles);

                OverlayAssets overlayAssets = OverlayAssets.load(ScrResourceProvider.borrow(storage), JUNGLE_TILESET);
                ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * OverlayPass.RGBA_BYTES);

                try {
                    int[] terrain = renderSceneFrame(world, pixels, 1, () -> {
                        classic.drawTerrain(index -> TILE);
                        classic.finishScene();
                    });

                    // Classic circles recolor GRP indices 0..7 with the tselect group of the relation.
                    for (int relation = SceneRenderer.RELATION_OWN; relation <= SceneRenderer.RELATION_ENEMY; relation++) {
                        int circleRelation = relation;
                        int[] circle = renderSceneFrame(world, pixels, 1, () -> {
                            classic.drawTerrain(index -> TILE);
                            classic.drawSelectionCircle(CIRCLE_IMAGE, circleRelation, CIRCLE_X, CIRCLE_Y);
                            classic.finishScene();
                        });
                        int matching = 0;

                        for (int index = 0; index < SIZE * SIZE; index++) {
                            if (circle[index] == terrain[index]) {
                                continue;
                            }

                            boolean groupColor = false;

                            for (int entry = 0; entry < SELECTION_GROUP_SIZE; entry++) {
                                groupColor |= circle[index] == overlayAssets.getSelectionColor(circleRelation, entry);
                            }

                            Assertions.assertTrue(groupColor, "circle pixel of relation " + circleRelation + " has a tselect color");
                            matching++;
                        }

                        Assertions.assertTrue(matching > MINIMUM_CIRCLE_PIXELS, "the circle is drawn: " + matching);
                    }

                    // Classic health bar: black border row, health row color, separator column.
                    int[] bar = renderSceneFrame(world, pixels, 1, () -> {
                        classic.drawTerrain(index -> TILE);
                        classic.drawHealthBar(HEALTH_BAR_X, HEALTH_BAR_Y, HEALTH_BAR_WIDTH, FULL_PERCENT, NO_BAR, NO_BAR);
                        classic.finishScene();
                    });
                    int barTop = HEALTH_BAR_Y - DRAWN_BAR_HEIGHT / 2;
                    int barLeft = HEALTH_BAR_X - DRAWN_BAR_WIDTH / 2;

                    Assertions.assertEquals(overlayAssets.getBarColor(BORDER_COLOR), bar[barTop * SIZE + barLeft + 1], "top border row");
                    Assertions.assertEquals(overlayAssets.getBarColor(FIRST_HEALTH_ROW_COLOR),
                                            bar[(barTop + 1) * SIZE + barLeft + 1],
                                            "first health row");
                    Assertions.assertEquals(overlayAssets.getBarColor(BORDER_COLOR),
                                            bar[(barTop + SEPARATOR_ROW) * SIZE + barLeft + SEPARATOR_COLUMN],
                                            "separator column");

                    // Fog keeps visible colors, dims explored tiles and blacks out unexplored tiles.
                    for (int state = 0; state < FOG_STATE_COUNT; state++) {
                        int flags = state == VISIBLE_STATE ? 0 : state == EXPLORED_STATE ? EXPLORED_FLAGS : UNEXPLORED_FLAGS;
                        int[] fog = renderSceneFrame(world, pixels, 1, () -> {
                            classic.drawTerrain(index -> TILE);
                            classic.drawFog(index -> flags, FIRST_PLAYER_VISION);
                            classic.finishScene();
                        });
                        float expected = state == VISIBLE_STATE ? 1 : state == EXPLORED_STATE ? EXPLORED_BRIGHTNESS : 0;

                        for (int index = 0; index < SIZE * SIZE; index += FOG_SAMPLE_STRIDE) {
                            int before = terrain[index] >>> GREEN_SHIFT & BYTE_MASK;
                            int after = fog[index] >>> GREEN_SHIFT & BYTE_MASK;

                            Assertions.assertTrue(Math.abs(after - before * expected) <= FOG_TOLERANCE,
                                                  "fog state " + state + ": " + before + " -> " + after);
                        }
                    }

                    // HD deferred lighting renders the lit scene and differs from the forward path.
                    HdSceneRenderer hdScene = new HdSceneRenderer(world, HdRenderAssets.load(ScrResourceProvider.borrow(storage), JUNGLE_TILESET,
                                                                                             HdRenderAssets.Quality.HD2));

                    try {
                        Scene zealot = () -> {
                            hdScene.drawTerrain(index -> TILE);
                            hdScene.drawImage(ZEALOT, 0, SceneRenderer.DRAW_FUNCTION_NORMAL, 0, 0, SPRITE_X, SPRITE_Y, false);
                            hdScene.drawSelectionCircle(CIRCLE_IMAGE, SceneRenderer.RELATION_OWN, SPRITE_X, HD_CIRCLE_Y);
                            hdScene.drawHealthBar(SPRITE_X, HD_HEALTH_BAR_Y, HEALTH_BAR_WIDTH, HD_HIT_POINTS_PERCENT, FULL_PERCENT, NO_BAR);
                            hdScene.finishScene();
                        };

                        int[] forward = renderSceneFrame(world, pixels, HD_SCALE, zealot);

                        hdScene.setLightingEnabled(true);
                        Assertions.assertTrue(hdScene.isLightingEnabled());

                        int[] lit = renderSceneFrame(world, pixels, HD_SCALE, zealot);
                        int changed = 0;

                        for (int index = 0; index < SIZE * SIZE; index++) {
                            changed += forward[index] != lit[index] ? 1 : 0;
                        }

                        Assertions.assertTrue(changed > MINIMUM_LIT_CHANGES, "lighting changes the scene: " + changed);

                        // Frames without lighting data retain their diffuse color; flat terrain receives directional lighting.
                        Scene flatTerrain = () -> {
                            hdScene.drawTerrain(index -> TILE);
                            hdScene.finishScene();
                        };
                        Scene burrow = () -> {
                            hdScene.drawTerrain(index -> TILE);
                            hdScene.drawImage(UNLIT_FRAME_IMAGE, UNLIT_FRAME, SceneRenderer.DRAW_FUNCTION_NORMAL, 0, 0, SPRITE_X, SPRITE_Y, false);
                            hdScene.finishScene();
                        };
                        int[] litTerrain = renderSceneFrame(world, pixels, HD_SCALE, flatTerrain);
                        int[] litBurrow = renderSceneFrame(world, pixels, HD_SCALE, burrow);

                        hdScene.setLightingEnabled(false);

                        int[] forwardTerrain = renderSceneFrame(world, pixels, HD_SCALE, flatTerrain);
                        int[] forwardBurrow = renderSceneFrame(world, pixels, HD_SCALE, burrow);
                        int burrowPixels = 0;
                        long litBurrowSum = 0;
                        long forwardBurrowSum = 0;

                        for (int index = 0; index < forwardBurrow.length; index++) {
                            if (forwardBurrow[index] == forwardTerrain[index]) {
                                continue;
                            }

                            burrowPixels++;

                            for (int shift = 0; shift <= RED_SHIFT; shift += Byte.SIZE) {
                                litBurrowSum += litBurrow[index] >>> shift & BYTE_MASK;
                                forwardBurrowSum += forwardBurrow[index] >>> shift & BYTE_MASK;
                            }
                        }

                        Assertions.assertTrue(burrowPixels > MINIMUM_BURROW_PIXELS, "the burrow frame is drawn");
                        // Its opaque pixels keep the diffuse color; edge pixels blend into the lit terrain's G-buffer values,
                        // so the hole is at most slightly darker, never black.
                        Assertions.assertEquals(DIFFUSE_RATIO,
                                                (double) litBurrowSum / forwardBurrowSum,
                                                DIFFUSE_RATIO_TOLERANCE,
                                                "unlit frames keep their diffuse color in the lit path");

                        long litSum = 0;
                        long forwardSum = 0;

                        for (int index = 0; index < litTerrain.length; index++) {
                            for (int shift = 0; shift <= RED_SHIFT; shift += Byte.SIZE) {
                                litSum += litTerrain[index] >>> shift & BYTE_MASK;
                                forwardSum += forwardTerrain[index] >>> shift & BYTE_MASK;
                            }
                        }

                        Assertions.assertEquals(LAMBERT_RATIO,
                                                (double) litSum / forwardSum,
                                                LAMBERT_RATIO_TOLERANCE,
                                                "lit terrain carries the directional lambert");

                        // Shadow images darken lit terrain.
                        hdScene.setLightingEnabled(true);

                        int[] litShadow = renderSceneFrame(world, pixels, HD_SCALE, () -> {
                            hdScene.drawTerrain(index -> TILE);
                            hdScene.drawImage(PROBE_SHADOW, 0, SceneRenderer.DRAW_FUNCTION_SHADOW, 0, 0, SPRITE_X, SPRITE_Y, false);
                            hdScene.finishScene();
                        });
                        long shadowSum = 0;

                        for (int value : litShadow) {
                            shadowSum += (value >>> RED_SHIFT & BYTE_MASK) + (value >>> GREEN_SHIFT & BYTE_MASK) + (value & BYTE_MASK);
                        }

                        Assertions.assertTrue(shadowSum < litSum - MINIMUM_SHADOW_DARKENING, "the probe shadow darkens lit terrain");

                        // The 65th Bunker flash still lights nearby terrain independently of the other 64 lights.
                        Scene crowded = () -> {
                            hdScene.drawTerrain(index -> TILE);

                            for (int flash = 0; flash < CROWDED_LIGHTS; flash++) {
                                hdScene.drawImage(BUNKER_FLASH, 0, SceneRenderer.DRAW_FUNCTION_REMAPPING, 0, 0, CROWD_X, CROWD_Y, false);
                            }

                            hdScene.finishScene();
                        };
                        Scene crowdedAndOne = () -> {
                            hdScene.drawTerrain(index -> TILE);

                            for (int flash = 0; flash < CROWDED_LIGHTS; flash++) {
                                hdScene.drawImage(BUNKER_FLASH, 0, SceneRenderer.DRAW_FUNCTION_REMAPPING, 0, 0, CROWD_X, CROWD_Y, false);
                            }

                            hdScene.drawImage(BUNKER_FLASH, 0, SceneRenderer.DRAW_FUNCTION_REMAPPING, 0, 0, LAST_LIGHT_X, LAST_LIGHT_Y, false);
                            hdScene.finishScene();
                        };
                        Scene one = () -> {
                            hdScene.drawTerrain(index -> TILE);
                            hdScene.drawImage(BUNKER_FLASH, 0, SceneRenderer.DRAW_FUNCTION_REMAPPING, 0, 0, LAST_LIGHT_X, LAST_LIGHT_Y, false);
                            hdScene.finishScene();
                        };
                        int[] withoutLast = renderSceneFrame(world, pixels, 1, crowded);
                        int[] withLast = renderSceneFrame(world, pixels, 1, crowdedAndOne);
                        int[] alone = renderSceneFrame(world, pixels, 1, one);
                        int beside = BESIDE_ROW * SIZE + BESIDE_COLUMN;

                        Assertions.assertNotEquals(withoutLast[beside], withLast[beside], "the 65th light is applied");
                        Assertions.assertEquals(alone[beside], withLast[beside], "the 65th light is applied in full");
                        Assertions.assertEquals(GL_NO_ERROR, glGetError());
                    } finally {
                        hdScene.close();
                    }
                } finally {
                    MemoryUtil.memFree(pixels);
                }
            }
        });
    }

    @RequiresInstallation
    @Test
    void classic_frame_is_unchanged_after_hd_scenes() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        HiddenContextScenario.run(SIZE, SIZE, "Scene switch verification", () -> {
            int[] tiles = new int[MAP_TILES * MAP_TILES];

            Arrays.fill(tiles, TILE);

            try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); WorldRenderer world = new WorldRenderer();
                 ClassicSceneRenderer classic = new ClassicSceneRenderer(world, ScrResourceProvider.borrow(storage), JUNGLE_TILESET)) {
                world.loadMap(ScrResourceProvider.borrow(storage), JUNGLE_TILESET, MAP_TILES, MAP_TILES, tiles);

                ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * OverlayPass.RGBA_BYTES);
                // Terrain, a shadowed unit, the modifier-9 mining spark, a cloaked unit, a circle and fog.
                BackendDrawing content = scene -> {
                    scene.drawTerrain(index -> TILE);
                    scene.drawImage(ZEALOT_SHADOW, 0, SceneRenderer.DRAW_FUNCTION_SHADOW, 0, 0, ZEALOT_X, ZEALOT_Y, false);
                    scene.drawImage(ZEALOT, 0, SceneRenderer.DRAW_FUNCTION_NORMAL, 0, 0, ZEALOT_X, ZEALOT_Y, false);
                    scene.drawImage(MINING_SPARK, 0, SceneRenderer.DRAW_FUNCTION_REMAPPING, 0, 0, MINING_SPARK_X, MINING_SPARK_Y, false);
                    scene.drawImage(ZEALOT,
                                    CLOAKED_FRAME,
                                    SceneRenderer.DRAW_FUNCTION_UNSEEN_CLOAKED,
                                    0,
                                    CLOAKED_PLAYER_COLOR,
                                    CLOAKED_X,
                                    CLOAKED_Y,
                                    false);
                    scene.drawSelectionCircle(CIRCLE_IMAGE, SceneRenderer.RELATION_OWN, SWITCH_CIRCLE_X, SWITCH_CIRCLE_Y);
                    scene.drawHealthBar(SWITCH_HEALTH_BAR_X, SWITCH_HEALTH_BAR_Y, HEALTH_BAR_WIDTH, HIT_POINTS_PERCENT, SHIELD_PERCENT, NO_ENERGY);
                    scene.drawFog(index -> index % MAP_TILES == EXPLORED_COLUMN ? EXPLORED_FLAGS : 0, FIRST_PLAYER_VISION);
                    scene.finishScene();
                };

                try {
                    int[] fresh = renderBackendFrame(world, classic, content, pixels);

                    for (HdRenderAssets.Quality quality : HdRenderAssets.Quality.values()) {
                        for (boolean lighting : new boolean[]{true, false}) {
                            HdSceneRenderer hdScene = new HdSceneRenderer(world, HdRenderAssets.load(ScrResourceProvider.borrow(storage), JUNGLE_TILESET,
                                                                                                     quality));

                            try {
                                hdScene.setLightingEnabled(lighting);
                                renderBackendFrame(world, hdScene, content, pixels);
                                Assertions.assertArrayEquals(fresh,
                                                             renderBackendFrame(world, classic, content, pixels),
                                                             "classic frame while the " + quality + " scene (lighting " + lighting + ") is open");
                                renderBackendFrame(world, hdScene, content, pixels);
                            } finally {
                                hdScene.close();
                            }

                            Assertions.assertArrayEquals(fresh,
                                                         renderBackendFrame(world, classic, content, pixels),
                                                         "classic frame after closing the " + quality + " scene (lighting " + lighting + ")");
                        }
                    }

                    Assertions.assertEquals(GL_NO_ERROR, glGetError());
                } finally {
                    MemoryUtil.memFree(pixels);
                }
            }
        });
    }

    @Test
    void get_brighten_alpha_should_compute_the_warp_flash_alpha() {
        Assertions.assertEquals(1.0f, SceneRenderer.getBrightenAlpha(FIRST_FLASH_DATA), ALPHA_TOLERANCE);
        Assertions.assertEquals(0.0f, SceneRenderer.getBrightenAlpha(LAST_FLASH_DATA), ALPHA_TOLERANCE);
        Assertions.assertEquals(1 - (float) Math.sqrt(1 - 7 / 15.0), SceneRenderer.getBrightenAlpha(MIDDLE_FLASH_DATA), ALPHA_TOLERANCE);
        Assertions.assertEquals(0.0f, SceneRenderer.getBrightenAlpha(BELOW_FLASH_DATA), "the square root of a negative number draws nothing");
        Assertions.assertEquals(0.0f, SceneRenderer.getBrightenAlpha(ABOVE_FLASH_DATA), "a negative alpha draws nothing");
        Assertions.assertEquals(1.0f, SceneRenderer.getBrightenAlpha(HIGH_BITS_FLASH_DATA), "only the data byte counts");
    }
}
