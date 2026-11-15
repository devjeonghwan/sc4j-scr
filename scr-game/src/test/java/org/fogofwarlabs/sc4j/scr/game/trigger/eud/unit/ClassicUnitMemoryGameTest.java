package org.fogofwarlabs.sc4j.scr.game.trigger.eud.unit;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks shield regeneration after writes through the classic unit memory adapter.
 */
@RequiresInstallation
final class ClassicUnitMemoryGameTest {
    private static final int SHIELD_POINTS_ADDRESS = 0x59CCA8 + 0x60;

    @Test
    void unit_memory_serves_game_shield_regeneration_after_a_shield_write() throws Exception {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = Game.create(data, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

            game.getUnitSystem().initializeUnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);

            UnitPool initializedPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(initializedPool);

            Unit unit = initializedPool.getUnit(1);

            unit.setUnitId(ScrUnitIds.PROTOSS_PROBE);
            game.getSpriteSystem().initializeSpritePool(1);

            SpritePool spritePool = game.getSpriteSystem().getSpritePool();

            Assertions.assertNotNull(spritePool);

            Sprite allocatedSprite = spritePool.allocate();

            Assertions.assertNotNull(allocatedSprite);

            unit.setSprite(allocatedSprite);

            UnitPool memoryPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(memoryPool);

            ClassicUnitMemory memory = new ClassicUnitMemory(memoryPool);

            memory.write(SHIELD_POINTS_ADDRESS, Integer.BYTES, 0);
            game.getUnitTimers().update(unit);

            Assertions.assertEquals(7, unit.getShieldPoints());
            Assertions.assertEquals(7, memory.read(SHIELD_POINTS_ADDRESS, Integer.BYTES));

            memory.write(SHIELD_POINTS_ADDRESS, Byte.BYTES, 20);
            game.getUnitTimers().update(unit);

            Assertions.assertEquals(27, unit.getShieldPoints());
            Assertions.assertEquals(27, memory.read(SHIELD_POINTS_ADDRESS, Integer.BYTES));
        }
    }
}
