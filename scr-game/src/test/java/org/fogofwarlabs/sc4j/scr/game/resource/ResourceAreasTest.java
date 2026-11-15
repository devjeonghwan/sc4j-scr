package org.fogofwarlabs.sc4j.scr.game.resource;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayLoaderUtils;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.state.RuntimeRandom;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainContours;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.*;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * Checks how the periodic resource-area update marks the areas that buildings occupy, and that the area tables of an initialized replay map ignore
 * the capture padding and reject out-of-bounds updates.
 */
@RequiresInstallation
final class ResourceAreasTest {
    private static final int UNIT_CAPACITY = 4;
    private static final int START_POSITION_WORDS = 2;

    private static final int AREA_VERTICAL_POSITION = 100;
    private static final int @NotNull [] AREA_HORIZONTAL_POSITIONS = {100, 200, 300};
    private static final int BUILDING_HORIZONTAL_POSITION = 200;
    private static final int @NotNull [] BUILDING_VERTICAL_POSITIONS = {200, 250};
    /**
     * A unit type past {@code units.dat} whose flag DWORD, read past the {@code units.dat} flags of the installation, has the building bit.
     */
    private static final int OVERRUN_BUILDING_TYPE = 230;
    /**
     * The occupancy flag of an area that a building occupies.
     */
    private static final int OCCUPIED_FLAG = 0x01;

    /**
     * The start position WORDs of the resource area initialization: a horizontal and a vertical WORD per playable slot.
     */
    private static final int START_POSITION_TABLE_WORDS = PlayerTable.PLAYABLE_PLAYER_SLOTS * START_POSITION_WORDS;

    /**
     * The iscript and movement instruction bound of the replay world.
     */
    private static final int INSTRUCTION_LIMIT = 4096;
    /**
     * The established CRT random state before the replay map construction.
     */
    private static final int RUNTIME_RANDOM_SEED = 0x5A123456;
    /**
     * The local vision comparison mask of the replay world: the bit of player {@code 0}.
     */
    private static final int LOCAL_PLAYER_MASK = 1;
    /**
     * The bytes of the resource area table capture, of one area record and the table offsets of the area count and of the update countdown.
     */
    private static final int RESOURCE_AREA_TABLE_SIZE = 12008;
    private static final int RESOURCE_AREA_RECORD_SIZE = 48;
    private static final int AREA_COUNT_OFFSET = 0x2EE0;
    private static final int UPDATE_COUNTDOWN_OFFSET = 0x2EE4;
    /**
     * The number of resource area records.
     */
    private static final int RESOURCE_AREA_CAPACITY = 250;
    /**
     * The update countdown in frames that an expired countdown is reset to.
     */
    private static final int UPDATE_INTERVAL = 450;
    /**
     * The factor and the divisor of the byte pattern that fills the resource area table capture.
     */
    private static final int SNAPSHOT_PATTERN_FACTOR = 73;
    private static final int SNAPSHOT_PATTERN_DIVISOR = 251;
    /**
     * The area record offsets of the mineral and gas count bytes, of the occupancy byte and of the mineral and gas totals, and the mask that clears
     * the low occupancy bit.
     */
    private static final int AREA_RESOURCE_COUNTS_OFFSET = 4;
    private static final int AREA_OCCUPANCY_OFFSET = 7;
    private static final int AREA_MINERAL_TOTAL_OFFSET = 8;
    private static final int AREA_GAS_TOTAL_OFFSET = 12;
    private static final byte LOW_OCCUPANCY_CLEAR_MASK = (byte) 0xFE;
    /**
     * The table offset of a padding byte of the first area record.
     */
    private static final int AREA_PADDING_OFFSET = 16;
    /**
     * The bit masks that corrupt one byte of a table copy: the padding and detached-copy mask and the source-table mask.
     */
    private static final int CORRUPTION_MASK = 127;
    private static final int SOURCE_CORRUPTION_MASK = 31;

    private static @NotNull Game createGame(@NotNull GameData gameData, @NotNull TerrainTileset terrain) {
        GameMap map = new GameMap(MineralSearchScenario.MAP_SIZE, MineralSearchScenario.MAP_SIZE, 0,
                                  new int[MineralSearchScenario.MAP_SIZE * MineralSearchScenario.MAP_SIZE],
                                  MineralSearchScenario.createWalkableFlags());
        Game game = Game.create(gameData, map, new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(UNIT_CAPACITY);
        game.getPathSystem().setTerrainContours(TerrainContours.create(map, terrain));

        return game;
    }

    private static void placeUnit(@NotNull Game game, int type, int verticalPosition) {
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit unit = pool.getUnit(UnitPoolScenario.allocate(pool));

        unit.setUnitId(type);
        unit.setStatus(UnitStatusFlags.COMPLETED);
        unit.setPrimaryOrder(ScrOrderIds.NOTHING);
        unit.setSprite(SpriteScenario.createSprite(0, BUILDING_HORIZONTAL_POSITION, verticalPosition, 0));
        pool.prependToOwnerList(unit, 0);
        pool.insertCreatedUnit(unit);
        pool.transferCreatedUnitToActive(unit);
    }

    private static void initializeAreas(@NotNull ResourceAreas areas) {
        int[] startPositions = new int[START_POSITION_TABLE_WORDS];
        int[] playerTypes = new int[PlayerTable.PLAYABLE_PLAYER_SLOTS];

        for (int areaIndex = 0; areaIndex < AREA_HORIZONTAL_POSITIONS.length; areaIndex++) {
            startPositions[areaIndex * START_POSITION_WORDS]     = AREA_HORIZONTAL_POSITIONS[areaIndex];
            startPositions[areaIndex * START_POSITION_WORDS + 1] = AREA_VERTICAL_POSITION;
        }

        Arrays.fill(playerTypes, PlayerTypes.NONE);

        areas.initialize(startPositions, playerTypes);
    }


    @Test
    void update_should_mark_at_most_one_unoccupied_area_per_building() throws Exception {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            TerrainTileset terrain = MineralSearchScenario.createWalkableTileset();
            Game game = createGame(gameData, terrain);
            ResourceAreas areas = new ResourceAreas(game, terrain);

            for (int verticalPosition : BUILDING_VERTICAL_POSITIONS) {
                placeUnit(game, ScrUnitIds.TERRAN_COMMAND_CENTER, verticalPosition);
            }

            initializeAreas(areas);
            areas.update();

            Assertions.assertEquals(AREA_HORIZONTAL_POSITIONS.length, areas.getAreaCount());
            Assertions.assertEquals(OCCUPIED_FLAG, areas.getOccupancyFlags(1), "area 1");
            Assertions.assertEquals(OCCUPIED_FLAG, areas.getOccupancyFlags(2), "area 2");
            Assertions.assertEquals(0, areas.getOccupancyFlags(3), "area 3");
        }
    }

    @Test
    void update_should_not_count_a_unit_type_past_units_dat_as_a_building() throws Exception {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            TerrainTileset terrain = MineralSearchScenario.createWalkableTileset();
            Game game = createGame(gameData, terrain);
            ResourceAreas areas = new ResourceAreas(game, terrain);

            Assertions.assertNotEquals(0, gameData.getUnitTypes().getFlags(OVERRUN_BUILDING_TYPE) & UnitTypeFlags.BUILDING);

            placeUnit(game, OVERRUN_BUILDING_TYPE, BUILDING_VERTICAL_POSITIONS[0]);
            initializeAreas(areas);
            areas.update();

            for (int area = 1; area <= areas.getAreaCount(); area++) {
                Assertions.assertEquals(0, areas.getOccupancyFlags(area), "area " + area);
            }
        }
    }

    @Test
    void resource_areas_ignore_capture_padding_and_reject_out_of_bounds_updates() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_ZVP_FIGHTING_SPIRIT)));
            byte[] mapData = replay.getMapData();

            Assertions.assertNotNull(mapData);

            ChkFile mapFile = ChkFile.parseTolerant(mapData);
            int[] sharing = new int[PlayerTable.PLAYER_SLOTS];

            for (int playerIndex = 0; playerIndex < sharing.length; playerIndex++) {
                sharing[playerIndex] = 1 << playerIndex;
            }

            Assertions.assertNotNull(gameData);

            LcgRandom random = new LcgRandom(0);
            UnitCreationContext creationContext = new UnitCreationContext(false, true, true, false, true, 0);

            Game game = ReplayLoaderUtils.createInitializedReplayWorld(creationContext,
                                                                       replay, mapFile,
                                                                       gameData,
                                                                       random,
                                                                       new RuntimeRandom(RUNTIME_RANDOM_SEED),
                                                                       sharing,
                                                                       LOCAL_PLAYER_MASK,
                                                                       RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                                       INSTRUCTION_LIMIT);
            ResourceAreas areas = new ResourceAreas(game, gameData.getTerrainTileset(mapFile.getTileset()));
            byte[] snapshot = new byte[RESOURCE_AREA_TABLE_SIZE];

            for (int offset = 0; offset < snapshot.length; offset++) {
                snapshot[offset] = (byte) (offset * SNAPSHOT_PATTERN_FACTOR + offset / SNAPSHOT_PATTERN_DIVISOR);
            }

            ResourceAreaAssert.load(areas, snapshot);

            ResourceAreaAssert.assertKnownFields(snapshot, areas);

            byte[] serialized = ResourceAreaAssert.createSnapshot(areas);

            Assertions.assertEquals(0, serialized[AREA_PADDING_OFFSET], "the saved table does not write record padding");

            snapshot[AREA_PADDING_OFFSET] ^= CORRUPTION_MASK;
            ResourceAreaAssert.load(areas, snapshot);

            Assertions.assertArrayEquals(serialized, ResourceAreaAssert.createSnapshot(areas), "the saved table does not read record padding");

            byte[] detached = ResourceAreaAssert.createSnapshot(areas);

            detached[0] ^= CORRUPTION_MASK;

            ResourceAreaAssert.assertKnownFields(snapshot, areas);

            snapshot[0] ^= SOURCE_CORRUPTION_MASK;

            Assertions.assertNotEquals(snapshot[0], ResourceAreaAssert.createSnapshot(areas)[0]);

            ByteBuffer expected = ByteBuffer.wrap(snapshot).order(ByteOrder.LITTLE_ENDIAN);

            expected.putInt(AREA_COUNT_OFFSET, RESOURCE_AREA_CAPACITY).putInt(UPDATE_COUNTDOWN_OFFSET, 0);
            ResourceAreaAssert.load(areas, snapshot);
            expected.putInt(UPDATE_COUNTDOWN_OFFSET, UPDATE_INTERVAL);

            for (int area = 1; area < RESOURCE_AREA_CAPACITY; area++) {
                snapshot[area * RESOURCE_AREA_RECORD_SIZE + AREA_OCCUPANCY_OFFSET] &= LOW_OCCUPANCY_CLEAR_MASK;
                expected.putShort(area * RESOURCE_AREA_RECORD_SIZE + AREA_RESOURCE_COUNTS_OFFSET, (short) 0);
                expected.putInt(area * RESOURCE_AREA_RECORD_SIZE + AREA_MINERAL_TOTAL_OFFSET, 0);
                expected.putInt(area * RESOURCE_AREA_RECORD_SIZE + AREA_GAS_TOTAL_OFFSET, 0);
            }

            Assertions.assertThrows(ArrayIndexOutOfBoundsException.class, areas::update);
            Assertions.assertEquals(UPDATE_INTERVAL, expected.getInt(UPDATE_COUNTDOWN_OFFSET));
            ResourceAreaAssert.assertKnownFields(snapshot, areas);

            Assertions.assertEquals(RESOURCE_AREA_CAPACITY, areas.getAreaCount());
            Assertions.assertThrows(IllegalArgumentException.class,
                                    () -> areas.initialize(new int[START_POSITION_TABLE_WORDS - 1], new int[PlayerTable.PLAYABLE_PLAYER_SLOTS]));
            ResourceAreaAssert.assertKnownFields(snapshot, areas);

            expected.putInt(AREA_COUNT_OFFSET, 1).putInt(UPDATE_COUNTDOWN_OFFSET, Integer.MIN_VALUE);
            ResourceAreaAssert.load(areas, snapshot);
            areas.update();
            expected.putInt(UPDATE_COUNTDOWN_OFFSET, Integer.MAX_VALUE);

            ResourceAreaAssert.assertKnownFields(snapshot, areas);

            areas.initialize(new int[START_POSITION_TABLE_WORDS], new int[PlayerTable.PLAYABLE_PLAYER_SLOTS]);

            // Without start locations, initialization clears every record and registers only the resource clusters.
            for (int area = 0; area < RESOURCE_AREA_CAPACITY; area++) {
                Assertions.assertEquals(0, areas.getStartLocationMarker(area), "start area " + area);

                if (area == 0 || area > areas.getAreaCount()) {
                    Assertions.assertEquals(0, areas.getHorizontalPosition(area), "horizontal area " + area);
                    Assertions.assertEquals(0, areas.getVerticalPosition(area), "vertical area " + area);
                    Assertions.assertEquals(0, areas.getMineralCount(area), "minerals area " + area);
                    Assertions.assertEquals(0, areas.getGasCount(area), "gas area " + area);
                    Assertions.assertEquals(0, areas.getOccupancyFlags(area), "occupancy area " + area);
                    Assertions.assertEquals(0, areas.getMineralTotal(area), "mineral total area " + area);
                    Assertions.assertEquals(0, areas.getGasTotal(area), "gas total area " + area);
                }
            }
        }
    }
}
