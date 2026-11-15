package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.game.ai.ComputerUnitRecord;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Verifies storage timing and retained bytes against extracted native initialization.
 */
final class UnitStorageConstructorTest {
    private static final int FILL_BYTE = 0xA5;
    private static final int FILL_DWORD = 0xA5A5A5A5;
    /**
     * The ranges that the pre-initializer clear resets, each an offset and a length.
     */
    private static final int @NotNull [] @NotNull [] CLEARED_RANGES = {
            {0x128, 24}, {0x160, 40}, {0xA8, 44}, {0x19C, 12}, {0x1A8, 28}, {0x140, 4}, {0xD7, 1}, {0x191, 1}, {0x1C8, 8}
    };
    private static final int PREVIOUS_VISIBLE_HIT_POINTS_OFFSET = 0xF2;
    private static final int LAST_ACID_SPORE_TIMER_INDEX = 8;

    private static final int UNITS_DAT_SIZE = 19876;
    private static final int HIT_POINTS_COLUMN_OFFSET = 3156;
    private static final int TWO_POOL_CAPACITY = 2;
    private static final int GENERATION_ADVANCES = 31;
    private static final int HIT_POINTS_OFFSET = 0x10;
    private static final int PRIMARY_ORDER_OFFSET = 0x69;
    private static final int ORDER_TARGET_UNIT_TYPE_OFFSET = 0x6C;
    private static final int UNIT_TYPE_OFFSET = 0x8C;
    private static final int CURRENT_BUTTON_SET_OFFSET = 0xD8;
    private static final int SECONDARY_ORDER_OFFSET = 0xEA;
    private static final int SECONDARY_ORDER_STATE_OFFSET = 0x146;
    private static final int SECONDARY_ORDER_POSITION_OFFSET = 0x150;
    /**
     * A reference field after the secondary order position that the initialization keeps.
     */
    private static final int RETAINED_REFERENCE_OFFSET = 0x158;
    private static final int RANDOM_SEED = 0x12345678;
    private static final int INITIALIZATION_RANDOM_SOURCE = 15;
    /**
     * The units.dat hit points of the SCV in the storage test and the starting hit points they give, a tenth of them unsigned.
     */
    private static final int MAXIMUM_HIT_POINTS = 0xFFFFFFFF;
    private static final int STARTING_HIT_POINTS = 429496729;
    /**
     * The secondary order fields that the storage test writes before the initialization: the order, its position and its state.
     */
    private static final int NOTHING_ORDER = 23;
    private static final int SECONDARY_ORDER_POSITION = 0x12345678;
    private static final int SECONDARY_ORDER_STATE = 199;
    private static final int RETAINED_REFERENCE = 0x76543210;
    /**
     * The button set of a unit under construction and the order target type identifier {@code None}.
     */
    private static final int CANCEL_CONSTRUCTION_BUTTON_SET = 231;
    private static final int NO_UNIT_TYPE = 228;

    /**
     * The next near-target index a Valkyrie leaves after forty Halo Rockets bullets.
     */
    private static final int RELEASED_NEAR_TARGET_INDEX = 12;
    /**
     * Local event bytes that a loaded game left in the slot.
     */
    private static final int RELEASED_LOCAL_EVENT_BYTES = 0x1234;
    /**
     * The nonzero gathering byte and silo ready DWORD a released resource or Nuclear Silo leaves in the slot.
     */
    private static final int RELEASED_ROLE_VALUE = 1;
    private static final int RELEASED_RESOURCES = 1234;
    private static final int RELEASED_BUTTON_SET = 228;

    @Test
    void clear_before_inner_initialization_should_match_the_native_pre_initializer_capture() {
        Unit unit = UnitTestUtils.createUnit(1);

        for (int offset = 0; offset < UnitMemoryCheck.SIZE; offset++) {
            UnitMemoryScenario.of(unit).writeMemoryByte(offset, FILL_BYTE);
        }

        UnitStorageConstructor.clearBeforeInnerInitialization(unit);

        byte[] expected = new byte[UnitMemoryCheck.SIZE];

        Arrays.fill(expected, (byte) FILL_BYTE);

        for (int[] range : CLEARED_RANGES) {
            Arrays.fill(expected, range[0], range[0] + range[1], (byte) 0);
        }

        expected[PREVIOUS_VISIBLE_HIT_POINTS_OFFSET]     = 1;
        expected[PREVIOUS_VISIBLE_HIT_POINTS_OFFSET + 1] = 0;

        UnitDiagnosticAssert.assertDomainEquals(expected, unit);

        byte[] diagnostic = UnitMemoryScenario.of(unit).exportCaptureBytes();

        for (int offset = 0; offset < diagnostic.length; offset++) {
            if (!UnitMemoryCheck.isDomainByte(offset)) {
                Assertions.assertEquals((byte) FILL_BYTE, diagnostic[offset], "Opaque capture byte " + Integer.toHexString(offset));
            }
        }
    }

    @Test
    void initialize_storage_after_inner_should_advance_generation_and_random_before_commit() throws IOException {
        byte[] data = new byte[UNITS_DAT_SIZE];
        ByteBuffer bytes = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        bytes.putInt(HIT_POINTS_COLUMN_OFFSET + ScrUnitIds.TERRAN_SCV * Integer.BYTES, MAXIMUM_HIT_POINTS);

        DatTable units = UnitsDat.createSchema().parse(data);
        UnitPool pool = new UnitPool(TWO_POOL_CAPACITY);
        Unit unit = pool.getUnit(1);

        UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, ScrUnitIds.TERRAN_SCV);

        for (int generation = 0; generation < GENERATION_ADVANCES; generation++) {
            pool.advanceGeneration(unit);
        }

        UnitMemoryScenario.of(unit).writeMemoryByte(SECONDARY_ORDER_OFFSET, NOTHING_ORDER);
        UnitMemoryScenario.of(unit).writeMemoryInteger(SECONDARY_ORDER_POSITION_OFFSET, SECONDARY_ORDER_POSITION);
        UnitMemoryScenario.of(unit).writeMemoryInteger(RETAINED_REFERENCE_OFFSET, RETAINED_REFERENCE);
        UnitMemoryScenario.of(unit).writeMemoryByte(SECONDARY_ORDER_STATE_OFFSET, SECONDARY_ORDER_STATE);

        LcgRandom random = new LcgRandom(RANDOM_SEED);

        UnitStorageConstructor.initializeStorageAfterInner(pool, unit, ScrUnitIds.TERRAN_SCV, UnitsDat.createTypeTable(units), random);

        Assertions.assertEquals(0, pool.getGeneration(1));
        Assertions.assertEquals(1, pool.getFirstFreeIndex());
        Assertions.assertEquals(0, pool.getAllocatedCount());
        Assertions.assertEquals(1, random.getCallCount(INITIALIZATION_RANDOM_SOURCE));
        Assertions.assertEquals(STARTING_HIT_POINTS, UnitMemoryScenario.of(unit).readMemoryInteger(HIT_POINTS_OFFSET));
        Assertions.assertEquals(CANCEL_CONSTRUCTION_BUTTON_SET, UnitMemoryScenario.of(unit).readMemoryUnsignedWord(CURRENT_BUTTON_SET_OFFSET));
        Assertions.assertEquals(NOTHING_ORDER, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(PRIMARY_ORDER_OFFSET));
        Assertions.assertEquals(NO_UNIT_TYPE, UnitMemoryScenario.of(unit).readMemoryUnsignedWord(ORDER_TARGET_UNIT_TYPE_OFFSET));
        Assertions.assertEquals(SECONDARY_ORDER_POSITION, UnitMemoryScenario.of(unit).readMemoryInteger(SECONDARY_ORDER_POSITION_OFFSET));
        Assertions.assertEquals(RETAINED_REFERENCE, UnitMemoryScenario.of(unit).readMemoryInteger(RETAINED_REFERENCE_OFFSET));
        Assertions.assertEquals(SECONDARY_ORDER_STATE, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(SECONDARY_ORDER_STATE_OFFSET));

        pool.commitInitializedUnit(unit);

        Assertions.assertEquals(0, pool.getGeneration(1));
        Assertions.assertEquals(1, pool.getAllocatedCount());
    }

    @Test
    void clear_before_inner_initialization_should_reset_the_state_a_released_unit_leaves_in_its_slot() {
        Unit unit = UnitTestUtils.createUnit(1);
        Unit cloaked = UnitTestUtils.createUnit(2);

        unit.setNextHitNearTargetIndex(RELEASED_NEAR_TARGET_INDEX);
        unit.setPreviousUnitType(ScrUnitIds.ZERG_DRONE);
        unit.setLocalEventBytes(RELEASED_LOCAL_EVENT_BYTES);
        unit.setPreviousCloakedUnit(cloaked);
        unit.setNextCloakedUnit(cloaked);
        unit.setComputerRecord(new ComputerUnitRecord(ComputerUnitRecord.KIND_MILITARY, unit.getIndex()));

        UnitStorageConstructor.clearBeforeInnerInitialization(unit);

        Assertions.assertEquals(0, unit.getNextHitNearTargetIndex());
        Assertions.assertEquals(0, unit.getPreviousUnitType());
        Assertions.assertEquals(0, unit.getLocalEventBytes());
        Assertions.assertNull(unit.getPreviousCloakedUnit());
        Assertions.assertNull(unit.getNextCloakedUnit());
        Assertions.assertNull(unit.getComputerRecord());
    }

    @Test
    void clear_before_inner_initialization_should_reset_the_resource_and_link_state_a_morph_keeps() {
        Unit unit = UnitTestUtils.createUnit(1);
        Unit linked = UnitTestUtils.createUnit(2);

        unit.setResourceBeingGatheredByte(RELEASED_ROLE_VALUE);
        unit.setFirstGatheringWorker(linked);
        unit.setLastGatheringWorker(linked);
        unit.setPreviousGatheringWorker(linked);
        unit.setNextGatheringWorker(linked);
        unit.setGatherTarget(linked);
        unit.setPowerupCarrier(linked);
        unit.setSiloNuke(linked);
        unit.setSiloReadyDword(RELEASED_ROLE_VALUE);
        unit.setNydusExit(linked);

        UnitStorageConstructor.clearBeforeInnerInitialization(unit);

        Assertions.assertEquals(0, unit.getResourceBeingGatheredByte());
        Assertions.assertNull(unit.getFirstGatheringWorker());
        Assertions.assertNull(unit.getLastGatheringWorker());
        Assertions.assertNull(unit.getPreviousGatheringWorker());
        Assertions.assertNull(unit.getNextGatheringWorker());
        Assertions.assertNull(unit.getGatherTarget());
        Assertions.assertNull(unit.getPowerupCarrier());
        Assertions.assertNull(unit.getSiloNuke());
        Assertions.assertEquals(0, unit.getSiloReadyDword());
        Assertions.assertNull(unit.getNydusExit());
    }

    @Test
    void clear_before_inner_initialization_should_keep_typed_state_past_its_reset_range() {
        Unit unit = UnitTestUtils.createUnit(1);

        for (int offset = 0; offset < UnitMemoryCheck.SIZE; offset++) {
            UnitMemoryScenario.of(unit).writeMemoryByte(offset, FILL_BYTE);
        }

        UnitStorageConstructor.clearBeforeInnerInitialization(unit);

        Assertions.assertEquals(1, unit.getPreviousVisibleHitPoints());
        Assertions.assertEquals(0, unit.getParasiteFlags());
        Assertions.assertEquals(0, unit.getBlindnessState());
        Assertions.assertEquals(0, unit.getAcidSporeCount());
        Assertions.assertEquals(0, unit.getAcidSporeTimer(LAST_ACID_SPORE_TIMER_INDEX));
        Assertions.assertEquals(FILL_BYTE, unit.getCreepTimer());
        Assertions.assertEquals(FILL_BYTE, unit.getCarryingFlags());
        Assertions.assertEquals(FILL_BYTE, unit.getLastAttackingPlayer());
        Assertions.assertEquals(FILL_BYTE, unit.getWireframeRandomizer());
        Assertions.assertEquals(FILL_BYTE, unit.getCloakCounter());
        Assertions.assertEquals(FILL_DWORD, unit.getDetectionFlags());
        Assertions.assertEquals(FILL_BYTE, unit.getRepulsionFlags());
        Assertions.assertEquals(FILL_BYTE, unit.getRepulsionCellX());
    }

    @Test
    void clear_before_inner_initialization_should_reset_the_same_state_for_every_slot() {
        UnitPool pool = new UnitPool(TWO_POOL_CAPACITY);

        for (int poolIndex = 1; poolIndex <= TWO_POOL_CAPACITY; poolIndex++) {
            Unit unit = pool.getUnit(poolIndex);

            unit.setResources(RELEASED_RESOURCES);
            unit.setCurrentButtonSet(RELEASED_BUTTON_SET);

            UnitStorageConstructor.clearBeforeInnerInitialization(unit);

            Assertions.assertEquals(0, unit.getResources());
            Assertions.assertEquals(RELEASED_BUTTON_SET, unit.getCurrentButtonSet());
            Assertions.assertEquals(0, pool.getGeneration(poolIndex));
        }
    }
}
