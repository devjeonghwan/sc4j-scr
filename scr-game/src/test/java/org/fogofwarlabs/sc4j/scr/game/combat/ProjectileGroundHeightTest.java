package org.fogofwarlabs.sc4j.scr.game.combat;

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
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks height reads on completed units and the terrain retained below creep in an initialized replay world.
 */
@RequiresInstallation
final class ProjectileGroundHeightTest {
    private static final int CREATION_POSITION_X = 3560;
    private static final int CREATION_POSITION_Y = 2160;
    private static final int TARGET_DISTANCE_TILES = 3;
    private static final int LOW_GROUND_HEIGHT = 0;
    /**
     * The native miss chance, out of {@code 256}, against a target on higher ground.
     */
    private static final int MISS_CHANCE_COVERED = 119;
    /**
     * The tile identifier of creep variant {@code 0}.
     */
    private static final int CREEP_TILE_IDENTIFIER = 0x10;

    /**
     * Sets the required low and high terrain under two genuine, completed ground units.
     *
     * @param game    the initialized world
     * @param terrain the borrowed terrain
     * @param source  the attacker
     * @param target  the target
     */
    private static void setGroundHeights(@NotNull Game game, @NotNull TerrainTileset terrain, @NotNull Unit source, @NotNull Unit target) {
        Sprite sourceSprite = source.getSpriteOrNull();
        Sprite targetSprite = target.getSpriteOrNull();

        Assertions.assertNotNull(sourceSprite);
        Assertions.assertNotNull(targetSprite);
        Assertions.assertNotEquals(0, source.getStatus() & UnitStatusFlags.COMPLETED);
        Assertions.assertNotEquals(0, target.getStatus() & UnitStatusFlags.COMPLETED);
        Assertions.assertEquals(0, (source.getStatus() | target.getStatus()) & UnitStatusFlags.FLYING);

        GameMap map = game.getMap();
        int sourceIndex = computeTileIndex(map, sourceSprite);
        int targetIndex = computeTileIndex(map, targetSprite);
        int sourceTile = GameMapScenario.findTileAtHeight(terrain, sourceSprite.getX(), sourceSprite.getY(), LOW_GROUND_HEIGHT);
        int targetTile = GameMapScenario.findTileAtHeight(terrain, targetSprite.getX(), targetSprite.getY(), TerrainTileset.HIGH_GROUND_HEIGHT);

        Assertions.assertNotEquals(sourceIndex, targetIndex, "Genuine placement must separate the terrain queries.");
        Assertions.assertTrue(sourceTile >= 0 && targetTile >= 0, "Installed terrain must represent the required miniature heights.");
        Assertions.assertTrue(map.hasOverlayTiles());

        map.restoreTileIdentifier(sourceIndex, sourceTile);
        map.restoreTileIdentifier(targetIndex, targetTile);
        map.restoreOverlayTileIdentifier(sourceIndex, 0);
        map.restoreOverlayTileIdentifier(targetIndex, 0);
        map.restoreTileFlags(targetIndex, map.getTileFlags(targetIndex) & ~MapTileFlags.PROVIDES_COVER);

        Assertions.assertEquals(0, game.getProjectilePhase().getDodgeChance(target));
    }

    /**
     * Computes the index of the map tile below a sprite.
     *
     * @param map    the map
     * @param sprite the sprite
     * @return the tile index
     */
    private static int computeTileIndex(@NotNull GameMap map, @NotNull Sprite sprite) {
        return sprite.getY() / TerrainTileset.TILE_SIZE * map.getWidth() + sprite.getX() / TerrainTileset.TILE_SIZE;
    }


    @Test
    void get_miss_chance_should_read_sprite_height_before_unit_position_storage() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.start(data);
            TerrainTileset terrain = data.getTerrainTileset(runtime.getGame().getMap().getTileset());

            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                Game game = runtime.getGame();
                Unit source = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE,
                                                             CREATION_POSITION_X, CREATION_POSITION_Y,
                                                             RunningGameScenario.HUMAN);
                Unit target = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE,
                                                             CREATION_POSITION_X + TARGET_DISTANCE_TILES * TerrainTileset.TILE_SIZE,
                                                             CREATION_POSITION_Y,
                                                             RunningGameScenario.HUMAN);

                setGroundHeights(game, terrain, source, target);

                Sprite sourceSprite = source.getSpriteOrNull();
                Sprite targetSprite = target.getSpriteOrNull();

                Assertions.assertNotNull(sourceSprite);
                Assertions.assertNotNull(targetSprite);

                source.setPositionX(targetSprite.getX());
                source.setPositionY(targetSprite.getY());

                Assertions.assertTrue(source.getPositionX() != sourceSprite.getX() || source.getPositionY() != sourceSprite.getY(),
                                      "The public WORD storage writes must differ from the retained sprite position.");
                Assertions.assertEquals(MISS_CHANCE_COVERED, game.getProjectilePhase().getMissChance(source, target));
            });
        }
    }

    @Test
    void get_miss_chance_should_compare_ground_height_of_terrain_retained_below_creep() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.start(data);
            TerrainTileset terrain = data.getTerrainTileset(runtime.getGame().getMap().getTileset());

            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                Game game = runtime.getGame();
                Unit source = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE,
                                                             CREATION_POSITION_X, CREATION_POSITION_Y,
                                                             RunningGameScenario.HUMAN);
                Unit target = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE,
                                                             CREATION_POSITION_X + TARGET_DISTANCE_TILES * TerrainTileset.TILE_SIZE,
                                                             CREATION_POSITION_Y,
                                                             RunningGameScenario.HUMAN);

                setGroundHeights(game, terrain, source, target);

                Sprite targetSprite = target.getSpriteOrNull();

                Assertions.assertNotNull(targetSprite);

                GameMap map = game.getMap();
                int targetIndex = computeTileIndex(map, targetSprite);

                Assertions.assertEquals(MISS_CHANCE_COVERED, game.getProjectilePhase().getMissChance(source, target));

                map.retainTerrainUnderCreep(targetIndex);
                map.setCreepVariant(targetIndex, 0);

                Assertions.assertEquals(CREEP_TILE_IDENTIFIER, map.getTileIdentifier(targetIndex));
                Assertions.assertEquals(MISS_CHANCE_COVERED, game.getProjectilePhase().getMissChance(source, target));
            });
        }
    }

    @Test
    void get_miss_chance_should_skip_ground_height_cover_comparison_when_attacker_flies() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.start(data);

            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                Game game = runtime.getGame();
                Unit source = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_WRAITH,
                                                             CREATION_POSITION_X, CREATION_POSITION_Y,
                                                             RunningGameScenario.HUMAN);
                Unit target = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE,
                                                             CREATION_POSITION_X + TARGET_DISTANCE_TILES * TerrainTileset.TILE_SIZE,
                                                             CREATION_POSITION_Y,
                                                             RunningGameScenario.HUMAN);
                Sprite targetSprite = target.getSpriteOrNull();

                Assertions.assertNotNull(targetSprite);
                Assertions.assertNotEquals(0, source.getStatus() & UnitStatusFlags.COMPLETED);
                Assertions.assertNotEquals(0, source.getStatus() & UnitStatusFlags.FLYING);
                Assertions.assertEquals(0, target.getStatus() & UnitStatusFlags.FLYING);

                GameMap map = game.getMap();
                int targetIndex = computeTileIndex(map, targetSprite);

                map.restoreTileFlags(targetIndex, map.getTileFlags(targetIndex) & ~MapTileFlags.PROVIDES_COVER);

                Assertions.assertEquals(0, game.getProjectilePhase().getDodgeChance(target), "The genuine target must have no independent cover.");
                Assertions.assertEquals(0, game.getProjectilePhase().getMissChance(source, target));
            });
        }
    }
}
