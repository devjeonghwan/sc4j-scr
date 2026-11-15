package org.fogofwarlabs.sc4j.scr.game.visibility;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.data.format.replay.SelectionRequest;
import org.fogofwarlabs.sc4j.scr.game.ai.UnitStrengthTable;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
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
import org.junit.jupiter.api.Assertions;

import java.io.IOException;

/**
 * Creates games on a small installed-tileset map with one completed unit of a given type, owned and selected by {@link #FIRST_PLAYER}, for the
 * cloaking and technology command tests.
 */
public final class SelectedUnitScenario {
    /**
     * The slot of the first player, who owns and selects the unit.
     */
    public static final int FIRST_PLAYER = 0;
    /**
     * The pool index of the created unit.
     */
    public static final int UNIT_POOL_INDEX = 1;

    /**
     * The instruction budget of the unit initialization.
     */
    private static final int INSTRUCTION_LIMIT = 10000;
    /**
     * The width and height of the map in tiles.
     */
    private static final int MAP_SIZE = 32;
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
    private static final int RANDOM_SEED = 1;
    /**
     * The capacity of the unit pool.
     */
    private static final int UNIT_POOL_CAPACITY = 2;
    /**
     * The capacity of the sprite pool.
     */
    private static final int SPRITE_POOL_CAPACITY = 8;
    /**
     * The capacity of the image pool.
     */
    private static final int IMAGE_POOL_CAPACITY = 64;
    /**
     * The capacity of the order pool.
     */
    private static final int ORDER_POOL_CAPACITY = 100;
    /**
     * The slot of the second player.
     */
    private static final int SECOND_PLAYER = 1;
    /**
     * The race of the players.
     */
    private static final int PLAYER_RACE = 0;
    /**
     * The team of the players.
     */
    private static final int PLAYER_TEAM = 0;
    /**
     * The horizontal and vertical WORD position of the created units.
     */
    private static final int UNIT_POSITION = 200;
    /**
     * The team size of the selection, which is not in a team game.
     */
    private static final int NO_TEAM_SIZE = 0;
    /**
     * The local selection slot of the selection.
     */
    private static final int LOCAL_PLAYER_INDEX = 8;
    /**
     * The unit identity width in bytes of the selection request.
     */
    private static final int HANDLE_WIDTH_BYTES = 2;

    /**
     * Creates a {@link Game} instance on a small installed-tileset map with one completed unit of the given type, owned and selected by
     * {@link #FIRST_PLAYER}.
     *
     * @param gameData the installed game data
     * @param unitType the unit type
     * @return the created {@link Game} instance
     * @throws IllegalStateException if the tileset or the terrain contours cannot be read
     */
    public static @NotNull Game createGame(@NotNull GameData gameData, int unitType) {
        GameMap map;

        try {
            int[] tileIdentifiers = new int[TILE_COUNT];

            map = new GameMap(MAP_SIZE, MAP_SIZE,
                              TILESET_INDEX,
                              tileIdentifiers,
                              gameData.getTerrainTileset(TILESET_INDEX).expandTileFlags(tileIdentifiers));
        } catch (IOException exception) {
            throw Exceptions.newIllegalStateException(exception, "{0}", exception);
        }

        GameMapScenario.setOverlays(map, new int[TILE_COUNT]);

        Game game = Game.create(gameData, map, new LcgRandom(RANDOM_SEED));

        try {
            game.getPathSystem().setTerrainContours(TerrainContours.create(map, gameData.getTerrainTileset(TILESET_INDEX)));
        } catch (IOException exception) {
            throw Exceptions.newIllegalStateException(exception, "{0}", exception);
        }

        game.getPlayerTable().setUnitAccounting(PlayerProgressTestUtils.createUnitAccounting());
        game.getUnitSystem().initializeUnitPool(UNIT_POOL_CAPACITY);
        game.getSpriteSystem().initializeSpritePool(SPRITE_POOL_CAPACITY);
        game.getSpriteSystem().initializeImagePool(IMAGE_POOL_CAPACITY);
        game.initializeOrderPool(ORDER_POOL_CAPACITY);
        game.initializeExecutionScope();
        game.initializeUnitSpatialIndex();
        game.getPlayerTable().setPlayerUpgrades(PlayerProgressTestUtils.createPlayerUpgrades(true));
        game.getPlayerTable().setPlayerRelations(new PlayerRelations(new int[]{
                PlayerTypes.HUMAN,
                PlayerTypes.HUMAN,
                PlayerTypes.NONE,
                PlayerTypes.NONE,
                PlayerTypes.NONE,
                PlayerTypes.NONE,
                PlayerTypes.NONE,
                PlayerTypes.NONE,
                PlayerTypes.NONE,
                PlayerTypes.NONE,
                PlayerTypes.NONE,
                PlayerTypes.RESCUE_PASSIVE
        }, false, false, false));
        game.getPlayerTable().setPlayer(FIRST_PLAYER, new Player(FIRST_PLAYER, FIRST_PLAYER, PlayerTypes.HUMAN, PLAYER_RACE, PLAYER_TEAM));
        game.getPlayerTable().setPlayer(SECOND_PLAYER, new Player(SECOND_PLAYER, SECOND_PLAYER, PlayerTypes.HUMAN, PLAYER_RACE, PLAYER_TEAM));
        game.setUnitStrengthTable(new UnitStrengthTable(gameData.getUnitTypes(), gameData.getWeaponTypes()));

        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(UNIT_POOL_INDEX);

        UnitStorageConstructor.clearBeforeInnerInitialization(unit);

        Assertions.assertTrue(game.getUnitInitializer().initialize(unit, unitType, UNIT_POSITION, UNIT_POSITION, FIRST_PLAYER, INSTRUCTION_LIMIT));

        unit.setStatus(unit.getStatus() | UnitStatusFlags.COMPLETED);
        unit.setPrimaryOrder(ScrOrderIds.PLAYER_GUARD);

        Sprite unitSprite = unit.getSpriteOrNull();

        Assertions.assertNotNull(unitSprite);

        unitSprite.setVisibilityFlags(Sprite.VISIBLE_TO_ALL_PLAYERS);

        UnitSpatialIndex unitSpatialIndex = game.getUnitSystem().getUnitSpatialIndex();

        Assertions.assertNotNull(unitSpatialIndex);

        unitSpatialIndex.insert(unit, gameData.getUnitTypes());

        UnitSelection selection = new UnitSelection(unitPool, false, NO_TEAM_SIZE, LOCAL_PLAYER_INDEX);

        game.setSelection(selection);
        selection.execute(game,
                          new SelectionRequest(SelectionRequest.Operation.REPLACE,
                                               HANDLE_WIDTH_BYTES,
                                               new long[]{Integer.toUnsignedLong(unitPool.getHandle(UNIT_POOL_INDEX))}),
                          FIRST_PLAYER);

        return game;
    }


    /**
     * Creates a {@link SelectedUnitScenario} instance.
     */
    private SelectedUnitScenario() {
    }
}
