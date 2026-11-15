package org.fogofwarlabs.sc4j.scr.game.unit.finder;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.OrdersDat;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.creep.CreepField;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.*;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Checks placement searches and predicates, movement destination writes and moves against captured results, and partial walkability
 * through the initialized graphics cache and fixed saved tile storage.
 */
@RequiresInstallation
final class UnitPositioningTest {
    private static final int TILE_VARIANTS_PER_GROUP = 16;
    private static final int MAXIMUM_TILE_GROUPS = 2048;
    private static final int FIRST_TERRAIN_TILE_IDENTIFIER = 32;
    private static final int FIRST_MINIATURE_INDEX = 0;
    private static final int LAST_MINIATURE_COLUMN = TerrainTileset.MINI_TILES_PER_ROW - 1;
    private static final int WORD_MASK = 0xFFFF;
    private static final int NEGATIVE_SECOND_MINIATURE_POSITION = -9;

    /**
     * The tileset of the test maps.
     */
    private static final int TILESET = 0;

    private static final int MAP_DIMENSION = 64;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int PLACEMENT_MAP_DIMENSION = 8;
    private static final int PLACEMENT_MAP_TILE_COUNT = PLACEMENT_MAP_DIMENSION * PLACEMENT_MAP_DIMENSION;
    private static final int PLACEMENT_UNIT_CAPACITY = 8;
    private static final int WALKABLE_TILE = 0x10000;
    private static final int CREEP_TILE = 0x400000;

    /**
     * A negative coordinate that signed division by the tile size still places in the first tile.
     */
    private static final int SIGNED_DIVISION_POINT = -31;
    /**
     * The tileset of the placement map: {@code 3} CV5 groups of {@code 52} bytes, and {@code 1} VF4 record of walkable mini-tiles.
     */
    private static final int TILE_GROUPS_BYTES = 3 * 52;
    private static final int MINI_TILE_COUNT = 16;
    private static final int WALKABLE_MINI_TILE = 1;

    private static final int UNIT_RECORD_SIZE = 488;
    private static final int MOVEMENT_DESTINATION_X_OFFSET = 0x20;
    private static final int MOVEMENT_DESTINATION_Y_OFFSET = 0x22;
    private static final int DIRECTION_TARGET_X_OFFSET = 0x34;
    private static final int DIRECTION_TARGET_Y_OFFSET = 0x36;
    private static final int MOVEMENT_FLAGS_OFFSET = 0x38;
    private static final int FIXED_POSITION_X_OFFSET = 0x44;
    private static final int FIXED_POSITION_Y_OFFSET = 0x48;
    private static final int PRIMARY_ORDER_OFFSET = 0x69;
    private static final int PRIMARY_ORDER_STATE_OFFSET = 0x6A;
    private static final int UNIT_TYPE_OFFSET = 0x8C;
    private static final int FIRST_ORDER_REFERENCE_OFFSET = 0xA8;
    private static final int STATUS_OFFSET = 0x140;
    private static final int MOVEMENT_DESTINATION_COUNTDOWN_OFFSET = 0x147;
    private static final int PATH_REFERENCE_OFFSET = 0x188;
    private static final int PATH_COLLISION_FLAGS_OFFSET = 0x191;
    private static final int TRAILING_CAPTURE_REFERENCE_OFFSET = 0x1C8;
    private static final int MINIMUM_HORIZONTAL_EDGE_INDEX_OFFSET = 0x1D4;
    private static final int REFERENCE_BYTES = 8;

    /**
     * The {@code BYTE} pattern that fills every unit record of a destination case before its fields are written.
     */
    private static final int UNIT_RECORD_FILL = 0xA5;

    private static final int STOP_ORDER = 1;
    private static final int FLYING_STATUS = 4;
    private static final int NO_COLLIDE_STATUS = 0x200000;
    private static final int STATUS_CASE_COUNT = 4;

    /**
     * The status case bits of a flying candidate and of a flying incoming unit.
     */
    private static final int CANDIDATE_FLYING = 2;
    private static final int INCOMING_FLYING = 1;
    /**
     * The order and order state of a destroyed unit.
     */
    private static final int DIE_ORDER = 0;
    private static final int DESTROYED_DIE_STATE = 1;

    /**
     * The pool index and the position of the unit whose placement is searched; the blockers precede it in the pool.
     */
    private static final int SEARCHER_POOL_INDEX = 5;
    private static final int SEARCHER_POSITION = 64;
    /**
     * The number of captured placement search cases.
     */
    private static final int PLACEMENT_CASE_COUNT = 1056;
    /**
     * The pool index of the first blocker; the other blockers follow it in the pool.
     */
    private static final int FIRST_BLOCKER_POOL_INDEX = 1;
    /**
     * The columns of the blocker and destination case tables: unit type, horizontal and vertical position, and the blocker status.
     */
    private static final int CASE_TYPE_COLUMN = 0;
    private static final int CASE_X_COLUMN = 1;
    private static final int CASE_Y_COLUMN = 2;
    private static final int BLOCKER_STATUS_COLUMN = 3;
    /**
     * The indexes of the horizontal and vertical coordinate in the search result.
     */
    private static final int RESULT_X_INDEX = 0;
    private static final int RESULT_Y_INDEX = 1;
    /**
     * The path collision flag of a unit below the air elevation level.
     */
    private static final int GROUND_ELEVATION_PATH_FLAG = 1;
    /**
     * The coordinates preset in the search result before each case.
     */
    private static final int PRESET_RESULT_X = 0xAAAA;
    private static final int PRESET_RESULT_Y = 0xBBBB;
    /**
     * The last error bytes preset before each case; a successful search keeps them.
     */
    private static final byte @NotNull [] PRESET_ERROR_BYTES = {81, 81, 81};

    private static final int INITIAL_MOVEMENT_FLAGS = 0xE4;
    private static final int DESTINATION_X = 100;
    private static final int DESTINATION_Y = 200;
    private static final int INITIAL_DESTINATION_X = 321;
    private static final int INITIAL_DESTINATION_Y = 654;

    private static final int CREATION_POSITION_X = 321;
    private static final int CREATION_POSITION_Y = 654;
    private static final int CREATION_OWNER = 3;
    private static final int CREATION_LOCAL_PLAYER = 0;
    private static final int CREATION_INSTRUCTION_LIMIT = 512;
    private static final int MOVED_X = 700;
    private static final int MOVED_Y = 900;
    private static final int FIXED_POINT_SHIFT = 8;
    private static final int MOVEMENT_DESTINATION_COUNTDOWN = 15;

    /**
     * The unit, sprite and image pool capacities of the move case.
     */
    private static final int MOVE_UNIT_CAPACITY = 1;
    private static final int MOVE_SPRITE_CAPACITY = 1;
    private static final int MOVE_IMAGE_CAPACITY = 2;
    /**
     * The pool index of a unit created alone, and the pool indexes of the candidate and the incoming unit of the type pair cases.
     */
    private static final int UNIT_POOL_INDEX = 1;
    private static final int CANDIDATE_POOL_INDEX = 1;
    private static final int INCOMING_POOL_INDEX = 2;
    /**
     * The order pool capacity of each liftability case.
     */
    private static final int ORDER_POOL_CAPACITY = 1;
    /**
     * The digest algorithm of the case outputs and the expected digest of the type pair cases.
     */
    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";
    private static final @NotNull String BLOCKS_PLACEMENT_DIGEST = "d67cfd37c067856e71bbd2142532e1795f033e18c8875c4c20122b609a104782";

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

    private static void verifyPlacementSearch(boolean regionComparison) throws IOException {
        int[] flags = new int[PLACEMENT_MAP_TILE_COUNT];

        Arrays.fill(flags, WALKABLE_TILE);

        GameMap map = new GameMap(PLACEMENT_MAP_DIMENSION, PLACEMENT_MAP_DIMENSION, TILESET, new int[PLACEMENT_MAP_TILE_COUNT], flags);
        Game game = Game.create(gameData, map, new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(PLACEMENT_UNIT_CAPACITY);
        game.initializeUnitSpatialIndex();

        byte[] miniatureFlags = new byte[MINI_TILE_COUNT * Short.BYTES];

        for (int miniatureIndex = 0; miniatureIndex < MINI_TILE_COUNT; miniatureIndex++) {
            miniatureFlags[miniatureIndex * Short.BYTES] = WALKABLE_MINI_TILE;
        }

        game.getPathSystem().setTerrainContours(TerrainContours.create(map, TerrainTileset.parse(new byte[TILE_GROUPS_BYTES], miniatureFlags)));

        int[][] blockers = {{0, 80, 80, 0}, {176, 160, 128, 2}, {0, 96, 96, 4}, {0, 24, 40, 0}};
        UnitPool unitPool = game.getUnitSystem().getUnitPool();
        UnitSpatialIndex placementIndex = game.getUnitSystem().getUnitSpatialIndex();

        Assertions.assertNotNull(unitPool);
        Assertions.assertNotNull(placementIndex);
        Assertions.assertNotNull(gameData);

        for (int blockerIndex = 0; blockerIndex < blockers.length; blockerIndex++) {
            Unit blocker = unitPool.getUnit(FIRST_BLOCKER_POOL_INDEX + blockerIndex);

            UnitMemoryScenario.of(blocker).writeMemoryWord(UNIT_TYPE_OFFSET, blockers[blockerIndex][CASE_TYPE_COLUMN]);
            UnitMemoryScenario.of(blocker).writeMemoryByte(PRIMARY_ORDER_OFFSET, STOP_ORDER);
            UnitMemoryScenario.of(blocker).writeMemoryInteger(STATUS_OFFSET, blockers[blockerIndex][BLOCKER_STATUS_COLUMN]);
            blocker.setSprite(SpriteScenario.createSprite(0, blockers[blockerIndex][CASE_X_COLUMN], blockers[blockerIndex][CASE_Y_COLUMN], 0));
            placementIndex.insert(blocker, gameData.getUnitTypes());
        }

        byte[] captured = GameTestResources.readNativeCapture(regionComparison ? GameTestResources.CAPTURE_REGION_PLACEMENT_SEARCH
                                                                               : GameTestResources.CAPTURE_PLACEMENT_SEARCH);
        ByteBuffer expected = ByteBuffer.wrap(captured).order(ByteOrder.LITTLE_ENDIAN);
        Unit unit = unitPool.getUnit(SEARCHER_POOL_INDEX);

        if (regionComparison) {
            UnitMemoryScenario.of(unit).writeMemoryByte(PATH_COLLISION_FLAGS_OFFSET, GROUND_ELEVATION_PATH_FLAG);
            unit.setSprite(SpriteScenario.createSprite(0, SEARCHER_POSITION, SEARCHER_POSITION, 0));

            TerrainContours placementContours = game.getPathSystem().getTerrainContours();

            Assertions.assertNotNull(placementContours);

            TerrainRegionScenario.configurePlacementComponents(placementContours.getRegions());
        }

        int[] result = new int[UnitPositioning.POSITION_COORDINATES];
        int caseIndex = 0;

        for (int unitType : new int[]{ScrUnitIds.TERRAN_MARINE, ScrUnitIds.TERRAN_SCV,
                                      ScrUnitIds.ZERG_DRONE, ScrUnitIds.PROTOSS_PROBE}) {
            UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);

            for (int status : new int[]{0, FLYING_STATUS}) {
                UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, status);

                for (int positionY : new int[]{-16, 0, 32, 64, 80, 96, 128, 160, 192, 224, 256, 280}) {
                    for (int positionX : new int[]{-16, 0, 24, 64, 80, 96, 128, 160, 224, 256, 280}) {
                        result[RESULT_X_INDEX] = PRESET_RESULT_X;
                        result[RESULT_Y_INDEX] = PRESET_RESULT_Y;
                        game.getGameContext().setLastErrorBytes(PRESET_ERROR_BYTES.clone());

                        boolean success = game.getUnitPositioning().find(unit, positionX, positionY, result);

                        Assertions.assertEquals(expected.get() != 0, success, "native case " + caseIndex);
                        Assertions.assertEquals(Short.toUnsignedInt(expected.getShort()), result[RESULT_X_INDEX],
                                                "horizontal native case " + caseIndex);
                        Assertions.assertEquals(Short.toUnsignedInt(expected.getShort()), result[RESULT_Y_INDEX],
                                                "vertical native case " + caseIndex);
                        Assertions.assertArrayEquals(success ? PRESET_ERROR_BYTES : "strEXITBLOCKED".getBytes(StandardCharsets.US_ASCII),
                                                     game.getGameContext().getLastErrorBytes());

                        caseIndex++;
                    }
                }
            }
        }

        Assertions.assertEquals(PLACEMENT_CASE_COUNT, caseIndex);
        Assertions.assertEquals(0, expected.remaining());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
        Assertions.assertFalse(game.hasInitializedWorldState());
    }


    @Test
    void partial_walkability_should_use_the_cached_megatile_instead_of_the_tile_identifier() throws IOException {
        Path installationDirectory = ExternalInput.INSTALLATION.getPath();

        Assertions.assertNotNull(installationDirectory);

        try (GameData data = GameData.loadFrom(installationDirectory)) {
            ReplayRuntime runtime = RunningGameScenario.start(data);
            Game game = runtime.getGame();
            TerrainTileset terrain = data.getTerrainTileset(game.getMap().getTileset());
            int walkableTile = -1;
            int blockedTile = -1;
            int limit = Math.min(terrain.getGroupCount(), MAXIMUM_TILE_GROUPS) * TILE_VARIANTS_PER_GROUP;

            for (int tileIdentifier = FIRST_TERRAIN_TILE_IDENTIFIER; tileIdentifier < limit && (walkableTile < 0 || blockedTile < 0);
                 tileIdentifier++) {
                boolean walkable = (terrain.getMiniatureFlags(tileIdentifier, FIRST_MINIATURE_INDEX) & TerrainTileset.MINI_TILE_WALKABLE) != 0;

                if (walkable && walkableTile < 0) {
                    walkableTile = tileIdentifier;
                } else if (!walkable && blockedTile < 0) {
                    blockedTile = tileIdentifier;
                }
            }

            Assertions.assertTrue(walkableTile >= 0 && blockedTile >= 0, "Installed terrain must supply opposite miniature walkability.");

            CreepField creep = game.getCreepField();
            GameMap map = game.getMap();

            Assertions.assertNotNull(creep);
            Assertions.assertEquals(0, terrain.getMiniatureFlags(blockedTile, FIRST_MINIATURE_INDEX) & TerrainTileset.MINI_TILE_WALKABLE);

            map.restoreTileIdentifier(0, blockedTile);
            map.restoreTileFlags(0, MapTileFlags.PARTIALLY_WALKABLE);
            creep.restoreGraphicsCache(0, terrain.getMegatileIdentifier(walkableTile) | CreepField.GRAPHICS_REFRESH_FLAG);

            int retainedCache = creep.getGraphicsCache(0);

            Assertions.assertTrue(game.getUnitPositioning().isTerrainPointWalkable(0, 0));
            Assertions.assertEquals(retainedCache, creep.getGraphicsCache(0), "A query must preserve the refresh flag and cached WORD.");
        }
    }

    @Test
    void partial_walkability_should_divide_signed_miniature_coordinates_toward_zero() throws IOException {
        Path installationDirectory = ExternalInput.INSTALLATION.getPath();

        Assertions.assertNotNull(installationDirectory);

        try (GameData data = GameData.loadFrom(installationDirectory)) {
            ReplayRuntime runtime = RunningGameScenario.start(data);
            Game game = runtime.getGame();
            TerrainTileset terrain = data.getTerrainTileset(game.getMap().getTileset());
            int selectedTile = -1;
            int limit = Math.min(terrain.getGroupCount(), MAXIMUM_TILE_GROUPS) * TILE_VARIANTS_PER_GROUP;

            for (int tileIdentifier = FIRST_TERRAIN_TILE_IDENTIFIER; tileIdentifier < limit && selectedTile < 0; tileIdentifier++) {
                int firstFlags = terrain.getMiniatureFlags(tileIdentifier, FIRST_MINIATURE_INDEX);
                int lastFlags = terrain.getMiniatureFlags(tileIdentifier, LAST_MINIATURE_COLUMN);

                if (((firstFlags ^ lastFlags) & TerrainTileset.MINI_TILE_WALKABLE) != 0) {
                    selectedTile = tileIdentifier;
                }
            }

            Assertions.assertTrue(selectedTile >= 0, "Installed terrain must distinguish signed division from shifted miniature coordinates.");

            CreepField creep = game.getCreepField();
            GameMap map = game.getMap();

            Assertions.assertNotNull(creep);

            map.restoreTileIdentifier(0, selectedTile);
            map.restoreTileFlags(0, MapTileFlags.PARTIALLY_WALKABLE);
            creep.restoreGraphicsCache(0, terrain.getMegatileIdentifier(selectedTile));

            boolean firstWalkable = (terrain.getMiniatureFlags(selectedTile, FIRST_MINIATURE_INDEX) & TerrainTileset.MINI_TILE_WALKABLE) != 0;
            boolean lastWalkable = (terrain.getMiniatureFlags(selectedTile, LAST_MINIATURE_COLUMN) & TerrainTileset.MINI_TILE_WALKABLE) != 0;

            Assertions.assertEquals(firstWalkable, game.getUnitPositioning().isTerrainPointWalkable(WORD_MASK, 0));
            Assertions.assertEquals(lastWalkable, game.getUnitPositioning().isTerrainPointWalkable(NEGATIVE_SECOND_MINIATURE_POSITION, 0));
        }
    }

    @Test
    void fixed_saved_tail_flags_should_decide_walkability_before_any_graphics_cache_query() throws IOException {
        Path installationDirectory = ExternalInput.INSTALLATION.getPath();

        Assertions.assertNotNull(installationDirectory);

        try (GameData data = GameData.loadFrom(installationDirectory)) {
            ReplayRuntime runtime = RunningGameScenario.start(data);
            Game game = runtime.getGame();
            GameMap map = game.getMap();
            int tailIndex = map.getTileCount();
            int positionY = map.getHeight() * TerrainTileset.TILE_SIZE;

            Assertions.assertTrue(tailIndex < GameMap.MAXIMUM_TILE_COUNT);
            Assertions.assertTrue(positionY <= Short.MAX_VALUE);

            ByteBuffer savedFlags = ByteBuffer.allocate(GameMap.MAXIMUM_TILE_COUNT * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            byte[] savedWords = new byte[GameMap.MAXIMUM_TILE_COUNT * Short.BYTES];

            savedFlags.putInt(tailIndex * Integer.BYTES, MapTileFlags.HAS_CREEP | MapTileFlags.PARTIALLY_WALKABLE);
            map.retainSavedArrays(savedWords, savedWords, savedFlags.array());

            Assertions.assertTrue(game.getUnitPositioning().isTerrainPointWalkable(0, positionY));

            savedFlags.putInt(tailIndex * Integer.BYTES, MapTileFlags.WALKABLE);
            map.retainSavedArrays(savedWords, savedWords, savedFlags.array());

            Assertions.assertTrue(game.getUnitPositioning().isTerrainPointWalkable(0, positionY));

            savedFlags.putInt(tailIndex * Integer.BYTES, 0);
            map.retainSavedArrays(savedWords, savedWords, savedFlags.array());

            Assertions.assertFalse(game.getUnitPositioning().isTerrainPointWalkable(0, positionY));
        }
    }

    @Test
    void set_movement_destination_should_apply_first_order_liftability_to_movement_flags() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(0));
        byte[] expected = HexFormat.of().parseHex(
                "c1e1e1e1c1c1e1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1e1e1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1e1e1c1c1c1e1c1c1c1c1c1c1c1" +
                "c1c1c1c1e1e1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1" +
                "c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1e1e1e1e1c1c1c1e1e1e1e1c1e1e1e1e1e1e1e1e1e1e1c1c1e1c1e1c1c1c1c1c1c1c1c1c1"
        );

        for (int orderType = 0; orderType < OrdersDat.ORDER_COUNT; orderType++) {
            Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);

            UnitMemoryScenario.of(unit).writeMemoryByte(MOVEMENT_FLAGS_OFFSET, INITIAL_MOVEMENT_FLAGS);

            OrderPool pool = new OrderPool(ORDER_POOL_CAPACITY);
            OrderPool.Order order = pool.allocate(orderType, 0, 0, 0, null);

            unit.setFirstOrder(order);

            Assertions.assertTrue(game.getUnitPositioning().setMovementDestination(unit, DESTINATION_X, DESTINATION_Y));

            Assertions.assertEquals(Byte.toUnsignedInt(expected[orderType]),
                                    UnitMemoryScenario.of(unit).readMemoryUnsignedByte(MOVEMENT_FLAGS_OFFSET),
                                    "native order " + orderType);
        }
    }

    @Test
    void matches_native_placement_search_cases() throws IOException {
        verifyPlacementSearch(false);
    }

    @Test
    void matches_native_region_placement_search_cases() throws IOException {
        verifyPlacementSearch(true);
    }

    @Test
    void matches_native_blocks_placement_type_pair_cases() throws NoSuchAlgorithmException {
        Unit candidate = UnitTestUtils.createUnit(CANDIDATE_POOL_INDEX);
        Unit incoming = UnitTestUtils.createUnit(INCOMING_POOL_INDEX);

        candidate.setSprite(SpriteScenario.createSprite(0, 0, 0, 0));
        UnitMemoryScenario.of(candidate).writeMemoryByte(PRIMARY_ORDER_OFFSET, STOP_ORDER);

        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        UnitCollisionRules rules = UnitTestUtils.createCollisionRules();

        Assertions.assertNotNull(gameData);

        for (int candidateType = 0; candidateType < UnitsDat.UNIT_COUNT; candidateType++) {
            for (int incomingType = 0; incomingType < UnitsDat.UNIT_COUNT; incomingType++) {
                UnitMemoryScenario.of(candidate).writeMemoryWord(UNIT_TYPE_OFFSET, candidateType);
                UnitMemoryScenario.of(incoming).writeMemoryWord(UNIT_TYPE_OFFSET, incomingType);

                for (int statusCase = 0; statusCase < STATUS_CASE_COUNT; statusCase++) {
                    UnitMemoryScenario.of(candidate).writeMemoryInteger(STATUS_OFFSET, statusCase >= CANDIDATE_FLYING ? FLYING_STATUS : 0);
                    UnitMemoryScenario.of(incoming).writeMemoryInteger(STATUS_OFFSET, (statusCase & INCOMING_FLYING) != 0 ? FLYING_STATUS : 0);

                    digest.update((byte) (rules.blocksPlacement(candidate, incoming, gameData.getUnitTypes()) ? 1 : 0));
                }
            }
        }

        Assertions.assertEquals(BLOCKS_PLACEMENT_DIGEST, HexFormat.of().formatHex(digest.digest()));

        UnitMemoryScenario.of(candidate).writeMemoryByte(PRIMARY_ORDER_OFFSET, DIE_ORDER);
        UnitMemoryScenario.of(candidate).writeMemoryByte(PRIMARY_ORDER_STATE_OFFSET, DESTROYED_DIE_STATE);

        Assertions.assertFalse(rules.blocksPlacement(candidate, incoming, gameData.getUnitTypes()));

        UnitMemoryScenario.of(candidate).writeMemoryByte(PRIMARY_ORDER_OFFSET, STOP_ORDER);
        UnitMemoryScenario.of(candidate).writeMemoryInteger(STATUS_OFFSET, NO_COLLIDE_STATUS);

        Assertions.assertFalse(rules.blocksPlacement(candidate, incoming, gameData.getUnitTypes()));
    }

    @Test
    void matches_native_set_movement_destination_cases() throws IOException, NoSuchAlgorithmException {
        int[][] inputs = {
                {0, -32768, -1},
                {0, 0, 0},
                {0, 2048, 2048},
                {0, 65535, 65535},
                {0, 321, 654},
                {7, -32768, -1},
                {7, 0, 0},
                {7, 2048, 2048},
                {7, 65535, 65535},
                {7, 321, 654},
                {41, -32768, -1},
                {41, 0, 0},
                {41, 2048, 2048},
                {41, 65535, 65535},
                {41, 321, 654},
                {64, -32768, -1},
                {64, 0, 0},
                {64, 2048, 2048},
                {64, 65535, 65535},
                {64, 321, 654}
        };

        String[] expectedHashes = {
                "5fddeabb874d5224ecf787d4bd47fad3570adf32fd37f16a37f4a7cfc6eea18f",
                "5fddeabb874d5224ecf787d4bd47fad3570adf32fd37f16a37f4a7cfc6eea18f",
                "5e638f938ca99e302d56590b2262e0131bc011cdf459ff15db3dda6de8fa66b9",
                "5fddeabb874d5224ecf787d4bd47fad3570adf32fd37f16a37f4a7cfc6eea18f",
                "3b59602f7985d942f0ab71611de2d202ffe06ae6324c48b8cfe4d26757c58e90",
                "9e98097bd46271d8c58169b9361b90f9b3816bba9c7ad9e7807e47b841fc9104",
                "9e98097bd46271d8c58169b9361b90f9b3816bba9c7ad9e7807e47b841fc9104",
                "6806baddbc8e415f470189c9bf4bbcb64a2d7fe383fb8ba724e40d48a3b080b7",
                "9e98097bd46271d8c58169b9361b90f9b3816bba9c7ad9e7807e47b841fc9104",
                "e6c8db306d1c99aa57862faf7d64ad9ef168e3a2d389fb68768b4b66be3a54e2",
                "26a471c3131e37929734701a2578ca0b8d34e6b2461594756688d4045821e619",
                "26a471c3131e37929734701a2578ca0b8d34e6b2461594756688d4045821e619",
                "c6ab26eae5335c0c60e33971d63602f86a2e7e39a4c5c21b4c0d4de09794fc81",
                "26a471c3131e37929734701a2578ca0b8d34e6b2461594756688d4045821e619",
                "303b32865b15d7fd56a4f531927404667b4052105dd34b051e3cbd98dbaa3fe5",
                "f816d43b56894de695ca8174987f8c7223fe439b0d930d680537d0bcacd4ca8c",
                "f816d43b56894de695ca8174987f8c7223fe439b0d930d680537d0bcacd4ca8c",
                "eb33348ad4f5fa9e5299dda87c06ed690cad663be33de4b88ce99a93d2b5f3d6",
                "f816d43b56894de695ca8174987f8c7223fe439b0d930d680537d0bcacd4ca8c",
                "2ec289a8eb8c49a62fd13c6dec10f6f837783bb7ebcb5894a8c728f589688885"
        };

        byte[] captured = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_PLACEMENT_DESTINATIONS);

        Assertions.assertEquals(inputs.length * UNIT_RECORD_SIZE, captured.length);

        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        for (int caseIndex = 0; caseIndex < inputs.length; caseIndex++) {
            int[] input = inputs[caseIndex];
            Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);

            for (int byteOffset = 0; byteOffset < UNIT_RECORD_SIZE; byteOffset++) {
                UnitMemoryScenario.of(unit).writeMemoryByte(byteOffset, UNIT_RECORD_FILL);
            }

            UnitMemoryScenario.of(unit).clearMemoryBytes(TRAILING_CAPTURE_REFERENCE_OFFSET, REFERENCE_BYTES);
            UnitMemoryScenario.of(unit).clearMemoryBytes(PATH_REFERENCE_OFFSET, REFERENCE_BYTES);
            UnitMemoryScenario.of(unit).clearMemoryBytes(FIRST_ORDER_REFERENCE_OFFSET, REFERENCE_BYTES);
            UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, input[CASE_TYPE_COLUMN]);
            UnitMemoryScenario.of(unit).writeMemoryWord(MOVEMENT_DESTINATION_X_OFFSET, INITIAL_DESTINATION_X);
            UnitMemoryScenario.of(unit).writeMemoryWord(MOVEMENT_DESTINATION_Y_OFFSET, INITIAL_DESTINATION_Y);

            Assertions.assertTrue(game.getUnitPositioning().setMovementDestination(unit, input[CASE_X_COLUMN], input[CASE_Y_COLUMN]));

            byte[] expected = Arrays.copyOfRange(captured, caseIndex * UNIT_RECORD_SIZE, (caseIndex + 1) * UNIT_RECORD_SIZE);

            Assertions.assertEquals(expectedHashes[caseIndex],
                                    HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST_ALGORITHM).digest(expected)));
            UnitDiagnosticAssert.assertDomainEquals(expected, unit, "Native destination case " + caseIndex);
        }
    }

    @Test
    void move_should_relocate_constructed_hidden_marine_before_spatial_registration() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(MOVE_UNIT_CAPACITY);
        game.getSpriteSystem().initializeSpritePool(MOVE_SPRITE_CAPACITY);
        game.getSpriteSystem().initializeImagePool(MOVE_IMAGE_CAPACITY);
        game.initializeUnitSpatialIndex();

        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();
        UnitStorageConstructor storageConstructor = game.getUnitStorageConstructor();
        int creationUnitType = ScrUnitIds.TERRAN_MARINE;
        UnitCreationContext creationInputs = new UnitCreationContext(false, false, false, false, false, CREATION_LOCAL_PLAYER);

        Unit unit = storageConstructor.createStorage(creationInputs,
                                                     accounting,
                                                     creationUnitType,
                                                     CREATION_POSITION_X, CREATION_POSITION_Y,
                                                     CREATION_OWNER,
                                                     0,
                                                     CREATION_INSTRUCTION_LIMIT);

        Assertions.assertNotNull(unit);

        game.getUnitPositioning().move(unit, MOVED_X, MOVED_Y);

        Assertions.assertEquals(MOVED_X, unit.getPositionX());
        Assertions.assertEquals(MOVED_Y, unit.getPositionY());

        Sprite sprite = unit.getSpriteOrNull();

        Assertions.assertNotNull(sprite);

        Assertions.assertEquals(MOVED_X, sprite.getX());
        Assertions.assertEquals(MOVED_Y, sprite.getY());
        Assertions.assertEquals(MOVED_X << FIXED_POINT_SHIFT, UnitMemoryScenario.of(unit).readMemoryInteger(FIXED_POSITION_X_OFFSET));
        Assertions.assertEquals(MOVED_Y << FIXED_POINT_SHIFT, UnitMemoryScenario.of(unit).readMemoryInteger(FIXED_POSITION_Y_OFFSET));
        Assertions.assertEquals(MOVED_X, UnitMemoryScenario.of(unit).readMemoryUnsignedWord(MOVEMENT_DESTINATION_X_OFFSET));
        Assertions.assertEquals(MOVED_Y, UnitMemoryScenario.of(unit).readMemoryUnsignedWord(MOVEMENT_DESTINATION_Y_OFFSET));
        Assertions.assertEquals(MOVED_X, UnitMemoryScenario.of(unit).readMemoryUnsignedWord(DIRECTION_TARGET_X_OFFSET));
        Assertions.assertEquals(MOVED_Y, UnitMemoryScenario.of(unit).readMemoryUnsignedWord(DIRECTION_TARGET_Y_OFFSET));
        Assertions.assertEquals(MOVEMENT_DESTINATION_COUNTDOWN,
                                UnitMemoryScenario.of(unit).readMemoryUnsignedByte(MOVEMENT_DESTINATION_COUNTDOWN_OFFSET));
        Assertions.assertEquals(-1, UnitMemoryScenario.of(unit).readMemoryInteger(MINIMUM_HORIZONTAL_EDGE_INDEX_OFFSET));

        UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();

        Assertions.assertNotNull(spatialIndex);

        Assertions.assertEquals(0, spatialIndex.getCount());
        Assertions.assertFalse(game.hasInitializedWorldState());
    }

    @Test
    void is_terrain_point_walkable_should_keep_tile_flag_precedence_and_signed_division() {
        int[] flags = new int[MAP_TILE_COUNT];
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT], flags), new LcgRandom(0));

        GameMapScenario.setFlags(game.getMap(), 0, CREEP_TILE);

        Assertions.assertTrue(game.getUnitPositioning().isTerrainPointWalkable(SIGNED_DIVISION_POINT, SIGNED_DIVISION_POINT));

        GameMapScenario.setFlags(game.getMap(), 0, WALKABLE_TILE);

        Assertions.assertTrue(game.getUnitPositioning().isTerrainPointWalkable(0, 0));

        GameMapScenario.setFlags(game.getMap(), 0, 0);

        Assertions.assertFalse(game.getUnitPositioning().isTerrainPointWalkable(0, 0));
    }
}
