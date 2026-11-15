package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.data.format.replay.SelectionRequest;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.ai.UnitStrengthTable;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.player.*;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMapScenario;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainContours;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitCreationContext;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStorageConstructor;
import org.fogofwarlabs.sc4j.scr.game.visibility.SightCoverage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;

/**
 * Builds the small construction game that the Terran construction, production and add-on tests share, creates completed units in it, and registers
 * completed prerequisites in a unit accounting.
 */
public final class ProductionScenario {
    /**
     * The test player of the construction game, the owner of the test units.
     */
    public static final int PLAYER = 0;
    /**
     * The second human player of the construction game.
     */
    public static final int ENEMY_PLAYER = 1;
    /**
     * The starting minerals of {@link #PLAYER}.
     */
    public static final int STARTING_MINERALS = 1000;
    /**
     * The starting gas of {@link #PLAYER}.
     */
    public static final int STARTING_GAS = 1000;

    /**
     * The player identifiers of {@link #PLAYER} and {@link #ENEMY_PLAYER}, and the team of every player.
     */
    private static final int PLAYER_ID = 0;
    private static final int ENEMY_PLAYER_ID = 1;
    private static final int TEAM = 0;
    /**
     * The local player comparison value of the unit creation context and the selection, a slot that owns no test unit.
     */
    private static final int LOCAL_PLAYER = 8;
    private static final int LOCAL_COMPARISON_MASK = 1 << PLAYER;
    /**
     * The team size of the selection outside a team game.
     */
    private static final int NO_TEAM_SIZE = 0;
    /**
     * The width of the unit handles of the selection commands, a WORD.
     */
    private static final int HANDLE_WIDTH = Short.BYTES;

    private static final int TILESET = 0;
    private static final int INSTRUCTION_LIMIT = 10000;
    private static final int MAP_DIMENSION = 32;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int UNIT_CAPACITY = 8;
    private static final int SPRITE_POOL_CAPACITY = 32;
    private static final int IMAGE_POOL_CAPACITY = 256;
    private static final int ORDER_POOL_CAPACITY = 100;
    /**
     * The pool index of the completed prerequisite that {@link #registerCompletedPrerequisite} counts.
     */
    private static final int PREREQUISITE_POOL_INDEX = 3;

    /**
     * Creates a {@link Game} instance of the construction game: an explored {@code 32} by {@code 32} map with its terrain contours, the unit
     * accounting and production statistics, the pools, {@link #PLAYER} with the starting resources, {@link #ENEMY_PLAYER} and the neutral player,
     * shared vision of each player alone and an empty selection.
     *
     * @param gameData the installed game data, which must be loaded
     * @return the created {@link Game} instance
     */
    public static @NotNull Game createConstructionGame(@Nullable GameData gameData) {
        GameMap map;
        int[] tileIdentifiers = new int[MAP_TILE_COUNT];

        try {
            Assertions.assertNotNull(gameData);

            int[] tileFlags = gameData.getTerrainTileset(TILESET).expandTileFlags(tileIdentifiers);

            for (int index = 0; index < tileFlags.length; index++) {
                tileFlags[index] &= ~MapTileFlags.NOT_VISIBLE_OR_EXPLORED;
            }

            map = new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, tileIdentifiers, tileFlags);
        } catch (IOException exception) {
            throw Exceptions.newIllegalStateException(exception, "{0}", exception);
        }

        GameMapScenario.setOverlays(map, new int[MAP_TILE_COUNT]);

        Game game = Game.create(gameData, map, new LcgRandom(1));

        try {
            game.getPathSystem().setTerrainContours(TerrainContours.create(map, gameData.getTerrainTileset(TILESET)));
        } catch (IOException exception) {
            throw Exceptions.newIllegalStateException(exception, "{0}", exception);
        }

        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();
        int[] playerTypes = new int[PlayerTable.PLAYER_SLOTS];

        playerTypes[PLAYER]                        = PlayerTypes.HUMAN;
        playerTypes[ENEMY_PLAYER]                  = PlayerTypes.HUMAN;
        playerTypes[PlayerTable.NEUTRAL_PLAYER_ID] = PlayerTypes.RESCUE_PASSIVE;

        accounting.initializeForGame(null);
        game.getPlayerTable().setUnitAccounting(accounting);
        game.getPlayerTable().setProductionStatistics(new ProductionStatistics(false, new int[ProductionStatistics.VALUE_COUNT],
                                                                               new float[ProductionStatistics.VALUE_COUNT]));
        game.getUnitSystem().initializeUnitPool(UNIT_CAPACITY);
        game.getSpriteSystem().initializeSpritePool(SPRITE_POOL_CAPACITY);
        game.getSpriteSystem().initializeImagePool(IMAGE_POOL_CAPACITY);
        game.initializeOrderPool(ORDER_POOL_CAPACITY);
        game.initializeExecutionScope();
        game.initializeUnitSpatialIndex();
        game.setUnitStrengthTable(new UnitStrengthTable(gameData.getUnitTypes(), gameData.getWeaponTypes()));
        game.getUnitSystem().setUnitCreationContext(new UnitCreationContext(false, false, false, false, true, LOCAL_PLAYER));
        game.getPlayerTable().setPlayerUpgrades(PlayerProgressTestUtils.createPlayerUpgrades(true));
        game.getPlayerTable().setPlayerRelations(new PlayerRelations(playerTypes, false, false, false));

        Player player = new Player(PLAYER, PLAYER_ID, PlayerTypes.HUMAN, ScrRaceIds.ZERG, TEAM);

        player.setMinerals(STARTING_MINERALS);
        player.setGas(STARTING_GAS);
        game.getPlayerTable().setPlayer(PLAYER, player);
        game.getPlayerTable().setPlayer(ENEMY_PLAYER, new Player(ENEMY_PLAYER, ENEMY_PLAYER_ID, PlayerTypes.HUMAN, ScrRaceIds.ZERG, TEAM));
        game.getPlayerTable().setPlayer(PlayerTable.NEUTRAL_PLAYER_ID,
                                        new Player(PlayerTable.NEUTRAL_PLAYER_ID, PlayerTable.NEUTRAL_PLAYER_ID, PlayerTypes.RESCUE_PASSIVE,
                                                   ScrRaceIds.ZERG, TEAM));

        SightCoverage sight = new SightCoverage(MAP_DIMENSION);
        int[] sharedVisionMasks = new int[PlayerTable.PLAYER_SLOTS];

        for (int index = 0; index < sharedVisionMasks.length; index++) {
            sharedVisionMasks[index] = 1 << index;
        }

        sight.setSharedVisionMasks(sharedVisionMasks);
        game.setSightCoverage(sight);
        game.getLocalVision().setComparisonMask(LOCAL_COMPARISON_MASK);

        UnitPool selectionPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(selectionPool);

        UnitSelection selection = new UnitSelection(selectionPool, false, NO_TEAM_SIZE, LOCAL_PLAYER);

        game.setSelection(selection);
        selection.execute(game, new SelectionRequest(SelectionRequest.Operation.REPLACE, HANDLE_WIDTH, new long[0]), PLAYER);

        return game;
    }

    /**
     * Creates a {@link Unit} instance in the construction game and completes it: a building through the resumed Terran construction of a zero build
     * time, any other unit through the completion after placement. The unit is left idle with no research or upgrade and full hit points.
     *
     * @param game      the game of {@link #createConstructionGame}
     * @param unitType  the unit type
     * @param positionX the horizontal position
     * @param positionY the vertical position
     * @param owner     the owning player
     * @return the created {@link Unit} instance
     */
    public static @NotNull Unit createCompletedUnit(@NotNull Game game, int unitType, int positionX, int positionY, int owner) {
        UnitCreationContext context = game.getUnitSystem().getUnitCreationContext();
        UnitStorageConstructor storageConstructor = game.getUnitStorageConstructor();
        UnitAccounting accounting = game.getPlayerTable().getUnitAccounting();

        Assertions.assertNotNull(context);
        Assertions.assertNotNull(accounting);

        Unit unit = storageConstructor.create(context, accounting, unitType, positionX, positionY, owner, 0, INSTRUCTION_LIMIT);

        Assertions.assertNotNull(unit);

        GameData gameData = game.getGameData();

        Assertions.assertNotNull(gameData);

        if ((gameData.getUnits().get("Special Ability Flags", unitType) & UnitTypeFlags.BUILDING) != 0) {
            unit.setRemainingBuildTime(0);
            game.getUnitTerranConstruction().resumeBuilding(unit, INSTRUCTION_LIMIT);
        } else {
            game.getUnitCompletion().completeAfterPlacement(new UnitCreationContext(false, false, false, false, false, PLAYER),
                                                            unit,
                                                            PlayerTypes.HUMAN,
                                                            INSTRUCTION_LIMIT);
        }

        unit.setPrimaryOrder(ScrOrderIds.NOTHING);
        unit.setResearchType(ScrTechIds.NONE);
        unit.setUpgradeType(ScrUpgradeIds.NONE);
        unit.setHitPoints(gameData.getUnits().get("Hit Points", unitType));

        return unit;
    }

    /**
     * Registers a completed prerequisite of {@link #PLAYER} in a unit accounting, as a detached unit that the accounting counts as created and
     * completed.
     *
     * @param gameData   the installed game data, which must be loaded
     * @param accounting the unit accounting
     * @param unitType   the unit type of the prerequisite
     */
    public static void registerCompletedPrerequisite(@Nullable GameData gameData, @NotNull UnitAccounting accounting, int unitType) {
        Unit prerequisite = UnitTestUtils.createUnit(PREREQUISITE_POOL_INDEX);

        prerequisite.setOwnerIndex(PLAYER);
        prerequisite.setUnitId(unitType);

        Assertions.assertNotNull(gameData);

        accounting.update(prerequisite, gameData.getUnitTypes(), 1, false);
        accounting.updateCompleted(prerequisite, gameData.getUnitTypes(), 1, false, null);
    }


    /**
     * Creates a {@link ProductionScenario} instance.
     */
    private ProductionScenario() {
    }
}
