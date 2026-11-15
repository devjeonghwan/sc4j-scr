package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.resource.ResourceAreas;
import org.fogofwarlabs.sc4j.scr.game.save.SaveReferences;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainContours;
import org.fogofwarlabs.sc4j.scr.game.test.GameResourcesExtension;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.InstallationTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.SharedReplayRunScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;

/**
 * Checks scheduler counters, region strength, independent state snapshots, and the computer saved-format bytes, owned buffer ranges and
 * retained prefix after a typed restoration fails, and the computer orders, unit status and random state of a recorded Terran-versus-computer replay.
 */
final class ComputerPlayerPhaseTest {
    /**
     * The SHA-256 digest of the native computer-player phase scheduler capture.
     */
    private static final @NotNull String SCHEDULER_CAPTURE_HASH = "a0823eeb275e7cc4031c525dabae5ee97de0dd31e13959a52aeb476ef31a3b3a";
    /**
     * The tileset of the scheduler map, Badlands.
     */
    private static final int SCHEDULER_TILESET = 0;
    /**
     * The width and the height in tiles of the scheduler map.
     */
    private static final int SCHEDULER_MAP_SIZE = 8;
    /**
     * The region counts of the scheduler cases.
     */
    private static final int @NotNull [] SCHEDULER_REGION_COUNTS = {7, 31, 447};
    /**
     * The scheduler cases of each region count.
     */
    private static final int SCHEDULER_CASE_COUNT = 128;
    /**
     * The unit capacity of the scheduler games.
     */
    private static final int SCHEDULER_UNIT_CAPACITY = 1700;
    /**
     * The values of the first scheduler counter; a case selects one value of each counter table by one base-4 digit of its index.
     */
    private static final int @NotNull [] FIRST_COUNTER_VALUES = {0, 1, 60, -1};
    /**
     * The values of the second scheduler counter.
     */
    private static final int @NotNull [] SECOND_COUNTER_VALUES = {0, 1, 15, 300};
    /**
     * The values of the third scheduler counter.
     */
    private static final int @NotNull [] THIRD_COUNTER_VALUES = {0, 1, 6, -1};
    /**
     * The values in each counter table, the base of the case index digits.
     */
    private static final int COUNTER_VALUE_COUNT = 4;
    /**
     * The case index bit that the fourth scheduler counter takes.
     */
    private static final int FOURTH_COUNTER_BIT = 64;
    /**
     * The case index bit that selects the alternative values of the fifth and the sixth scheduler counter.
     */
    private static final int ODD_CASE_BIT = 1;
    /**
     * The period of the fifth scheduler counter, which an odd case takes as its value.
     */
    private static final int FIFTH_COUNTER_PERIOD = 7;
    /**
     * The value from which an odd case subtracts its index for the sixth scheduler counter.
     */
    private static final int SIXTH_COUNTER_BASE = 2499;
    /**
     * The period of the seventh and the eighth scheduler counter.
     */
    private static final int LAST_COUNTER_PERIOD = 8;
    /**
     * The period of the human player slots of a case; the other slots are empty.
     */
    private static final int HUMAN_PLAYER_PERIOD = 3;
    /**
     * The regional strength step of one case.
     */
    private static final int CASE_STRENGTH_STEP = 1234567;
    /**
     * The regional strength step of one player.
     */
    private static final int PLAYER_STRENGTH_STEP = 65537;
    /**
     * The size of a unit record in the capture.
     */
    private static final int UNIT_RECORD_SIZE = 488;
    /**
     * The first unit offset that a case fills.
     */
    private static final int UNIT_FILL_START = 32;
    /**
     * The fill step of one unit.
     */
    private static final int UNIT_FILL_STEP = 17;
    /**
     * The unit offset of the marker value.
     */
    private static final int UNIT_MARKER_OFFSET = 0x140;
    /**
     * The marker value before the unit index is mixed into it.
     */
    private static final int UNIT_MARKER = 0xFEDCBA82;
    /**
     * The shift of the unit index that is mixed into the marker value.
     */
    private static final int UNIT_MARKER_SHIFT = 12;
    /**
     * The elapsed clock ticks of the scheduler updates.
     */
    private static final int @NotNull [] SCHEDULER_CLOCKS = {0, 359, 360, 4500, -1};
    /**
     * The seed of the scheduler state hashes.
     */
    private static final int STATE_HASH_SEED = 123;

    private static final int MILITARY_RECORD_SIZE = 0x14;
    private static final int GUARD_RECORD_SIZE = 0x20;
    private static final int BUILDING_RECORD_SIZE = 0x2C;
    private static final int TOWN_RECORD_SIZE = 0x1CC;
    private static final int RECORD_COUNT = 1000;
    private static final int TOWN_COUNT = 100;

    private static final int SCHEDULER_COUNTER_COUNT = 8;
    private static final int REGION_COUNT = 1;

    private static final int NEXT_LINK = 0;
    private static final int PREVIOUS_LINK = Integer.BYTES;
    /**
     * The one-based link of the first record.
     */
    private static final int FIRST_RECORD_LINK = 1;
    /**
     * The one-based link of the second record, which heads the free list while the first record is in use.
     */
    private static final int SECOND_RECORD_LINK = FIRST_RECORD_LINK + 1;

    private static final int MILITARY_BYTE = 0x09;
    private static final int MILITARY_WORD = 0x0A;
    private static final int GUARD_WORD = 0x0A;
    private static final int GUARD_RETRIES = 0x09;
    private static final int GUARD_TYPE = 0x10;
    private static final int BUILDING_WORD = 0x0E;
    private static final int TOWN_BYTE = 0x1F;
    private static final int TOWN_OWNER = 0x18;
    private static final int REGION_MILITARY_HEAD = 0x30;

    private static final int BYTE_PAYLOAD = 0xE7;
    private static final int WORD_PAYLOAD = 0xB6D3;
    private static final int RETRY_COUNT = 0x91;

    private static final int SENTINEL_SIZE = 16;
    /**
     * The sentinel ranges around each section: one before and one after it.
     */
    private static final int SENTINEL_RANGE_COUNT = 2;
    private static final int BUFFER_POSITION = 7;
    private static final byte SENTINEL = (byte) 0xA5;

    /**
     * The frame of the recorded replay at which the Probe builds the Pylon.
     */
    private static final int PYLON_BUILD_FRAME = 1208;
    /**
     * The frame of the recorded replay at which the builder status is inspected.
     */
    private static final int BUILDER_STATUS_FRAME = 1210;
    /**
     * The frame of the recorded replay at which the SCV is claimed.
     */
    private static final int CLAIMED_WORKER_FRAME = 2668;
    /**
     * The last frame of the recorded replay.
     */
    private static final int LAST_FRAME = 7054;
    /**
     * The pool index of the building Probe in the recorded replay.
     */
    private static final int PROBE_INDEX = 3337;
    /**
     * The pool index of the claimed SCV in the recorded replay.
     */
    private static final int SCV_INDEX = 3341;

    private static @Nullable GameData saveGameData;

    private enum ComputerSaveRange {
        MILITARY,
        GUARDS,
        GUARD_HEADS,
        BUILDINGS,
        TOWNS,
        TOWN_HEADS,
        REGIONS,
        SCRIPT_HEAD
    }

    private static final class ComputerSaveBuffers {
        private final @NotNull ByteBuffer military;
        private final @NotNull ByteBuffer @NotNull [] regions;
        private final @NotNull ByteBuffer guards;
        private final @NotNull ByteBuffer guardHeads;
        private final @NotNull ByteBuffer workers;
        private final @NotNull ByteBuffer buildings;
        private final @NotNull ByteBuffer towns;
        private final @NotNull ByteBuffer townHeads;
        private final @NotNull ByteBuffer scripts;
        private final @NotNull ByteBuffer scriptHead;

        private ComputerSaveBuffers(boolean direct, byte fill) {
            this.military   = createBuffer(ComputerPlayerPhase.SAVED_MILITARY_SIZE, direct, fill);
            this.regions    = new ByteBuffer[PlayerTable.PLAYABLE_PLAYER_SLOTS];
            this.guards     = createBuffer(ComputerPlayerPhase.SAVED_GUARDS_SIZE, direct, fill);
            this.guardHeads = createBuffer(ComputerPlayerPhase.SAVED_GUARD_HEADS_SIZE, direct, fill);
            this.workers    = createBuffer(ComputerPlayerPhase.SAVED_WORKERS_SIZE, direct, fill);
            this.buildings  = createBuffer(ComputerPlayerPhase.SAVED_BUILDINGS_SIZE, direct, fill);
            this.towns      = createBuffer(ComputerPlayerPhase.SAVED_TOWNS_SIZE, direct, fill);
            this.townHeads  = createBuffer(ComputerPlayerPhase.SAVED_TOWN_HEADS_SIZE, direct, fill);
            this.scripts    = createBuffer(ComputerPlayerPhase.SAVED_SCRIPTS_SIZE, direct, fill);
            this.scriptHead = createBuffer(ComputerPlayerPhase.SAVED_SCRIPT_HEAD_SIZE, direct, fill);

            for (int owner = 0; owner < this.regions.length; owner++) {
                this.regions[owner] = createBuffer(ComputerPlayerPhase.SAVED_REGION_RECORD_SIZE, direct, fill);
            }
        }
    }

    @AfterAll
    static void closeGameData() {
        if (saveGameData != null) {
            saveGameData.close();
        }
    }

    /**
     * Returns the installed game data of the computer save tests, which the first call loads.
     *
     * @return the installed game data
     * @throws IOException if the installed game data cannot be read
     */
    private static @NotNull GameData getGameData() throws IOException {
        if (saveGameData == null) {
            saveGameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
        }

        return saveGameData;
    }

    private static @NotNull ByteBuffer createBuffer(int size, boolean direct, byte fill) {
        ByteBuffer storage = direct
                             ? ByteBuffer.allocateDirect(size + SENTINEL_SIZE * SENTINEL_RANGE_COUNT)
                             : ByteBuffer.allocate(size + SENTINEL_SIZE * SENTINEL_RANGE_COUNT);

        for (int index = 0; index < storage.capacity(); index++) {
            storage.put(index, index >= SENTINEL_SIZE && index < SENTINEL_SIZE + size ? fill : SENTINEL);
        }

        ByteBuffer section = storage.slice(SENTINEL_SIZE, size + SENTINEL_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        section.position(BUFFER_POSITION);
        section.mark();

        return section;
    }

    private static void assertSection(@NotNull ByteBuffer expected, @NotNull ByteBuffer actual, int size) {
        byte[] expectedBytes = new byte[size];
        byte[] actualBytes = new byte[size];

        expected.get(0, expectedBytes);
        actual.get(0, actualBytes);

        Assertions.assertArrayEquals(expectedBytes, actualBytes);
        Assertions.assertEquals(size + SENTINEL_SIZE, actual.limit());
        Assertions.assertEquals(BUFFER_POSITION, actual.position());

        actual.reset();

        Assertions.assertEquals(BUFFER_POSITION, actual.position());

        for (int index = size; index < actual.limit(); index++) {
            Assertions.assertEquals(SENTINEL, actual.get(index), "Trailing byte " + index);
        }
    }

    private @Nullable ComputerPlayerPhase phase;
    private @Nullable SaveReferences references;

    /**
     * Creates the computer-player phase and the save references that the computer save tests write and read, on the installed game data.
     *
     * @throws IOException if the installed game data cannot be read
     */
    private void createPhase() throws IOException {
        GameData installed = getGameData();
        Game game = Game.create(installed, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(1);
        game.getSpriteSystem().initializeSpritePool(1);
        game.getSpriteSystem().initializeSpriteEffectPool(1);
        game.initializeOrderPool(1);
        game.setUnitStrengthTable(new UnitStrengthTable(installed.getUnitTypes(), installed.getWeaponTypes()));

        this.phase = new ComputerPlayerPhase(game, REGION_COUNT, new int[SCHEDULER_COUNTER_COUNT], new int[PlayerTable.PLAYABLE_PLAYER_SLOTS]);

        this.phase.initializeScripts(false);

        this.references = new SaveReferences(game);
    }

    private void writeSections(@NotNull ComputerSaveBuffers saved) {
        Assertions.assertNotNull(this.phase);
        Assertions.assertNotNull(this.references);

        this.phase.writeSaveRegions(saved.military, saved.regions, this.references);
        this.phase.writeSaveGuards(saved.guards, saved.guardHeads, this.references);
        this.phase.writeSaveTowns(saved.workers, saved.buildings, saved.towns, saved.townHeads, this.references);
        this.phase.writeSaveScripts(saved.scripts, saved.scriptHead);
    }

    private void readSections(@NotNull ComputerSaveBuffers saved) {
        Assertions.assertNotNull(this.phase);
        Assertions.assertNotNull(this.references);

        this.phase.readSaveRegions(REGION_COUNT, saved.military, saved.regions, this.references);
        this.phase.readSaveGuards(saved.guards, saved.guardHeads, this.references);
        this.phase.readSaveTowns(saved.workers, saved.buildings, saved.towns, saved.townHeads, this.references);
    }


    @RequiresInstallation
    @Test
    void matches_native_computer_phase_scheduler_cases() throws Exception {
        byte[] capture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_COMPUTER_PHASE_SCHEDULER, SCHEDULER_CAPTURE_HASH);
        ByteBuffer expected = ByteBuffer.wrap(capture).order(ByteOrder.LITTLE_ENDIAN);

        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); GameData gameData = GameData.load(storage)) {
            TerrainTileset terrain = TerrainTileset.load(storage, SCHEDULER_TILESET);
            GameMap map = new GameMap(SCHEDULER_MAP_SIZE, SCHEDULER_MAP_SIZE, 0, new int[SCHEDULER_MAP_SIZE * SCHEDULER_MAP_SIZE],
                                      terrain.expandTileFlags(new int[SCHEDULER_MAP_SIZE * SCHEDULER_MAP_SIZE]));
            TerrainContours collision = TerrainContours.create(map, terrain);

            for (int regionCount : SCHEDULER_REGION_COUNTS) {
                for (int caseIndex = 0; caseIndex < SCHEDULER_CASE_COUNT; caseIndex++) {
                    Game game = Game.create(gameData, map, new LcgRandom(0));

                    game.getUnitSystem().initializeUnitPool(SCHEDULER_UNIT_CAPACITY);
                    game.getPathSystem().setTerrainContours(collision);
                    game.setResourceAreas(new ResourceAreas(game, terrain));

                    boolean odd = (caseIndex & ODD_CASE_BIT) != 0;
                    int[] counters = {FIRST_COUNTER_VALUES[caseIndex % COUNTER_VALUE_COUNT],
                                      SECOND_COUNTER_VALUES[caseIndex / COUNTER_VALUE_COUNT % COUNTER_VALUE_COUNT],
                                      THIRD_COUNTER_VALUES[caseIndex / (COUNTER_VALUE_COUNT * COUNTER_VALUE_COUNT) % COUNTER_VALUE_COUNT],
                                      caseIndex & FOURTH_COUNTER_BIT,
                                      odd ? FIFTH_COUNTER_PERIOD : caseIndex % FIFTH_COUNTER_PERIOD,
                                      odd ? SIXTH_COUNTER_BASE - caseIndex : caseIndex,
                                      caseIndex % LAST_COUNTER_PERIOD, caseIndex / LAST_COUNTER_PERIOD % LAST_COUNTER_PERIOD};
                    int[] strengths = new int[regionCount * PlayerTable.PLAYABLE_PLAYER_SLOTS];
                    int[] playerTypes = new int[PlayerTable.PLAYABLE_PLAYER_SLOTS];

                    for (int playerIndex = 0; playerIndex < PlayerTable.PLAYABLE_PLAYER_SLOTS; playerIndex++) {
                        playerTypes[playerIndex] = (playerIndex + caseIndex) % HUMAN_PLAYER_PERIOD == 0 ? PlayerTypes.HUMAN : PlayerTypes.NONE;

                        for (int regionIndex = 0; regionIndex < regionCount; regionIndex++) {
                            strengths[playerIndex * regionCount + regionIndex] = caseIndex * CASE_STRENGTH_STEP + playerIndex * PLAYER_STRENGTH_STEP +
                                                                                 regionIndex;
                        }
                    }

                    ComputerPlayerPhase casePhase = new ComputerPlayerPhase(game, regionCount, counters, strengths);
                    Unit[] units = new Unit[PlayerTable.PLAYABLE_PLAYER_SLOTS];
                    UnitPool pool = game.getUnitSystem().getUnitPool();

                    Assertions.assertNotNull(pool);

                    for (int unitIndex = 0; unitIndex < units.length; unitIndex++) {
                        Unit unit = pool.getUnit(UnitPoolScenario.allocate(pool));

                        for (int offset = UNIT_FILL_START; offset < UNIT_RECORD_SIZE; offset++) {
                            UnitMemoryScenario.of(unit).writeMemoryByte(offset, caseIndex + unitIndex * UNIT_FILL_STEP + offset);
                        }

                        UnitMemoryScenario.of(unit).writeMemoryInteger(UNIT_MARKER_OFFSET, UNIT_MARKER ^ (unitIndex << UNIT_MARKER_SHIFT));
                        pool.insertCreatedUnit(unit);
                        units[unitIndex] = unit;
                    }

                    casePhase.update(playerTypes, SCHEDULER_CLOCKS[caseIndex % SCHEDULER_CLOCKS.length]);

                    for (int value : casePhase.getCounterSnapshot()) {
                        Assertions.assertEquals(expected.getInt(), value, "Scheduler case " + caseIndex);
                    }

                    for (int playerIndex = 0; playerIndex < PlayerTable.PLAYABLE_PLAYER_SLOTS; playerIndex++) {
                        for (int regionIndex = 0; regionIndex < regionCount; regionIndex++) {
                            Assertions.assertEquals(expected.getInt(),
                                                    casePhase.getRegionalStrength(playerIndex, regionIndex),
                                                    "Regional strength case " + caseIndex);
                        }
                    }

                    for (Unit unit : units) {
                        byte[] expectedUnit = new byte[UNIT_RECORD_SIZE];

                        expected.get(expectedUnit);

                        UnitDiagnosticAssert.assertDomainEquals(expectedUnit, unit, "Scheduler unit case " + caseIndex);
                    }

                    Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
                }
            }
        }

        Assertions.assertEquals(0, expected.remaining());
    }

    @Test
    void scheduler_state_does_not_share_constructor_or_snapshot_arrays() {
        int[] counters = {60, 15, 6, 0, 1, 2499, 3, 4};
        ComputerPlayerPhase schedulerPhase = new ComputerPlayerPhase(Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0)),
                                                                     1,
                                                                     counters,
                                                                     new int[PlayerTable.PLAYABLE_PLAYER_SLOTS]);

        int initialHash = schedulerPhase.computeStateHash(STATE_HASH_SEED);

        counters[0] = -1;

        int[] snapshot = schedulerPhase.getCounterSnapshot();

        snapshot[1] = -1;

        Assertions.assertArrayEquals(new int[]{60, 15, 6, 0, 1, 2499, 3, 4}, schedulerPhase.getCounterSnapshot());
        Assertions.assertEquals(initialHash, schedulerPhase.computeStateHash(STATE_HASH_SEED));
    }

    @RequiresInstallation
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void computer_save_writers_replace_owned_bytes_and_preserve_buffer_state(boolean direct) throws IOException {
        this.createPhase();

        ComputerSaveBuffers expected = new ComputerSaveBuffers(direct, (byte) 0);
        ComputerSaveBuffers actual = new ComputerSaveBuffers(direct, SENTINEL);

        this.writeSections(expected);
        this.writeSections(actual);

        assertSection(expected.military, actual.military, ComputerPlayerPhase.SAVED_MILITARY_SIZE);
        assertSection(expected.guards, actual.guards, ComputerPlayerPhase.SAVED_GUARDS_SIZE);
        assertSection(expected.guardHeads, actual.guardHeads, ComputerPlayerPhase.SAVED_GUARD_HEADS_SIZE);
        assertSection(expected.workers, actual.workers, ComputerPlayerPhase.SAVED_WORKERS_SIZE);
        assertSection(expected.buildings, actual.buildings, ComputerPlayerPhase.SAVED_BUILDINGS_SIZE);
        assertSection(expected.towns, actual.towns, ComputerPlayerPhase.SAVED_TOWNS_SIZE);
        assertSection(expected.townHeads, actual.townHeads, ComputerPlayerPhase.SAVED_TOWN_HEADS_SIZE);
        assertSection(expected.scripts, actual.scripts, ComputerPlayerPhase.SAVED_SCRIPTS_SIZE);
        assertSection(expected.scriptHead, actual.scriptHead, ComputerPlayerPhase.SAVED_SCRIPT_HEAD_SIZE);

        for (int owner = 0; owner < actual.regions.length; owner++) {
            assertSection(expected.regions[owner], actual.regions[owner], ComputerPlayerPhase.SAVED_REGION_RECORD_SIZE);
        }
    }

    @RequiresInstallation
    @Test
    void computer_save_round_trip_preserves_all_five_payload_widths() throws IOException {
        this.createPhase();

        ComputerSaveBuffers saved = new ComputerSaveBuffers(false, (byte) 0);

        this.writeSections(saved);

        saved.military.putInt(NEXT_LINK, 0);
        saved.military.putInt(MILITARY_RECORD_SIZE + PREVIOUS_LINK, 0);
        saved.military.putInt(RECORD_COUNT * MILITARY_RECORD_SIZE, SECOND_RECORD_LINK);
        saved.regions[0].putInt(REGION_MILITARY_HEAD, FIRST_RECORD_LINK);
        saved.military.put(MILITARY_BYTE, (byte) BYTE_PAYLOAD);
        saved.military.putShort(MILITARY_WORD, (short) WORD_PAYLOAD);
        saved.guards.putShort(GUARD_WORD, (short) WORD_PAYLOAD);
        saved.buildings.putShort(BUILDING_WORD, (short) WORD_PAYLOAD);
        saved.towns.putInt(NEXT_LINK, 0);
        saved.towns.putInt(TOWN_RECORD_SIZE + PREVIOUS_LINK, 0);
        saved.towns.putInt(TOWN_COUNT * TOWN_RECORD_SIZE, SECOND_RECORD_LINK);
        saved.townHeads.putInt(Integer.BYTES, FIRST_RECORD_LINK);
        saved.towns.put(TOWN_OWNER, (byte) 0);
        saved.towns.put(TOWN_BYTE, (byte) BYTE_PAYLOAD);

        this.readSections(saved);

        saved.military.put(MILITARY_BYTE, (byte) 0);
        saved.military.putShort(MILITARY_WORD, (short) 0);
        saved.guards.putShort(GUARD_WORD, (short) 0);
        saved.buildings.putShort(BUILDING_WORD, (short) 0);
        saved.towns.put(TOWN_BYTE, (byte) 0);

        ComputerSaveBuffers restored = new ComputerSaveBuffers(true, SENTINEL);

        this.writeSections(restored);

        Assertions.assertEquals(BYTE_PAYLOAD, Byte.toUnsignedInt(restored.military.get(MILITARY_BYTE)));
        Assertions.assertEquals(WORD_PAYLOAD, Short.toUnsignedInt(restored.military.getShort(MILITARY_WORD)));
        Assertions.assertEquals(WORD_PAYLOAD, Short.toUnsignedInt(restored.guards.getShort(GUARD_WORD)));
        Assertions.assertEquals(WORD_PAYLOAD, Short.toUnsignedInt(restored.buildings.getShort(BUILDING_WORD)));
        Assertions.assertEquals(BYTE_PAYLOAD, Byte.toUnsignedInt(restored.towns.get(TOWN_BYTE)));

        ComputerSaveBuffers repeated = new ComputerSaveBuffers(false, SENTINEL);

        this.writeSections(repeated);

        assertSection(restored.military, repeated.military, ComputerPlayerPhase.SAVED_MILITARY_SIZE);
        assertSection(restored.guards, repeated.guards, ComputerPlayerPhase.SAVED_GUARDS_SIZE);
        assertSection(restored.buildings, repeated.buildings, ComputerPlayerPhase.SAVED_BUILDINGS_SIZE);
        assertSection(restored.towns, repeated.towns, ComputerPlayerPhase.SAVED_TOWNS_SIZE);
    }

    @RequiresInstallation
    @ParameterizedTest
    @EnumSource(ComputerSaveRange.class)
    void computer_save_writers_replace_each_owned_range(@NotNull ComputerSaveRange range) throws IOException {
        this.createPhase();

        ComputerSaveBuffers expected = new ComputerSaveBuffers(false, (byte) 0);
        ComputerSaveBuffers actual = new ComputerSaveBuffers(true, SENTINEL);

        this.writeSections(expected);
        this.writeSections(actual);

        switch (range) {
            case MILITARY -> assertSection(expected.military, actual.military, ComputerPlayerPhase.SAVED_MILITARY_SIZE);
            case GUARDS -> assertSection(expected.guards, actual.guards, ComputerPlayerPhase.SAVED_GUARDS_SIZE);
            case GUARD_HEADS -> assertSection(expected.guardHeads, actual.guardHeads, ComputerPlayerPhase.SAVED_GUARD_HEADS_SIZE);
            case BUILDINGS -> assertSection(expected.buildings, actual.buildings, ComputerPlayerPhase.SAVED_BUILDINGS_SIZE);
            case TOWNS -> assertSection(expected.towns, actual.towns, ComputerPlayerPhase.SAVED_TOWNS_SIZE);
            case TOWN_HEADS -> assertSection(expected.townHeads, actual.townHeads, ComputerPlayerPhase.SAVED_TOWN_HEADS_SIZE);
            case SCRIPT_HEAD -> assertSection(expected.scriptHead, actual.scriptHead, ComputerPlayerPhase.SAVED_SCRIPT_HEAD_SIZE);
            case REGIONS -> {
                for (int owner = 0; owner < actual.regions.length; owner++) {
                    assertSection(expected.regions[owner], actual.regions[owner], ComputerPlayerPhase.SAVED_REGION_RECORD_SIZE);
                }
            }
        }
    }

    @RequiresInstallation
    @Test
    void free_save_clears_town_and_military_payloads_and_retains_guard_and_building_words() throws IOException {
        this.createPhase();

        ComputerSaveBuffers saved = new ComputerSaveBuffers(false, (byte) 0);

        this.writeSections(saved);

        saved.military.put(MILITARY_BYTE, (byte) BYTE_PAYLOAD);
        saved.military.putShort(MILITARY_WORD, (short) WORD_PAYLOAD);
        saved.guards.putShort(GUARD_WORD, (short) WORD_PAYLOAD);
        saved.buildings.putShort(BUILDING_WORD, (short) WORD_PAYLOAD);
        saved.towns.put(TOWN_BYTE, (byte) BYTE_PAYLOAD);

        this.readSections(saved);

        ComputerSaveBuffers restored = new ComputerSaveBuffers(false, SENTINEL);

        this.writeSections(restored);

        Assertions.assertEquals(0, restored.military.get(MILITARY_BYTE));
        Assertions.assertEquals(0, restored.military.getShort(MILITARY_WORD));
        Assertions.assertEquals(WORD_PAYLOAD, Short.toUnsignedInt(restored.guards.getShort(GUARD_WORD)));
        Assertions.assertEquals(WORD_PAYLOAD, Short.toUnsignedInt(restored.buildings.getShort(BUILDING_WORD)));
        Assertions.assertEquals(0, restored.towns.get(TOWN_BYTE));
    }

    @RequiresInstallation
    @Test
    void read_save_guards_should_retain_successful_prefix_when_a_later_link_fails() throws IOException {
        this.createPhase();

        Assertions.assertNotNull(this.phase);
        Assertions.assertNotNull(this.references);

        ComputerSaveBuffers saved = new ComputerSaveBuffers(false, (byte) 0);

        this.writeSections(saved);

        saved.guards.put(GUARD_RETRIES, (byte) RETRY_COUNT);
        saved.guards.putShort(GUARD_WORD, (short) WORD_PAYLOAD);
        saved.guards.putInt(GUARD_RECORD_SIZE + NEXT_LINK, RECORD_COUNT + 1);

        Assertions.assertThrows(UncheckedIOException.class, () -> this.phase.readSaveGuards(saved.guards, saved.guardHeads, this.references));

        ComputerSaveBuffers restored = new ComputerSaveBuffers(false, (byte) 0);

        this.phase.writeSaveGuards(restored.guards, restored.guardHeads, this.references);

        Assertions.assertEquals(RETRY_COUNT, Byte.toUnsignedInt(restored.guards.get(GUARD_RETRIES)));
        Assertions.assertEquals(WORD_PAYLOAD, Short.toUnsignedInt(restored.guards.getShort(GUARD_WORD)));
    }

    @RequiresInstallation
    @Test
    void read_save_towns_should_retain_building_prefix_when_a_later_link_fails() throws IOException {
        this.createPhase();

        Assertions.assertNotNull(this.phase);
        Assertions.assertNotNull(this.references);

        ComputerSaveBuffers saved = new ComputerSaveBuffers(false, (byte) 0);

        this.writeSections(saved);

        saved.buildings.putShort(BUILDING_WORD, (short) WORD_PAYLOAD);
        saved.buildings.putInt(BUILDING_RECORD_SIZE + NEXT_LINK, RECORD_COUNT + 1);

        Assertions.assertThrows(UncheckedIOException.class,
                                () -> this.phase.readSaveTowns(saved.workers, saved.buildings, saved.towns, saved.townHeads, this.references));

        ComputerSaveBuffers restored = new ComputerSaveBuffers(false, (byte) 0);

        this.phase.writeSaveTowns(restored.workers, restored.buildings, restored.towns, restored.townHeads, this.references);

        Assertions.assertEquals(saved.buildings.getInt(NEXT_LINK), restored.buildings.getInt(NEXT_LINK));
        Assertions.assertEquals(WORD_PAYLOAD, Short.toUnsignedInt(restored.buildings.getShort(BUILDING_WORD)));
    }

    @RequiresInstallation
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void write_save_guards_should_keep_free_mutation_before_short_or_read_only_failure(boolean readOnly) throws IOException {
        this.createPhase();

        Assertions.assertNotNull(this.phase);
        Assertions.assertNotNull(this.references);

        ComputerSaveBuffers saved = new ComputerSaveBuffers(false, (byte) 0);

        this.writeSections(saved);

        saved.guards.put(GUARD_RETRIES, (byte) RETRY_COUNT);
        saved.guards.putShort(GUARD_TYPE, (short) WORD_PAYLOAD);
        saved.guards.putShort(GUARD_WORD, (short) WORD_PAYLOAD);

        this.phase.readSaveGuards(saved.guards, saved.guardHeads, this.references);

        int expectedNext = saved.guards.getInt(NEXT_LINK);
        ByteBuffer records = readOnly ? saved.guards.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
                                      : saved.guards.duplicate().order(ByteOrder.LITTLE_ENDIAN);

        if (!readOnly) {
            records.putInt(NEXT_LINK, 0);
            records.limit(Integer.BYTES);
        }

        Class<? extends RuntimeException> failureType = readOnly ? ReadOnlyBufferException.class : IndexOutOfBoundsException.class;

        Assertions.assertThrows(failureType, () -> this.phase.writeSaveGuards(records, saved.guardHeads, this.references));

        if (!readOnly) {
            Assertions.assertEquals(expectedNext, records.getInt(NEXT_LINK));
        }

        ComputerSaveBuffers restored = new ComputerSaveBuffers(false, (byte) 0);

        this.phase.writeSaveGuards(restored.guards, restored.guardHeads, this.references);

        Assertions.assertEquals(RETRY_COUNT, Byte.toUnsignedInt(restored.guards.get(GUARD_RETRIES)));
        Assertions.assertEquals(0, restored.guards.getShort(GUARD_TYPE));
        Assertions.assertEquals(WORD_PAYLOAD, Short.toUnsignedInt(restored.guards.getShort(GUARD_WORD)));
    }

    @RequiresInstallation
    @ExtendWith(GameResourcesExtension.class)
    @Test
    void terran_vs_computer_replay_matches_recorded_orders_status_and_random_state(@NotNull InstallationTestResources resources) throws Exception {
        SharedReplayRunScenario run = resources.getReplayRun(GameTestResources.getPath(GameTestResources.REPLAY_TERRAN_VS_COMPUTER));

        run.inspect(PYLON_BUILD_FRAME, game -> {
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);
            Assertions.assertEquals(31, pool.getUnit(PROBE_INDEX).getPrimaryOrder(), "Probe 3337 builds the Pylon from frame 1208.");
            Assertions.assertEquals(1757, game.getRandom().getTotalRandomCounts());
        });
        run.inspect(BUILDER_STATUS_FRAME, game -> {
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);
            Assertions.assertEquals(0x930001, pool.getUnit(PROBE_INDEX).getStatus(), "The computer builder carries status 0x800000.");
        });
        run.inspect(CLAIMED_WORKER_FRAME, game -> {
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);
            Assertions.assertEquals(0x08130081, pool.getUnit(SCV_INDEX).getStatus(), "SCV 3341 is claimed (0x80).");
            Assertions.assertEquals(3863, game.getRandom().getTotalRandomCounts());
        });
        run.inspect(LAST_FRAME, game -> {
            Assertions.assertEquals(1017114206, game.getRandom().getState());
            Assertions.assertEquals(15312, game.getRandom().getTotalRandomCounts());
        });
    }
}
