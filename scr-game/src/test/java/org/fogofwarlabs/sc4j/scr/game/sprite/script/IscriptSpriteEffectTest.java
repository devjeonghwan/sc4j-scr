package org.fogofwarlabs.sc4j.scr.game.sprite.script;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.data.type.constant.SpriteIds;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.sprite.*;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.visibility.SightCoverage;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks effect elevation, sprite creation and resource recycling.
 */
@RequiresInstallation
final class IscriptSpriteEffectTest {
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
     * The script offset of a {@code spruluselo} instruction.
     */
    private static final int UNDERLAY_OFFSET = 11374;
    /**
     * The opcode of {@code spruluselo}.
     */
    private static final int UNDERLAY_OPCODE = 0x13;
    /**
     * The script offset of a {@code lowsprul} instruction.
     */
    private static final int LOW_UNDERLAY_OFFSET = 21214;
    /**
     * The opcode of {@code lowsprul}.
     */
    private static final int LOW_UNDERLAY_OPCODE = 0x11;
    /**
     * The elevation level of the source sprite.
     */
    private static final int ELEVATION = 7;
    /**
     * The lowest elevation level, at which the lowered underlay elevation saturates.
     */
    private static final int LOWEST_ELEVATION = 0;
    /**
     * The elevation levels by which a directional underlay sprite is lower than its source.
     */
    private static final int ELEVATION_STEP = 1;
    /**
     * The frame directions of the source image: before, at and past {@link #LOWERING_DIRECTION}.
     */
    private static final int @NotNull [] DIRECTIONS = {7, 8, 16};
    /**
     * The first frame direction whose underlay sprite is {@link #ELEVATION_STEP} elevation level lower.
     */
    private static final int LOWERING_DIRECTION = 8;
    /**
     * The capacity of the image pool of the elevation test.
     */
    private static final int IMAGE_POOL_CAPACITY = 16;
    /**
     * The capacity of the effect pool of the elevation test.
     */
    private static final int EFFECT_POOL_CAPACITY = 2;
    /**
     * The capacity of the image pool of the birth effect test.
     */
    private static final int BIRTH_IMAGE_POOL_CAPACITY = 32;
    /**
     * The capacity of the effect pool of the birth effect test.
     */
    private static final int BIRTH_EFFECT_POOL_CAPACITY = 4;
    /**
     * The horizontal position of the source sprite.
     */
    private static final int SOURCE_POSITION_X = 100;
    /**
     * The vertical position of the source sprite.
     */
    private static final int SOURCE_POSITION_Y = 120;
    /**
     * The image type of the source sprite's image.
     */
    private static final int SOURCE_IMAGE = 19;
    /**
     * The horizontal offset of the source image.
     */
    private static final int SOURCE_OFFSET_X = 3;
    /**
     * The vertical offset of the source image.
     */
    private static final int SOURCE_OFFSET_Y = -2;
    /**
     * The instruction limit of the sprite executions and effect updates.
     */
    private static final int INSTRUCTION_LIMIT = 10000;
    /**
     * The horizontal source position plus the horizontal image offset.
     */
    private static final int EFFECT_BASE_X = 103;
    /**
     * The vertical source position plus the vertical image offset.
     */
    private static final int EFFECT_BASE_Y = 118;
    /**
     * The elevation level of a {@code lowsprul} sprite.
     */
    private static final int LOW_ELEVATION = 1;
    /**
     * The visibility flags of a sprite visible to every player.
     */
    private static final int ALL_PLAYERS_VISIBILITY = 255;
    /**
     * The effect updates the birth effect may take to end.
     */
    private static final int UPDATE_LIMIT = 200;
    /**
     * The effect count after the underlay instruction: the spawned effect.
     */
    private static final int SPAWNED_EFFECT_COUNT = 1;
    /**
     * The owner of the spawned effect sprite.
     */
    private static final int EFFECT_OWNER = 0;
    /**
     * The operand of {@code lowsprul} that holds the sprite type.
     */
    private static final int SPRITE_OPERAND = 0;
    /**
     * The operand of {@code lowsprul} that holds the horizontal offset.
     */
    private static final int OFFSET_X_OPERAND = 1;
    /**
     * The operand of {@code lowsprul} that holds the vertical offset.
     */
    private static final int OFFSET_Y_OPERAND = 2;
    /**
     * The pool index of the source unit.
     */
    private static final int UNIT_POOL_INDEX = 1;

    @Test
    void execute_sprite_should_lower_directional_underlay_elevation_and_saturate_at_zero() throws Exception {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Assertions.assertEquals(UNDERLAY_OPCODE, data.getIscript().decodeInstruction(UNDERLAY_OFFSET).getOpcode());

            for (int elevation : new int[]{LOWEST_ELEVATION, ELEVATION}) {
                for (int direction : DIRECTIONS) {
                    Game game = Game.create(data,
                                            new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT], new int[MAP_TILE_COUNT]),
                                            new LcgRandom(RANDOM_SEED));

                    game.getSpriteSystem().initializeImagePool(IMAGE_POOL_CAPACITY);
                    game.getSpriteSystem().initializeSpriteEffectPool(EFFECT_POOL_CAPACITY);

                    SightCoverage coverage = new SightCoverage(MAP_DIMENSION);

                    coverage.setSharedVisionMasks(new int[PlayerTable.PLAYER_SLOTS]);
                    game.setSightCoverage(coverage);
                    game.getLocalVision().setComparisonMask(Sprite.VISIBLE_TO_ALL_PLAYERS);

                    Sprite source = SpriteScenario.createSprite(game, SpriteIds.DRONE, SOURCE_POSITION_X, SOURCE_POSITION_Y, elevation);
                    SpriteImage image = SpriteScenario.createImage(game, SOURCE_IMAGE, 0, 0);

                    source.insertHeadImage(image);
                    source.setMainImage(image);
                    image.setFrameIndexOffset(direction);
                    image.setProgramCounter(UNDERLAY_OFFSET);

                    game.getIscriptExecutor().executeSprite(source, null, INSTRUCTION_LIMIT);

                    SpriteEffectPool pool = game.getSpriteSystem().getSpriteEffectPool();

                    Assertions.assertNotNull(pool);

                    Assertions.assertEquals(SPAWNED_EFFECT_COUNT, pool.getAllocatedCount());
                    Assertions.assertEquals(Math.max(LOWEST_ELEVATION, elevation - (direction >= LOWERING_DIRECTION ? ELEVATION_STEP : 0)),
                                            pool.getSpriteEffect(pool.getFirstActiveIndex()).getSprite().getElevationLevel());
                }
            }
        }
    }

    @Test
    void birth_effect_spawns_independently_and_recycles_its_sprite_and_images() throws Exception {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = Game.create(data,
                                    new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT], new int[MAP_TILE_COUNT]),
                                    new LcgRandom(RANDOM_SEED));

            game.getSpriteSystem().initializeImagePool(BIRTH_IMAGE_POOL_CAPACITY);
            game.getSpriteSystem().initializeSpriteEffectPool(BIRTH_EFFECT_POOL_CAPACITY);

            SightCoverage coverage = new SightCoverage(MAP_DIMENSION);

            coverage.setSharedVisionMasks(new int[PlayerTable.PLAYER_SLOTS]);
            game.setSightCoverage(coverage);
            game.getLocalVision().setComparisonMask(Sprite.VISIBLE_TO_ALL_PLAYERS);

            Sprite source = SpriteScenario.createSprite(game, SpriteIds.DRONE, SOURCE_POSITION_X, SOURCE_POSITION_Y, ELEVATION);
            SpriteImage image = SpriteScenario.createImage(game, SOURCE_IMAGE, SOURCE_OFFSET_X, SOURCE_OFFSET_Y);

            source.insertHeadImage(image);
            source.setMainImage(image);
            image.setProgramCounter(LOW_UNDERLAY_OFFSET);

            IscriptInstruction instruction = data.getIscript().decodeInstruction(LOW_UNDERLAY_OFFSET);

            Assertions.assertEquals(LOW_UNDERLAY_OPCODE, instruction.getOpcode());

            Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);

            unit.setUnitId(ScrUnitIds.ZERG_DRONE);
            unit.setSprite(source);

            game.getIscriptExecutor().executeSprite(source, unit, INSTRUCTION_LIMIT);

            SpriteEffectPool pool = game.getSpriteSystem().getSpriteEffectPool();

            Assertions.assertNotNull(pool);

            Assertions.assertEquals(SPAWNED_EFFECT_COUNT, pool.getAllocatedCount());

            SpriteEffect effect = pool.getSpriteEffect(pool.getFirstActiveIndex());
            Sprite sprite = effect.getSprite();

            Assertions.assertNotSame(source, sprite);
            Assertions.assertEquals(instruction.getOperands()[SPRITE_OPERAND], sprite.getSpriteTypeId());
            Assertions.assertEquals(EFFECT_BASE_X + instruction.getOperands()[OFFSET_X_OPERAND], sprite.getX());
            Assertions.assertEquals(EFFECT_BASE_Y + instruction.getOperands()[OFFSET_Y_OPERAND], sprite.getY());
            Assertions.assertEquals(LOW_ELEVATION, sprite.getElevationLevel());
            Assertions.assertEquals(EFFECT_OWNER, sprite.getOwner());
            Assertions.assertEquals(ALL_PLAYERS_VISIBILITY, sprite.getVisibilityFlags());

            int spriteIndex = sprite.getPoolIndex();

            for (int frame = 0; frame < UPDATE_LIMIT && pool.getAllocatedCount() != 0; frame++) {
                game.getIscriptExecutor().updatePrimarySpriteEffects(INSTRUCTION_LIMIT);
            }

            Assertions.assertEquals(0, pool.getAllocatedCount());

            SpritePool observedSpritePool = game.getSpriteSystem().getSpritePool();

            Assertions.assertNotNull(observedSpritePool);

            Assertions.assertFalse(observedSpritePool.isAllocated(spriteIndex));
            Assertions.assertSame(source, unit.getSpriteOrNull());
        }
    }
}
