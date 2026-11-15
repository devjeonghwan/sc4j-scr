package org.fogofwarlabs.sc4j.scr.game.sprite.script;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptHeader;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImagePool;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks installed script frames, random waits, free-list dispatch, lifted-building deaths, underlays and the overlays that scripts create on cloaked
 * units and hallucinations.
 */
@RequiresInstallation
final class IscriptExecutorTest {
    /**
     * The owner of the created units.
     */
    private static final int HUMAN = 1;
    /**
     * The random seed of the games without a running scenario.
     */
    private static final int RANDOM_SEED = 0;

    /**
     * The width and height of the map of the games without a running scenario, in tiles.
     */
    private static final int MAP_DIMENSION = 1;
    /**
     * The tile count of the map of the games without a running scenario.
     */
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    /**
     * The tileset of the map of the games without a running scenario.
     */
    private static final int TILESET = 0;
    /**
     * The sprite type of the fixture sprites.
     */
    private static final int SPRITE_TYPE = 0;
    /**
     * The image type of the fixture images that run a script chosen by the test.
     */
    private static final int IMAGE_TYPE = 0;

    /**
     * The animation force that ignores the sprite's nonbreaking flag.
     */
    private static final int FORCED = 1;
    /**
     * The animation force that honors the sprite's nonbreaking flag.
     */
    private static final int NOT_FORCED = 0;

    /**
     * The installed script whose Init animation plays its frame sequence without random opcodes.
     */
    private static final int FRAME_SEQUENCE_SCRIPT = 33;
    /**
     * The frame of each invocation of the Init animation of {@link #FRAME_SEQUENCE_SCRIPT}.
     */
    private static final int @NotNull [] FRAME_SEQUENCE = {0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7};

    /**
     * The installed script whose GndAttkToIdle animation sets a frame and a {@code waitrand} wait.
     */
    private static final int WAITRAND_SCRIPT = 4;
    /**
     * The frame the GndAttkToIdle animation of {@link #WAITRAND_SCRIPT} sets.
     */
    private static final int WAITRAND_FRAME = 17;
    /**
     * The wait the {@code waitrand} of {@link #WAITRAND_SCRIPT} sets with {@link #RANDOM_SEED}.
     */
    private static final int WAITRAND_WAIT = 24;
    /**
     * The random source of {@code waitrand}.
     */
    private static final int WAIT_RANDOM_SOURCE = 3;
    /**
     * The number of random numbers one {@code waitrand} draws.
     */
    private static final int WAITRAND_DRAW_COUNT = 1;

    /**
     * The image pool size of the free-list test: the ended image {@code 0} and the free images {@code 1..3}.
     */
    private static final int FREE_LIST_POOL_SIZE = 4;
    /**
     * The pool index of the image whose Death animation ends it in the free-list test.
     */
    private static final int ENDED_IMAGE_INDEX = 0;
    /**
     * The pool index of the last free image in the free-list test.
     */
    private static final int LAST_FREE_INDEX = 1;
    /**
     * The pool index of the free image after the ended image in the free-list test.
     */
    private static final int SECOND_FREE_INDEX = 2;
    /**
     * The pool index of the free-list head in the free-list test.
     */
    private static final int FREE_HEAD_INDEX = 3;
    /**
     * The {@code images.dat} index of the image in the free-list test.
     */
    private static final int FREE_LIST_IMAGE = 582;
    /**
     * The installed script whose Death animation ends the image in the free-list test.
     */
    private static final int FREE_LIST_SCRIPT = 352;
    /**
     * The instruction limit of the free-list animation dispatch.
     */
    private static final int FREE_LIST_LIMIT = 64;

    /**
     * The {@code uflunstable} opcode.
     */
    private static final int UFLUNSTABLE_OPCODE = 0x12;

    /**
     * The shared lifted-building death script that {@code liftoffcondjmp} jumps to in the installed {@code iscript.bin}.
     */
    private static final int LIFTED_DEATH_SCRIPT = 31008;
    /**
     * The bytes at the start of {@link #LIFTED_DEATH_SCRIPT} within which a program counter stops the death frames.
     */
    private static final int LIFTED_DEATH_ENTRY_LENGTH = 2;

    /**
     * The offset of the {@code liftoffcondjmp} in the grounded death of a Barracks.
     */
    private static final int LIFTED_DEATH_JUMP = 31042;

    /**
     * The installed script that uses {@code imguluselo}.
     */
    private static final int UNDERLAY_SCRIPT = 316;
    /**
     * The image that runs {@link #UNDERLAY_SCRIPT}.
     */
    private static final int UNDERLAY_IMAGE = 547;
    /**
     * The first underlay the {@code imguluselo} instructions of {@link #UNDERLAY_SCRIPT} add.
     */
    private static final int FIRST_UNDERLAY = 550;
    /**
     * The second underlay the {@code imguluselo} instructions of {@link #UNDERLAY_SCRIPT} add.
     */
    private static final int SECOND_UNDERLAY = 552;

    /**
     * The frames after which the death scripts have passed their first wait.
     */
    private static final int DEATH_FRAMES = 5;

    /**
     * The engine flame overlay a Wraith's Walking script creates; {@code images.dat} draws it only while not cloaked.
     */
    private static final int WRAITH_ENGINE_FLAME = 245;

    /**
     * The first overlay an Archon's Init script creates after its waits; {@code images.dat} gives it draw function {@code 0} and draws it while
     * cloaked.
     */
    private static final int ARCHON_OVERLAY = 135;

    /**
     * The frames after which the scripts of the tested units (Archon, Wraith) have created their overlays.
     */
    private static final int OVERLAY_FRAMES = 40;

    /**
     * The first of the cloak draw functions.
     */
    private static final int FIRST_CLOAK_DRAW_FUNCTION = 2;
    /**
     * The last of the cloak draw functions.
     */
    private static final int LAST_CLOAK_DRAW_FUNCTION = 7;
    /**
     * The hallucination draw function.
     */
    private static final int HALLUCINATION_DRAW_FUNCTION = 16;

    /**
     * The vertical position of the units created in a row.
     */
    private static final int UNIT_ROW_Y = 3200;
    /**
     * The horizontal position of the grounded Barracks.
     */
    private static final int GROUNDED_BARRACKS_X = 1000;
    /**
     * The horizontal position of the lifted Barracks.
     */
    private static final int LIFTED_BARRACKS_X = 1400;
    /**
     * The horizontal position of the cloaked Wraith.
     */
    private static final int WRAITH_X = 1800;
    /**
     * The horizontal position of the cloaked Archon.
     */
    private static final int CLOAKED_ARCHON_X = 2200;
    /**
     * The horizontal position of the hallucinated Archon.
     */
    private static final int HALLUCINATED_ARCHON_X = 2600;
    /**
     * The horizontal position of the Marine that receives the underlay image.
     */
    private static final int MARINE_X = 3740;
    /**
     * The vertical position of the Marine that receives the underlay image.
     */
    private static final int MARINE_Y = 2380;
    /**
     * The image flag of an image that is not drawn.
     */
    private static final int HIDDEN_IMAGE_FLAG = 0x40;

    private static @Nullable GameData gameData;
    private static @Nullable ReplayRuntime runtime;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
        runtime  = RunningGameScenario.start(gameData);
    }

    @AfterAll
    static void closeGameData() {
        runtime = null;

        if (gameData != null) {
            gameData.close();
        }
    }

    /**
     * Runs the Death animation of a unit's sprite, then executes its scripts for at most {@link #DEATH_FRAMES} frames while the program counter of
     * the main image lies past the first {@link #LIFTED_DEATH_ENTRY_LENGTH} bytes of {@link #LIFTED_DEATH_SCRIPT} up to {@link #LIFTED_DEATH_JUMP}.
     *
     * @param game the running game
     * @param unit the dying unit
     * @return the program counter of the main image
     */
    private static int runDeathPastWait(@NotNull Game game, @NotNull Unit unit) {
        Sprite sprite = unit.getSpriteOrNull();

        Assertions.assertNotNull(sprite);

        game.getIscriptExecutor().runSpriteAnimation(sprite, unit, IscriptExecutor.ANIMATION_DEATH, FORCED, RunningGameScenario.INSTRUCTION_LIMIT);

        SpriteImage image = sprite.getMainImage();

        Assertions.assertNotNull(image);

        for (int frame = 0; frame < DEATH_FRAMES && image.getProgramCounter() <= LIFTED_DEATH_JUMP &&
                            image.getProgramCounter() > LIFTED_DEATH_SCRIPT + LIFTED_DEATH_ENTRY_LENGTH;
             frame++) {
            game.getIscriptExecutor().executeSprite(sprite, unit, RunningGameScenario.INSTRUCTION_LIMIT);
        }

        return image.getProgramCounter();
    }

    /**
     * Executes the scripts of a unit's sprite until an image of a type exists.
     *
     * @param game      the running game
     * @param unit      the unit whose sprite runs its scripts
     * @param imageType the created {@code images.dat} index
     * @return the created image
     */
    private static @NotNull SpriteImage executeUntilImage(@NotNull Game game, @NotNull Unit unit, int imageType) {
        Sprite sprite = unit.getSpriteOrNull();

        Assertions.assertNotNull(sprite);

        for (int frame = 0; frame < OVERLAY_FRAMES; frame++) {
            for (SpriteImage image : sprite.getImages()) {
                if (image.getImageTypeId() == imageType) {
                    return image;
                }
            }

            game.getIscriptExecutor().executeSprite(sprite, unit, RunningGameScenario.INSTRUCTION_LIMIT);
        }

        return Assertions.fail("The scripts of unit type " + unit.getUnitId() + " create image " + imageType);
    }


    @Test
    void explosion_init_animation_runs_its_frame_sequence_without_random() {
        GameMap map = new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]);
        Game game = Game.create(gameData, map, new LcgRandom(RANDOM_SEED));
        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
        SpriteImage image = SpriteScenario.createImage(game, IMAGE_TYPE, 0, 0);

        image.setFlags(SpriteImage.FLAG_USES_FULL_ISCRIPT);
        sprite.insertTailImage(image);
        sprite.setMainImage(image);

        Assertions.assertTrue(game.getIscriptExecutor().runAnimation(image, sprite, null, FRAME_SEQUENCE_SCRIPT, IscriptExecutor.ANIMATION_INIT));

        Assertions.assertEquals(0, image.getDrawnFrameIndex());

        for (int invocationIndex = 1; invocationIndex < FRAME_SEQUENCE.length; invocationIndex++) {
            Assertions.assertTrue(game.getIscriptExecutor().execute(image, sprite, null),
                                  "The animation ended early at invocation " + invocationIndex);

            Assertions.assertEquals(FRAME_SEQUENCE[invocationIndex], image.getDrawnFrameIndex(),
                                    "The frame index mismatches at invocation " + invocationIndex);
        }

        Assertions.assertFalse(game.getIscriptExecutor().execute(image, sprite, null), "The animation did not end at invocation 16");

        // The animation has no random opcodes; the RNG must not be consumed.
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void run_animation_should_compute_waitrand_wait_deterministically() {
        GameMap map = new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]);
        Game game = Game.create(gameData, map, new LcgRandom(RANDOM_SEED));
        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
        SpriteImage image = SpriteScenario.createImage(game, IMAGE_TYPE, 0, 0);

        image.setFlags(SpriteImage.FLAG_USES_FULL_ISCRIPT);
        sprite.insertTailImage(image);
        sprite.setMainImage(image);

        Assertions.assertTrue(game.getIscriptExecutor().runAnimation(image, sprite, null,
                                                                     WAITRAND_SCRIPT, IscriptExecutor.ANIMATION_GROUND_ATTACK_TO_IDLE));

        Assertions.assertEquals(WAITRAND_FRAME, image.getDrawnFrameIndex());
        Assertions.assertEquals(WAITRAND_WAIT, image.getWait());
        Assertions.assertEquals(WAITRAND_DRAW_COUNT, game.getRandom().getCallCount(WAIT_RANDOM_SOURCE));
    }

    /**
     * Checks free-list traversal after an image ends, including rejected freed storage.
     */
    @Test
    void run_sprite_animation_should_walk_the_free_list_after_an_ended_image() {
        for (boolean freshEntry : new boolean[]{false, true}) {
            GameMap map = new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]);
            Game game = Game.create(gameData, map, new LcgRandom(RANDOM_SEED));

            game.getSpriteSystem().initializeImagePool(FREE_LIST_POOL_SIZE);

            Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
            SpriteImage image = SpriteScenario.createImage(game, FREE_LIST_IMAGE, 0, 0);

            image.setScriptIdentifier(FREE_LIST_SCRIPT);
            sprite.insertHeadImage(image);
            sprite.setMainImage(image);

            SpriteImagePool pool = game.getSpriteSystem().getImagePool();

            Assertions.assertNotNull(pool);

            // The free list is 3, 2, 1; the ended image 0 goes after the head: 3, 0, 2, 1.
            pool.getImage(SECOND_FREE_INDEX).setAnimationType(IscriptExecutor.ANIMATION_DEATH);
            pool.getImage(LAST_FREE_INDEX).setAnimationType(freshEntry ? IscriptExecutor.ANIMATION_INIT : IscriptExecutor.ANIMATION_DEATH);

            if (freshEntry) {
                Assertions.assertThrows(UnsupportedOperationException.class,
                                        () -> game.getIscriptExecutor().runSpriteAnimation(sprite,
                                                                                           null,
                                                                                           IscriptExecutor.ANIMATION_DEATH,
                                                                                           FORCED,
                                                                                           FREE_LIST_LIMIT));
            } else {
                game.getIscriptExecutor().runSpriteAnimation(sprite, null, IscriptExecutor.ANIMATION_DEATH, FORCED, FREE_LIST_LIMIT);
            }

            Assertions.assertTrue(sprite.getImages().isEmpty());
            Assertions.assertEquals(FREE_HEAD_INDEX, pool.getFirstFreeIndex());
            Assertions.assertEquals(ENDED_IMAGE_INDEX, pool.getNextFreeIndex(FREE_HEAD_INDEX));
            Assertions.assertEquals(SECOND_FREE_INDEX, pool.getNextFreeIndex(ENDED_IMAGE_INDEX));
        }
    }

    @Test
    void installed_scripts_do_not_use_uflunstable() {
        Assertions.assertNotNull(gameData);

        for (IscriptHeader header : gameData.getIscript().getHeaders()) {
            for (IscriptHeader.Animation animation : header.getAnimations()) {
                for (IscriptInstruction instruction : animation.getInstructions()) {
                    Assertions.assertNotEquals(UFLUNSTABLE_OPCODE, instruction.getOpcode(), "uflunstable in script " + header.getId());
                }
            }
        }
    }

    @Test
    void flying_building_dies_through_the_lifted_death_script() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit grounded = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_BARRACKS, GROUNDED_BARRACKS_X, UNIT_ROW_Y, HUMAN);
            Unit lifted = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_BARRACKS, LIFTED_BARRACKS_X, UNIT_ROW_Y, HUMAN);

            lifted.setStatus(lifted.getStatus() | UnitStatusFlags.FLYING);

            int groundedCounter = runDeathPastWait(game, grounded);
            int liftedCounter = runDeathPastWait(game, lifted);

            Assertions.assertTrue(groundedCounter > LIFTED_DEATH_JUMP, "A grounded building goes on after the jump: " + groundedCounter);
            Assertions.assertTrue(liftedCounter >= LIFTED_DEATH_SCRIPT && liftedCounter < LIFTED_DEATH_JUMP,
                                  "liftoffcondjmp jumps for a flying building: " + liftedCounter);
        });
    }

    @Test
    void imguluselo_adds_both_underlays_of_the_image() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            GameData runningGameData = game.getGameData();

            Assertions.assertNotNull(runningGameData);

            DatTable images = runningGameData.getImages();

            Assertions.assertEquals(UNDERLAY_SCRIPT, images.get("Iscript", UNDERLAY_IMAGE));

            Unit marine = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE, MARINE_X, MARINE_Y, HUMAN);
            Sprite sprite = marine.getSpriteOrNull();

            Assertions.assertNotNull(sprite);

            Assertions.assertDoesNotThrow(() -> game.getIscriptExecutor().createImage(sprite,
                                                                                      sprite.getImages().getFirst(),
                                                                                      UNDERLAY_IMAGE,
                                                                                      0, 0,
                                                                                      0,
                                                                                      marine,
                                                                                      RunningGameScenario.INSTRUCTION_LIMIT));

            // The Init script of image 547 waits one frame before its imguluselo instructions.
            game.getIscriptExecutor().executeSprite(sprite, marine, RunningGameScenario.INSTRUCTION_LIMIT);
            game.getIscriptExecutor().executeSprite(sprite, marine, RunningGameScenario.INSTRUCTION_LIMIT);

            List<Integer> types = new ArrayList<>();

            for (SpriteImage image : sprite.getImages()) {
                types.add(image.getImageTypeId());
            }

            Assertions.assertTrue(types.contains(FIRST_UNDERLAY) && types.contains(SECOND_UNDERLAY),
                                  "imguluselo adds the underlays of image " + UNDERLAY_IMAGE + ": " + types);
        });
    }

    @Test
    void overlay_of_a_cloaked_unit_is_hidden_when_not_drawn_while_cloaked() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit wraith = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_WRAITH, WRAITH_X, UNIT_ROW_Y, HUMAN);
            Sprite sprite = wraith.getSpriteOrNull();

            Assertions.assertNotNull(sprite);

            game.getUnitCloaking().cloakUnit(wraith);
            game.getIscriptExecutor().runSpriteAnimation(sprite, wraith,
                                                         IscriptExecutor.ANIMATION_WALKING, NOT_FORCED,
                                                         RunningGameScenario.INSTRUCTION_LIMIT);

            SpriteImage flame = executeUntilImage(game, wraith, WRAITH_ENGINE_FLAME);

            Assertions.assertNotEquals(0, flame.getFlags() & HIDDEN_IMAGE_FLAG, "flags " + Integer.toHexString(flame.getFlags()));
        });
    }

    @Test
    void overlay_of_a_cloaked_unit_takes_the_cloak_draw_function_of_the_primary_image() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit archon = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_ARCHON, CLOAKED_ARCHON_X, UNIT_ROW_Y, HUMAN);

            game.getUnitCloaking().cloakUnit(archon);

            SpriteImage overlay = executeUntilImage(game, archon, ARCHON_OVERLAY);

            Assertions.assertEquals(0, overlay.getFlags() & HIDDEN_IMAGE_FLAG);
            Assertions.assertTrue(overlay.getModifier() >= FIRST_CLOAK_DRAW_FUNCTION && overlay.getModifier() <= LAST_CLOAK_DRAW_FUNCTION,
                                  "draw function " + overlay.getModifier());
        });
    }

    @Test
    void overlay_of_a_hallucination_takes_the_hallucination_draw_function() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit archon = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_ARCHON, HALLUCINATED_ARCHON_X, UNIT_ROW_Y, HUMAN);

            game.getUnitHallucination().convertToHallucination(archon, false, true, HUMAN, RunningGameScenario.INSTRUCTION_LIMIT);

            SpriteImage overlay = executeUntilImage(game, archon, ARCHON_OVERLAY);

            Assertions.assertEquals(HALLUCINATION_DRAW_FUNCTION, overlay.getModifier());
        });
    }
}
