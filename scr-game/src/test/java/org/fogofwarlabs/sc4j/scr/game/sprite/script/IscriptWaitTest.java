package org.fogofwarlabs.sc4j.scr.game.sprite.script;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImagePool;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks byte wait wrap, expiration and random wait evaluation.
 */
@RequiresInstallation
final class IscriptWaitTest {
    /**
     * The largest wait byte.
     */
    private static final int MAXIMUM_WAIT = 255;
    /**
     * The bytes of {@code wait 0} followed by {@code wait 2}.
     */
    private static final byte @NotNull [] WAIT_ZERO_SEQUENCE = {5, 0, 5, 2};
    /**
     * The bytes of {@code wait 1} followed by {@code end}.
     */
    private static final byte @NotNull [] WAIT_ONE_END_SEQUENCE = {5, 1, 0x16};
    /**
     * The bytes of {@code waitrand 0 0}.
     */
    private static final byte @NotNull [] WAIT_RANDOM_ZERO_SEQUENCE = {6, 0, 0};
    /**
     * The byte size of a {@code wait} instruction.
     */
    private static final int WAIT_SIZE = 2;
    /**
     * The byte size of {@code wait 0} and {@code wait 2}.
     */
    private static final int TWO_WAITS_SIZE = 4;
    /**
     * The wait that {@code wait 2} leaves after its first execution.
     */
    private static final int SECOND_WAIT_LEFT = 1;
    /**
     * The wait that {@code wait 1} leaves after its execution.
     */
    private static final int WAIT_ONE_LEFT = 0;
    /**
     * The byte size of {@code wait 1} and {@code end}.
     */
    private static final int WAIT_END_SIZE = 3;
    /**
     * The byte size of a {@code waitrand} instruction.
     */
    private static final int WAIT_RANDOM_SIZE = 3;
    /**
     * The random call site of {@code waitrand}.
     */
    private static final int WAIT_RANDOM_SOURCE = 3;
    /**
     * The enabled state of a random generator that does not draw.
     */
    private static final int RANDOM_DISABLED = 0;
    /**
     * The enabled state of a random generator that draws.
     */
    private static final int RANDOM_ENABLED = 1;
    /**
     * The width and height of the test map in tiles.
     */
    private static final int MAP_DIMENSION = 1;
    /**
     * The tile count of the test map.
     */
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    /**
     * The tileset of the test map.
     */
    private static final int TILESET = 0;
    /**
     * The seed of the game random generator.
     */
    private static final int RANDOM_SEED = 0;
    /**
     * The sprite type of the owning sprites, which the waits do not depend on.
     */
    private static final int SPRITE_TYPE = 0;
    /**
     * The image type of the waiting images, which the waits do not depend on.
     */
    private static final int IMAGE_TYPE = 0;
    /**
     * The installed script file that the instruction sequences are found in.
     */
    private static final @NotNull String ISCRIPT_FILE = "scripts/iscript.bin";

    @Test
    void execute_should_wrap_zero_wait_and_continue_on_the_invocation_that_expires_it() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); GameData gameData = GameData.load(storage)) {
            int instructionOffset = SpriteScenario.findSequence(storage.readFile(ISCRIPT_FILE), WAIT_ZERO_SEQUENCE);
            Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));
            Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
            SpriteImage image = SpriteScenario.createImage(game, IMAGE_TYPE, 0, 0);

            image.setProgramCounter(instructionOffset);

            Assertions.assertTrue(game.getIscriptExecutor().execute(image, sprite, null));

            Assertions.assertEquals(MAXIMUM_WAIT, image.getWait());
            Assertions.assertEquals(instructionOffset + WAIT_SIZE, image.getProgramCounter());

            for (int remaining = MAXIMUM_WAIT - 1; remaining >= 0; remaining--) {
                Assertions.assertTrue(game.getIscriptExecutor().execute(image, sprite, null));

                Assertions.assertEquals(remaining, image.getWait());
                Assertions.assertEquals(instructionOffset + WAIT_SIZE, image.getProgramCounter());
            }

            Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());

            Assertions.assertTrue(game.getIscriptExecutor().execute(image, sprite, null));

            Assertions.assertEquals(SECOND_WAIT_LEFT, image.getWait());
            Assertions.assertEquals(instructionOffset + TWO_WAITS_SIZE, image.getProgramCounter());
        }
    }

    @Test
    void execute_should_resume_after_wait_one_on_the_next_invocation() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); GameData gameData = GameData.load(storage)) {
            int instructionOffset = SpriteScenario.findSequence(storage.readFile(ISCRIPT_FILE), WAIT_ONE_END_SEQUENCE);
            Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));
            Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
            SpriteImage image = SpriteScenario.createImage(game, IMAGE_TYPE, 0, 0);

            image.setProgramCounter(instructionOffset);

            Assertions.assertTrue(game.getIscriptExecutor().execute(image, sprite, null));

            Assertions.assertEquals(WAIT_ONE_LEFT, image.getWait());
            Assertions.assertEquals(instructionOffset + WAIT_SIZE, image.getProgramCounter());

            Assertions.assertFalse(game.getIscriptExecutor().execute(image, sprite, null));

            Assertions.assertEquals(instructionOffset + WAIT_END_SIZE, image.getProgramCounter());

            SpriteImagePool observedImagePool = game.getSpriteSystem().getImagePool();

            Assertions.assertNotNull(observedImagePool);

            Assertions.assertFalse(observedImagePool.isAllocated(image.getIndex()));
        }
    }

    @Test
    void execute_should_wrap_zero_range_waitrand_and_draw_random_only_at_the_opcode() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); GameData gameData = GameData.load(storage)) {
            int instructionOffset = SpriteScenario.findSequence(storage.readFile(ISCRIPT_FILE), WAIT_RANDOM_ZERO_SEQUENCE);

            for (int enabled : new int[]{RANDOM_DISABLED, RANDOM_ENABLED}) {
                LcgRandom random = new LcgRandom(RANDOM_SEED);

                random.setEnabled(enabled);

                Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), random);
                Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
                SpriteImage image = SpriteScenario.createImage(game, IMAGE_TYPE, 0, 0);

                image.setProgramCounter(instructionOffset);

                Assertions.assertTrue(game.getIscriptExecutor().execute(image, sprite, null));

                Assertions.assertEquals(MAXIMUM_WAIT, image.getWait());
                Assertions.assertEquals(instructionOffset + WAIT_RANDOM_SIZE, image.getProgramCounter());
                Assertions.assertEquals(enabled, random.getCallCount(WAIT_RANDOM_SOURCE));
                Assertions.assertEquals(enabled, random.getTotalRandomCounts());

                for (int remaining = MAXIMUM_WAIT - 1; remaining >= 0; remaining--) {
                    Assertions.assertTrue(game.getIscriptExecutor().execute(image, sprite, null));

                    Assertions.assertEquals(remaining, image.getWait());
                    Assertions.assertEquals(instructionOffset + WAIT_RANDOM_SIZE, image.getProgramCounter());
                }

                Assertions.assertEquals(enabled, random.getCallCount(WAIT_RANDOM_SOURCE));
                Assertions.assertEquals(enabled, random.getTotalRandomCounts());
            }
        }
    }
}
