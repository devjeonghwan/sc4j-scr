package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMapScenario;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Checks ground and air elevation filters through completed units with independent public unit WORD storage.
 */
@RequiresInstallation
final class TriggerLocationsTest {
    private static final int CREATION_POSITION_X = 3560;
    private static final int CREATION_POSITION_Y = 2160;
    private static final int POSITION_DISTANCE_TILES = 3;
    private static final int LOW_GROUND_HEIGHT = 0;

    /**
     * Checks aligned positions first, then changes only the unit WORDs while retaining the created sprite.
     *
     * @param unitType      the genuine ground or flying type
     * @param elevationFlag its low-ground elevation flag
     * @throws IOException if the installation or replay cannot be loaded
     */
    private static void assertSpriteElevation(int unitType, int elevationFlag) throws IOException {
        Path installationDirectory = ExternalInput.INSTALLATION.getPath();

        Assertions.assertNotNull(installationDirectory);

        try (GameData data = GameData.loadFrom(installationDirectory)) {
            ReplayRuntime runtime = RunningGameScenario.start(data);
            TerrainTileset terrain = data.getTerrainTileset(runtime.getGame().getMap().getTileset());

            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                Game game = runtime.getGame();
                Unit unit = RunningGameScenario.createUnit(game, unitType, CREATION_POSITION_X, CREATION_POSITION_Y, RunningGameScenario.HUMAN);
                Sprite sprite = unit.getSpriteOrNull();

                Assertions.assertNotNull(sprite);
                Assertions.assertNotEquals(0, unit.getStatus() & UnitStatusFlags.COMPLETED);
                Assertions.assertEquals(unitType == ScrUnitIds.TERRAN_WRAITH, (unit.getStatus() & UnitStatusFlags.FLYING) != 0);

                GameMap map = game.getMap();
                int destinationX = sprite.getX() + POSITION_DISTANCE_TILES * TerrainTileset.TILE_SIZE;
                int lowIndex = sprite.getY() / TerrainTileset.TILE_SIZE * map.getWidth() + sprite.getX() / TerrainTileset.TILE_SIZE;
                int highIndex = sprite.getY() / TerrainTileset.TILE_SIZE * map.getWidth() + destinationX / TerrainTileset.TILE_SIZE;
                int lowTile = GameMapScenario.findTileAtHeight(terrain, sprite.getX(), sprite.getY(), LOW_GROUND_HEIGHT);
                int highTile = GameMapScenario.findTileAtHeight(terrain, destinationX, sprite.getY(), TerrainTileset.HIGH_GROUND_HEIGHT);

                Assertions.assertNotEquals(lowIndex, highIndex);
                Assertions.assertTrue(highIndex < map.getTileCount());
                Assertions.assertTrue(lowTile >= 0 && highTile >= 0, "Installed terrain must represent both required heights.");
                Assertions.assertTrue(map.hasOverlayTiles());

                map.restoreTileIdentifier(lowIndex, lowTile);
                map.restoreTileIdentifier(highIndex, highTile);
                map.restoreOverlayTileIdentifier(lowIndex, 0);
                map.restoreOverlayTileIdentifier(highIndex, 0);

                Assertions.assertTrue(game.getTriggerLocations().isExcludedByElevation(terrain, unit, elevationFlag));

                unit.setPositionX(destinationX);
                unit.setPositionY(sprite.getY());

                Assertions.assertNotEquals(unit.getPositionX(), sprite.getX(), "Public unit position storage must differ from the sprite.");
                Assertions.assertTrue(game.getTriggerLocations().isExcludedByElevation(terrain, unit, elevationFlag));
                Assertions.assertFalse(game.getTriggerLocations().isExcludedByElevation(terrain, unit,
                                                                                        elevationFlag << TerrainTileset.HIGH_GROUND_HEIGHT));
            });
        }
    }


    @Test
    void ground_elevation_should_read_the_sprite_before_unit_position_storage() throws IOException {
        assertSpriteElevation(ScrUnitIds.TERRAN_MARINE, TriggerLocations.GROUND_ELEVATION_FLAG);
    }

    @Test
    void air_elevation_should_still_read_the_ground_under_the_sprite() throws IOException {
        assertSpriteElevation(ScrUnitIds.TERRAN_WRAITH, TriggerLocations.AIR_ELEVATION_FLAG);
    }
}
