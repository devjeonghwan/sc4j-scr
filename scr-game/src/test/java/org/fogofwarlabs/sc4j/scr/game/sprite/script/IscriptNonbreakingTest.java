package org.fogofwarlabs.sc4j.scr.game.sprite.script;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks animation barriers and queued orders at script completion.
 */
@RequiresInstallation
final class IscriptNonbreakingTest {
    /**
     * The width and height of the test map in tiles.
     */
    private static final int MAP_DIMENSION = 8;
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
    private static final int RANDOM_SEED = 1;
    /**
     * The capacity of the image pool.
     */
    private static final int IMAGE_POOL_CAPACITY = 4;
    /**
     * The capacity of the order pool.
     */
    private static final int ORDER_POOL_CAPACITY = 4;
    /**
     * The horizontal position of the worker sprite.
     */
    private static final int SPRITE_POSITION_X = 100;
    /**
     * The vertical position of the worker sprite.
     */
    private static final int SPRITE_POSITION_Y = 100;
    /**
     * The image type of the worker's main image.
     */
    private static final int IMAGE_TYPE = 21;
    /**
     * The script offset of a {@code nobrkcodestart} instruction.
     */
    private static final int NONBREAKING_START_OFFSET = 15940;
    /**
     * The opcode of {@code nobrkcodestart}.
     */
    private static final int NONBREAKING_START_OPCODE = 0x2E;
    /**
     * The opcode of {@code nobrkcodeend}.
     */
    private static final int NONBREAKING_END_OPCODE = 0x2F;
    /**
     * The instruction limit of a single instruction.
     */
    private static final int INSTRUCTION_LIMIT = 1;
    /**
     * The instruction limit of the requested Walking animation.
     */
    private static final int ANIMATION_INSTRUCTION_LIMIT = 1000;
    /**
     * The instructions the search for {@code nobrkcodeend} scans at most.
     */
    private static final int SEARCH_INSTRUCTION_LIMIT = 256;
    /**
     * The horizontal target of the queued Move order.
     */
    private static final int MOVE_TARGET_X = 120;
    /**
     * The vertical target of the queued Move order.
     */
    private static final int MOVE_TARGET_Y = 130;
    /**
     * The unit status bit of a running nonbreaking code block.
     */
    private static final int NO_BREAK_STATUS = 0x2000;
    /**
     * The sprite flag of a running nonbreaking code block.
     */
    private static final int NO_BREAK_SPRITE_FLAG = 0x80;
    /**
     * The Move order.
     */
    private static final int MOVE_ORDER = 6;
    /**
     * The user action flag of a finished order.
     */
    private static final int ORDER_DONE_FLAG = 0x1;
    /**
     * The animation force that honors the sprite's nonbreaking flag.
     */
    private static final int UNFORCED = 0;
    /**
     * The sprite type of the worker sprite, which the nonbreaking block does not depend on.
     */
    private static final int SPRITE_TYPE = 0;
    /**
     * The pool index of the worker.
     */
    private static final int UNIT_POOL_INDEX = 1;

    @Test
    void nonbreaking_code_blocks_animation_until_its_end_opcode_releases_the_queued_order() throws Exception {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = Game.create(data, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(RANDOM_SEED));

            game.getSpriteSystem().initializeImagePool(IMAGE_POOL_CAPACITY);
            game.initializeOrderPool(ORDER_POOL_CAPACITY);

            Sprite sprite = SpriteScenario.createSprite(game, SPRITE_TYPE, SPRITE_POSITION_X, SPRITE_POSITION_Y, 0);
            SpriteImage image = SpriteScenario.createImage(game, IMAGE_TYPE, 0, 0);

            sprite.insertHeadImage(image);
            sprite.setMainImage(image);

            Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);

            unit.setUnitId(ScrUnitIds.TERRAN_SCV);
            unit.setSprite(sprite);
            image.setAnimationType(IscriptExecutor.ANIMATION_BUILT);
            image.setProgramCounter(NONBREAKING_START_OFFSET);

            Assertions.assertEquals(NONBREAKING_START_OPCODE, data.getIscript().decodeInstruction(NONBREAKING_START_OFFSET).getOpcode());

            Assertions.assertThrows(IllegalStateException.class, () -> game.getIscriptExecutor().executeSprite(sprite, unit, INSTRUCTION_LIMIT));

            Assertions.assertEquals(NO_BREAK_STATUS, unit.getStatus() & NO_BREAK_STATUS);
            Assertions.assertEquals(NO_BREAK_SPRITE_FLAG, sprite.getFlags() & NO_BREAK_SPRITE_FLAG);

            int retainedCounter = image.getProgramCounter();

            game.getIscriptExecutor().runSpriteAnimation(sprite, unit, IscriptExecutor.ANIMATION_WALKING, UNFORCED, ANIMATION_INSTRUCTION_LIMIT);

            Assertions.assertEquals(retainedCounter, image.getProgramCounter());

            game.getOrders().queueFront(unit, ScrOrderIds.MOVE, null, MOVE_TARGET_X, MOVE_TARGET_Y, ScrUnitIds.NONE);
            unit.setUserActionFlags(ORDER_DONE_FLAG);

            int endOffset = NONBREAKING_START_OFFSET;

            for (int count = 0; count < SEARCH_INSTRUCTION_LIMIT &&
                                data.getIscript().decodeInstruction(endOffset).getOpcode() != NONBREAKING_END_OPCODE; count++) {
                endOffset += data.getIscript().getInstructionSize(endOffset);
            }

            Assertions.assertEquals(NONBREAKING_END_OPCODE, data.getIscript().decodeInstruction(endOffset).getOpcode());

            image.setProgramCounter(endOffset);

            Assertions.assertThrows(IllegalStateException.class, () -> game.getIscriptExecutor().executeSprite(sprite, unit, INSTRUCTION_LIMIT));

            Assertions.assertEquals(0, unit.getStatus() & NO_BREAK_STATUS);
            Assertions.assertEquals(0, sprite.getFlags() & NO_BREAK_SPRITE_FLAG);
            Assertions.assertNull(unit.getFirstOrder());
            Assertions.assertEquals(MOVE_ORDER, unit.getPrimaryOrder());
            Assertions.assertEquals(MOVE_TARGET_X, unit.getOrderTargetX());
            Assertions.assertEquals(MOVE_TARGET_Y, unit.getOrderTargetY());
            Assertions.assertEquals(0, unit.getUserActionFlags() & ORDER_DONE_FLAG);
        }
    }
}
