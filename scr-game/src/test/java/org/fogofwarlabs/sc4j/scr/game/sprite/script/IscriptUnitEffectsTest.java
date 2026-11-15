package org.fogofwarlabs.sc4j.scr.game.sprite.script;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPoolScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks image offsets, attack flags and unit movement effects.
 */
@RequiresInstallation
final class IscriptUnitEffectsTest {
    /**
     * The bytes of {@code setvertpos 0} followed by {@code wait 1}.
     */
    private static final byte @NotNull [] VERTICAL_OFFSET_SEQUENCE = {3, 0, 5, 1};
    /**
     * The bytes of {@code gotorepeatattk} followed by {@code wait 1}.
     */
    private static final byte @NotNull [] REPEAT_ATTACK_SEQUENCE = {0x2A, 5, 1};
    /**
     * The bytes of {@code setflspeed 0}, {@code playfram 0} and {@code wait 2}.
     */
    private static final byte @NotNull [] SET_SPEED_SEQUENCE = {0x37, 0, 0, 0, 0, 0, 5, 2};
    /**
     * The iscript of the marine.
     */
    private static final int MARINE_SCRIPT = 78;
    /**
     * The opcode of {@code move}.
     */
    private static final int MOVE_OPCODE = 0x29;
    /**
     * The operands of the {@code move} at the marine's Walking entry.
     */
    private static final int @NotNull [] MOVE_OPERANDS = {4};
    /**
     * The byte size of a {@code move} instruction.
     */
    private static final int MOVE_SIZE = 2;
    /**
     * The opcode of {@code wait}.
     */
    private static final int WAIT_OPCODE = 5;
    /**
     * The capacity of the unit pool.
     */
    private static final int UNIT_POOL_CAPACITY = 1;
    /**
     * The pool index of the unit context.
     */
    private static final int UNIT_POOL_INDEX = 1;
    /**
     * The Walking animation.
     */
    private static final int WALKING_ANIMATION = 11;
    /**
     * The unit status without any flag.
     */
    private static final int NO_STATUS = 0;
    /**
     * The vertical offset that {@code setvertpos 0} sets.
     */
    private static final int SET_OFFSET_Y = 0;
    /**
     * The wait that {@code wait 1} leaves after its execution.
     */
    private static final int REMAINING_WAIT = 0;
    /**
     * The top speed that {@code setflspeed 0} writes.
     */
    private static final int SET_TOP_SPEED = 0;
    /**
     * The initial state hash of the unit hash comparison.
     */
    private static final int INITIAL_HASH = 0;
    /**
     * The vertical offset of an image before {@code setvertpos 0}.
     */
    private static final int INITIAL_OFFSET_Y = -7;
    /**
     * Image flags that include bits above the flag byte, without the redraw flag.
     */
    private static final int IMAGE_FLAGS = 0x8220;
    /**
     * {@link #IMAGE_FLAGS} with the redraw flag.
     */
    private static final int REDRAWN_IMAGE_FLAGS = 0x8221;
    /**
     * The program counter advance over {@code setvertpos 0} and {@code wait 1}.
     */
    private static final int VERTICAL_OFFSET_ADVANCE = 4;
    /**
     * The vertical offsets of an image before an image preview of {@code setvertpos 0}.
     */
    private static final int @NotNull [] OLD_OFFSETS = {0, 1, -1, 127, -128};
    /**
     * The instruction limit of the image previews.
     */
    private static final int PREVIEW_INSTRUCTION_LIMIT = 2;
    /**
     * The marine image type.
     */
    private static final int MARINE_IMAGE = 239;
    /**
     * The horizontal position of the unit.
     */
    private static final int UNIT_POSITION_X = 128;
    /**
     * The vertical position of the unit.
     */
    private static final int UNIT_POSITION_Y = 256;
    /**
     * The heading and movement direction of the unit.
     */
    private static final int HEADING = 32;
    /**
     * The current and next speed that the marine's {@code move} gives a unit with the speed upgrade.
     */
    private static final int MOVE_SPEED = 1536;
    /**
     * The horizontal velocity of {@link #MOVE_SPEED} at {@link #HEADING}.
     */
    private static final int MOVE_VELOCITY_X = 1086;
    /**
     * The vertical velocity of {@link #MOVE_SPEED} at {@link #HEADING}.
     */
    private static final int MOVE_VELOCITY_Y = -1086;
    /**
     * The program counter advance over the marine's Walking {@code move} and its {@code wait}.
     */
    private static final int MOVE_ADVANCE = 4;
    /**
     * The program counter advance over {@code gotorepeatattk} and {@code wait 1}.
     */
    private static final int REPEAT_ATTACK_ADVANCE = 3;
    /**
     * The program counter advance over {@code setflspeed 0}, {@code playfram 0} and {@code wait 2}.
     */
    private static final int SET_SPEED_ADVANCE = 8;
    /**
     * The top speed of the unit.
     */
    private static final int TOP_SPEED = 4096;
    /**
     * The current speed of the unit.
     */
    private static final int CURRENT_SPEED = 1024;
    /**
     * The next speed of the unit.
     */
    private static final int NEXT_SPEED = 2048;
    /**
     * The horizontal velocity of the unit.
     */
    private static final int VELOCITY_X = 3072;
    /**
     * The vertical velocity of the unit.
     */
    private static final int VELOCITY_Y = -4096;
    /**
     * The movement flag byte values.
     */
    private static final int MOVEMENT_FLAG_RANGE = 256;
    /**
     * The movement flag that requests a repeated attack.
     */
    private static final int REPEAT_ATTACK_FLAG = 8;
    /**
     * The movement flag bits other than {@link #REPEAT_ATTACK_FLAG}.
     */
    private static final int KEPT_MOVEMENT_FLAGS = 0xF7;
    /**
     * The movement flags of the unit whose top speed is written.
     */
    private static final int MOVEMENT_FLAGS = 0xA5;
    /**
     * The top speed of the unit before {@code setflspeed 0}.
     */
    private static final int OLD_TOP_SPEED = 0x87654321;
    /**
     * The wait that {@code wait 2} leaves after its first execution.
     */
    private static final int SET_SPEED_WAIT = 1;
    /**
     * The instruction limit of the image preview of {@code setflspeed}.
     */
    private static final int SET_SPEED_PREVIEW_LIMIT = 3;
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
     * The sprite type of the owning sprites, which the unit effects do not depend on.
     */
    private static final int SPRITE_TYPE = 0;
    /**
     * The image type of the executing images outside the movement test, which the unit effects do not depend on.
     */
    private static final int IMAGE_TYPE = 0;
    /**
     * The installed script file that the instruction sequences are found in.
     */
    private static final @NotNull String ISCRIPT_FILE = "scripts/iscript.bin";

    private static @Nullable GameData gameData;

    private static int verticalOffset;
    private static int repeatAttackOffset;
    private static int setSpeedOffset;
    private static int movementOffset;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());

        byte[] script = gameData.readInstalledFile(ISCRIPT_FILE);

        verticalOffset     = SpriteScenario.findSequence(script, VERTICAL_OFFSET_SEQUENCE);
        repeatAttackOffset = SpriteScenario.findSequence(script, REPEAT_ATTACK_SEQUENCE);
        setSpeedOffset     = SpriteScenario.findSequence(script, SET_SPEED_SEQUENCE);
        movementOffset     = gameData.getIscript().getAnimationOffset(MARINE_SCRIPT, WALKING_ANIMATION);

        Assertions.assertEquals(MOVE_OPCODE, gameData.getIscript().decodeInstruction(movementOffset).getOpcode());
        Assertions.assertArrayEquals(MOVE_OPERANDS, gameData.getIscript().decodeInstruction(movementOffset).getOperands());
        Assertions.assertEquals(WAIT_OPCODE, gameData.getIscript().decodeInstruction(movementOffset + MOVE_SIZE).getOpcode());
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            gameData.close();
        }
    }

    private static @NotNull Unit createUnit(@NotNull Game game, int horizontalPosition, int verticalPosition) {
        UnitPool pool = game.getUnitSystem().getUnitPool();

        if (pool == null) {
            game.getUnitSystem().initializeUnitPool(UNIT_POOL_CAPACITY);
            pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            UnitPoolScenario.allocate(pool);
        }

        Unit unit = pool.getUnit(UNIT_POOL_INDEX);

        unit.setPositionX(horizontalPosition);
        unit.setPositionY(verticalPosition);
        unit.setStatus(NO_STATUS);

        return unit;
    }


    @Test
    void execute_should_skip_vertical_offset_for_cloaked_or_detector_required_units() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));
        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);

        for (int status : new int[]{
                NO_STATUS, UnitStatusFlags.REQUIRES_DETECTOR, UnitStatusFlags.CLOAKED, UnitStatusFlags.REQUIRES_DETECTOR | UnitStatusFlags.CLOAKED
        }) {
            SpriteImage image = SpriteScenario.createImage(game, IMAGE_TYPE, 0, INITIAL_OFFSET_Y);

            image.setFlags(IMAGE_FLAGS);
            image.setProgramCounter(verticalOffset);

            Unit unit = createUnit(game, 0, 0);

            unit.setStatus(status);
            unit.setSpeedIncreaseByte(0);
            unit.setSpeedDecreaseByte(0);
            unit.setHeading(0);
            unit.setMovementFlags(0);
            unit.setTopSpeed(0);
            unit.setCurrentSpeed(0);
            unit.setNextSpeed(0);
            unit.setVelocityX(0);
            unit.setVelocityY(0);

            Assertions.assertTrue(game.getIscriptExecutor().execute(image, sprite, unit));

            Assertions.assertEquals(status == NO_STATUS ? SET_OFFSET_Y : INITIAL_OFFSET_Y, image.getOffsetY());
            Assertions.assertEquals(status == NO_STATUS ? REDRAWN_IMAGE_FLAGS : IMAGE_FLAGS, image.getFlags());
            Assertions.assertEquals(verticalOffset + VERTICAL_OFFSET_ADVANCE, image.getProgramCounter());
            Assertions.assertEquals(REMAINING_WAIT, image.getWait());
        }

        SpriteImage image = SpriteScenario.createImage(game, IMAGE_TYPE, 0, INITIAL_OFFSET_Y);

        image.setFlags(IMAGE_FLAGS);
        image.setProgramCounter(verticalOffset);

        Unit unit = createUnit(game, 0, 0);

        Assertions.assertTrue(game.getIscriptExecutor().execute(image, sprite, unit));

        Assertions.assertEquals(SET_OFFSET_Y, image.getOffsetY());
        Assertions.assertEquals(REDRAWN_IMAGE_FLAGS, image.getFlags());
        Assertions.assertEquals(verticalOffset + VERTICAL_OFFSET_ADVANCE, image.getProgramCounter());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void execute_image_preview_should_permit_vertical_offset_but_reject_move_without_unit() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));
        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);

        for (int oldOffset : OLD_OFFSETS) {
            SpriteImage image = SpriteScenario.createImage(game, IMAGE_TYPE, 0, oldOffset);

            image.setFlags(IMAGE_FLAGS);
            image.setProgramCounter(verticalOffset);

            Assertions.assertTrue(game.getIscriptExecutor().executeImagePreview(image, sprite, PREVIEW_INSTRUCTION_LIMIT));

            Assertions.assertEquals(SET_OFFSET_Y, image.getOffsetY());
            Assertions.assertEquals(oldOffset == SET_OFFSET_Y ? IMAGE_FLAGS : REDRAWN_IMAGE_FLAGS, image.getFlags());
        }

        SpriteImage image = SpriteScenario.createImage(game, IMAGE_TYPE, 0, 0);

        image.setProgramCounter(movementOffset);

        UnsupportedOperationException exception = Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> game.getIscriptExecutor().executeImagePreview(image, sprite, PREVIEW_INSTRUCTION_LIMIT)
        );

        Assertions.assertTrue(exception.getMessage().contains("move"));
        Assertions.assertEquals(movementOffset, image.getProgramCounter());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void execute_should_apply_move_effects_from_canonical_unit_fields() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));
        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
        SpriteImage image = SpriteScenario.createImage(game, MARINE_IMAGE, 0, 0);

        image.setProgramCounter(movementOffset);

        Unit unit = createUnit(game, UNIT_POSITION_X, UNIT_POSITION_Y);

        unit.setStatus(UnitStatusFlags.SPEED_UPGRADE);
        unit.setSpeedIncreaseByte(0);
        unit.setSpeedDecreaseByte(0);
        unit.setHeading(HEADING);
        unit.setMovementDirection(HEADING);
        unit.setMovementFlags(0);
        unit.setTopSpeed(0);
        unit.setCurrentSpeed(0);
        unit.setNextSpeed(0);
        unit.setVelocityX(0);
        unit.setVelocityY(0);

        Assertions.assertTrue(game.getIscriptExecutor().execute(image, sprite, unit));

        Assertions.assertEquals(MOVE_SPEED, unit.getNextSpeed());
        Assertions.assertEquals(MOVE_SPEED, unit.getCurrentSpeed());
        Assertions.assertEquals(MOVE_VELOCITY_X, unit.getVelocityX());
        Assertions.assertEquals(MOVE_VELOCITY_Y, unit.getVelocityY());
        Assertions.assertEquals(UNIT_POSITION_X, unit.getPositionX());
        Assertions.assertEquals(UNIT_POSITION_Y, unit.getPositionY());
        Assertions.assertEquals(movementOffset + MOVE_ADVANCE, image.getProgramCounter());
        Assertions.assertEquals(REMAINING_WAIT, image.getWait());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void execute_should_use_zero_initialized_pooled_movement_fields() {
        for (int instructionOffset : new int[]{repeatAttackOffset, setSpeedOffset}) {
            Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));
            Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
            SpriteImage image = SpriteScenario.createImage(game, IMAGE_TYPE, 0, 0);

            image.setProgramCounter(instructionOffset);

            Unit unit = createUnit(game, 0, 0);

            unit.setNextSpeed(NEXT_SPEED);

            Assertions.assertTrue(game.getIscriptExecutor().execute(image, sprite, unit));

            Assertions.assertEquals(instructionOffset + (instructionOffset == repeatAttackOffset ? REPEAT_ATTACK_ADVANCE : SET_SPEED_ADVANCE),
                                    image.getProgramCounter());
            Assertions.assertEquals(NEXT_SPEED, unit.getNextSpeed());
        }
    }

    @Test
    void execute_should_clear_only_the_repeat_attack_flag_and_permit_absent_unit() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));
        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
        Unit unit = createUnit(game, UNIT_POSITION_X, UNIT_POSITION_Y);

        for (int movementFlags = 0; movementFlags < MOVEMENT_FLAG_RANGE; movementFlags++) {
            unit.setStatus(NO_STATUS);
            unit.setSpeedIncreaseByte(0);
            unit.setSpeedDecreaseByte(0);
            unit.setHeading(HEADING);
            unit.setMovementDirection(HEADING);
            unit.setMovementFlags(movementFlags);
            unit.setTopSpeed(TOP_SPEED);
            unit.setCurrentSpeed(CURRENT_SPEED);
            unit.setNextSpeed(NEXT_SPEED);
            unit.setVelocityX(VELOCITY_X);
            unit.setVelocityY(VELOCITY_Y);

            SpriteImage image = SpriteScenario.createImage(game, IMAGE_TYPE, 0, 0);

            image.setProgramCounter(repeatAttackOffset);

            long initialHash = unit.computeStateHash(INITIAL_HASH);

            Assertions.assertTrue(game.getIscriptExecutor().execute(image, sprite, unit));

            Assertions.assertEquals(movementFlags & KEPT_MOVEMENT_FLAGS, unit.getMovementFlags());
            Assertions.assertEquals(repeatAttackOffset + REPEAT_ATTACK_ADVANCE, image.getProgramCounter());
            Assertions.assertEquals(REMAINING_WAIT, image.getWait());

            if ((movementFlags & REPEAT_ATTACK_FLAG) == 0) {
                Assertions.assertEquals(initialHash, unit.computeStateHash(INITIAL_HASH), "without the repeat attack flag the unit is unchanged");
            }

            Assertions.assertEquals(TOP_SPEED, unit.getTopSpeed());
            Assertions.assertEquals(CURRENT_SPEED, unit.getCurrentSpeed());
            Assertions.assertEquals(NEXT_SPEED, unit.getNextSpeed());
            Assertions.assertEquals(VELOCITY_X, unit.getVelocityX());
            Assertions.assertEquals(VELOCITY_Y, unit.getVelocityY());
        }

        SpriteImage image = SpriteScenario.createImage(game, IMAGE_TYPE, 0, 0);

        image.setProgramCounter(repeatAttackOffset);

        Assertions.assertTrue(game.getIscriptExecutor().execute(image, sprite, null));

        Assertions.assertEquals(repeatAttackOffset + REPEAT_ATTACK_ADVANCE, image.getProgramCounter());

        image.setProgramCounter(repeatAttackOffset);

        Assertions.assertTrue(game.getIscriptExecutor().executeImagePreview(image, sprite, PREVIEW_INSTRUCTION_LIMIT));

        Assertions.assertEquals(repeatAttackOffset + REPEAT_ATTACK_ADVANCE, image.getProgramCounter());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void execute_should_write_top_speed_without_changing_current_speed_next_speed_or_velocity() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));
        Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, 0, 0, 0);
        SpriteImage image = SpriteScenario.createImage(game, IMAGE_TYPE, 0, 0);

        image.setProgramCounter(setSpeedOffset);

        Unit unit = createUnit(game, UNIT_POSITION_X, UNIT_POSITION_Y);

        unit.setStatus(NO_STATUS);
        unit.setSpeedIncreaseByte(0);
        unit.setSpeedDecreaseByte(0);
        unit.setHeading(HEADING);
        unit.setMovementDirection(HEADING);
        unit.setMovementFlags(MOVEMENT_FLAGS);
        unit.setTopSpeed(OLD_TOP_SPEED);
        unit.setCurrentSpeed(CURRENT_SPEED);
        unit.setNextSpeed(NEXT_SPEED);
        unit.setVelocityX(VELOCITY_X);
        unit.setVelocityY(VELOCITY_Y);

        Assertions.assertTrue(game.getIscriptExecutor().execute(image, sprite, unit));

        Assertions.assertEquals(SET_TOP_SPEED, unit.getTopSpeed());
        Assertions.assertEquals(MOVEMENT_FLAGS, unit.getMovementFlags());
        Assertions.assertEquals(CURRENT_SPEED, unit.getCurrentSpeed());
        Assertions.assertEquals(NEXT_SPEED, unit.getNextSpeed());
        Assertions.assertEquals(VELOCITY_X, unit.getVelocityX());
        Assertions.assertEquals(VELOCITY_Y, unit.getVelocityY());
        Assertions.assertEquals(UNIT_POSITION_X, unit.getPositionX());
        Assertions.assertEquals(UNIT_POSITION_Y, unit.getPositionY());
        Assertions.assertEquals(setSpeedOffset + SET_SPEED_ADVANCE, image.getProgramCounter());
        Assertions.assertEquals(SET_SPEED_WAIT, image.getWait());

        image.setWait(0);
        image.setProgramCounter(setSpeedOffset);

        Assertions.assertThrows(IllegalStateException.class, () -> game.getIscriptExecutor().execute(image, sprite, null));

        Assertions.assertEquals(setSpeedOffset, image.getProgramCounter());

        Assertions.assertThrows(UnsupportedOperationException.class,
                                () -> game.getIscriptExecutor().executeImagePreview(image, sprite, SET_SPEED_PREVIEW_LIMIT));

        Assertions.assertEquals(setSpeedOffset, image.getProgramCounter());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }
}
