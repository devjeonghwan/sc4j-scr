package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.game.ai.UnitStrengthTable;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerRelations;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMapScenario;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainContours;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStorageConstructor;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;

/**
 * Builds the small two-player Zerg game on an empty {@code 32} by {@code 32} map that the ability tests check one routine in, and initializes
 * completed, visible units of the first player in it.
 */
public final class TwoUnitGameScenario {
    /**
     * The pool index of the caster unit of {@link #createGame(GameData, int, int)} and {@link #createGame(GameData, int, int, int)}.
     */
    public static final int CASTER_INDEX = 1;
    /**
     * The pool index of the target unit of {@link #createGame(GameData, int, int)} and {@link #createGame(GameData, int, int, int)}.
     */
    public static final int TARGET_INDEX = 2;
    /**
     * The pool index of the third unit of {@link #createGame(GameData, int, int, int)}.
     */
    public static final int THIRD_INDEX = 3;
    /**
     * The player of the initialized units.
     */
    public static final int PLAYER = 0;
    /**
     * The second human player of the game, who owns no initialized unit.
     */
    public static final int OTHER_PLAYER = 1;

    /**
     * The tileset of the test maps.
     */
    private static final int TILESET = 0;
    /**
     * The team of both test players.
     */
    private static final int TEAM = 0;
    /**
     * The player identifier of the test player.
     */
    private static final int PLAYER_ID = 0;
    /**
     * The player identifier of the second test player.
     */
    private static final int OTHER_PLAYER_ID = 1;

    private static final int INSTRUCTION_LIMIT = 10000;
    private static final int MAP_SIZE = 32;
    private static final int TILE_COUNT = MAP_SIZE * MAP_SIZE;
    private static final int TWO_UNIT_CAPACITY = 2;
    private static final int THREE_UNIT_CAPACITY = 3;
    private static final int SPRITE_CAPACITY = 8;
    private static final int IMAGE_CAPACITY = 64;
    private static final int ORDER_CAPACITY = 100;
    private static final int CASTER_POSITION_X = 200;
    private static final int TARGET_POSITION_X = 240;
    private static final int THIRD_POSITION_X = 320;
    private static final int UNIT_POSITION_Y = 200;

    /**
     * Creates a {@link Game} instance of the two-player game without units: the map, the pools, the execution scope, the unit spatial index, the
     * player tables and the unit strength table.
     *
     * @param gameData the installed game data, which must be loaded
     * @return the created {@link Game} instance
     */
    public static @NotNull Game createGame(@Nullable GameData gameData) {
        return createEmptyGame(gameData, TWO_UNIT_CAPACITY);
    }

    /**
     * Creates a {@link Game} instance of the two-player game with a caster at {@link #CASTER_INDEX} and a target at {@link #TARGET_INDEX}, both in
     * the player guard order.
     *
     * @param gameData   the installed game data, which must be loaded
     * @param casterType the unit type of the caster
     * @param targetType the unit type of the target
     * @return the created {@link Game} instance
     */
    public static @NotNull Game createGame(@Nullable GameData gameData, int casterType, int targetType) {
        Game game = createGame(gameData);

        initializeUnit(game, CASTER_INDEX, casterType, CASTER_POSITION_X, UNIT_POSITION_Y, ScrOrderIds.PLAYER_GUARD);
        initializeUnit(game, TARGET_INDEX, targetType, TARGET_POSITION_X, UNIT_POSITION_Y, ScrOrderIds.PLAYER_GUARD);

        return game;
    }

    /**
     * Creates a {@link Game} instance of the two-player game with a caster at {@link #CASTER_INDEX}, a target at {@link #TARGET_INDEX} and a third
     * unit at {@link #THIRD_INDEX}, all in the player guard order.
     *
     * @param gameData   the installed game data, which must be loaded
     * @param casterType the unit type of the caster
     * @param targetType the unit type of the target
     * @param thirdType  the unit type of the third unit
     * @return the created {@link Game} instance
     */
    public static @NotNull Game createGame(@Nullable GameData gameData, int casterType, int targetType, int thirdType) {
        Game game = createEmptyGame(gameData, THREE_UNIT_CAPACITY);

        initializeUnit(game, CASTER_INDEX, casterType, CASTER_POSITION_X, UNIT_POSITION_Y, ScrOrderIds.PLAYER_GUARD);
        initializeUnit(game, TARGET_INDEX, targetType, TARGET_POSITION_X, UNIT_POSITION_Y, ScrOrderIds.PLAYER_GUARD);
        initializeUnit(game, THIRD_INDEX, thirdType, THIRD_POSITION_X, UNIT_POSITION_Y, ScrOrderIds.PLAYER_GUARD);

        return game;
    }

    /**
     * Creates a {@link Game} instance of the two-player game without units and with the given unit pool capacity.
     *
     * @param gameData     the installed game data, which must be loaded
     * @param unitCapacity the unit pool capacity
     * @return the created {@link Game} instance
     */
    private static @NotNull Game createEmptyGame(@Nullable GameData gameData, int unitCapacity) {
        GameMap map;

        try {
            int[] tileIdentifiers = new int[TILE_COUNT];

            Assertions.assertNotNull(gameData);

            map = new GameMap(MAP_SIZE, MAP_SIZE, TILESET, tileIdentifiers, gameData.getTerrainTileset(TILESET).expandTileFlags(tileIdentifiers));
        } catch (IOException exception) {
            throw Exceptions.newIllegalStateException(exception, "{0}", exception);
        }

        GameMapScenario.setOverlays(map, new int[TILE_COUNT]);

        Game game = Game.create(gameData, map, new LcgRandom(1));

        try {
            game.getPathSystem().setTerrainContours(TerrainContours.create(map, gameData.getTerrainTileset(TILESET)));
        } catch (IOException exception) {
            throw Exceptions.newIllegalStateException(exception, "{0}", exception);
        }

        game.getPlayerTable().setUnitAccounting(PlayerProgressTestUtils.createUnitAccounting());
        game.getUnitSystem().initializeUnitPool(unitCapacity);
        game.getSpriteSystem().initializeSpritePool(SPRITE_CAPACITY);
        game.getSpriteSystem().initializeImagePool(IMAGE_CAPACITY);
        game.initializeOrderPool(ORDER_CAPACITY);
        game.initializeExecutionScope();
        game.initializeUnitSpatialIndex();

        game.getPlayerTable().setPlayerUpgrades(PlayerProgressTestUtils.createPlayerUpgrades(true));
        game.getPlayerTable().setPlayerTechnology(PlayerProgressTestUtils.createPlayerTechnology(true));
        game.getPlayerTable().setPlayerRelations(new PlayerRelations(
                new int[]{PlayerTypes.HUMAN, PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.NONE,
                          PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                          PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.RESCUE_PASSIVE},
                false,
                false,
                false
        ));
        game.getPlayerTable().setPlayer(PLAYER, new Player(PLAYER, PLAYER_ID, PlayerTypes.HUMAN, ScrRaceIds.ZERG, TEAM));
        game.getPlayerTable().setPlayer(OTHER_PLAYER, new Player(OTHER_PLAYER, OTHER_PLAYER_ID, PlayerTypes.HUMAN, ScrRaceIds.ZERG, TEAM));
        game.setUnitStrengthTable(new UnitStrengthTable(gameData.getUnitTypes(), gameData.getWeaponTypes()));

        return game;
    }

    /**
     * Initializes a unit of the first player in a pool slot as a completed unit with full hit points in an order, makes its sprite visible to all
     * players and inserts it into the unit spatial index.
     *
     * @param game      the game of {@link #createGame(GameData)}
     * @param poolIndex the pool slot of the unit
     * @param unitType  the unit type
     * @param positionX the horizontal position
     * @param positionY the vertical position
     * @param order     the primary order
     */
    public static void initializeUnit(@NotNull Game game, int poolIndex, int unitType, int positionX, int positionY, int order) {
        GameData gameData = game.requireGameData();
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit unit = pool.getUnit(poolIndex);

        UnitStorageConstructor.clearBeforeInnerInitialization(unit);

        Assertions.assertTrue(game.getUnitInitializer().initialize(unit, unitType, positionX, positionY, PLAYER, INSTRUCTION_LIMIT));

        unit.setStatus(unit.getStatus() | UnitStatusFlags.COMPLETED);
        unit.setPrimaryOrder(order);
        unit.setHitPoints(gameData.getUnits().get("Hit Points", unitType));

        Sprite sprite = unit.getSpriteOrNull();

        Assertions.assertNotNull(sprite);

        sprite.setVisibilityFlags(Sprite.VISIBLE_TO_ALL_PLAYERS);

        UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();

        Assertions.assertNotNull(spatialIndex);

        spatialIndex.insert(unit, gameData.getUnitTypes());
    }


    /**
     * Creates a {@link TwoUnitGameScenario} instance.
     */
    private TwoUnitGameScenario() {
    }
}
