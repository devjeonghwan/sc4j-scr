package org.fogofwarlabs.sc4j.scr.client.world;

import org.fogofwarlabs.sc4j.base.exception.IgnoreException;
import org.fogofwarlabs.sc4j.engine.client.host.ClientHost;
import org.fogofwarlabs.sc4j.engine.client.host.HostWindow;
import org.fogofwarlabs.sc4j.engine.client.host.WindowSettings;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.dat.ImagesDat;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrResourceProvider;
import org.fogofwarlabs.sc4j.scr.data.type.constant.ImageIds;
import org.fogofwarlabs.sc4j.scr.data.type.constant.SpriteIds;
import org.fogofwarlabs.sc4j.scr.game.ability.UnitHallucination;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.SelectionImages;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitCreationContext;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitTypeTraitsUtils;
import org.fogofwarlabs.sc4j.scr.game.visibility.LocalVision;
import org.fogofwarlabs.sc4j.scr.render.ClassicSceneRenderer;
import org.fogofwarlabs.sc4j.scr.render.OverlayPass;
import org.fogofwarlabs.sc4j.scr.render.SceneRenderer;
import org.fogofwarlabs.sc4j.scr.render.WorldRenderer;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Checks the cloak draw functions of seen and unseen cloaked images, custom relation images in every graphics mode and selected health bars in the
 * Classic renderer.
 */
final class WorldSceneExtractionTest {
    /**
     * The cloak views of a sprite: none, seen through detection and unseen.
     */
    private static final int CLOAK_VIEW_NONE = 0;
    private static final int CLOAK_VIEW_VISIBLE = 1;
    private static final int CLOAK_VIEW_HIDDEN = 2;

    private static final int FRAME_SIZE = 128;
    private static final int FULL_PERCENT = 100;
    private static final int HALF_PERCENT = 50;
    private static final int ABSENT_BAR_PERCENT = -1;
    private static final int FIXED_POINT_FRACTION_BITS = 8;
    private static final int SPRITE_X = FRAME_SIZE / 2;
    private static final int SPRITE_Y = FRAME_SIZE / 2;
    private static final int UNIT_CREATION_X = 3560;
    private static final int UNIT_CREATION_Y = 2160;
    /**
     * The SCV image, whose directional frames distinguish the submitted frame and flip.
     */
    private static final int ANIMATED_IMAGE = 0;
    /**
     * The selection-circle draw function the relation images are given.
     */
    private static final int SELECTION_CIRCLE_MODIFIER = 13;
    private static final int SOLID_CIRCLE_COUNT = ImageIds.SELECTION_CIRCLE_DASHED_22PIXELS - ImageIds.SELECTION_CIRCLE_22PIXELS;
    private static final int LAST_SOLID_CIRCLE = ImageIds.SELECTION_CIRCLE_22PIXELS + SOLID_CIRCLE_COUNT - 1;
    private static final int @NotNull [] IMAGE_TYPES = {ImageIds.MINERAL_CHUNK_TYPE2, ImageIds.HALLUCINATION_HIT, ANIMATED_IMAGE,
                                                        ImageIds.SELECTION_CIRCLE_22PIXELS, LAST_SOLID_CIRCLE,
                                                        ImageIds.SELECTION_CIRCLE_DASHED_22PIXELS};
    private static final @NotNull String CARBOT_SKIN = "Carbot";
    private static final @NotNull String RENDER_SMOKE_PROPERTY = "sc4j.render.smoke";
    private static final @NotNull String RENDER_SMOKE_MESSAGE = "Pass -Psc4j.render.smoke=true to run the GPU check.";

    /**
     * Hides every image of a sprite.
     *
     * @param sprite the sprite
     */
    private static void hideImages(@NotNull Sprite sprite) {
        for (SpriteImage image : sprite.getImages()) {
            image.setFlags(image.getFlags() | SpriteImage.FLAG_HIDDEN);
        }
    }

    /**
     * Hides every image of every allocated sprite.
     *
     * @param sprites the sprite pool
     */
    private static void hideAllImages(@NotNull SpritePool sprites) {
        for (int index = 0; index < sprites.getCapacity(); index++) {
            if (sprites.isAllocated(index)) {
                hideImages(sprites.getSprite(index));
            }
        }
    }

    /**
     * Reads the framebuffer pixels of the frame just drawn.
     *
     * @param pixels the readback buffer
     * @return the pixels, one packed {@code int} per pixel
     */
    private static int @NotNull [] readPixels(@NotNull ByteBuffer pixels) {
        glReadPixels(0, 0, FRAME_SIZE, FRAME_SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

        int[] result = new int[FRAME_SIZE * FRAME_SIZE];

        for (int index = 0; index < result.length; index++) {
            result[index] = pixels.getInt(index * OverlayPass.RGBA_BYTES);
        }

        return result;
    }

    /**
     * Reads real framebuffer pixels after extraction and an optional expected relation image.
     *
     * @param world          the classic world renderer
     * @param scene          the scene backend
     * @param extraction     the scene extraction under test
     * @param game           the game state
     * @param referenceImage the relation image drawn directly as the expectation, or {@code null} for none
     * @param pixels         the readback buffer
     * @return the framebuffer pixels
     * @throws IOException if an image cannot be read
     */
    private static int @NotNull [] renderFrame(@NotNull WorldRenderer world, @NotNull SceneRenderer scene,
                                               @NotNull WorldSceneExtraction extraction,
                                               @NotNull Game game,
                                               @Nullable SpriteImage referenceImage,
                                               @NotNull ByteBuffer pixels) throws IOException {
        world.beginFrame(FRAME_SIZE, FRAME_SIZE, FRAME_SIZE, FRAME_SIZE, 0, 0, 1);

        List<String> warnings = new ArrayList<>();

        try {
            extraction.draw(scene, world, new ScrGameView(game), null, owner -> owner, warnings::add);

            if (referenceImage != null) {
                int imageType = referenceImage.getImageTypeId();

                scene.drawRelationImage(imageType,
                                        referenceImage.getDrawnFrameIndex(),
                                        SceneRenderer.RELATION_OWN,
                                        SPRITE_X,
                                        SPRITE_Y,
                                        (referenceImage.getFlags() & SpriteImage.FLAG_HORIZONTALLY_FLIPPED) != 0);
            }
        } finally {
            scene.finishScene();
            world.endFrame();
        }

        Assertions.assertTrue(warnings.isEmpty(), () -> String.join("\n", warnings));

        return readPixels(pixels);
    }

    /**
     * Gives every checked image type the relation draw function on a test sprite and compares its extracted pixels with the image drawn directly.
     *
     * @param world   the classic world renderer
     * @param scene   the scene backend
     * @param runtime the running game
     * @param pixels  the readback buffer
     * @throws IOException if an image cannot be read
     */
    private static void checkRelationImages(@NotNull WorldRenderer world, @NotNull SceneRenderer scene,
                                            @NotNull ReplayRuntime runtime,
                                            @NotNull ByteBuffer pixels) throws IOException {
        Game game = runtime.getGame();
        WorldSceneExtraction extraction = new WorldSceneExtraction();
        Sprite[] testSprite = new Sprite[1];

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            SpritePool sprites = game.getSpriteSystem().getSpritePool();

            Assertions.assertNotNull(sprites);

            hideAllImages(sprites);

            Sprite sprite = game.getIscriptExecutor().createSprite(SpriteIds.CURSOR_MARKER,
                                                                   SPRITE_X,
                                                                   SPRITE_Y,
                                                                   RunningGameScenario.HUMAN,
                                                                   null,
                                                                   RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNotNull(sprite);
            testSprite[0] = sprite;
            sprite.setVisibilityFlags(LocalVision.ALL_PLAYERS);

            hideImages(sprite);

            game.getLocalVision().acceptDialog(LocalVision.ALL_PLAYERS, true);
        });

        Sprite sprite = testSprite[0];

        Assertions.assertNotNull(sprite);

        for (int imageType : IMAGE_TYPES) {
            SpriteImage[] createdImage = new SpriteImage[1];

            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                game.requireGameData().getImages().set(ImagesDat.DRAW_FUNCTION, imageType, SpriteImage.MODIFIER_SELECTION_CIRCLE);

                SpriteImage image = game.getIscriptExecutor().createImage(sprite,
                                                                          sprite.getMainImage(),
                                                                          imageType,
                                                                          0, 0,
                                                                          0,
                                                                          null,
                                                                          RunningGameScenario.INSTRUCTION_LIMIT);

                Assertions.assertNotNull(image);
                createdImage[0] = image;
                image.setModifierData1(SelectionImages.COLOR_OWN);
                image.setFrameIndex(imageType == ANIMATED_IMAGE ? 1 : 0);
                image.setFrameIndexOffset(0);

                int flags = image.getFlags() | SpriteImage.FLAG_HIDDEN;

                image.setFlags(imageType == ImageIds.SELECTION_CIRCLE_22PIXELS || imageType == LAST_SOLID_CIRCLE
                               ? flags & ~SpriteImage.FLAG_HORIZONTALLY_FLIPPED
                               : flags | SpriteImage.FLAG_HORIZONTALLY_FLIPPED);
            });

            SpriteImage image = createdImage[0];

            Assertions.assertNotNull(image);
            Assertions.assertEquals(SELECTION_CIRCLE_MODIFIER, image.getModifier());

            int[] baseline = renderFrame(world, scene, extraction, game, null, pixels);
            int[] expected = renderFrame(world, scene, extraction, game, image, pixels);

            Assertions.assertFalse(Arrays.equals(baseline, expected), "image " + imageType + " draws visible relation pixels");
            RunningGameScenario.runInCommandDispatch(runtime, () -> image.setFlags(image.getFlags() & ~SpriteImage.FLAG_HIDDEN));

            Assertions.assertArrayEquals(expected,
                                         renderFrame(world, scene, extraction, game, null, pixels),
                                         "image " + imageType + " retains its frame, position, relation color and flip");
            RunningGameScenario.runInCommandDispatch(runtime, () -> image.setFlags(image.getFlags() | SpriteImage.FLAG_HIDDEN));
        }
    }

    /**
     * Reads real framebuffer pixels after extraction and an optional expected health bar.
     *
     * @param world         the classic world renderer
     * @param scene         the scene backend
     * @param extraction    the scene extraction under test
     * @param game          the game state
     * @param sprite        the sprite that carries the health bar
     * @param referenceBar  the health bar drawn directly as the expectation, or {@code null} for none
     * @param energyPercent the energy or lifetime percentage of the expected bar
     * @param pixels        the readback buffer
     * @return the framebuffer pixels
     * @throws IOException if an image cannot be read
     */
    private static int @NotNull [] renderHealthBarFrame(@NotNull WorldRenderer world, @NotNull SceneRenderer scene,
                                                        @NotNull WorldSceneExtraction extraction,
                                                        @NotNull Game game,
                                                        @NotNull Sprite sprite, @Nullable SpriteImage referenceBar,
                                                        int energyPercent,
                                                        @NotNull ByteBuffer pixels) throws IOException {
        world.beginFrame(FRAME_SIZE, FRAME_SIZE, FRAME_SIZE, FRAME_SIZE, 0, 0, 1);

        List<String> warnings = new ArrayList<>();

        try {
            extraction.draw(scene, world, new ScrGameView(game), null, owner -> owner, warnings::add);

            if (referenceBar != null) {
                scene.drawHealthBar(sprite.getX() + referenceBar.getOffsetX(),
                                    sprite.getY() + referenceBar.getOffsetY(),
                                    game.requireGameData().getSpriteTypes().getHealthBarWidth(sprite.getSpriteTypeId()),
                                    FULL_PERCENT,
                                    ABSENT_BAR_PERCENT,
                                    energyPercent);
            }
        } finally {
            scene.finishScene();
            world.endFrame();
        }

        Assertions.assertTrue(warnings.isEmpty(), () -> String.join("\n", warnings));

        return readPixels(pixels);
    }

    /**
     * Selects a completed unit through the game APIs and compares its real health-bar image with the expected row.
     *
     * @param world                 the classic world renderer
     * @param scene                 the scene backend
     * @param runtime               the running game
     * @param unitTypeIdentifier    the unit type to create
     * @param hallucination         whether the unit is turned into a hallucination
     * @param expectedEnergyPercent the expected energy or lifetime percentage
     * @param pixels                the readback buffer
     * @throws IOException if an image cannot be read
     */
    private static void checkHealthBar(@NotNull WorldRenderer world, @NotNull SceneRenderer scene,
                                       @NotNull ReplayRuntime runtime,
                                       int unitTypeIdentifier, boolean hallucination, int expectedEnergyPercent,
                                       @NotNull ByteBuffer pixels) throws IOException {
        Game game = runtime.getGame();
        Unit[] createdUnit = new Unit[1];

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            SpritePool sprites = game.getSpriteSystem().getSpritePool();

            Assertions.assertNotNull(sprites);

            hideAllImages(sprites);

            Unit unit = RunningGameScenario.createUnit(game, unitTypeIdentifier, UNIT_CREATION_X, UNIT_CREATION_Y, RunningGameScenario.HUMAN);

            createdUnit[0] = unit;

            game.getUnitPositioning().move(unit, SPRITE_X, SPRITE_Y);
            game.getUnitVisibility().updateFog(unit);

            if (hallucination) {
                UnitCreationContext creation = game.getUnitSystem().getUnitCreationContext();

                Assertions.assertNotNull(creation);
                Assertions.assertNotNull(game.getUnitStrengthTable());
                game.getUnitHallucination().convertToHallucination(unit,
                                                                   creation.preservesNegativeTypeCounts(),
                                                                   true,
                                                                   RunningGameScenario.HUMAN,
                                                                   RunningGameScenario.INSTRUCTION_LIMIT);
                unit.setEnergy(0);
                unit.setRemainingLifetime(UnitHallucination.HALLUCINATION_REMOVAL_TIMER * HALF_PERCENT / FULL_PERCENT);
            } else if (UnitTypeTraitsUtils.isSpellcaster(game.requireGameData().getUnitTypes(), unit)) {
                int maximumEnergy = game.getUnitInitializer().getMaximumEnergy(unit, game.requireGameData().getUnitTypes());

                unit.setEnergy(maximumEnergy * HALF_PERCENT / FULL_PERCENT);
                Assertions.assertEquals(HALF_PERCENT,
                                        (unit.getEnergy() >> FIXED_POINT_FRACTION_BITS) * FULL_PERCENT /
                                        (maximumEnergy >> FIXED_POINT_FRACTION_BITS));
            } else {
                int maximumEnergy = game.getUnitInitializer().getMaximumEnergy(unit, game.requireGameData().getUnitTypes());

                unit.setEnergy(maximumEnergy * HALF_PERCENT / FULL_PERCENT);
                Assertions.assertTrue(unit.getEnergy() > 0);
                Assertions.assertEquals(0, unit.getRemainingLifetime());
            }

            Assertions.assertEquals(FULL_PERCENT, UnitQueries.getHitPointPercent(game.requireGameData().getUnitTypes(), unit));
            Assertions.assertEquals(0, game.requireGameData().getUnitTypes().getShieldEnabled(unitTypeIdentifier));
            game.getInterfaceLink().getLocalSelection().setSelectedPoolIndexRaw(0, unit.getIndex());
            game.getSelectionImages().selectUnit(unit, 0);

            Sprite sprite = unit.getSpriteOrNull();

            Assertions.assertNotNull(sprite);
            sprite.setVisibilityFlags(LocalVision.ALL_PLAYERS);

            hideImages(sprite);

            game.getLocalVision().acceptDialog(LocalVision.ALL_PLAYERS, true);
        });

        Unit unit = createdUnit[0];

        Assertions.assertNotNull(unit);

        Sprite sprite = unit.getSpriteOrNull();

        Assertions.assertNotNull(sprite);

        SpriteImage healthBar = null;

        for (SpriteImage image : sprite.getImages()) {
            if (image.getModifier() == SpriteImage.MODIFIER_HEALTH_BAR) {
                Assertions.assertNull(healthBar, "The selected unit has exactly one health bar.");
                healthBar = image;
            }
        }

        Assertions.assertNotNull(healthBar);
        Assertions.assertEquals(unit.getIndex(), healthBar.getModifierData1());

        WorldSceneExtraction extraction = new WorldSceneExtraction();
        int[] baseline = renderHealthBarFrame(world, scene, extraction, game, sprite, null, expectedEnergyPercent, pixels);
        int[] expected = renderHealthBarFrame(world, scene, extraction, game, sprite, healthBar, expectedEnergyPercent, pixels);

        Assertions.assertFalse(Arrays.equals(baseline, expected), "The selected unit's health bar draws visible pixels.");

        SpriteImage selectedBar = healthBar;

        RunningGameScenario.runInCommandDispatch(runtime, () -> selectedBar.setFlags(selectedBar.getFlags() & ~SpriteImage.FLAG_HIDDEN));
        Assertions.assertArrayEquals(expected,
                                     renderHealthBarFrame(world, scene, extraction, game, sprite, null, expectedEnergyPercent, pixels),
                                     "The extracted health bar retains its position, health and energy or lifetime row.");
    }

    /**
     * Creates the hidden window of an OpenGL core profile context in the frame size.
     *
     * @param host  the host
     * @param title the window title
     * @return the created window
     */
    private static @NotNull HostWindow createHiddenWindow(@NotNull ClientHost host, @NotNull String title) {
        return host.createWindow(WindowSettings.builder(title, FRAME_SIZE, FRAME_SIZE).build());
    }

    /**
     * Loads the terrain of a game map into the world renderer.
     *
     * @param world   the classic world renderer
     * @param storage the installation storage
     * @param map     the game map
     * @throws IOException if a required asset or tile reference is invalid
     */
    private static void loadMap(@NotNull WorldRenderer world, @NotNull CascStorage storage, @NotNull GameMap map) throws IOException {
        int[] tiles = new int[map.getWidth() * map.getHeight()];

        for (int index = 0; index < tiles.length; index++) {
            tiles[index] = map.getTileIdentifier(index);
        }

        world.loadMap(ScrResourceProvider.borrow(storage), map.getTileset(), map.getWidth(), map.getHeight(), tiles);
    }

    /**
     * Owns the installed assets, running game and hidden Classic-renderer context for one health-bar case.
     *
     * @param unitTypeIdentifier    the unit type to create
     * @param hallucination         whether the unit is turned into a hallucination
     * @param expectedEnergyPercent the expected energy or lifetime percentage
     * @throws IOException if the installation or an image cannot be read
     */
    private static void checkClassicHealthBar(int unitTypeIdentifier, boolean hallucination, int expectedEnergyPercent) throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        Path installationPath = ExternalInput.INSTALLATION.getPath();

        Assertions.assertNotNull(installationPath);

        ClientHost host = ClientHost.initialize();

        try {
            HostWindow window = createHiddenWindow(host, "Health bar verification");

            window.makeContextCurrent();
            host.createCapabilities();

            try (CascStorage storage = CascStorage.open(installationPath); GameData gameData = GameData.loadFrom(installationPath);
                 WorldRenderer world = new WorldRenderer()) {
                ReplayRuntime runtime = RunningGameScenario.start(gameData);
                GameMap map = runtime.getGame().getMap();

                loadMap(world, storage, map);

                ByteBuffer pixels = MemoryUtil.memAlloc(FRAME_SIZE * FRAME_SIZE * OverlayPass.RGBA_BYTES);

                try (ClassicSceneRenderer scene = new ClassicSceneRenderer(world, ScrResourceProvider.borrow(storage), map.getTileset())) {
                    checkHealthBar(world, scene, runtime, unitTypeIdentifier, hallucination, expectedEnergyPercent, pixels);
                    Assertions.assertEquals(GL_NO_ERROR, glGetError());
                } finally {
                    IgnoreException.execute(
                            () -> MemoryUtil.memFree(pixels)
                    );
                }
            }
        } finally {
            host.close();
        }
    }

    @Test
    void get_view_modifier_should_use_visible_draw_functions_for_seen_cloaked_images() {
        // Modifiers 2..4 become 5..7 and normal images become 6.
        Assertions.assertEquals(5, WorldSceneExtraction.getViewModifier(2, CLOAK_VIEW_VISIBLE, false));
        Assertions.assertEquals(6, WorldSceneExtraction.getViewModifier(3, CLOAK_VIEW_VISIBLE, false));
        Assertions.assertEquals(7, WorldSceneExtraction.getViewModifier(4, CLOAK_VIEW_VISIBLE, false));
        Assertions.assertEquals(6, WorldSceneExtraction.getViewModifier(0, CLOAK_VIEW_VISIBLE, false));
        Assertions.assertEquals(10, WorldSceneExtraction.getViewModifier(10, CLOAK_VIEW_VISIBLE, false), "shadows keep their draw function");
        // A seen burrowed sprite draws normally.
        Assertions.assertEquals(0, WorldSceneExtraction.getViewModifier(3, CLOAK_VIEW_VISIBLE, true));
    }

    @Test
    void get_view_modifier_should_keep_shared_draw_functions_for_unseen_cloaked_images() {
        // Modifiers 5..7 become 2..4; a burrowed sprite draws 0, 2 and 4 as 3.
        Assertions.assertEquals(3, WorldSceneExtraction.getViewModifier(3, CLOAK_VIEW_HIDDEN, false));
        Assertions.assertEquals(2, WorldSceneExtraction.getViewModifier(5, CLOAK_VIEW_HIDDEN, false));
        Assertions.assertEquals(0, WorldSceneExtraction.getViewModifier(0, CLOAK_VIEW_HIDDEN, false));
        Assertions.assertEquals(3, WorldSceneExtraction.getViewModifier(0, CLOAK_VIEW_HIDDEN, true));
        Assertions.assertEquals(3, WorldSceneExtraction.getViewModifier(3, CLOAK_VIEW_NONE, false), "units without detection keep their images");
    }


    @RequiresInstallation
    @Test
    void draw_should_show_remaining_lifetime_for_a_hallucinated_spellcaster() throws IOException {
        checkClassicHealthBar(ScrUnitIds.TERRAN_GHOST, true, HALF_PERCENT);
    }

    @RequiresInstallation
    @Test
    void draw_should_show_energy_for_an_ordinary_spellcaster() throws IOException {
        checkClassicHealthBar(ScrUnitIds.TERRAN_GHOST, false, HALF_PERCENT);
    }

    @RequiresInstallation
    @Test
    void draw_should_omit_energy_for_an_ordinary_non_spellcaster() throws IOException {
        checkClassicHealthBar(ScrUnitIds.TERRAN_MARINE, false, ABSENT_BAR_PERCENT);
    }


    @RequiresInstallation
    @Test
    void draw_should_preserve_custom_relation_images_in_every_graphics_mode() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        ClientHost host = ClientHost.initialize();

        try {
            HostWindow window = createHiddenWindow(host, "World scene verification");

            window.makeContextCurrent();
            host.createCapabilities();

            Path gamePath = ExternalInput.INSTALLATION.getPath();

            try (CascStorage storage = CascStorage.open(gamePath); GameData gameData = GameData.loadFrom(gamePath);
                 WorldRenderer world = new WorldRenderer()) {
                ReplayRuntime runtime = RunningGameScenario.start(gameData);
                GameMap map = runtime.getGame().getMap();

                loadMap(world, storage, map);

                ByteBuffer pixels = MemoryUtil.memAlloc(FRAME_SIZE * FRAME_SIZE * OverlayPass.RGBA_BYTES);

                try {
                    try (ClassicSceneRenderer scene = new ClassicSceneRenderer(world, ScrResourceProvider.borrow(storage), map.getTileset())) {
                        checkRelationImages(world, scene, runtime, pixels);
                    }

                    for (HdRenderAssets.Quality quality : HdRenderAssets.Quality.values()) {
                        HdRenderAssets qualityAssets = HdRenderAssets.load(ScrResourceProvider.borrow(storage), map.getTileset(), quality);
                        HdSceneRenderer scene = new HdSceneRenderer(world, qualityAssets);

                        try {
                            world.setRenderScaleLimit(quality.getScale());
                            checkRelationImages(world, scene, runtime, pixels);
                        } finally {
                            IgnoreException.execute(
                                    scene::close
                            );
                        }
                    }

                    HdRenderAssets assets = HdRenderAssets.load(ScrResourceProvider.borrow(storage), map.getTileset(), HdRenderAssets.Quality.HD2,
                                                                CARBOT_SKIN);
                    HdSceneRenderer scene = new HdSceneRenderer(world, assets);

                    try {
                        world.setRenderScaleLimit(HdRenderAssets.Quality.HD2.getScale());
                        checkRelationImages(world, scene, runtime, pixels);
                    } finally {
                        IgnoreException.execute(
                                scene::close
                        );
                    }

                    Assertions.assertEquals(GL_NO_ERROR, glGetError());
                } finally {
                    IgnoreException.execute(
                            () -> MemoryUtil.memFree(pixels)
                    );
                }
            }
        } finally {
            host.close();
        }
    }
}
