package org.fogofwarlabs.sc4j.scr.game.map;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.ai.UnitStrengthTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.player.ProductionStatistics;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.*;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitRegistry;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.fogofwarlabs.sc4j.scr.game.visibility.SightCoverage;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.util.Arrays;

/**
 * Builds the starting-unit world of the starting-unit and Larva tests: a hidden map of walkable tiles of the first tileset with its contours and
 * foliage, the unit, sprite, image and order pools with the spatial index and the unit registry, the sight coverage and the player tables.
 */
public final class StartingUnitScenario {
    /**
     * The width and height of the starting-unit map in tiles.
     */
    public static final int MAP_SIZE = 64;
    /**
     * The bit of player {@code 0}, the local player of the starting-unit world: the local vision comparison mask and the checked players of the
     * vision dialog.
     */
    public static final int LOCAL_PLAYER_MASK = 1;

    /**
     * The seed of the game random generator.
     */
    private static final int RANDOM_SEED = 0x12345678;
    /**
     * The seed the foliage is populated with.
     */
    private static final int FOLIAGE_SEED = 0x12345678;
    /**
     * The tiles of one terrain tile group.
     */
    private static final int TILES_PER_GROUP = 16;
    /**
     * The miniature tiles of one terrain tile.
     */
    private static final int MINIATURES_PER_TILE = 16;
    /**
     * The image and order pool entries the scenario reserves per unit.
     */
    private static final int POOL_ENTRIES_PER_UNIT = 16;

    /**
     * Creates a {@link Game} instance of the starting-unit world: a map of {@link #MAP_SIZE} by {@link #MAP_SIZE} tiles of the first walkable tile
     * outside the creep group, every tile hidden and unexplored, with its contours and populated foliage, pools of the given unit capacity, every
     * player sharing only its own vision, the local vision of {@link #LOCAL_PLAYER_MASK}, and the unit accounting, production statistics and
     * upgrades of a new game.
     *
     * @param gameData the installed data
     * @param capacity the unit capacity, which also sizes the sprite, image and order pools
     * @return the created {@link Game} instance
     * @throws IOException if the terrain contours cannot be created
     */
    public static @NotNull Game createGame(@NotNull GameData gameData, int capacity) throws IOException {
        TerrainTileset terrain = gameData.getTerrainTileset(0);
        int walkableTile = -1;

        for (int tileIdentifier = 0; tileIdentifier < terrain.getGroupCount() * TILES_PER_GROUP; tileIdentifier++) {
            if (TerrainTileset.isCreepTile(tileIdentifier)) {
                continue;
            }

            boolean walkable = true;

            for (int miniatureIndex = 0; miniatureIndex < MINIATURES_PER_TILE; miniatureIndex++) {
                walkable &= (terrain.getMiniatureFlags(tileIdentifier, miniatureIndex) & TerrainTileset.MINI_TILE_WALKABLE) != 0;
            }

            if (walkable) {
                walkableTile = tileIdentifier;

                break;
            }
        }

        Assertions.assertTrue(walkableTile >= 0);

        int[] tiles = new int[MAP_SIZE * MAP_SIZE];

        Arrays.fill(tiles, walkableTile);

        int[] flags = terrain.expandTileFlags(tiles);

        for (int tileIndex = 0; tileIndex < flags.length; tileIndex++) {
            flags[tileIndex] |= MapTileFlags.NOT_VISIBLE_OR_EXPLORED;
        }

        GameMap map = new GameMap(MAP_SIZE, MAP_SIZE, 0, tiles, flags);

        GameMapScenario.setOverlays(map, new int[MAP_SIZE * MAP_SIZE]);

        Game game = Game.create(gameData, map, new LcgRandom(RANDOM_SEED));

        game.getUnitSystem().initializeUnitPool(capacity);
        game.getSpriteSystem().initializeSpritePool(capacity);
        game.getSpriteSystem().initializeImagePool(capacity * POOL_ENTRIES_PER_UNIT);
        game.initializeOrderPool(capacity * POOL_ENTRIES_PER_UNIT);
        game.initializeExecutionScope();
        game.initializeUnitSpatialIndex();

        UnitSpatialIndex flyingIndex = game.getUnitSystem().getUnitSpatialIndex();

        Assertions.assertNotNull(flyingIndex);

        flyingIndex.initializeFlyingUnits();

        UnitPool registryPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(registryPool);

        game.getUnitSystem().setUnitRegistry(new UnitRegistry(registryPool));
        game.getPathSystem().setTerrainContours(TerrainContours.create(map, terrain));
        game.setUnitStrengthTable(new UnitStrengthTable(gameData.getUnitTypes(), gameData.getWeaponTypes()));

        Foliage foliage = gameData.createFoliage(MAP_SIZE, 0);

        foliage.populate(map, FOLIAGE_SEED);
        game.setFoliage(foliage);

        SightCoverage sightCoverage = new SightCoverage(MAP_SIZE);
        int[] sharing = new int[PlayerTable.PLAYER_SLOTS];

        for (int playerIndex = 0; playerIndex < sharing.length; playerIndex++) {
            sharing[playerIndex] = 1 << playerIndex;
        }

        sightCoverage.setSharedVisionMasks(sharing);
        game.setSightCoverage(sightCoverage);
        game.getLocalVision().setComparisonMask(LOCAL_PLAYER_MASK);

        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();

        accounting.initializeForGame(null);
        game.getPlayerTable().setUnitAccounting(accounting);
        game.getPlayerTable().setProductionStatistics(new ProductionStatistics(false,
                                                                               new int[ProductionStatistics.VALUE_COUNT],
                                                                               new float[ProductionStatistics.VALUE_COUNT]));

        PlayerUpgrades upgrades = PlayerProgressTestUtils.createPlayerUpgrades(true);

        upgrades.initializeDefaults(gameData.getUpgradeTypes());
        game.getPlayerTable().setPlayerUpgrades(upgrades);

        return game;
    }


    /**
     * Creates a {@link StartingUnitScenario} instance.
     */
    private StartingUnitScenario() {
    }
}
