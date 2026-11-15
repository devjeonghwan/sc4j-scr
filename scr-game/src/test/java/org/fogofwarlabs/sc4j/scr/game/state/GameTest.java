package org.fogofwarlabs.sc4j.scr.game.state;

import org.fogofwarlabs.sc4j.engine.game.EngineLimits;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.game.combat.ScrCombatSystem;
import org.fogofwarlabs.sc4j.scr.game.movement.ScrCollisionSystem;
import org.fogofwarlabs.sc4j.scr.game.movement.ScrMovementSystem;
import org.fogofwarlabs.sc4j.scr.game.path.ScrPathfindingSystem;
import org.fogofwarlabs.sc4j.scr.game.player.ScrEconomySystem;
import org.fogofwarlabs.sc4j.scr.game.production.ScrProductionSystem;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.ScrSpatialIndex;
import org.fogofwarlabs.sc4j.scr.game.visibility.ScrVisibilitySystem;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests the SC:R game as the game system of an engine game: the adopted random generator, the frame clock, the frame
 * sequence, the pool sizes, the views of the SC:R systems under the engine system keys and the state hash.
 */
final class GameTest {
    private static final int FRAME = 5;
    private static final int FRAME_STEP_COUNT = 6;

    private static final int IMAGES = 5000;
    private static final int SPRITES = 2500;
    private static final int SPRITE_EFFECTS = 500;
    private static final int UNITS = 1700;
    private static final int BULLETS = 100;
    private static final int ORDERS = 2000;

    private static @NotNull GameMap createMap() {
        return new GameMap(1, 1, 0, new int[1]);
    }


    @Test
    void create_should_attach_the_game_system_to_an_engine_game_with_the_setup_random_generator() {
        LcgRandom random = new LcgRandom(1);
        Game game = Game.create(null, createMap(), random);
        org.fogofwarlabs.sc4j.engine.game.Game engineGame = game.getEngineGame();

        game.setCurrentFrame(FRAME);

        Assertions.assertSame(game, engineGame.getModSystem(Game.SYSTEM_KEY));
        Assertions.assertSame(random, engineGame.getRandom());
        Assertions.assertEquals(FRAME, engineGame.getClock().getFrame());
        Assertions.assertEquals(FRAME_STEP_COUNT, engineGame.getFrameRunner().getPhases().getCount());
        Assertions.assertEquals(0, engineGame.getUnits().getCapacity());
        Assertions.assertNull(game.getUnitSystem().getUnitPool());
    }

    @Test
    void create_should_size_the_pools_by_the_engine_limits_of_the_native_limits() {
        int[] limits = {IMAGES, SPRITES, SPRITE_EFFECTS, UNITS, BULLETS, ORDERS, SPRITE_EFFECTS};
        Game game = Game.create(new GameSetup.Builder(null, createMap(), new LcgRandom(1)).setLimits(limits).build());
        EngineLimits engineLimits = game.getEngineGame().getLimits();

        Assertions.assertEquals(UNITS, engineLimits.getUnits());
        Assertions.assertEquals(SPRITES, engineLimits.getSprites());
        Assertions.assertEquals(IMAGES, engineLimits.getImages());
        Assertions.assertEquals(ORDERS, engineLimits.getOrders());
        Assertions.assertEquals(BULLETS, engineLimits.getProjectiles());
        Assertions.assertEquals(UNITS, game.getUnitSystem().requireUnitPool().getCapacity());
        Assertions.assertEquals(0, game.getEngineGame().getUnits().getCapacity());
    }

    @Test
    void create_should_serve_the_engine_systems_with_views_of_the_scr_systems() {
        org.fogofwarlabs.sc4j.engine.game.Game engineGame = Game.create(null, createMap(), new LcgRandom(1)).getEngineGame();

        Assertions.assertInstanceOf(ScrEconomySystem.class, engineGame.getEconomy());
        Assertions.assertInstanceOf(ScrSpatialIndex.class, engineGame.getSpatialIndex());
        Assertions.assertInstanceOf(ScrVisibilitySystem.class, engineGame.getVisibility());
        Assertions.assertInstanceOf(ScrCollisionSystem.class, engineGame.getCollision());
        Assertions.assertInstanceOf(ScrPathfindingSystem.class, engineGame.getPathfinding());
        Assertions.assertInstanceOf(ScrMovementSystem.class, engineGame.getMovement());
        Assertions.assertInstanceOf(ScrCombatSystem.class, engineGame.getCombat());
        Assertions.assertInstanceOf(ScrProductionSystem.class, engineGame.getProduction());
    }

    @Test
    void engine_state_hash_should_change_with_the_game_state_hash() {
        Game game = Game.create(null, createMap(), new LcgRandom(1));
        long engineHash = game.getEngineGame().computeStateHash();
        long gameHash = game.computeStateHash();

        game.incrementTurnCount();

        Assertions.assertNotEquals(gameHash, game.computeStateHash());
        Assertions.assertNotEquals(engineHash, game.getEngineGame().computeStateHash());
    }
}
