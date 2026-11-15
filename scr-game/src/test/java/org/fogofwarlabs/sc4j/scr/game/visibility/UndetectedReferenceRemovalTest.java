package org.fogofwarlabs.sc4j.scr.game.visibility;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks detection masks against the twelve existing owner lists.
 */
@RequiresInstallation
final class UndetectedReferenceRemovalTest {
    /**
     * The width and height of the map in tiles.
     */
    private static final int MAP_SIZE = 16;
    /**
     * The number of tiles of the map.
     */
    private static final int TILE_COUNT = MAP_SIZE * MAP_SIZE;
    /**
     * The tileset index of the map.
     */
    private static final int TILESET_INDEX = 0;
    /**
     * The seed of the game random generator.
     */
    private static final int RANDOM_SEED = 0;
    /**
     * The capacity of the unit pool, which holds the cloaked and the neutral unit.
     */
    private static final int UNIT_POOL_CAPACITY = 3;
    /**
     * The pool index of the cloaked unit.
     */
    private static final int CLOAKED_POOL_INDEX = 1;
    /**
     * The pool index of the neutral unit.
     */
    private static final int NEUTRAL_POOL_INDEX = 2;
    /**
     * The owner of the cloaked unit.
     */
    private static final int CLOAKED_OWNER = 0;
    /**
     * The detection bits from {@code 12} up, which select no owner list.
     */
    private static final int HIGHER_DETECTION_BITS = 0xFFFFF000;
    /**
     * Every detection bit.
     */
    private static final int ALL_DETECTION_BITS = 0xFFFFFFFF;

    @Test
    void remove_undetected_references_should_ignore_detection_bits_from_twelve_up() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            GameMap map = new GameMap(MAP_SIZE, MAP_SIZE, TILESET_INDEX, new int[TILE_COUNT], new int[TILE_COUNT]);
            Game game = Game.create(gameData, map, new LcgRandom(RANDOM_SEED));

            game.getUnitSystem().initializeUnitPool(UNIT_POOL_CAPACITY);

            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            Unit cloaked = pool.getUnit(CLOAKED_POOL_INDEX);
            Unit neutral = pool.getUnit(NEUTRAL_POOL_INDEX);

            pool.prependToOwnerList(cloaked, CLOAKED_OWNER);
            pool.prependToOwnerList(neutral, PlayerTable.NEUTRAL_PLAYER_ID);
            neutral.setOrderTargetUnit(cloaked);

            // Higher detection bits select no existing owner list.
            game.getUnitCloaking().removeUndetectedReferences(cloaked, HIGHER_DETECTION_BITS);

            Assertions.assertSame(cloaked, neutral.getOrderTargetUnit());

            // The neutral player drops its target; higher detection bits are ignored.
            game.getUnitCloaking().removeUndetectedReferences(cloaked, ALL_DETECTION_BITS);

            Assertions.assertNull(neutral.getOrderTargetUnit());
        }
    }
}
