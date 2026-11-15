package org.fogofwarlabs.sc4j.scr.game.state;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.game.frame.GameExecutionScope;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteEffectPool;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImagePool;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks unit pool generations, initialization readiness and owned-pool preservation.
 */
final class GameStateLifecycleTest {
    private static final int POOL_CAPACITY = 2;
    private static final int REPLACEMENT_POOL_CAPACITY = 3;

    @Test
    void advance_generation_should_change_generation_before_allocation_commits() {
        Game game = Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(1);

        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        int initialGeneration = pool.getGeneration(1);

        pool.advanceGeneration(pool.getUnit(1));

        Assertions.assertNotEquals(initialGeneration, pool.getGeneration(1));
        Assertions.assertFalse(pool.isAllocated(1));
        Assertions.assertEquals(1, pool.getFirstFreeIndex());
    }

    @Test
    void finish_initialization_should_not_publish_world_readiness() {
        Game game = Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

        Assertions.assertNull(game.getUnitSystem().getUnitPool());

        game.initializeExecutionScope();

        GameExecutionScope scope = game.getExecutionScope();

        Assertions.assertNotNull(scope);
        Assertions.assertTrue(scope.isInitializationActive());

        scope.finishInitialization();

        Assertions.assertFalse(scope.isInitializationActive());
        Assertions.assertFalse(game.hasInitializedWorldState());
        Assertions.assertThrows(IllegalStateException.class, game::markWorldInitialized);
    }

    @Test
    void mark_world_initialized_should_close_initialization_and_keep_owned_pools() {
        Game game = Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

        game.initializeExecutionScope();
        game.getUnitSystem().initializeUnitPool(POOL_CAPACITY);
        game.getSpriteSystem().initializeSpritePool(POOL_CAPACITY);
        game.getSpriteSystem().initializeImagePool(POOL_CAPACITY);
        game.getSpriteSystem().initializeSpriteEffectPool(POOL_CAPACITY);

        UnitPool units = game.getUnitSystem().getUnitPool();
        SpritePool sprites = game.getSpriteSystem().getSpritePool();
        SpriteImagePool images = game.getSpriteSystem().getImagePool();
        SpriteEffectPool effects = game.getSpriteSystem().getSpriteEffectPool();

        Assertions.assertNotNull(units);

        UnitSelection selection = new UnitSelection(units, false, 0, 0);

        game.setSelection(selection);
        game.markWorldInitialized();

        Assertions.assertTrue(game.hasInitializedWorldState());

        GameExecutionScope initializedScope = game.getExecutionScope();

        Assertions.assertNotNull(initializedScope);
        Assertions.assertFalse(initializedScope.isInitializationActive());
        Assertions.assertThrows(IllegalStateException.class, () -> game.getUnitSystem().initializeUnitPool(REPLACEMENT_POOL_CAPACITY));
        Assertions.assertThrows(IllegalStateException.class, () -> game.getSpriteSystem().initializeSpritePool(REPLACEMENT_POOL_CAPACITY));
        Assertions.assertThrows(IllegalStateException.class, () -> game.getSpriteSystem().initializeImagePool(REPLACEMENT_POOL_CAPACITY));
        Assertions.assertThrows(IllegalStateException.class, () -> game.getSpriteSystem().initializeSpriteEffectPool(REPLACEMENT_POOL_CAPACITY));
        Assertions.assertSame(units, game.getUnitSystem().getUnitPool());
        Assertions.assertSame(sprites, game.getSpriteSystem().getSpritePool());
        Assertions.assertSame(images, game.getSpriteSystem().getImagePool());
        Assertions.assertSame(effects, game.getSpriteSystem().getSpriteEffectPool());
        Assertions.assertSame(selection, game.getSelection());
    }
}
