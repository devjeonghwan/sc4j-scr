package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.ai.UnitStrengthTable;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImagePool;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks damage overlays and image recycling after health restoration.
 */
@RequiresInstallation
final class UnitHealthTest {
    private static final int MAP_DIMENSION = 8;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int IMAGE_CAPACITY = 64;
    private static final int SPRITE_POSITION = 100;
    private static final int INSTRUCTION_LIMIT = 10000;
    private static final int EXCESS_HIT_POINTS = 100;

    @Test
    void set_hit_points_should_add_damage_flames_and_recycle_them_on_full_health() throws Exception {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = Game.create(data, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(1));

            game.getRandom().setEnabled(1);
            game.getSpriteSystem().initializeImagePool(IMAGE_CAPACITY);
            game.setUnitStrengthTable(new UnitStrengthTable(data.getUnitTypes(), data.getWeaponTypes()));

            int unitType = ScrUnitIds.TERRAN_SUPPLY_DEPOT;
            int spriteType = data.getFlingy().get("Sprite", data.getUnits().get("Graphics", unitType));
            int imageType = data.getSprites().get("Image", spriteType);
            Sprite sprite = SpriteScenario.createSprite(game, spriteType, SPRITE_POSITION, SPRITE_POSITION, 0);
            SpriteImage image = SpriteScenario.createImage(game, imageType, 0, 0);

            sprite.insertHeadImage(image);
            sprite.setMainImage(image);

            Unit unit = UnitTestUtils.createUnit(1);

            unit.setUnitId(unitType);
            unit.setStatus(UnitStatusFlags.COMPLETED);
            unit.setSprite(sprite);

            int states = data.getImageDamageOverlayCount(imageType);

            Assertions.assertTrue(states > 0);

            unit.setDamageOverlayCount(states);

            int maximum = data.getUnits().get("Hit Points", unitType);

            game.getUnitHealth().setHitPoints(unit, maximum / 2, INSTRUCTION_LIMIT);

            Assertions.assertTrue(sprite.getImages().size() > 1);
            Assertions.assertTrue(unit.getDamageOverlayCount() < states);
            Assertions.assertTrue(game.getRandom().getTotalRandomCounts() > 0);

            game.getUnitHealth().setHitPoints(unit, maximum + EXCESS_HIT_POINTS, INSTRUCTION_LIMIT);

            Assertions.assertEquals(maximum, unit.getHitPoints());
            Assertions.assertEquals(states, unit.getDamageOverlayCount());
            Assertions.assertEquals(1, sprite.getImages().size());

            SpriteImagePool observedImagePool = game.getSpriteSystem().getImagePool();

            Assertions.assertNotNull(observedImagePool);
            Assertions.assertEquals(1, observedImagePool.getAllocatedCount());
        }
    }
}
