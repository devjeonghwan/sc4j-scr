package org.fogofwarlabs.sc4j.scr.game.combat;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.data.format.replay.SelectionRequest;
import org.fogofwarlabs.sc4j.scr.game.ai.ComputerPlayerPhase;
import org.fogofwarlabs.sc4j.scr.game.ai.UnitStrengthTable;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrameScenario;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerRelations;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
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
import org.fogofwarlabs.sc4j.scr.game.visibility.SightCoverage;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;

/**
 * Builds the small combat worlds of the combat and command tests: a map of {@link #MAP_SIDE_TILES} by {@link #MAP_SIDE_TILES} tiles of the first
 * tileset with its terrain contours, two human players with their pools, the sight coverage and frame state of a combat, and completed units that
 * guard their position. The duel world places a unit of each player and selects the first one.
 */
public final class CombatGameScenario {
    /**
     * The width and height of the map in tiles.
     */
    public static final int MAP_SIDE_TILES = 32;
    /**
     * The image and order instruction budget of the created units.
     */
    public static final int INSTRUCTION_LIMIT = 10000;
    /**
     * The pool index of the first unit of the duel world.
     */
    public static final int FIRST_UNIT_INDEX = 1;
    /**
     * The pool index of the second unit of the duel world.
     */
    public static final int SECOND_UNIT_INDEX = 2;
    /**
     * The horizontal position of the first unit of the duel world, which belongs to player {@code 0}.
     */
    public static final int FIRST_UNIT_X = 200;
    /**
     * The vertical position of the first unit of the duel world.
     */
    public static final int FIRST_UNIT_Y = 200;
    /**
     * The horizontal position of the second unit of the duel world, which belongs to player {@code 1} and stands east of the first unit.
     */
    public static final int SECOND_UNIT_X = 220;
    /**
     * The vertical position of the second unit of the duel world.
     */
    public static final int SECOND_UNIT_Y = 200;

    private static final int TILE_COUNT = MAP_SIDE_TILES * MAP_SIDE_TILES;
    private static final int ORDER_CAPACITY = 100;
    /**
     * The vision bit of player {@code 0}: the shared vision mask of every player is this bit shifted by the player index, and the local vision
     * compares this bit.
     */
    private static final int FIRST_PLAYER_BIT = 1;

    /**
     * The unit, sprite and image capacities of the duel world.
     */
    private static final int DUEL_UNIT_CAPACITY = 2;
    private static final int DUEL_SPRITE_CAPACITY = 8;
    private static final int DUEL_IMAGE_CAPACITY = 64;
    /**
     * The scheduler counters of the computer-player phase of the duel world.
     */
    private static final int COMPUTER_COUNTER_COUNT = 8;
    /**
     * The team size of the duel selection outside a team game.
     */
    private static final int TEAM_SIZE = 0;
    /**
     * The local selection slot the team predicate of the duel selection compares.
     */
    private static final int LOCAL_PLAYER_INDEX = 8;
    /**
     * The unit handle width in bytes of the duel selection request.
     */
    private static final int HANDLE_WIDTH_BYTES = 2;

    /**
     * Creates a {@link Game} instance on a map of {@link #MAP_SIDE_TILES} by {@link #MAP_SIDE_TILES} tiles of the first tileset, with zero tile
     * identifiers and overlays, its terrain contours and the random seed {@code 1}.
     *
     * @param gameData the installed data that holds the tileset
     * @return the created {@link Game} instance
     * @throws IllegalStateException if the tileset cannot be read
     */
    public static @NotNull Game createGame(@NotNull GameData gameData) {
        GameMap map;

        try {
            int[] tileIdentifiers = new int[TILE_COUNT];

            map = new GameMap(MAP_SIDE_TILES, MAP_SIDE_TILES, 0, tileIdentifiers, gameData.getTerrainTileset(0).expandTileFlags(tileIdentifiers));
        } catch (IOException exception) {
            throw Exceptions.newIllegalStateException(exception, "{0}", exception);
        }

        GameMapScenario.setOverlays(map, new int[TILE_COUNT]);

        Game game = Game.create(gameData, map, new LcgRandom(1));

        try {
            game.getPathSystem().setTerrainContours(TerrainContours.create(map, gameData.getTerrainTileset(0)));
        } catch (IOException exception) {
            throw Exceptions.newIllegalStateException(exception, "{0}", exception);
        }

        return game;
    }

    /**
     * Initializes the unit accounting, the unit, sprite, image and order pools, the execution scope, the unit spatial index, the upgrades and the
     * relations of two human players and the rescuable player {@code 11}, and the records of the two human players.
     *
     * @param game           the game of {@link #createGame}
     * @param unitCapacity   the unit pool capacity
     * @param spriteCapacity the sprite pool capacity
     * @param imageCapacity  the image pool capacity
     */
    public static void initializeWorld(@NotNull Game game, int unitCapacity, int spriteCapacity, int imageCapacity) {
        int[] playerTypes = {PlayerTypes.HUMAN, PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.NONE,
                             PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                             PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.RESCUE_PASSIVE};

        game.getPlayerTable().setUnitAccounting(PlayerProgressTestUtils.createUnitAccounting());
        game.getUnitSystem().initializeUnitPool(unitCapacity);
        game.getSpriteSystem().initializeSpritePool(spriteCapacity);
        game.getSpriteSystem().initializeImagePool(imageCapacity);
        game.initializeOrderPool(ORDER_CAPACITY);
        game.initializeExecutionScope();
        game.initializeUnitSpatialIndex();
        game.getPlayerTable().setPlayerUpgrades(PlayerProgressTestUtils.createPlayerUpgrades(true));
        game.getPlayerTable().setPlayerRelations(new PlayerRelations(playerTypes, false, false, false));
        game.getPlayerTable().setPlayer(0, new Player(0, 0, PlayerTypes.HUMAN, 0, 0));
        game.getPlayerTable().setPlayer(1, new Player(1, 1, PlayerTypes.HUMAN, 0, 0));
    }

    /**
     * Initializes the unit strength table, the sight coverage in which every player shares only its own vision, the local vision of player {@code 0}
     * and the frame state of {@link GameFrameScenario#createFrame}.
     *
     * @param game     the game of {@link #initializeWorld}
     * @param gameData the installed data of the game
     * @throws IllegalStateException if the tileset cannot be read
     */
    public static void initializeCombat(@NotNull Game game, @NotNull GameData gameData) {
        game.setUnitStrengthTable(new UnitStrengthTable(gameData.getUnitTypes(), gameData.getWeaponTypes()));

        SightCoverage sight = new SightCoverage(MAP_SIDE_TILES);
        int[] sharedVisionMasks = new int[PlayerTable.PLAYER_SLOTS];

        for (int index = 0; index < sharedVisionMasks.length; index++) {
            sharedVisionMasks[index] = FIRST_PLAYER_BIT << index;
        }

        sight.setSharedVisionMasks(sharedVisionMasks);
        game.setSightCoverage(sight);
        game.getLocalVision().setComparisonMask(FIRST_PLAYER_BIT);

        try {
            game.setFrame(GameFrameScenario.createFrame(gameData));
        } catch (IOException exception) {
            throw Exceptions.newIllegalStateException(exception, "{0}", exception);
        }
    }

    /**
     * Creates a {@link Game} instance of the duel world: the world of {@link #createGame}, a computer-player phase over its regions,
     * {@link #initializeWorld} with the duel capacities and {@link #initializeCombat}, the first unit of player {@code 0} at
     * ({@link #FIRST_UNIT_X}, {@link #FIRST_UNIT_Y}), the second unit of player {@code 1} at ({@link #SECOND_UNIT_X}, {@link #SECOND_UNIT_Y}),
     * and a selection of the first unit.
     *
     * @param gameData       the installed data
     * @param firstUnitType  the type of the first unit
     * @param secondUnitType the type of the second unit
     * @return the created {@link Game} instance
     */
    public static @NotNull Game createDuelGame(@NotNull GameData gameData, int firstUnitType, int secondUnitType) {
        Game game = createGame(gameData);
        TerrainContours contours = game.getPathSystem().getTerrainContours();

        Assertions.assertNotNull(contours);

        int regionCount = contours.getRegions().getRegionCount();

        game.setComputerPlayerPhase(new ComputerPlayerPhase(game,
                                                            regionCount,
                                                            new int[COMPUTER_COUNTER_COUNT],
                                                            new int[regionCount * PlayerTable.PLAYABLE_PLAYER_SLOTS]));
        initializeWorld(game, DUEL_UNIT_CAPACITY, DUEL_SPRITE_CAPACITY, DUEL_IMAGE_CAPACITY);
        initializeCombat(game, gameData);
        createUnit(game, gameData, FIRST_UNIT_INDEX, firstUnitType, FIRST_UNIT_X, FIRST_UNIT_Y, 0);
        createUnit(game, gameData, SECOND_UNIT_INDEX, secondUnitType, SECOND_UNIT_X, SECOND_UNIT_Y, 1);

        UnitSelection selection = new UnitSelection(game.getUnitSystem().requireUnitPool(), false, TEAM_SIZE, LOCAL_PLAYER_INDEX);

        game.setSelection(selection);
        selection.execute(game,
                          new SelectionRequest(SelectionRequest.Operation.REPLACE,
                                               HANDLE_WIDTH_BYTES,
                                               new long[]{Integer.toUnsignedLong(game.getUnitSystem().getUnitPool().getHandle(FIRST_UNIT_INDEX))}),
                          0);

        return game;
    }

    /**
     * Creates a completed unit in a pool slot that guards its position: the unit is initialized with its {@code units.dat} hit points, its sprite is
     * visible to every player and the unit is inserted into the spatial index.
     *
     * @param game      the game of {@link #initializeWorld}
     * @param gameData  the installed data of the game
     * @param poolIndex the pool index of the unit
     * @param unitType  the unit type
     * @param positionX the horizontal position
     * @param positionY the vertical position
     * @param owner     the owning player
     */
    public static void createUnit(@NotNull Game game, @NotNull GameData gameData,
                                  int poolIndex, int unitType,
                                  int positionX, int positionY,
                                  int owner) {
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(poolIndex);

        UnitStorageConstructor.clearBeforeInnerInitialization(unit);

        Assertions.assertTrue(game.getUnitInitializer().initialize(unit, unitType, positionX, positionY, owner, INSTRUCTION_LIMIT));
        unit.setStatus(unit.getStatus() | UnitStatusFlags.COMPLETED);
        unit.setPrimaryOrder(ScrOrderIds.PLAYER_GUARD);
        unit.setHitPoints(gameData.getUnits().get("Hit Points", unitType));

        Sprite unitSprite = unit.getSpriteOrNull();

        Assertions.assertNotNull(unitSprite);

        unitSprite.setVisibilityFlags(Sprite.VISIBLE_TO_ALL_PLAYERS);

        UnitSpatialIndex unitSpatialIndex = game.getUnitSystem().getUnitSpatialIndex();

        Assertions.assertNotNull(unitSpatialIndex);

        unitSpatialIndex.insert(unit, gameData.getUnitTypes());
    }


    /**
     * Creates a {@link CombatGameScenario} instance.
     */
    private CombatGameScenario() {
    }
}
