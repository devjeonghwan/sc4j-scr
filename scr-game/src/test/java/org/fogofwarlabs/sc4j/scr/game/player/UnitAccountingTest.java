package org.fogofwarlabs.sc4j.scr.game.player;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.type.constant.TriggerUnitGroups;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitGroupFlags;
import org.fogofwarlabs.sc4j.scr.game.map.StartingUnitLoader;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitMemoryScenario;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Checks that unit accounting updates, initializes and imports the counters of complete game-context counter snapshots, which the tests address by
 * game-context offset because the counter layout is EUD-visible game-context state.
 */
final class UnitAccountingTest {
    /**
     * The DWORD count of an original accounting snapshot.
     */
    private static final int CONTEXT_WORD_COUNT = UnitAccountingAssert.CONTEXT_BYTE_COUNT / Integer.BYTES;
    /**
     * The factor that gives every DWORD of a distinct snapshot its own value.
     */
    private static final int DISTINCT_WORD_FACTOR = 0x13579BDF;

    /**
     * The number of player slots, the DWORDs of a per-player counter group and of a type counter row.
     */
    private static final int PLAYER_SLOTS = 12;
    /**
     * The size of one per-player counter group, a DWORD for every player slot.
     */
    private static final int COUNTER_GROUP_SIZE = PLAYER_SLOTS * Integer.BYTES;
    /**
     * The per-player counter groups the accounting owns, one bit per group from {@link UnitAccountingAssert#COUNTERS_OFFSET}: the unit, building and
     * factory counts, the production scores and the supply counters; the score groups between them and the custom score after them are not accounting
     * counters.
     */
    private static final int ACCOUNTING_COUNTER_GROUPS = 0x07FCD3A7;
    /**
     * The game-context offset of the completed unit counters.
     */
    private static final int COMPLETED_UNIT_COUNTS_OFFSET = 0x2D24;
    /**
     * The game-context offset of the produced unit counters.
     */
    private static final int PRODUCED_UNIT_COUNTS_OFFSET = 0x2D54;
    /**
     * The game-context offset of the units lost counters, a score group between the accounting counters.
     */
    private static final int UNITS_LOST_OFFSET = 0x2D84;
    /**
     * The game-context offset of the unit production scores.
     */
    private static final int UNIT_PRODUCTION_SCORES_OFFSET = 0x2DE4;
    /**
     * The game-context offset of the building counters.
     */
    private static final int BUILDING_COUNTS_OFFSET = 0x2E44;
    /**
     * The game-context offset of the Zerg supply provided counters.
     */
    private static final int ZERG_SUPPLY_PROVIDED_OFFSET = 0x3054;
    /**
     * The game-context offset of the Zerg supply used counters.
     */
    private static final int ZERG_SUPPLY_USED_OFFSET = 0x3084;
    /**
     * The game-context offset of the Zerg supply caps.
     */
    private static final int ZERG_SUPPLY_CAPS_OFFSET = 0x30B4;
    /**
     * The game-context offset of the Terran supply caps.
     */
    private static final int TERRAN_SUPPLY_CAPS_OFFSET = 0x3144;
    /**
     * The game-context offset of the Protoss supply caps.
     */
    private static final int PROTOSS_SUPPLY_CAPS_OFFSET = 0x31D4;
    /**
     * The game-context offset after the type counters.
     */
    private static final int TYPE_COUNTS_END = 0x87B4;

    /**
     * The byte length of a {@code units.dat} image.
     */
    private static final int UNITS_DAT_SIZE = 19876;
    /**
     * The {@code units.dat} image offset of the Special Ability Flags DWORDs.
     */
    private static final int FLAGS_COLUMN_OFFSET = 7032;
    /**
     * The {@code units.dat} image offset of the StarEdit Group Flags bytes.
     */
    private static final int GROUP_FLAGS_COLUMN_OFFSET = 16684;
    /**
     * The {@code units.dat} image offset of the Supply Provided bytes.
     */
    private static final int SUPPLY_PROVIDED_COLUMN_OFFSET = 16912;
    /**
     * The {@code units.dat} image offset of the Supply Required bytes.
     */
    private static final int SUPPLY_REQUIRED_COLUMN_OFFSET = 17140;
    /**
     * The {@code units.dat} image offset of the Build Score WORDs.
     */
    private static final int BUILD_SCORE_COLUMN_OFFSET = 17824;

    /**
     * The pool index of the accounted unit.
     */
    private static final int UNIT_POOL_INDEX = 1;
    /**
     * The unit record offset of the unit type WORD.
     */
    private static final int UNIT_TYPE_OFFSET = 0x8C;
    /**
     * The unit record offset of the owner BYTE.
     */
    private static final int OWNER_OFFSET = 0x68;
    /**
     * The unit record offset of the status DWORD.
     */
    private static final int STATUS_OFFSET = 0x140;
    /**
     * The unit record offset of the build queue index BYTE.
     */
    private static final int BUILD_QUEUE_INDEX_OFFSET = 0xE8;
    /**
     * The unit record offset of the build queue WORDs.
     */
    private static final int BUILD_QUEUE_OFFSET = 0xDC;
    /**
     * The build queue index whose WORD is the energy, the first WORD after the {@link Unit#BUILD_QUEUE_SLOTS} queue slots.
     */
    private static final int ENERGY_QUEUE_INDEX = 5;

    /**
     * The field of a queued supply case that holds the unit type.
     */
    private static final int CASE_UNIT_TYPE = 0;
    /**
     * The field of a queued supply case that holds the owner.
     */
    private static final int CASE_OWNER = 1;
    /**
     * The field of a queued supply case that holds the unit status.
     */
    private static final int CASE_STATUS = 2;
    /**
     * The field of a queued supply case that holds the {@code units.dat} Special Ability Flags of the unit type.
     */
    private static final int CASE_FLAGS = 3;
    /**
     * The field of a queued supply case that holds the {@code units.dat} StarEdit Group Flags of the unit type.
     */
    private static final int CASE_GROUP_FLAGS = 4;
    /**
     * The field of a queued supply case that holds the {@code units.dat} Supply Required of the unit type.
     */
    private static final int CASE_SUPPLY_REQUIRED = 5;
    /**
     * The field of a queued supply case that holds the queued unit type.
     */
    private static final int CASE_QUEUED_TYPE = 6;
    /**
     * The field of a queued supply case that holds the {@code units.dat} Special Ability Flags of the queued type.
     */
    private static final int CASE_QUEUED_FLAGS = 7;
    /**
     * The field of a queued supply case that holds the {@code units.dat} Supply Required of the queued type.
     */
    private static final int CASE_QUEUED_SUPPLY_REQUIRED = 8;
    /**
     * The field of a queued supply case that holds the build queue index.
     */
    private static final int CASE_QUEUE_INDEX = 9;
    /**
     * The field of a queued supply case that holds the accounting change.
     */
    private static final int CASE_CHANGE = 10;
    /**
     * The field of a queued supply case that holds the accounting flag that preserves negative type counts.
     */
    private static final int CASE_PRESERVE_NEGATIVE_TYPE_COUNTS = 11;
    /**
     * The field of a queued supply case that holds the BYTE that fills every counter before the update.
     */
    private static final int CASE_FILL_BYTE = 12;
    /**
     * The length of a counter write of a queued supply case: the game-context offset and the written DWORD.
     */
    private static final int CASE_WRITE_LENGTH = 2;
    /**
     * The factor that repeats a BYTE in every BYTE of a DWORD.
     */
    private static final int BYTE_REPLICATION = 0x01010101;

    /**
     * The first player slot.
     */
    private static final int FIRST_PLAYER = 0;
    /**
     * The neutral player slot, the owner of the preplaced egg queues.
     */
    private static final int NEUTRAL_PLAYER = 11;
    /**
     * The owner of the accounted units and counters.
     */
    private static final int OWNER = 2;
    /**
     * The ordinary supply cap of a player.
     */
    private static final int SUPPLY_CAP = 400;
    /**
     * The number of Terran slots in the first force of the supply cap test: slots {@code 1} and {@code 2}.
     */
    private static final int FIRST_FORCE_TERRAN_SLOTS = 2;
    /**
     * The number of Zerg slots in the second force of the supply cap test: slots {@code 4}, {@code 5} and {@code 6}.
     */
    private static final int SECOND_FORCE_ZERG_SLOTS = 3;
    /**
     * The team count of the supply cap test.
     */
    private static final int TWO_TEAMS = 2;
    /**
     * The leader slot of the first force.
     */
    private static final int FIRST_FORCE_LEADER = 0;
    /**
     * The leader slot of the second force.
     */
    private static final int SECOND_FORCE_LEADER = 5;
    /**
     * The leader slot of a force without players.
     */
    private static final int NO_FORCE_OWNER = -1;
    /**
     * The Supply Required of the accounted unit types.
     */
    private static final int SUPPLY_REQUIRED = 17;
    /**
     * The {@code units.dat} Supply Provided of the accounted unit type, also the provided supply counter of the availability test.
     */
    private static final int SUPPLY_PROVIDED = 3;
    /**
     * A supply DWORD with the sign bit set, negative as a signed value.
     */
    private static final int UNSIGNED_LARGE_SUPPLY = 0x80000000;
    /**
     * The Build Score of the accounted unit type.
     */
    private static final int BUILD_SCORE = 7;
    /**
     * The building count of the captured snapshot.
     */
    private static final int BUILDING_COUNT = 5;
    /**
     * The DWORD written at game-context offset {@code 0}, outside the accounting counters.
     */
    private static final int NON_COUNTER_WORD = 1234567;
    /**
     * The completed type count of the unit before the update.
     */
    private static final int INITIAL_COMPLETED_COUNT = -2;
    /**
     * The completed type count of the unit after the update.
     */
    private static final int UPDATED_COMPLETED_COUNT = -1;

    private static int @NotNull [] createDistinctSnapshot() {
        int[] snapshot = new int[CONTEXT_WORD_COUNT];

        for (int wordIndex = 0; wordIndex < snapshot.length; wordIndex++) {
            snapshot[wordIndex] = wordIndex * DISTINCT_WORD_FACTOR;
        }

        return snapshot;
    }

    private static boolean isKnownCounter(int offset) {
        int group = (offset - UnitAccountingAssert.COUNTERS_OFFSET) / COUNTER_GROUP_SIZE;
        boolean knownPlayerCounter = offset >= UnitAccountingAssert.COUNTERS_OFFSET && offset < UnitAccountingAssert.TYPE_COUNTS_OFFSET &&
                                     (ACCOUNTING_COUNTER_GROUPS >>> group & 1) != 0;
        boolean knownTypeCounter = offset >= UnitAccountingAssert.TYPE_COUNTS_OFFSET && offset < TYPE_COUNTS_END;

        return knownPlayerCounter || knownTypeCounter;
    }

    private static boolean isInCounterGroup(int offset, int groupOffset) {
        return offset >= groupOffset && offset < groupOffset + COUNTER_GROUP_SIZE;
    }

    private static int getCounterIndex(int groupOffset, int player) {
        return (groupOffset + player * Integer.BYTES) / Integer.BYTES;
    }

    private static int getTypeCountIndex(int tableOffset, int unitType, int player) {
        return getCounterIndex(tableOffset, unitType * PLAYER_SLOTS + player);
    }


    @Test
    void update_should_retain_type_counts_for_empty_preplaced_egg_queues() throws IOException {
        for (int unitType : new int[]{ScrUnitIds.ZERG_EGG, ScrUnitIds.ZERG_COCOON, ScrUnitIds.ZERG_LURKER_EGG}) {
            for (int queueIndex = 0; queueIndex < Unit.BUILD_QUEUE_SLOTS; queueIndex++) {
                byte[] data = new byte[UNITS_DAT_SIZE];

                data[GROUP_FLAGS_COLUMN_OFFSET + unitType]     = UnitGroupFlags.ZERG;
                data[SUPPLY_REQUIRED_COLUMN_OFFSET + unitType] = SUPPLY_REQUIRED;

                DatTable units = UnitsDat.createSchema().parse(data);
                Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);

                unit.setUnitId(unitType);
                unit.setOwnerIndex(NEUTRAL_PLAYER);
                unit.setFirstBuildQueueType(ScrUnitIds.NONE);
                unit.setSecondBuildQueueType(ScrUnitIds.NONE);
                unit.setThirdBuildQueueType(ScrUnitIds.NONE);
                unit.setFourthBuildQueueType(ScrUnitIds.NONE);
                unit.setFifthBuildQueueType(ScrUnitIds.NONE);
                unit.setBuildQueueIndex(queueIndex);

                int[] expected = new int[CONTEXT_WORD_COUNT];
                UnitAccounting accounting = UnitAccountingAssert.fromCapture(expected);

                accounting.update(unit, UnitsDat.createTypeTable(units), 1, false);

                expected[getTypeCountIndex(UnitAccountingAssert.TYPE_COUNTS_OFFSET, unitType, NEUTRAL_PLAYER)] = 1;
                expected[getCounterIndex(UnitAccountingAssert.COUNTERS_OFFSET, NEUTRAL_PLAYER)]                = 1;

                UnitAccountingAssert.assertKnownCounters(expected, accounting);

                accounting.update(unit, UnitsDat.createTypeTable(units), -1, false);

                Arrays.fill(expected, 0);

                UnitAccountingAssert.assertKnownCounters(expected, accounting);
            }
        }
    }

    /**
     * Checks that an Egg at queue index {@code 5} reads its queued type from the EUD-visible energy WORD of the unit record.
     */
    @Test
    void update_should_read_the_energy_as_the_queued_type_of_an_egg_at_queue_index_five() throws IOException {
        byte[] data = new byte[UNITS_DAT_SIZE];

        data[GROUP_FLAGS_COLUMN_OFFSET + ScrUnitIds.ZERG_EGG]           = UnitGroupFlags.ZERG;
        data[SUPPLY_REQUIRED_COLUMN_OFFSET + ScrUnitIds.ZERG_HYDRALISK] = SUPPLY_REQUIRED;

        DatTable units = UnitsDat.createSchema().parse(data);
        Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);

        unit.setUnitId(ScrUnitIds.ZERG_EGG);
        unit.setOwnerIndex(OWNER);
        unit.setBuildQueueIndex(ENERGY_QUEUE_INDEX);
        unit.setEnergy(ScrUnitIds.ZERG_HYDRALISK);

        int[] expected = new int[CONTEXT_WORD_COUNT];
        UnitAccounting accounting = UnitAccountingAssert.fromCapture(expected);

        accounting.update(unit, UnitsDat.createTypeTable(units), 1, false);

        expected[getTypeCountIndex(UnitAccountingAssert.TYPE_COUNTS_OFFSET, ScrUnitIds.ZERG_EGG, OWNER)] = 1;
        expected[getCounterIndex(UnitAccountingAssert.COUNTERS_OFFSET, OWNER)]                           = 1;
        expected[getCounterIndex(ZERG_SUPPLY_USED_OFFSET, OWNER)]                                        = SUPPLY_REQUIRED;

        UnitAccountingAssert.assertKnownCounters(expected, accounting);
    }

    @Test
    void context_view_excludes_non_counter_words_and_snapshot_copies_building_counts() {
        int[] capture = new int[CONTEXT_WORD_COUNT];

        capture[0]                                      = NON_COUNTER_WORD;
        capture[UNITS_LOST_OFFSET / Integer.BYTES]      = -1;
        capture[BUILDING_COUNTS_OFFSET / Integer.BYTES] = BUILDING_COUNT;

        UnitAccounting accounting = UnitAccountingAssert.fromCapture(capture);

        Assertions.assertFalse(accounting.hasContextDword(0), "offset 0 lies outside the accounting ranges");
        Assertions.assertFalse(accounting.hasContextDword(UNITS_LOST_OFFSET), "offset 0x2D84 lies outside the accounting ranges");
        Assertions.assertTrue(accounting.hasContextDword(BUILDING_COUNTS_OFFSET));
        Assertions.assertEquals(BUILDING_COUNT, accounting.readContextDword(BUILDING_COUNTS_OFFSET));

        UnitAccounting.TriggerSnapshot snapshot = new UnitAccounting.TriggerSnapshot();

        accounting.copyTriggerSnapshot(snapshot);

        Assertions.assertEquals(BUILDING_COUNT, snapshot.getBuildingCount(FIRST_PLAYER));

        accounting.initializeForGame(null);

        Assertions.assertEquals(BUILDING_COUNT, snapshot.getBuildingCount(FIRST_PLAYER));

        accounting.copyTriggerSnapshot(snapshot);

        Assertions.assertEquals(0, snapshot.getBuildingCount(FIRST_PLAYER));
    }

    /**
     * Checks the captured queued supply cases, whose {@code units.dat} images and units are written by their {@code units.dat} file and unit record
     * offsets because the captures hold them in the game's layouts.
     */
    @Test
    void matches_native_queued_supply_accounting_cases() throws IOException {
        int[][] fixtures = {
                {97, 5, 0, 0, 9, 253, 98, 1024, 185, 0, 2, 0, 94},
                {176, 1, 0, 1024, 57, 43, 177, 1024, 17, 2, 1, 0, 236},
                {148, 2, 0, 0, 42, 230, 149, 1024, 165, 4, Integer.MAX_VALUE, 0, 252},
                {36, 2, 1, 1024, 47, 197, 37, 1024, 81, 4, 1, 1, 247}
        };
        long[][] writes = {
                {11528, 1583242848L, 12440, 1583243074L, 17528, 1583242848L},
                {11512, 3974950125L, 12184, 3974950125L, 12424, 3974950210L, 21304, 0},
                {11516, 2096954619L, 12188, 2096954619L, 12572, 4244438038L, 19964, 2096954619L},
                {11516, 4160223224L, 12188, 4160223224L, 12428, 4160223385L, 14588, 4160223224L}
        };

        for (int fixtureIndex = 0; fixtureIndex < fixtures.length; fixtureIndex++) {
            int[] fixture = fixtures[fixtureIndex];
            byte[] data = new byte[UNITS_DAT_SIZE];
            ByteBuffer bytes = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

            bytes.putInt(FLAGS_COLUMN_OFFSET + fixture[CASE_UNIT_TYPE] * Integer.BYTES, fixture[CASE_FLAGS]);
            bytes.putInt(FLAGS_COLUMN_OFFSET + fixture[CASE_QUEUED_TYPE] * Integer.BYTES, fixture[CASE_QUEUED_FLAGS]);
            data[GROUP_FLAGS_COLUMN_OFFSET + fixture[CASE_UNIT_TYPE]]       = (byte) fixture[CASE_GROUP_FLAGS];
            data[SUPPLY_REQUIRED_COLUMN_OFFSET + fixture[CASE_UNIT_TYPE]]   = (byte) fixture[CASE_SUPPLY_REQUIRED];
            data[SUPPLY_REQUIRED_COLUMN_OFFSET + fixture[CASE_QUEUED_TYPE]] = (byte) fixture[CASE_QUEUED_SUPPLY_REQUIRED];

            DatTable units = UnitsDat.createSchema().parse(data);
            Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);

            UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, fixture[CASE_UNIT_TYPE]);
            UnitMemoryScenario.of(unit).writeMemoryByte(OWNER_OFFSET, fixture[CASE_OWNER]);
            UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, fixture[CASE_STATUS]);
            UnitMemoryScenario.of(unit).writeMemoryByte(BUILD_QUEUE_INDEX_OFFSET, fixture[CASE_QUEUE_INDEX]);
            UnitMemoryScenario.of(unit).writeMemoryWord(BUILD_QUEUE_OFFSET + fixture[CASE_QUEUE_INDEX] * Short.BYTES, fixture[CASE_QUEUED_TYPE]);

            int[] expected = new int[CONTEXT_WORD_COUNT];

            Arrays.fill(expected, fixture[CASE_FILL_BYTE] * BYTE_REPLICATION);

            UnitAccounting accounting = UnitAccountingAssert.fromCapture(expected);

            accounting.update(unit, UnitsDat.createTypeTable(units), fixture[CASE_CHANGE], fixture[CASE_PRESERVE_NEGATIVE_TYPE_COUNTS] != 0);

            for (int writeIndex = 0; writeIndex < writes[fixtureIndex].length; writeIndex += CASE_WRITE_LENGTH) {
                expected[(int) writes[fixtureIndex][writeIndex] / Integer.BYTES] = (int) writes[fixtureIndex][writeIndex + 1];
            }

            UnitAccountingAssert.assertKnownCounters(expected, accounting);
        }
    }

    @Test
    void distinct_capture_words_import_into_represented_counters() {
        int[] snapshot = createDistinctSnapshot();
        UnitAccounting accounting = UnitAccountingAssert.fromCapture(snapshot);

        UnitAccountingAssert.assertKnownCounters(snapshot, accounting);
    }

    @Test
    void initialize_for_game_should_clear_counters_and_set_ordinary_supply_caps() {
        int[] snapshot = createDistinctSnapshot();
        UnitAccounting accounting = UnitAccountingAssert.fromCapture(snapshot);

        accounting.initializeForGame(null);

        for (int offset = 0; offset < UnitAccountingAssert.CONTEXT_BYTE_COUNT; offset += Integer.BYTES) {
            boolean supplyCap = isInCounterGroup(offset, ZERG_SUPPLY_CAPS_OFFSET) || isInCounterGroup(offset, TERRAN_SUPPLY_CAPS_OFFSET) ||
                                isInCounterGroup(offset, PROTOSS_SUPPLY_CAPS_OFFSET);

            snapshot[offset / Integer.BYTES] = isKnownCounter(offset) ? supplyCap ? SUPPLY_CAP : 0 : snapshot[offset / Integer.BYTES];
        }

        UnitAccountingAssert.assertKnownCounters(snapshot, accounting);
    }

    @Test
    void initialize_for_game_should_set_team_supply_caps_only_for_force_leaders() {
        int zerg = ScrRaceIds.ZERG;
        int terran = ScrRaceIds.TERRAN;
        int protoss = ScrRaceIds.PROTOSS;
        int[] slotRaces = {zerg, terran, terran, protoss, zerg, zerg, zerg, protoss};
        int[] forceLeaders = {FIRST_FORCE_LEADER, SECOND_FORCE_LEADER, NO_FORCE_OWNER, NO_FORCE_OWNER};
        int[] snapshot = createDistinctSnapshot();
        UnitAccounting accounting = UnitAccountingAssert.fromCapture(snapshot);

        accounting.initializeForGame(new StartingUnitLoader.TeamStart(TWO_TEAMS, slotRaces, forceLeaders));

        for (int offset = 0; offset < UnitAccountingAssert.CONTEXT_BYTE_COUNT; offset += Integer.BYTES) {
            snapshot[offset / Integer.BYTES] = isKnownCounter(offset) ? 0 : snapshot[offset / Integer.BYTES];
        }

        snapshot[getCounterIndex(ZERG_SUPPLY_CAPS_OFFSET, FIRST_FORCE_LEADER)]     = SUPPLY_CAP;
        snapshot[getCounterIndex(TERRAN_SUPPLY_CAPS_OFFSET, FIRST_FORCE_LEADER)]   = FIRST_FORCE_TERRAN_SLOTS * SUPPLY_CAP;
        snapshot[getCounterIndex(PROTOSS_SUPPLY_CAPS_OFFSET, FIRST_FORCE_LEADER)]  = SUPPLY_CAP;
        snapshot[getCounterIndex(ZERG_SUPPLY_CAPS_OFFSET, SECOND_FORCE_LEADER)]    = SECOND_FORCE_ZERG_SLOTS * SUPPLY_CAP;
        snapshot[getCounterIndex(TERRAN_SUPPLY_CAPS_OFFSET, SECOND_FORCE_LEADER)]  = SUPPLY_CAP;
        snapshot[getCounterIndex(PROTOSS_SUPPLY_CAPS_OFFSET, SECOND_FORCE_LEADER)] = SUPPLY_CAP;

        UnitAccountingAssert.assertKnownCounters(snapshot, accounting);
    }

    @Test
    void get_supply_available_should_take_the_unsigned_minimum_of_provided_supply_and_cap() {
        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();
        int counterIndex = ScrRaceIds.ZERG * PLAYER_SLOTS + OWNER;
        int providedOffset = ZERG_SUPPLY_PROVIDED_OFFSET + OWNER * Integer.BYTES;
        int capOffset = ZERG_SUPPLY_CAPS_OFFSET + OWNER * Integer.BYTES;

        accounting.writeContextDword(providedOffset, SUPPLY_PROVIDED);
        accounting.writeContextDword(capOffset, SUPPLY_CAP);

        Assertions.assertEquals(SUPPLY_PROVIDED, accounting.getSupplyAvailable(counterIndex));

        // A provided supply with the sign bit set is larger than any cap below it.
        accounting.writeContextDword(providedOffset, UNSIGNED_LARGE_SUPPLY);

        Assertions.assertEquals(SUPPLY_CAP, accounting.getSupplyAvailable(counterIndex));

        accounting.writeContextDword(providedOffset, SUPPLY_PROVIDED);
        accounting.writeContextDword(capOffset, UNSIGNED_LARGE_SUPPLY);

        Assertions.assertEquals(SUPPLY_PROVIDED, accounting.getSupplyAvailable(counterIndex));
    }

    @Test
    void update_completed_should_keep_counter_writes_before_missing_production_statistics() throws IOException {
        byte[] data = new byte[UNITS_DAT_SIZE];
        int unitType = ScrUnitIds.TERRAN_MARINE;

        data[GROUP_FLAGS_COLUMN_OFFSET + unitType]     = UnitGroupFlags.ZERG | UnitGroupFlags.MEN;
        data[SUPPLY_PROVIDED_COLUMN_OFFSET + unitType] = SUPPLY_PROVIDED;
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putShort(BUILD_SCORE_COLUMN_OFFSET + unitType * Short.BYTES, (short) BUILD_SCORE);

        DatTable units = UnitsDat.createSchema().parse(data);
        Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);

        UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);
        UnitMemoryScenario.of(unit).writeMemoryByte(OWNER_OFFSET, FIRST_PLAYER);

        int[] expected = new int[CONTEXT_WORD_COUNT];

        expected[getTypeCountIndex(UnitAccountingAssert.COMPLETED_TYPE_COUNTS_OFFSET, unitType, FIRST_PLAYER)] = INITIAL_COMPLETED_COUNT;

        UnitAccounting accounting = UnitAccountingAssert.fromCapture(expected);

        Assertions.assertThrows(UnsupportedOperationException.class,
                                () -> accounting.updateCompleted(unit, UnitsDat.createTypeTable(units), 1, true, null));

        expected[getTypeCountIndex(UnitAccountingAssert.COMPLETED_TYPE_COUNTS_OFFSET, unitType, FIRST_PLAYER)] = UPDATED_COMPLETED_COUNT;
        expected[getCounterIndex(ZERG_SUPPLY_PROVIDED_OFFSET, FIRST_PLAYER)]                                   = SUPPLY_PROVIDED;
        expected[getCounterIndex(COMPLETED_UNIT_COUNTS_OFFSET, FIRST_PLAYER)]                                  = 1;
        expected[getCounterIndex(PRODUCED_UNIT_COUNTS_OFFSET, FIRST_PLAYER)]                                   = 1;
        expected[getCounterIndex(UNIT_PRODUCTION_SCORES_OFFSET, FIRST_PLAYER)]                                 = BUILD_SCORE;

        UnitAccountingAssert.assertKnownCounters(expected, accounting);
    }

    @Test
    void update_should_keep_type_count_change_before_unsupported_queued_supply() throws IOException {
        byte[] data = new byte[UNITS_DAT_SIZE];

        data[GROUP_FLAGS_COLUMN_OFFSET + ScrUnitIds.ZERG_EGG] = UnitGroupFlags.ZERG;

        DatTable units = UnitsDat.createSchema().parse(data);
        Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);

        UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, ScrUnitIds.ZERG_EGG);
        UnitMemoryScenario.of(unit).writeMemoryByte(OWNER_OFFSET, OWNER);
        UnitMemoryScenario.of(unit).writeMemoryByte(BUILD_QUEUE_INDEX_OFFSET, 0);
        UnitMemoryScenario.of(unit).writeMemoryWord(BUILD_QUEUE_OFFSET, TriggerUnitGroups.ANY_UNIT);

        int[] expected = new int[CONTEXT_WORD_COUNT];
        UnitAccounting accounting = UnitAccountingAssert.fromCapture(expected);

        Assertions.assertThrows(UnsupportedOperationException.class, () -> accounting.update(unit, UnitsDat.createTypeTable(units), -1, false));

        expected[getTypeCountIndex(UnitAccountingAssert.TYPE_COUNTS_OFFSET, ScrUnitIds.ZERG_EGG, OWNER)] = -1;

        UnitAccountingAssert.assertKnownCounters(expected, accounting);
    }

    /**
     * Checks that unit type {@code 228}, whose row is past its table, counts in the first row of the table that follows it.
     */
    @Test
    void type_228_counts_in_the_first_row_of_the_completed_counts_and_of_the_kills() throws IOException {
        DatTable units = UnitsDat.createSchema().parse(new byte[UnitsDat.createSchema().getExpectedSize()]);
        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();
        Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);

        unit.setUnitId(ScrUnitIds.NONE);
        unit.setOwnerIndex(OWNER);

        accounting.update(unit, UnitsDat.createTypeTable(units), 1, false);
        accounting.updateCompleted(unit, UnitsDat.createTypeTable(units), 1, false, null);

        Assertions.assertEquals(1, accounting.getTypeCount(ScrUnitIds.NONE, OWNER));
        Assertions.assertEquals(1, accounting.getCompletedTypeCount(ScrUnitIds.TERRAN_MARINE, OWNER));
        Assertions.assertEquals(1, accounting.getCompletedTypeCount(ScrUnitIds.NONE, OWNER));
        Assertions.assertEquals(1, accounting.getScores().getKills(OWNER, ScrUnitIds.TERRAN_MARINE));
    }
}
