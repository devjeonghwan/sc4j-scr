package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

/**
 * Checks per-tile placement results and building acceptance on terrain and around units.
 */
@RequiresInstallation
final class UnitPlacementTest {
    /**
     * The tileset of the test maps.
     */
    private static final int TILESET = 0;

    private static final int MAP_DIMENSION = 32;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int UNIT_CAPACITY = 8;
    /**
     * The unit capacity of a game with a builder and a blocker.
     */
    private static final int BUILDER_AND_BLOCKER_CAPACITY = 2;
    private static final int PROTOSS_RACE = 2;

    /**
     * The human player whose record and placements every case uses.
     */
    private static final int PLAYER = 0;
    /**
     * The player identifier of the test player.
     */
    private static final int PLAYER_ID = 0;
    /**
     * The team of the test player.
     */
    private static final int TEAM = 0;
    /**
     * The tile flag bits set while player {@link #PLAYER} does not see and has not explored the tile.
     */
    private static final int PLAYER_NOT_VISIBLE = 1;
    private static final int PLAYER_NOT_EXPLORED = 0x100;

    /**
     * The pool indexes of the builder, of the blocker and of the Command Center that places the add-on.
     */
    private static final int BUILDER_POOL_INDEX = 1;
    private static final int BLOCKER_POOL_INDEX = 2;
    private static final int COMMAND_CENTER_POOL_INDEX = 1;

    /**
     * The top-left tile of the evaluated building sites.
     */
    private static final int SITE_TILE = 9;
    /**
     * The pixel center of a Pylon at {@link #SITE_TILE}, {@code 1} tile right of and below its top-left tile.
     */
    private static final int PYLON_CENTER = SITE_TILE * TerrainTileset.TILE_SIZE + TerrainTileset.TILE_SIZE;
    /**
     * The footprint width and height of a Pylon in tiles.
     */
    private static final int PYLON_TILE_WIDTH = 2;
    private static final int PYLON_TILE_HEIGHT = 2;
    /**
     * The top-left tile of a footprint that leaves the map on the right.
     */
    private static final int OFF_MAP_TILE_X = 31;
    private static final int OFF_MAP_TILE_Y = 5;
    /**
     * The column and row of the unbuildable tile, the bottom-right tile of the Pylon site at {@link #SITE_TILE}.
     */
    private static final int UNBUILDABLE_TILE = 10;
    /**
     * The column and row of the top-left tile of the unexplored sites; the creep case leaves only this tile unexplored.
     */
    private static final int UNEXPLORED_TILE = 4;
    /**
     * The column and row of the top-left tile of the Refinery site without a geyser.
     */
    private static final int REFINERY_TILE = 6;
    /**
     * The column and row of the bottom-right tile within a Refinery footprint.
     */
    private static final int REFINERY_LAST_COLUMN = 3;
    private static final int REFINERY_LAST_ROW = 1;
    /**
     * The horizontal and vertical pixel position of the blocker on the Pylon site.
     */
    private static final int BLOCKER_POSITION = 320;
    /**
     * The horizontal and vertical pixel position of the Command Center that places the add-on.
     */
    private static final int COMMAND_CENTER_POSITION = 200;
    /**
     * The top-left tile of the valid Comsat Station site.
     */
    private static final int ADDON_TILE_X = 12;
    private static final int ADDON_TILE_Y = 10;
    /**
     * The divisor from a placement box size to the offset of its center.
     */
    private static final int CENTER_DIVISOR = 2;

    /**
     * The placement codes: valid, an undetected unit, off the map, blocked, no creep, no geyser and unexplored.
     */
    private static final int VALID_CODE = 0;
    private static final int UNDETECTED_CODE = 1;
    private static final int OFF_MAP_CODE = 3;
    private static final int BLOCKED_CODE = 4;
    private static final int NO_CREEP_CODE = 5;
    private static final int NO_GEYSER_CODE = 8;
    private static final int UNEXPLORED_CODE = 10;
    /**
     * The length of the per-tile code array.
     */
    private static final int TILE_CODE_COUNT = 48;

    /**
     * The {@code stat_txt} string indexes of the undetected, off-map and unexplored messages.
     */
    private static final int UNDETECTED_MESSAGE = 853;
    private static final int OFF_MAP_MESSAGE = 854;
    private static final int UNEXPLORED_MESSAGE = 857;

    /**
     * The seed and the random source of the varied terrain.
     */
    private static final int TERRAIN_SEED = 7;
    private static final int TERRAIN_RANDOM_SOURCE = 0;
    /**
     * The option bits of a compared placement: the undetected check and the fogged check.
     */
    private static final int UNDETECTED_OPTION = 1;
    private static final int FOGGED_OPTION = 2;
    /**
     * The number of compared option combinations.
     */
    private static final int OPTION_COUNT = 4;

    /**
     * The building type of the tile flag placements.
     */
    private static final int PYLON = ScrUnitIds.PROTOSS_PYLON;
    /**
     * The horizontal and vertical position of the tile flag placements and of their blocker.
     */
    private static final int PYLON_POSITION = 320;

    private static @Nullable GameData gameData;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            gameData.close();
        }
    }

    private static @NotNull Game createState(int @NotNull [] tileFlags) {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT], tileFlags), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(UNIT_CAPACITY);
        game.initializeUnitSpatialIndex();

        game.getPlayerTable().setPlayer(PLAYER, new Player(PLAYER, PLAYER_ID, PlayerTypes.HUMAN, PROTOSS_RACE, TEAM));

        return game;
    }

    private static boolean canPlaceOnTileFlags(int flags, boolean checkInvisible) {
        int[] tileFlags = new int[MAP_TILE_COUNT];

        Arrays.fill(tileFlags, flags);

        return createState(tileFlags).getUnitPlacement().canPlaceBuilding(null, PLAYER, PYLON, PYLON_POSITION, PYLON_POSITION, true, checkInvisible);
    }


    @Test
    void evaluate_building_should_mark_every_tile_valid_on_clear_footprint() {
        int[] gameTileFlags = new int[MAP_TILE_COUNT];

        Arrays.fill(gameTileFlags, MapTileFlags.WALKABLE);

        Game game = createState(gameTileFlags);
        PlacementResult result = game.getUnitPlacement().evaluateBuilding(null,
                                                                          PLAYER,
                                                                          ScrUnitIds.PROTOSS_PYLON,
                                                                          SITE_TILE, SITE_TILE,
                                                                          false,
                                                                          false,
                                                                          false,
                                                                          null);

        Assertions.assertTrue(result.isValid());
        Assertions.assertEquals(PYLON_TILE_WIDTH, result.getTileWidth());
        Assertions.assertEquals(PYLON_TILE_HEIGHT, result.getTileHeight());

        for (int row = 0; row < PYLON_TILE_HEIGHT; row++) {
            for (int column = 0; column < PYLON_TILE_WIDTH; column++) {
                Assertions.assertEquals(VALID_CODE, result.getTileCode(column, row));
            }
        }

        Assertions.assertEquals(0, PlacementResult.getMessageId(PlacementResult.VALID));
    }

    @Test
    void evaluate_building_should_fill_every_tile_code_with_off_map_for_off_map_footprint() {
        int[] gameTileFlags = new int[MAP_TILE_COUNT];

        Arrays.fill(gameTileFlags, MapTileFlags.WALKABLE);

        Game game = createState(gameTileFlags);
        PlacementResult result = game.getUnitPlacement().evaluateBuilding(null,
                                                                          PLAYER,
                                                                          ScrUnitIds.PROTOSS_PYLON,
                                                                          OFF_MAP_TILE_X, OFF_MAP_TILE_Y,
                                                                          false,
                                                                          false,
                                                                          false,
                                                                          null);

        Assertions.assertEquals(OFF_MAP_CODE, result.getCode());

        byte[] expected = new byte[TILE_CODE_COUNT];

        Arrays.fill(expected, (byte) OFF_MAP_CODE);

        Assertions.assertArrayEquals(expected, result.getTileCodes());
        Assertions.assertEquals(OFF_MAP_MESSAGE, PlacementResult.getMessageId(PlacementResult.OFF_MAP));
    }

    @Test
    void evaluate_building_should_block_only_the_unbuildable_tile() {
        int[] flags = new int[MAP_TILE_COUNT];

        Arrays.fill(flags, MapTileFlags.WALKABLE);

        flags[UNBUILDABLE_TILE * MAP_DIMENSION + UNBUILDABLE_TILE] |= MapTileFlags.UNBUILDABLE;

        Game game = createState(flags);
        PlacementResult result = game.getUnitPlacement().evaluateBuilding(null,
                                                                          PLAYER,
                                                                          ScrUnitIds.PROTOSS_PYLON,
                                                                          SITE_TILE, SITE_TILE,
                                                                          false,
                                                                          false,
                                                                          false,
                                                                          null);

        Assertions.assertEquals(BLOCKED_CODE, result.getCode());
        Assertions.assertEquals(VALID_CODE, result.getTileCode(0, 0));
        Assertions.assertEquals(VALID_CODE, result.getTileCode(1, 0));
        Assertions.assertEquals(VALID_CODE, result.getTileCode(0, 1));
        Assertions.assertEquals(BLOCKED_CODE, result.getTileCode(1, 1));

        Assertions.assertFalse(game.getUnitPlacement().canPlaceBuilding(null,
                                                                        PLAYER,
                                                                        ScrUnitIds.PROTOSS_PYLON,
                                                                        PYLON_CENTER, PYLON_CENTER,
                                                                        true,
                                                                        false));
    }

    @Test
    void evaluate_building_should_let_only_beacons_pass_unbuildable_tiles_with_cv5_flag() {
        int[] gameTileFlags = new int[MAP_TILE_COUNT];

        Arrays.fill(gameTileFlags, MapTileFlags.WALKABLE | MapTileFlags.UNBUILDABLE | MapTileFlags.FLAG_80000000);

        Game game = createState(gameTileFlags);

        Assertions.assertEquals(BLOCKED_CODE,
                                game.getUnitPlacement().evaluateBuilding(null,
                                                                         PLAYER,
                                                                         ScrUnitIds.PROTOSS_PYLON,
                                                                         SITE_TILE, SITE_TILE,
                                                                         false,
                                                                         false,
                                                                         false,
                                                                         null).getCode());

        for (int beacon : new int[]{ScrUnitIds.SPECIAL_ZERG_BEACON, ScrUnitIds.SPECIAL_PROTOSS_FLAG_BEACON}) {
            Assertions.assertTrue(game.getUnitPlacement().evaluateBuilding(null,
                                                                           PLAYER,
                                                                           beacon,
                                                                           SITE_TILE, SITE_TILE,
                                                                           false,
                                                                           false,
                                                                           false,
                                                                           null).isValid(),
                                  "beacon " + beacon);
        }

        int[] plainTileFlags = new int[MAP_TILE_COUNT];

        Arrays.fill(plainTileFlags, MapTileFlags.WALKABLE | MapTileFlags.UNBUILDABLE);

        Game plain = createState(plainTileFlags);

        Assertions.assertEquals(BLOCKED_CODE,
                                plain.getUnitPlacement().evaluateBuilding(null,
                                                                          PLAYER,
                                                                          ScrUnitIds.SPECIAL_ZERG_BEACON,
                                                                          SITE_TILE, SITE_TILE,
                                                                          false,
                                                                          false,
                                                                          false,
                                                                          null).getCode());
    }

    @Test
    void evaluate_building_should_report_unexplored_tiles_for_human_owner() {
        int[] gameTileFlags = new int[MAP_TILE_COUNT];

        Arrays.fill(gameTileFlags, MapTileFlags.WALKABLE | PLAYER_NOT_EXPLORED);

        Game game = createState(gameTileFlags);
        PlacementResult result = game.getUnitPlacement().evaluateBuilding(null,
                                                                          PLAYER,
                                                                          ScrUnitIds.PROTOSS_PYLON,
                                                                          UNEXPLORED_TILE, UNEXPLORED_TILE,
                                                                          false,
                                                                          false,
                                                                          false,
                                                                          null);

        Assertions.assertEquals(UNEXPLORED_CODE, result.getCode());
        Assertions.assertEquals(UNEXPLORED_CODE, result.getTileCode(1, 1));
        Assertions.assertEquals(UNEXPLORED_MESSAGE, PlacementResult.getMessageId(PlacementResult.UNEXPLORED));
    }

    @Test
    void evaluate_building_should_show_no_creep_on_unexplored_tile_but_report_unexplored() {
        int[] flags = new int[MAP_TILE_COUNT];

        Arrays.fill(flags, MapTileFlags.WALKABLE | MapTileFlags.HAS_CREEP);

        flags[UNEXPLORED_TILE * MAP_DIMENSION + UNEXPLORED_TILE] = MapTileFlags.WALKABLE | PLAYER_NOT_EXPLORED;

        Game game = createState(flags);
        int pool = ScrUnitIds.ZERG_SPAWNING_POOL;
        PlacementResult result = game.getUnitPlacement().evaluateBuilding(null,
                                                                          PLAYER,
                                                                          pool,
                                                                          UNEXPLORED_TILE, UNEXPLORED_TILE,
                                                                          false,
                                                                          false,
                                                                          true,
                                                                          null);

        Assertions.assertEquals(UNEXPLORED_CODE, result.getCode());
        Assertions.assertEquals(NO_CREEP_CODE, result.getTileCode(0, 0));
        Assertions.assertEquals(VALID_CODE, result.getTileCode(1, 0));
    }

    @Test
    void evaluate_building_should_report_no_geyser_for_refinery_without_geyser() {
        int[] gameTileFlags = new int[MAP_TILE_COUNT];

        Arrays.fill(gameTileFlags, MapTileFlags.WALKABLE);

        Game game = createState(gameTileFlags);
        PlacementResult result = game.getUnitPlacement().evaluateBuilding(null,
                                                                          PLAYER,
                                                                          ScrUnitIds.TERRAN_REFINERY,
                                                                          REFINERY_TILE, REFINERY_TILE,
                                                                          false,
                                                                          false,
                                                                          false,
                                                                          null);

        Assertions.assertEquals(NO_GEYSER_CODE, result.getCode());
        Assertions.assertEquals(NO_GEYSER_CODE, result.getTileCode(REFINERY_LAST_COLUMN, REFINERY_LAST_ROW));
    }

    @Test
    void evaluate_building_should_report_undetected_unit_only_with_undetected_check() {
        int[] gameTileFlags = new int[MAP_TILE_COUNT];

        Arrays.fill(gameTileFlags, MapTileFlags.WALKABLE);

        Game game = createState(gameTileFlags);
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit builder = pool.getUnit(BUILDER_POOL_INDEX);

        builder.setUnitId(ScrUnitIds.PROTOSS_PROBE);
        builder.setOwnerIndex(PLAYER);

        Unit blocker = pool.getUnit(BLOCKER_POOL_INDEX);

        blocker.setUnitId(ScrUnitIds.PROTOSS_DARK_TEMPLAR);
        blocker.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.REQUIRES_DETECTOR);
        blocker.setSprite(SpriteScenario.createSprite(0, BLOCKER_POSITION, BLOCKER_POSITION, 0));

        UnitSpatialIndex insertionIndex = game.getUnitSystem().getUnitSpatialIndex();

        Assertions.assertNotNull(insertionIndex);
        Assertions.assertNotNull(gameData);

        insertionIndex.insert(blocker, gameData.getUnitTypes());

        int pylon = ScrUnitIds.PROTOSS_PYLON;

        Assertions.assertTrue(game.getUnitPlacement().evaluateBuilding(builder,
                                                                       PLAYER,
                                                                       pylon,
                                                                       SITE_TILE, SITE_TILE,
                                                                       false,
                                                                       false,
                                                                       false,
                                                                       null).isValid());

        PlacementResult checked = game.getUnitPlacement().evaluateBuilding(builder, PLAYER, pylon, SITE_TILE, SITE_TILE, false, true, false, null);

        Assertions.assertEquals(UNDETECTED_CODE, checked.getCode());
        Assertions.assertEquals(UNDETECTED_MESSAGE, PlacementResult.getMessageId(PlacementResult.UNDETECTED));

        blocker.setStatus(UnitStatusFlags.COMPLETED);

        PlacementResult detected = game.getUnitPlacement().evaluateBuilding(builder, PLAYER, pylon, SITE_TILE, SITE_TILE, false, true, false, null);

        Assertions.assertEquals(BLOCKED_CODE, detected.getCode());
    }

    @Test
    void evaluate_addon_should_check_parent_footprint_when_addon_is_valid_or_state_is_remembered() {
        int[] gameTileFlags = new int[MAP_TILE_COUNT];

        Arrays.fill(gameTileFlags, MapTileFlags.WALKABLE);

        Game game = createState(gameTileFlags);
        UnitPool centerPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(centerPool);

        Unit center = centerPool.getUnit(COMMAND_CENTER_POOL_INDEX);

        center.setUnitId(ScrUnitIds.TERRAN_COMMAND_CENTER);
        center.setOwnerIndex(PLAYER);
        center.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING);
        center.setSprite(SpriteScenario.createSprite(0, COMMAND_CENTER_POSITION, COMMAND_CENTER_POSITION, 0));

        int comsat = ScrUnitIds.TERRAN_COMSAT_STATION;
        PlacementResult valid = game.getUnitPlacement().evaluateAddon(center, PLAYER, comsat, ADDON_TILE_X, ADDON_TILE_Y, false, false, false, null);

        Assertions.assertTrue(valid.isValid());
        Assertions.assertNotNull(valid.getParent());

        Assertions.assertNotNull(gameData);

        int offsetX = gameData.getUnits().get("Addon Horizontal", comsat - ScrUnitIds.TERRAN_COMMAND_CENTER) / TerrainTileset.TILE_SIZE;

        Assertions.assertEquals(ADDON_TILE_X - offsetX, valid.getParent().getTileX());

        PlacementResult offMapParent = game.getUnitPlacement().evaluateAddon(center,
                                                                             PLAYER,
                                                                             comsat,
                                                                             offsetX - 1, ADDON_TILE_Y,
                                                                             false,
                                                                             false,
                                                                             false,
                                                                             null);

        Assertions.assertEquals(VALID_CODE, offMapParent.getFootprintCode());
        Assertions.assertEquals(OFF_MAP_CODE, offMapParent.getCode());

        PlacementResult offMapAddon = game.getUnitPlacement().evaluateAddon(center,
                                                                            PLAYER,
                                                                            comsat,
                                                                            OFF_MAP_TILE_X, ADDON_TILE_Y,
                                                                            false,
                                                                            false,
                                                                            false,
                                                                            null);

        Assertions.assertEquals(OFF_MAP_CODE, offMapAddon.getCode());
        Assertions.assertNull(offMapAddon.getParent());

        PlacementResult rememberedOffMapAddon = game.getUnitPlacement().evaluateAddon(center,
                                                                                      PLAYER,
                                                                                      comsat,
                                                                                      OFF_MAP_TILE_X, ADDON_TILE_Y,
                                                                                      true,
                                                                                      false,
                                                                                      false,
                                                                                      null);
        PlacementResult rememberedParent = rememberedOffMapAddon.getParent();

        Assertions.assertEquals(OFF_MAP_CODE, rememberedOffMapAddon.getCode());
        Assertions.assertNotNull(rememberedParent);
        Assertions.assertEquals(OFF_MAP_TILE_X - offsetX, rememberedParent.getTileX());
        Assertions.assertEquals(VALID_CODE, rememberedParent.getCode());
    }

    @Test
    void can_place_building_should_agree_with_evaluated_codes_on_varied_terrain() {
        int[] flags = new int[MAP_TILE_COUNT];
        LcgRandom random = new LcgRandom(TERRAIN_SEED);
        int[] choices = {MapTileFlags.WALKABLE, MapTileFlags.WALKABLE, MapTileFlags.WALKABLE,
                         MapTileFlags.WALKABLE | MapTileFlags.UNBUILDABLE,
                         MapTileFlags.WALKABLE | PLAYER_NOT_VISIBLE, MapTileFlags.WALKABLE | PLAYER_NOT_EXPLORED,
                         MapTileFlags.WALKABLE | MapTileFlags.HAS_CREEP,
                         MapTileFlags.WALKABLE | MapTileFlags.OCCUPIED, MapTileFlags.WALKABLE | MapTileFlags.OCCUPIED | PLAYER_NOT_VISIBLE,
                         MapTileFlags.WALKABLE | MapTileFlags.PARTIALLY_WALKABLE};

        for (int index = 0; index < flags.length; index++) {
            flags[index] = choices[Integer.remainderUnsigned(random.next(TERRAIN_RANDOM_SOURCE), choices.length)];
        }

        Game game = createState(flags);
        int[] types = {ScrUnitIds.PROTOSS_PYLON,
                       ScrUnitIds.ZERG_SPAWNING_POOL,
                       ScrUnitIds.TERRAN_BARRACKS,
                       ScrUnitIds.TERRAN_REFINERY};

        Assertions.assertNotNull(gameData);

        for (int type : types) {
            int width = gameData.getUnits().get("Placement Box Width", type);
            int height = gameData.getUnits().get("Placement Box Height", type);

            for (int tileY = -1; tileY < MAP_DIMENSION; tileY++) {
                for (int tileX = -1; tileX < MAP_DIMENSION; tileX++) {
                    for (int options = 0; options < OPTION_COUNT; options++) {
                        boolean undetected = (options & UNDETECTED_OPTION) != 0;
                        boolean fogged = (options & FOGGED_OPTION) != 0;

                        boolean expected = game.getUnitPlacement().evaluateBuilding(null,
                                                                                    PLAYER,
                                                                                    type,
                                                                                    tileX, tileY,
                                                                                    false,
                                                                                    undetected,
                                                                                    fogged,
                                                                                    null).isValid();

                        Assertions.assertEquals(expected,
                                                game.getUnitPlacement().canPlaceBuilding(null,
                                                                                         PLAYER,
                                                                                         type,
                                                                                         tileX * TerrainTileset.TILE_SIZE + width / CENTER_DIVISOR,
                                                                                         tileY * TerrainTileset.TILE_SIZE + height / CENTER_DIVISOR,
                                                                                         undetected,
                                                                                         fogged),
                                                "type " + type + " at " + tileX + "," + tileY);
                    }
                }
            }
        }
    }

    @Test
    void can_place_building_should_distinguish_unexplored_invisible_and_visible_terrain() {
        Assertions.assertTrue(canPlaceOnTileFlags(MapTileFlags.WALKABLE, false));
        Assertions.assertFalse(canPlaceOnTileFlags(MapTileFlags.WALKABLE | PLAYER_NOT_EXPLORED, false));
        Assertions.assertFalse(canPlaceOnTileFlags(MapTileFlags.WALKABLE | MapTileFlags.UNBUILDABLE, false));
        Assertions.assertTrue(canPlaceOnTileFlags(MapTileFlags.WALKABLE | MapTileFlags.UNBUILDABLE | PLAYER_NOT_VISIBLE, false));
        Assertions.assertFalse(canPlaceOnTileFlags(MapTileFlags.WALKABLE | MapTileFlags.HAS_CREEP, false));
    }

    @Test
    void can_place_building_should_ignore_occupancy_on_invisible_tiles_unless_requested() {
        Assertions.assertFalse(canPlaceOnTileFlags(MapTileFlags.WALKABLE | MapTileFlags.OCCUPIED, false));
        Assertions.assertTrue(canPlaceOnTileFlags(MapTileFlags.WALKABLE | MapTileFlags.OCCUPIED | PLAYER_NOT_VISIBLE, false));
        Assertions.assertFalse(canPlaceOnTileFlags(MapTileFlags.WALKABLE | MapTileFlags.OCCUPIED | PLAYER_NOT_VISIBLE, true));
    }

    @Test
    void can_place_building_should_block_on_undetected_ground_unit_only_with_undetected_check() {
        int[] flags = new int[MAP_TILE_COUNT];

        Arrays.fill(flags, MapTileFlags.WALKABLE);

        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT], flags), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(BUILDER_AND_BLOCKER_CAPACITY);
        game.initializeUnitSpatialIndex();

        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit builder = pool.getUnit(BUILDER_POOL_INDEX);

        builder.setUnitId(ScrUnitIds.PROTOSS_PROBE);
        builder.setOwnerIndex(PLAYER);

        Unit blocker = pool.getUnit(BLOCKER_POOL_INDEX);

        blocker.setUnitId(ScrUnitIds.ZERG_DRONE);
        blocker.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.REQUIRES_DETECTOR);
        blocker.setSprite(SpriteScenario.createSprite(0, PYLON_POSITION, PYLON_POSITION, 0));

        UnitSpatialIndex insertionIndex = game.getUnitSystem().getUnitSpatialIndex();

        Assertions.assertNotNull(insertionIndex);
        Assertions.assertNotNull(gameData);

        insertionIndex.insert(blocker, gameData.getUnitTypes());

        Assertions.assertTrue(game.getUnitPlacement().canPlaceBuilding(builder, PLAYER, PYLON, PYLON_POSITION, PYLON_POSITION, false, false));
        Assertions.assertFalse(game.getUnitPlacement().canPlaceBuilding(builder, PLAYER, PYLON, PYLON_POSITION, PYLON_POSITION, true, false));

        blocker.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.FLYING);

        Assertions.assertTrue(game.getUnitPlacement().canPlaceBuilding(builder, PLAYER, PYLON, PYLON_POSITION, PYLON_POSITION, true, false));
    }
}
