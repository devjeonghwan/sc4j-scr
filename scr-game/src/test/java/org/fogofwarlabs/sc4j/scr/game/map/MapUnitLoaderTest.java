package org.fogofwarlabs.sc4j.scr.game.map;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.GameSettings;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkSection;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkUnit;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.ai.ComputerPlayerPhase;
import org.fogofwarlabs.sc4j.scr.game.combat.ProjectilePhase;
import org.fogofwarlabs.sc4j.scr.game.creep.CreepRecession;
import org.fogofwarlabs.sc4j.scr.game.frame.GameExecutionScope;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.frame.UnitPhase;
import org.fogofwarlabs.sc4j.scr.game.movement.UnitMovement;
import org.fogofwarlabs.sc4j.scr.game.path.LocalPathSearch;
import org.fogofwarlabs.sc4j.scr.game.path.PathObstacleCollector;
import org.fogofwarlabs.sc4j.scr.game.path.PathPool;
import org.fogofwarlabs.sc4j.scr.game.path.PathPoolAssert;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.StartLocation;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayLoaderUtils;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.resource.ResourceAreaAssert;
import org.fogofwarlabs.sc4j.scr.game.resource.ResourceAreas;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteEffectPool;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.state.GameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.RuntimeRandom;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainContours;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.*;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.fogofwarlabs.sc4j.scr.game.visibility.MapVisibility;
import org.fogofwarlabs.sc4j.scr.game.visibility.SightCoverage;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * Checks {@code UNIT} section routing, start locations and camera, and the units, resource areas, path targets and local collision of an initialized
 * replay map.
 */
@RequiresInstallation
final class MapUnitLoaderTest {
    /**
     * The iscript and movement instruction bound of the replay world.
     */
    private static final int INSTRUCTION_LIMIT = 4096;
    /**
     * The iscript instruction bound of the {@code UNIT} section cases.
     */
    private static final int UNIT_SECTION_INSTRUCTION_LIMIT = 1024;
    /**
     * The established CRT random state before the replay map construction.
     */
    private static final int RUNTIME_RANDOM_SEED = 0x5A123456;
    /**
     * The size of the square map of the {@code UNIT} section cases.
     */
    private static final int UNIT_SECTION_MAP_SIZE = 128;
    /**
     * The name of the section the cases build.
     */
    private static final @NotNull String UNIT_SECTION = "UNIT";
    /**
     * The bytes of a CHK section header (the name and the size DWORD) and the header offset of the size DWORD.
     */
    private static final int SECTION_HEADER_SIZE = 8;
    private static final int SECTION_SIZE_OFFSET = 4;
    /**
     * The bytes of one {@code UNIT} record and the record offsets of its coordinates, type and owner.
     */
    private static final int UNIT_RECORD_SIZE = 36;
    private static final int RECORD_X_OFFSET = 4;
    private static final int RECORD_Y_OFFSET = 6;
    private static final int RECORD_UNIT_TYPE_OFFSET = 8;
    private static final int RECORD_OWNER_OFFSET = 16;
    /**
     * The columns of a {@code UNIT} section case input row.
     */
    private static final int INPUT_UNIT_TYPE = 0;
    private static final int INPUT_OWNER = 1;
    private static final int INPUT_X = 2;
    private static final int INPUT_Y = 3;
    /**
     * The start location records at the head of the {@code UNIT} section cases.
     */
    private static final int START_RECORD_COUNT = 3;
    /**
     * The number of start position WORDs: a horizontal and a vertical WORD per playable slot.
     */
    private static final int START_POSITION_WORDS = 16;
    /**
     * The WORDs of one start position: the horizontal WORD and the vertical WORD.
     */
    private static final int POSITION_COMPONENT_COUNT = 2;
    /**
     * The value the start positions hold before a {@code UNIT} occurrence replaces them.
     */
    private static final int START_POSITION_FILL = 0xAAAA;
    /**
     * The victory condition ID of the {@code UNIT} section cases without the map settings.
     */
    private static final int NON_MAP_SETTINGS_VICTORY = 1;
    /**
     * The native local player comparison DWORD of the {@code UNIT} section cases.
     */
    private static final int UNIT_SECTION_LOCAL_PLAYER = 3;
    /**
     * The vertical camera WORD before each {@code UNIT} section case.
     */
    private static final int PREVIOUS_CAMERA_Y = 19;
    /**
     * The bytes of one captured unit record and of its diagnostic header.
     */
    private static final int UNIT_CAPTURE_SIZE = 488;
    private static final int UNIT_SNAPSHOT_HEADER_SIZE = 24;
    /**
     * The bytes of the resource area table capture.
     */
    private static final int RESOURCE_AREA_TABLE_SIZE = 12008;
    /**
     * The resource area tables of the expected capture: after the initialization and after the first frame.
     */
    private static final int EXPECTED_AREA_TABLE_COUNT = 2;
    /**
     * The unit pool indices of the larva and of the unit whose path the second frame adjusts.
     */
    private static final int LARVA_POOL_INDEX = 1572;
    private static final int ADJUSTED_UNIT_POOL_INDEX = 1566;
    /**
     * The number of captured path target cases and the bytes of one case.
     */
    private static final int PATH_TARGET_CASE_COUNT = 3400;
    private static final int PATH_TARGET_CASE_SIZE = 12;
    /**
     * The number of captured local collision cases.
     */
    private static final int COLLISION_CASE_COUNT = 256;
    /**
     * The number of contour boundary directions.
     */
    private static final int DIRECTION_COUNT = 4;
    /**
     * The shifts of the direction and terminal bytes of a packed collision candidate.
     */
    private static final int CANDIDATE_DIRECTIONS_SHIFT = 32;
    private static final int CANDIDATE_TERMINAL_SHIFT = 40;
    /**
     * The offsets of the local collision capture: the candidate count, the candidate records and their direction and terminal bytes, the segment
     * count and records, and the per-direction boundary counts and records.
     */
    private static final int CANDIDATE_COUNT_OFFSET = 0x98;
    private static final int CANDIDATE_POSITION_OFFSET = 0x9C;
    private static final int CANDIDATE_DIRECTIONS_OFFSET = 0xA0;
    private static final int CANDIDATE_TERMINAL_OFFSET = 0xA1;
    private static final int CANDIDATE_RECORD_SIZE = 8;
    private static final int SEGMENT_COUNT_OFFSET = 0x1C4;
    private static final int SEGMENTS_OFFSET = 0x1C8;
    private static final int BOUNDARY_COUNTS_OFFSET = 0x308;
    private static final int BOUNDARIES_OFFSET = 0x310;
    private static final int BOUNDARY_DIRECTION_STRIDE = 0x400;
    private static final int BOUNDARY_RECORD_SIZE = 8;
    private static final int SEGMENT_RECORD_SIZE = 8;
    /**
     * The destination and target-edge rectangle of the larva's local collision, and the position and direction mask it collects from.
     */
    private static final int COLLISION_DESTINATION_X = 3818;
    private static final int COLLISION_DESTINATION_Y = 324;
    private static final long COLLISION_EXCLUSION_RECTANGLE = 0x8300830083008300L;
    private static final int COLLISION_POSITION_X = 3808;
    private static final int COLLISION_POSITION_Y = 314;
    private static final int COLLISION_DIRECTIONS = 255;
    /**
     * The map units and all units the replay map initialization creates (the map units and the starting units).
     */
    private static final int MAP_UNIT_CREATION_COUNT = 122;
    private static final int CREATED_UNIT_COUNT = 135;
    /**
     * The starting units of the Protoss player: the Nexus and four Probes.
     */
    private static final int PROTOSS_STARTING_UNIT_COUNT = 5;
    /**
     * The bytes of the initialization snapshot header: the unit count, the random state and count and the creep random state.
     */
    private static final int SNAPSHOT_HEADER_SIZE = 16;
    /**
     * The bytes of the resource area input header (four DWORDs, the start position WORDs and the player type bytes) and of the input of one tile (the
     * tile WORD and the overlay WORD).
     */
    private static final int AREA_INPUT_HEADER_SIZE = 56;
    private static final int AREA_INPUT_TILE_SIZE = 4;
    /**
     * The number of contour controls in the failure capture.
     */
    private static final int CONTOUR_CONTROL_COUNT = 12;
    /**
     * The axes of the spatial index and the bytes of one spatial entry in the failure capture (the unit index and the coordinate DWORD on both axes).
     */
    private static final int AXIS_COUNT = 2;
    private static final int SPATIAL_ENTRY_SIZE = 16;
    /**
     * The bytes of the header of one unit in the failure capture (the pool index, the sprite position and the sprite flags DWORDs) and of one unit
     * extent record (four WORDs).
     */
    private static final int FAILURE_UNIT_HEADER_SIZE = 16;
    private static final int UNIT_EXTENT_RECORD_SIZE = 8;
    /**
     * The local vision comparison mask of the cases: the bit of player {@code 0}.
     */
    private static final int LOCAL_PLAYER_MASK = 1;
    /**
     * The player update countdown, the unit status refresh countdown and the unit status refresh cycle the computer-player scheduler starts with; its
     * other counters start at zero.
     */
    private static final int PLAYER_UPDATE_COUNTDOWN = 60;
    private static final int UNIT_STATUS_REFRESH_COUNTDOWN = 15;
    private static final int UNIT_STATUS_REFRESH_CYCLE = 6;
    /**
     * The primary and secondary order redistribution timers of the unit phase.
     */
    private static final int PRIMARY_REDISTRIBUTION_TIMER = 10;
    private static final int SECONDARY_REDISTRIBUTION_TIMER = 150;
    /**
     * The number of display refresh values and of map visibility controls.
     */
    private static final int DISPLAY_REFRESH_VALUE_COUNT = 6;
    private static final int VISIBILITY_CONTROL_COUNT = 5;
    /**
     * The simulation frame duration the replay owners are populated with.
     */
    private static final int FRAME_DURATION = 42;
    /**
     * The frame phase index before the first update of the remaining-frame loop and the frame phase index after the loop.
     */
    private static final int LOOP_START_PHASE_INDEX = 3;
    private static final int FINAL_PHASE_INDEX = 9;
    /**
     * The map version of an expansion map saved by the remastered editor.
     */
    private static final int REMASTERED_MAP_VERSION = 206;
    /**
     * The SHA-256 digest of the native local collision candidates collected in the map unit loading cases.
     */
    private static final @NotNull String LOCAL_COLLISION_COLLECTED_HASH = "1b9a2bfebc07715c66f9edc279ebcfef19a45b6c1cde3ae69b3dd8b57164d65e";
    /**
     * The SHA-256 digest of the native local collision results after the final update of the map unit loading cases.
     */
    private static final @NotNull String LOCAL_COLLISION_FINAL_HASH = "501aba2165541a11172b2fea0a4772fc0835e158b733306974ce6f3a84f5ee56";
    /**
     * The digest algorithm of the capture hashes.
     */
    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";

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


    @Test
    void matches_native_unit_section_routing_camera_and_start_cases() throws IOException {
        int[][] inputs = {{214, 0, 320, 640}, {214, 3, 3808, 3792}, {214, 9, 32, 64},
                          {176, 11, 512, 768}, {177, 0, 576, 768}, {178, 11, 640, 768}, {188, 11, 704, 768},
                          {0, 0, 1024, 1024}, {0, 11, 1024, 1024}, {89, 11, 1088, 1088}, {91, 11, 1152, 1152}};
        ByteBuffer sectionBytes = ByteBuffer.allocate(SECTION_HEADER_SIZE + inputs.length * UNIT_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        sectionBytes.put(UNIT_SECTION.getBytes(StandardCharsets.US_ASCII)).putInt(inputs.length * UNIT_RECORD_SIZE);

        for (int recordIndex = 0; recordIndex < inputs.length; recordIndex++) {
            int offset = SECTION_HEADER_SIZE + recordIndex * UNIT_RECORD_SIZE;

            sectionBytes.putShort(offset + RECORD_X_OFFSET, (short) inputs[recordIndex][INPUT_X]);
            sectionBytes.putShort(offset + RECORD_Y_OFFSET, (short) inputs[recordIndex][INPUT_Y]);
            sectionBytes.putShort(offset + RECORD_UNIT_TYPE_OFFSET, (short) inputs[recordIndex][INPUT_UNIT_TYPE]);
            sectionBytes.put(offset + RECORD_OWNER_OFFSET, (byte) inputs[recordIndex][INPUT_OWNER]);
        }

        ChkFile allRecords = ChkFile.parseTolerant(sectionBytes.array());
        byte[] startsOnly = Arrays.copyOf(sectionBytes.array(), SECTION_HEADER_SIZE + START_RECORD_COUNT * UNIT_RECORD_SIZE);

        ByteBuffer.wrap(startsOnly).order(ByteOrder.LITTLE_ENDIAN).putInt(SECTION_SIZE_OFFSET, START_RECORD_COUNT * UNIT_RECORD_SIZE);

        ChkFile startRecords = ChkFile.parseTolerant(startsOnly);
        byte[] capture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_UNIT_SECTION);
        ByteBuffer expected = ByteBuffer.wrap(capture).order(ByteOrder.LITTLE_ENDIAN);
        int cases = 0;

        while (expected.hasRemaining()) {
            boolean restricted = expected.getInt() != 0;
            boolean mapSettings = expected.getInt() != 0;
            boolean replayCamera = expected.getInt() != 0;
            int previousCamera = expected.getInt();
            int playerType = expected.getInt();
            int expectedRequests = expected.getInt();
            int[] expectedStarts = new int[START_POSITION_WORDS];

            for (int coordinate = 0; coordinate < START_POSITION_WORDS; coordinate++) {
                expectedStarts[coordinate] = Short.toUnsignedInt(expected.getShort());
            }

            int[] expectedCamera = {Short.toUnsignedInt(expected.getShort()), Short.toUnsignedInt(expected.getShort())};
            Game game = Game.create(gameData,
                                    new GameMap(UNIT_SECTION_MAP_SIZE, UNIT_SECTION_MAP_SIZE, 0,
                                                new int[UNIT_SECTION_MAP_SIZE * UNIT_SECTION_MAP_SIZE],
                                                new int[UNIT_SECTION_MAP_SIZE * UNIT_SECTION_MAP_SIZE]),
                                    new LcgRandom(0));

            for (int playerIndex = 0; playerIndex < PlayerTable.PLAYER_SLOTS; playerIndex++) {
                game.getPlayerTable().setPlayer(playerIndex, new Player(playerIndex, playerIndex, playerType, 0, 0));
            }

            game.getUnitSystem().initializeUnitPool(1);

            SightCoverage sightCoverage = new SightCoverage(UNIT_SECTION_MAP_SIZE);

            sightCoverage.setSharedVisionMasks(new int[PlayerTable.PLAYER_SLOTS]);
            game.setSightCoverage(sightCoverage);
            game.getLocalVision().setComparisonMask(LOCAL_PLAYER_MASK);

            boolean[] melee = new boolean[PlayerTable.PLAYABLE_PLAYER_SLOTS];

            melee[0] = true;

            GameSettings settings = new GameSettings(mapSettings ? 0 : NON_MAP_SETTINGS_VICTORY, 0, 0, 0, melee, 0);
            int requests = 0;
            List<ChkUnit> records = allRecords.getUnits();

            for (int recordIndex = 0; recordIndex < records.size(); recordIndex++) {
                if (MapUnitLoader.requestsCreation(game, records.get(recordIndex), settings, restricted)) {
                    requests |= 1 << recordIndex;
                }
            }

            Assertions.assertEquals(expectedRequests, requests, "routing case=" + cases);

            int[] starts = new int[START_POSITION_WORDS];

            Arrays.fill(starts, START_POSITION_FILL);

            int[] camera = {previousCamera, PREVIOUS_CAMERA_Y};

            ChkSection section = startRecords.getSections(UNIT_SECTION).getFirst();
            List<ChkUnit> occurrenceRecords = startRecords.getUnits();

            Assertions.assertTrue(MapUnitLoader.load(new UnitCreationContext(false, false, false, false, true, UNIT_SECTION_LOCAL_PLAYER), game,
                                                     section,
                                                     occurrenceRecords,
                                                     settings,
                                                     starts,
                                                     camera,
                                                     RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                     replayCamera,
                                                     restricted,
                                                     true,
                                                     UNIT_SECTION_INSTRUCTION_LIMIT).isEmpty());
            Assertions.assertArrayEquals(expectedStarts, starts, "starts case=" + cases);
            Assertions.assertArrayEquals(expectedCamera, camera, "camera case=" + cases);
            Assertions.assertEquals(1, game.getBuildingPowerRefresh());

            cases++;
        }

        Assertions.assertEquals(144, cases);
    }

    @Test
    void replay_map_initialization_creates_units_and_matches_native_resource_areas() throws Exception {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_ZVP_FIGHTING_SPIRIT)));
        byte[] mapData = replay.getMapData();

        Assertions.assertNotNull(mapData);

        ChkFile mapFile = ChkFile.parseTolerant(mapData);

        Assertions.assertNotNull(gameData);

        TerrainTileset terrain = gameData.getTerrainTileset(mapFile.getTileset());
        int[] sharing = new int[PlayerTable.PLAYER_SLOTS];

        for (int playerIndex = 0; playerIndex < sharing.length; playerIndex++) {
            sharing[playerIndex] = 1 << playerIndex;
        }

        GameData resources = gameData;
        LcgRandom random = new LcgRandom(0);
        UnitCreationContext creationContext = new UnitCreationContext(false, true, true, false, true, 0);

        Game game = ReplayLoaderUtils.createInitializedReplayWorld(creationContext,
                                                                   replay, mapFile,
                                                                   resources,
                                                                   random,
                                                                   new RuntimeRandom(RUNTIME_RANDOM_SEED),
                                                                   sharing,
                                                                   LOCAL_PLAYER_MASK,
                                                                   RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                                   INSTRUCTION_LIMIT);
        GameMap map = game.getMap();
        int[] starts = new int[START_POSITION_WORDS];

        for (StartLocation location : game.getPlayerTable().getStartLocations()) {
            starts[location.getOwner() * POSITION_COMPONENT_COUNT]     = location.getX();
            starts[location.getOwner() * POSITION_COMPONENT_COUNT + 1] = location.getY();
        }

        List<MapSpriteEffectLoader.Creation> thingyEvents = game.getMapSpriteEffectCreations();

        Assertions.assertEquals(84, thingyEvents.size());

        SpriteEffectPool spriteEffects = game.getSpriteSystem().getSpriteEffectPool();

        Assertions.assertNotNull(spriteEffects);

        Assertions.assertEquals(85, spriteEffects.getAllocatedCount());

        List<MapUnitLoader.Creation> events = game.getMapUnitCreations();

        Assertions.assertEquals(MAP_UNIT_CREATION_COUNT, events.size());

        for (int eventIndex = 0; eventIndex < events.size(); eventIndex++) {
            MapUnitLoader.Creation event = events.get(eventIndex);
            int expectedIndex = eventIndex == 0 ? 1 : replay.getLimits().getUnitCapacity() - eventIndex + 1;

            Assertions.assertEquals(expectedIndex, event.getPoolIndex());
            Assertions.assertEquals(1, event.getGeneration());
            Assertions.assertEquals(11, event.getOwner());
            Assertions.assertTrue(event.getRandomCallsAfter() >= event.getRandomCallsBefore());
        }

        Assertions.assertEquals(3808, starts[0]);
        Assertions.assertEquals(272, starts[1]);
        Assertions.assertEquals(3808, starts[6]);
        Assertions.assertEquals(3792, starts[7]);

        int[] expectedTypes = {154, 64, 64, 64, 64, 131, 35, 35, 42, 41, 41, 41, 41};

        UnitPool units = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(units);

        Assertions.assertEquals(CREATED_UNIT_COUNT, units.getAllocatedCount());

        for (int creationIndex = 0; creationIndex < expectedTypes.length; creationIndex++) {
            int poolIndex = replay.getLimits().getUnitCapacity() - (MAP_UNIT_CREATION_COUNT - 1) - creationIndex;
            Unit unit = units.getUnit(poolIndex);

            Assertions.assertEquals(expectedTypes[creationIndex], unit.getUnitId());
            Assertions.assertEquals(creationIndex < PROTOSS_STARTING_UNIT_COUNT ? 3 : 0, unit.getOwnerIndex());
            Assertions.assertEquals(1, units.getGeneration(poolIndex));
        }

        ByteBuffer snapshot = ByteBuffer.allocate(SNAPSHOT_HEADER_SIZE + CREATED_UNIT_COUNT * (UNIT_SNAPSHOT_HEADER_SIZE + UNIT_CAPTURE_SIZE))
                                        .order(ByteOrder.LITTLE_ENDIAN);
        StringBuilder spriteSnapshot = new StringBuilder("{\"scope\":\"Explicit initialization fixture; not original runtime capture\",\"units\":[");

        snapshot.putInt(CREATED_UNIT_COUNT).putInt(game.getRandom().getState()).putInt(game.getRandom().getTotalRandomCounts());

        snapshot.putInt(game.getRuntimeRandom().getState());

        for (int creationIndex = 0; creationIndex < CREATED_UNIT_COUNT; creationIndex++) {
            int poolIndex = creationIndex == 0 ? 1 : replay.getLimits().getUnitCapacity() - creationIndex + 1;
            Unit unit = units.getUnit(poolIndex);
            Unit parent = unit.getParentUnit();
            Unit subunit = unit.getSubunit();

            snapshot.putInt(poolIndex);
            snapshot.putInt(units.getGeneration(poolIndex))
                    .putInt(parent == null ? 0 : parent.getIndex()).putInt(subunit == null ? 0 : subunit.getIndex());

            Sprite sprite = unit.getSpriteOrNull();

            Assertions.assertNotNull(sprite);

            snapshot.putInt(sprite.getX());
            snapshot.putInt(sprite.getY()).put(UnitMemoryScenario.of(unit).exportMemoryBytes());

            if (creationIndex != 0) {
                spriteSnapshot.append(',');
            }

            spriteSnapshot.append("{\"poolIndex\":").append(poolIndex).append(",\"spriteType\":").append(sprite.getSpriteTypeId())
                          .append(",\"elevation\":").append(sprite.getElevationLevel()).append(",\"flags\":").append(sprite.getFlags())
                          .append(",\"owner\":").append(sprite.getOwner()).append(",\"images\":[");

            for (int imageIndex = 0; imageIndex < sprite.getImages().size(); imageIndex++) {
                SpriteImage image = sprite.getImages().get(imageIndex);

                if (imageIndex != 0) {
                    spriteSnapshot.append(',');
                }

                spriteSnapshot.append("{\"type\":").append(image.getImageTypeId()).append(",\"flags\":").append(image.getFlags())
                              .append(",\"programCounter\":").append(image.getProgramCounter())
                              .append(",\"wait\":").append(image.getWait()).append('}');
            }

            spriteSnapshot.append("]}");
        }

        // Diagnostic snapshots for the native comparison tools go to the build directory, never into the source tree.
        Path snapshotPath = Path.of("build", "test-output", "map-unit-loader", "native-initialization.bin");

        Files.createDirectories(snapshotPath.getParent());
        Files.write(snapshotPath, snapshot.array());
        Files.writeString(snapshotPath.resolveSibling("native-initialization-sprites.json"), spriteSnapshot.append("]}\n"));

        int[] playerTypes = new int[PlayerTable.PLAYABLE_PLAYER_SLOTS];
        ByteBuffer areaInput = ByteBuffer.allocate(AREA_INPUT_HEADER_SIZE + CREATED_UNIT_COUNT * Integer.BYTES +
                                                   map.getTileCount() * AREA_INPUT_TILE_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        areaInput.putInt(map.getWidth()).putInt(map.getHeight()).putInt(CREATED_UNIT_COUNT).putInt(game.getRandom().getState());

        for (int coordinate : starts) {
            areaInput.putShort((short) coordinate);
        }

        for (int playerIndex = 0; playerIndex < playerTypes.length; playerIndex++) {
            playerTypes[playerIndex] = replay.getHeader().getPlayers().get(playerIndex).getType();
            areaInput.put((byte) playerTypes[playerIndex]);
        }

        for (int poolIndex = units.getFirstCreatedIndex(true); poolIndex != 0; poolIndex = units.getNextCreatedIndex(poolIndex)) {
            areaInput.putInt(poolIndex);
        }

        for (int tileIndex = 0; tileIndex < map.getTileCount(); tileIndex++) {
            areaInput.putShort((short) map.getTileIdentifier(tileIndex));
        }

        for (int tileIndex = 0; tileIndex < map.getTileCount(); tileIndex++) {
            areaInput.putShort((short) map.getOverlayTileIdentifier(tileIndex));
        }

        Files.write(snapshotPath.resolveSibling("native-resource-area-input.bin"), areaInput.array());

        byte[] expectedAreas = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_RESOURCE_AREAS);

        Assertions.assertEquals(RESOURCE_AREA_TABLE_SIZE * EXPECTED_AREA_TABLE_COUNT + CREATED_UNIT_COUNT, expectedAreas.length);

        ResourceAreas areas = new ResourceAreas(game, terrain);

        areas.initialize(starts, playerTypes);
        game.setResourceAreas(areas);

        Assertions.assertEquals(13, areas.getAreaCount());
        ResourceAreaAssert.assertKnownFields(Arrays.copyOfRange(expectedAreas, 0, RESOURCE_AREA_TABLE_SIZE), areas);

        Files.write(snapshotPath.resolveSibling("native-resource-areas-java.bin"), ResourceAreaAssert.createSnapshot(areas));

        byte[] resourceAssignments = new byte[CREATED_UNIT_COUNT];

        for (int creationIndex = 0; creationIndex < CREATED_UNIT_COUNT; creationIndex++) {
            int poolIndex = creationIndex == 0 ? 1 : replay.getLimits().getUnitCapacity() - creationIndex + 1;

            resourceAssignments[creationIndex] = (byte) units.getUnit(poolIndex).getResourceAreaIndex();
        }

        Files.write(snapshotPath.resolveSibling("native-resource-assignments-java.bin"), resourceAssignments);

        Assertions.assertArrayEquals(Arrays.copyOfRange(expectedAreas, RESOURCE_AREA_TABLE_SIZE, RESOURCE_AREA_TABLE_SIZE + CREATED_UNIT_COUNT),
                                     resourceAssignments);

        TerrainContours regionContours = game.getPathSystem().getTerrainContours();

        Assertions.assertNotNull(regionContours);

        int regionCount = regionContours.getRegions().getRegionCount();
        ComputerPlayerPhase computerPhase = new ComputerPlayerPhase(game,
                                                                    regionCount,
                                                                    new int[]{PLAYER_UPDATE_COUNTDOWN,
                                                                              UNIT_STATUS_REFRESH_COUNTDOWN,
                                                                              UNIT_STATUS_REFRESH_CYCLE,
                                                                              0, 0, 0, 0, 0},
                                                                    new int[regionCount * PlayerTable.PLAYABLE_PLAYER_SLOTS]);

        game.setComputerPlayerPhase(computerPhase);

        Assertions.assertFalse(game.hasInitializedWorldState());

        UnitPhase.Context phaseContext = new UnitPhase.Context(PRIMARY_REDISTRIBUTION_TIMER, SECONDARY_REDISTRIBUTION_TIMER);

        phaseContext.initializeLurkerHitHistory();
        phaseContext.getDisplayState().initializeDisplayRefresh(new long[DISPLAY_REFRESH_VALUE_COUNT]);

        UnitRegistry replayRegistry = game.getUnitSystem().getUnitRegistry();

        Assertions.assertNotNull(replayRegistry);

        replayRegistry.populateReplayOwners(game, FRAME_DURATION);
        game.getUnitSystem().setUnitCreationContext(new UnitCreationContext(false, true, true, false, true, 0));

        CreepRecession recession = new CreepRecession(0, CreepRecession.RECORD_COUNT, new int[CreepRecession.LIST_COUNT]);

        game.setCreepRecession(recession);

        MapVisibility visibility = new MapVisibility(terrain, new int[VISIBILITY_CONTROL_COUNT]);
        ProjectilePhase.Context projectileContext = new ProjectilePhase.Context();

        projectileContext.initializeEmptyLists();

        int secondarySpriteEffectLimit = replay.getLimits().getEffectiveValues()[GameSetup.SECONDARY_SPRITE_EFFECT_LIMIT];
        SpriteEffectPool secondarySpriteEffects = new SpriteEffectPool(secondarySpriteEffectLimit);
        GameExecutionScope executionScope = game.getExecutionScope();

        Assertions.assertNotNull(executionScope);

        executionScope.finishInitialization();

        LocalPathSearch movementSearch = game.getPathSystem().getLocalPathSearch();

        Assertions.assertNotNull(movementSearch);

        movementSearch.initializeMovementContext(false, 0);

        GameFrame frame = new GameFrame(phaseContext, new UnitMovement.Context(0, 0), projectileContext, secondarySpriteEffects, visibility);

        game.setFrame(frame);
        frame.update(game, 0, false, false, new int[]{0, 0, RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT},
                     INSTRUCTION_LIMIT);

        Assertions.assertEquals(1, frame.getPhaseIndex());
        ResourceAreaAssert.assertKnownFields(Arrays.copyOfRange(expectedAreas, RESOURCE_AREA_TABLE_SIZE + CREATED_UNIT_COUNT, expectedAreas.length),
                                             areas);

        Files.write(snapshotPath.resolveSibling("native-resource-areas-updated-java.bin"), ResourceAreaAssert.createSnapshot(areas));

        Assertions.assertEquals(136, units.getAllocatedCount());
        Assertions.assertEquals(0, replayRegistry.getProductionEventCount());
        Assertions.assertTrue(replayRegistry.hasPendingSubscriberEffects());

        byte[] pathTargets = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_PATH_TARGETS);
        ByteBuffer targets = ByteBuffer.wrap(pathTargets).order(ByteOrder.LITTLE_ENDIAN);

        Assertions.assertEquals(PATH_TARGET_CASE_COUNT * PATH_TARGET_CASE_SIZE, targets.remaining());

        Unit searchingLarva = units.getUnit(LARVA_POOL_INDEX);

        while (targets.hasRemaining()) {
            int positionX = targets.getInt();
            int positionY = targets.getInt();
            int expectedIndex = targets.getInt();
            UnitSpatialIndex targetIndex = game.getUnitSystem().getUnitSpatialIndex();

            Assertions.assertNotNull(targetIndex);

            Unit target = targetIndex.findPathTarget(game.getHooks().getCollisionRules(),
                                                     searchingLarva, gameData.getUnitTypes(),
                                                     positionX, positionY, 0, 0);

            Assertions.assertEquals(expectedIndex, target == null ? 0 : target.getIndex(), "position=" + positionX + "," + positionY);
        }

        byte[] collisionCases = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_LOCAL_COLLISION_CASES);
        ByteBuffer expectedCollision = ByteBuffer.wrap(collisionCases).order(ByteOrder.LITTLE_ENDIAN);
        PathObstacleCollector caseCollision = new PathObstacleCollector();

        caseCollision.initialize(game,
                                 units.getUnit(LARVA_POOL_INDEX),
                                 0,
                                 COLLISION_DESTINATION_X, COLLISION_DESTINATION_Y,
                                 COLLISION_EXCLUSION_RECTANGLE);
        caseCollision.initializeMovementContext(false, 0);

        for (int caseIndex = 0; caseIndex < COLLISION_CASE_COUNT; caseIndex++) {
            int positionX = expectedCollision.getInt();
            int positionY = expectedCollision.getInt();
            int directions = expectedCollision.getInt();
            int candidateCount = expectedCollision.getInt();
            int segmentCount = expectedCollision.getInt();

            caseCollision.collect(positionX, positionY, directions);

            Assertions.assertEquals(candidateCount, caseCollision.getCandidateCount(), "collision case=" + caseIndex);
            Assertions.assertEquals(segmentCount, caseCollision.getSegmentCount(), "collision case=" + caseIndex);

            for (int candidateIndex = 0; candidateIndex < candidateCount; candidateIndex++) {
                long candidate = Integer.toUnsignedLong(caseCollision.getCandidatePosition(candidateIndex)) |
                                 (long) caseCollision.getCandidateDirections(candidateIndex) << CANDIDATE_DIRECTIONS_SHIFT |
                                 (long) caseCollision.getCandidateTerminal(candidateIndex) << CANDIDATE_TERMINAL_SHIFT;

                Assertions.assertEquals(expectedCollision.getLong(), candidate, "collision case=" + caseIndex + ",candidate=" + candidateIndex);
            }

            for (int segmentIndex = 0; segmentIndex < segmentCount; segmentIndex++) {
                Assertions.assertEquals(expectedCollision.getLong(), caseCollision.getSegment(segmentIndex),
                                        "collision case=" + caseIndex + ",segment=" + segmentIndex);
            }
        }

        Assertions.assertFalse(expectedCollision.hasRemaining());

        frame.update(game, 0, false, false, new int[]{0, 0, RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT},
                     INSTRUCTION_LIMIT);

        byte[] expectedLocalCollision = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_LOCAL_COLLISION_COLLECTED);

        Assertions.assertEquals(LOCAL_COLLISION_COLLECTED_HASH,
                                HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST_ALGORITHM).digest(expectedLocalCollision)));

        ByteBuffer expectedCollection = ByteBuffer.wrap(expectedLocalCollision).order(ByteOrder.LITTLE_ENDIAN);
        Unit movingUnit = units.getUnit(LARVA_POOL_INDEX);
        PathObstacleCollector localCollision = new PathObstacleCollector();

        localCollision.initializeMovementContext(false, 0);
        localCollision.initialize(game, movingUnit, 0, COLLISION_DESTINATION_X, COLLISION_DESTINATION_Y, COLLISION_EXCLUSION_RECTANGLE);
        localCollision.collectBoundaries(COLLISION_POSITION_X, COLLISION_POSITION_Y, COLLISION_DIRECTIONS);
        localCollision.collectCardinalCandidates(COLLISION_POSITION_X, COLLISION_POSITION_Y, COLLISION_DIRECTIONS);

        for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
            int count = expectedCollection.getShort(BOUNDARY_COUNTS_OFFSET + direction * Short.BYTES);

            Assertions.assertEquals(count, localCollision.getBoundaryCount(direction), "Local contour direction " + direction);

            for (int index = 0; index < count; index++) {
                int boundaryOffset = BOUNDARIES_OFFSET + direction * BOUNDARY_DIRECTION_STRIDE + index * BOUNDARY_RECORD_SIZE;

                Assertions.assertEquals(expectedCollection.getLong(boundaryOffset), localCollision.getBoundary(direction, index),
                                        "Local contour record " + direction + ":" + index);
            }
        }

        Assertions.assertEquals(expectedCollection.getShort(CANDIDATE_COUNT_OFFSET), localCollision.getCandidateCount());

        for (int index = 0; index < localCollision.getCandidateCount(); index++) {
            int candidateOffset = index * CANDIDATE_RECORD_SIZE;

            Assertions.assertEquals(expectedCollection.getInt(CANDIDATE_POSITION_OFFSET + candidateOffset),
                                    localCollision.getCandidatePosition(index));
            Assertions.assertEquals(Byte.toUnsignedInt(expectedLocalCollision[CANDIDATE_DIRECTIONS_OFFSET + candidateOffset]),
                                    localCollision.getCandidateDirections(index));
            Assertions.assertEquals(Byte.toUnsignedInt(expectedLocalCollision[CANDIDATE_TERMINAL_OFFSET + candidateOffset]),
                                    localCollision.getCandidateTerminal(index));
        }

        byte[] expectedFinalCollision = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_LOCAL_COLLISION_FINAL);

        Assertions.assertEquals(LOCAL_COLLISION_FINAL_HASH,
                                HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST_ALGORITHM).digest(expectedFinalCollision)));

        ByteBuffer expectedFinal = ByteBuffer.wrap(expectedFinalCollision).order(ByteOrder.LITTLE_ENDIAN);

        localCollision.collect(COLLISION_POSITION_X, COLLISION_POSITION_Y, COLLISION_DIRECTIONS);

        Assertions.assertEquals(expectedFinal.getShort(CANDIDATE_COUNT_OFFSET), localCollision.getCandidateCount());

        for (int index = 0; index < localCollision.getCandidateCount(); index++) {
            int candidateOffset = index * CANDIDATE_RECORD_SIZE;

            Assertions.assertEquals(expectedFinal.getInt(CANDIDATE_POSITION_OFFSET + candidateOffset), localCollision.getCandidatePosition(index),
                                    "Candidate " + index);
            Assertions.assertEquals(Byte.toUnsignedInt(expectedFinalCollision[CANDIDATE_DIRECTIONS_OFFSET + candidateOffset]),
                                    localCollision.getCandidateDirections(index),
                                    "Directions " + index);
            Assertions.assertEquals(Byte.toUnsignedInt(expectedFinalCollision[CANDIDATE_TERMINAL_OFFSET + candidateOffset]),
                                    localCollision.getCandidateTerminal(index),
                                    "Terminal " + index);
        }

        Assertions.assertEquals(expectedFinal.getShort(SEGMENT_COUNT_OFFSET), localCollision.getSegmentCount());

        for (int index = 0; index < localCollision.getSegmentCount(); index++) {
            Assertions.assertEquals(expectedFinal.getLong(SEGMENTS_OFFSET + index * SEGMENT_RECORD_SIZE), localCollision.getSegment(index));
        }

        Assertions.assertTrue(movingUnit.getPathIndex() >= 0);

        byte[] initialPath = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_MOVEMENT_PATH);

        PathPool initialPaths = game.getPathSystem().getPathPool();

        Assertions.assertNotNull(initialPaths);

        PathPoolAssert.assertRecordMatches(initialPath, 0, initialPaths, movingUnit.getPathIndex());

        Files.write(snapshotPath.resolveSibling("native-movement-state25-unit.bin"), UnitMemoryScenario.of(movingUnit).exportMemoryBytes());

        Path movingOutput = snapshotPath.resolveSibling("native-movement-state25-unit.json");

        String movingPrefix = "{\"scope\":\"Java explicit phase fixture before phase2, not original process\",\"poolIndex\":1572,\"pathIndex\":" +
                              movingUnit.getPathIndex() + ",\"spriteX\":";

        Sprite movingSprite = movingUnit.getSpriteOrNull();

        Assertions.assertNotNull(movingSprite);

        String movingHorizontal = movingPrefix + movingSprite.getX() + ",\"spriteY\":";
        String movingVertical = movingHorizontal + movingSprite.getY() + ",\"spriteFlags\":";

        Files.writeString(movingOutput, movingVertical + movingSprite.getFlags() + "}\n");

        frame.update(game, 0, false, false, new int[]{0, 0, RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT},
                     INSTRUCTION_LIMIT);

        Unit adjustedUnit = units.getUnit(ADJUSTED_UNIT_POOL_INDEX);

        Assertions.assertTrue(adjustedUnit.getPathIndex() >= 0);

        byte[] adjustedPath = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_ADJUSTED_MOVEMENT_PATH);

        PathPool adjustedPaths = game.getPathSystem().getPathPool();

        Assertions.assertNotNull(adjustedPaths);

        PathPoolAssert.assertRecordMatches(adjustedPath, 0, adjustedPaths, adjustedUnit.getPathIndex());

        Assertions.assertEquals(0, adjustedPaths.getTimer(adjustedUnit.getPathIndex()));

        for (int phaseIndex = LOOP_START_PHASE_INDEX; phaseIndex < FINAL_PHASE_INDEX; phaseIndex++) {
            try {
                frame.update(game, 0, false, false, new int[]{0, 0, RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT},
                             INSTRUCTION_LIMIT);
            } catch (UnsupportedOperationException exception) {
                Files.write(snapshotPath.resolveSibling("native-movement-failure-adjusted-unit.bin"),
                            UnitMemoryScenario.of(units.getUnit(ADJUSTED_UNIT_POOL_INDEX)).exportMemoryBytes());

                TerrainContours collision = game.getPathSystem().getTerrainContours();

                Assertions.assertNotNull(collision);

                int boundaryCount = 0;

                for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
                    boundaryCount += collision.getBoundaryCount(direction);
                }

                ByteBuffer collisionBytes = ByteBuffer.allocate(DIRECTION_COUNT * Integer.BYTES + CONTOUR_CONTROL_COUNT * Short.BYTES +
                                                                boundaryCount * BOUNDARY_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);

                for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
                    collisionBytes.putInt(collision.getBoundaryCount(direction));
                }

                for (int controlIndex = 0; controlIndex < CONTOUR_CONTROL_COUNT; controlIndex++) {
                    collisionBytes.putShort((short) collision.getControl(controlIndex));
                }

                for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
                    for (int boundaryIndex = 0; boundaryIndex < collision.getBoundaryCount(direction); boundaryIndex++) {
                        collisionBytes.putLong(collision.getBoundary(direction, boundaryIndex));
                    }
                }

                Files.write(snapshotPath.resolveSibling("native-movement-failure-contours.bin"), collisionBytes.array());

                UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();

                Assertions.assertNotNull(spatialIndex);

                ByteBuffer spatialBytes = ByteBuffer.allocate(Integer.BYTES + spatialIndex.getCount() * SPATIAL_ENTRY_SIZE)
                                                    .order(ByteOrder.LITTLE_ENDIAN);

                spatialBytes.putInt(spatialIndex.getCount());

                for (int axis = 0; axis < AXIS_COUNT; axis++) {
                    for (int entryIndex = 0; entryIndex < spatialIndex.getCount(); entryIndex++) {
                        spatialBytes.putInt(spatialIndex.getUnitIndex(axis == 0, entryIndex));
                        spatialBytes.putInt(spatialIndex.getCoordinate(axis == 0, entryIndex));
                    }
                }

                Files.write(snapshotPath.resolveSibling("native-movement-failure-spatial.bin"), spatialBytes.array());

                ByteBuffer unitBytes = ByteBuffer.allocate(Integer.BYTES +
                                                           units.getAllocatedCount() * (FAILURE_UNIT_HEADER_SIZE + UNIT_CAPTURE_SIZE))
                                                 .order(ByteOrder.LITTLE_ENDIAN);

                unitBytes.putInt(units.getAllocatedCount());

                for (int unitIndex = 1; unitIndex <= units.getCapacity(); unitIndex++) {
                    if (!units.isAllocated(unitIndex)) {
                        continue;
                    }

                    Unit unit = units.getUnit(unitIndex);

                    unitBytes.putInt(unitIndex);
                    unitBytes.putInt(unit.getSpriteOrNull() == null ? 0 : unit.getSpriteOrNull().getX());
                    unitBytes.putInt(unit.getSpriteOrNull() == null ? 0 : unit.getSpriteOrNull().getY());
                    unitBytes.putInt(unit.getSpriteOrNull() == null ? -1 : unit.getSpriteOrNull().getFlags());
                    unitBytes.put(UnitMemoryScenario.of(unit).exportMemoryBytes());
                }

                Files.write(snapshotPath.resolveSibling("native-movement-failure-units.bin"), unitBytes.array());

                ByteBuffer extentBytes = ByteBuffer.allocate(UnitsDat.UNIT_COUNT * UNIT_EXTENT_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);

                for (int unitType = 0; unitType < UnitsDat.UNIT_COUNT; unitType++) {
                    extentBytes.putShort((short) gameData.getUnits().get("Unit Size Left", unitType));
                    extentBytes.putShort((short) gameData.getUnits().get("Unit Size Up", unitType));
                    extentBytes.putShort((short) gameData.getUnits().get("Unit Size Right", unitType));
                    extentBytes.putShort((short) gameData.getUnits().get("Unit Size Down", unitType));
                }

                Files.write(snapshotPath.resolveSibling("native-movement-failure-extents.bin"), extentBytes.array());

                Unit larva = units.getUnit(LARVA_POOL_INDEX);

                Files.write(snapshotPath.resolveSibling("native-movement-failure-unit.bin"), UnitMemoryScenario.of(larva).exportMemoryBytes());

                Unit target = larva.getMovementTargetUnit();

                Path failedOutput = snapshotPath.resolveSibling("native-movement-failure-unit.json");

                String failedPrefix = "{\"scope\":\"Java explicit phase fixture, not original process\",\"phase\":" + phaseIndex +
                                      ",\"poolIndex\":1572,\"spriteX\":";

                Sprite failedSprite = larva.getSpriteOrNull();

                Assertions.assertNotNull(failedSprite);

                String failedHorizontal = failedPrefix + failedSprite.getX() + ",\"spriteY\":";
                String failedVertical = failedHorizontal + failedSprite.getY() + ",\"spriteFlags\":";

                Files.writeString(failedOutput, failedVertical + failedSprite.getFlags() +
                                                ",\"movementTargetIndex\":" + (target == null ? 0 : target.getIndex()) + "}\n");

                throw exception;
            }
        }

        Assertions.assertEquals(9, frame.getPhaseIndex());
    }

    @Test
    void version_206_map_starts_with_add_ons_owned_by_their_buildings_owner() throws IOException {
        Path path = GameTestResources.getPath(GameTestResources.REPLAY_UMS_SURVIVAL);

        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(path));
        byte[] mapData = replay.getMapData();

        Assertions.assertNotNull(mapData);

        ChkFile map = ChkFile.parseTolerant(mapData);

        Assertions.assertEquals(REMASTERED_MAP_VERSION, MapSettingsLoaderUtils.readVersion(map));

        try (GameData loadedData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = new ReplayRuntime(replay, map, loadedData,
                                                      RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                      RunningGameScenario.INSTRUCTION_LIMIT);

            runtime.start(0, 0);

            Assertions.assertNull(runtime.getFailure());

            int linkedAddons = 0;
            long linkedRecords = map.getUnits().stream().filter(unit -> unit.getLinkedSerial() != 0 &&
                                                                        unit.getUnitId() >= ScrUnitIds.TERRAN_COMMAND_CENTER &&
                                                                        unit.getUnitId() <= ScrUnitIds.TERRAN_MACHINE_SHOP).count();
            UnitPool pool = runtime.getGame().getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            for (int index = pool.getFirstCreatedIndex(true); index != 0; index = pool.getNextCreatedIndex(index)) {
                Unit unit = pool.getUnit(index);

                if (unit.getAddon() != null) {
                    linkedAddons++;

                    Assertions.assertEquals(unit.getOwnerIndex(), unit.getAddon().getOwnerIndex(), "An add-on belongs to its building's owner.");
                }
            }

            Assertions.assertTrue(linkedRecords == 0 || linkedAddons > 0, "Linked UNIT records connect add-ons: " + linkedRecords);
        }
    }
}
