package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.engine.player.Player;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnits;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.game.movement.FlingyMotionUtils;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * Checks the engine fields of a unit, unit direction vectors, the rank increase byte and the narrowing, packing and reset ranges of the unit
 * fields.
 */
final class UnitTest {
    /**
     * An owner BYTE past the players of a pool without game players.
     */
    private static final int OWNER_WITHOUT_PLAYER = 13;
    /**
     * A coordinate past the signed WORD range of the native position.
     */
    private static final int TRUNCATED_POSITION = 0x18000;
    private static final int HIGHLIGHTED_ORDER_COUNT_OFFSET = 0xC8;
    /**
     * The native byte offset of the rank increase, independent of its neighbors.
     */
    private static final int RANK_INCREASE_OFFSET = 0xD2;
    private static final int USER_ACTION_FLAGS_OFFSET = 0xD7;
    private static final int CURRENT_BUTTON_SET_OFFSET = 0xD8;
    private static final int CURRENT_BUTTON_SET_HIGH_OFFSET = 0xD9;
    private static final int CLOAK_COUNTER_OFFSET = 0xDA;
    private static final int PREVIOUS_VISIBLE_HIT_POINTS_OFFSET = 0xF2;
    /**
     * The high BYTE of the first loaded unit handle, followed by the low BYTE of the second.
     */
    private static final int LOADED_HANDLE_BOUNDARY_OFFSET = 0xF5;
    private static final int RESOURCE_FIELDS_OFFSET = 0x128;
    private static final int RESOURCE_ANIMATION_OFFSET = 0x12A;
    private static final int UPPER_LARVA_SIDE_HIGH_OFFSET = 0x12B;
    private static final int RESOURCE_FIELDS_END = 0x130;
    private static final int RESOURCE_AREA_INDEX_OFFSET = 0x138;
    private static final int CARRYING_FLAGS_OFFSET = 0x144;
    private static final int DETECTION_FLAGS_OFFSET = 0x148;
    private static final int HEALING_STATE_OFFSET = 0x193;
    private static final int LIFETIME_AND_DURATIONS_OFFSET = 0x19C;
    private static final int FIRST_STATUS_TIMERS_OFFSET = 0x1A0;
    private static final int SECOND_STATUS_TIMERS_OFFSET = 0x1A4;
    private static final int LIFETIME_AND_DURATIONS_END = 0x1A8;
    private static final int STATUS_EFFECTS_OFFSET = 0x1B1;
    private static final int STATUS_EFFECTS_END = 0x1B5;
    private static final int ACID_SPORE_COUNT_OFFSET = 0x1B6;
    private static final int SECOND_ACID_SPORE_TIMER_OFFSET = 0x1B8;
    private static final int ACID_SPORE_TIMERS_END = 0x1C0;
    private static final int LOADED_HANDLE_EXTENSION_OFFSET = 0x1C2;
    private static final int LOADED_HANDLE_EXTENSION_HIGH_OFFSET = 0x1C3;
    private static final int LOADED_HANDLE_EXTENSION_END = 0x1C4;
    private static final int REPULSION_FIELD_OFFSET = 0x1E4;

    private static final int LARVA_SIDE_COUNT = 4;
    private static final int ACID_SPORE_TIMER_COUNT = 9;
    private static final int CLEARED_ACID_SPORE_TIMERS = 3;
    private static final int LOADED_HANDLE_COUNT = 8;
    private static final int RETAINED_HANDLE_INDEX = 3;
    private static final int LAST_LOADED_HANDLE_INDEX = 7;
    /**
     * The multiplier and addend of the distinct byte pattern written over the unit memory.
     */
    private static final int PATTERN_MULTIPLIER = 73;
    private static final int PATTERN_ADDEND = 91;
    private static final int FILL_BYTE = 0xA5;
    private static final int FILL_WORD = 0xA5A5;

    @Test
    void native_setters_write_the_engine_type_owner_and_position() {
        Unit unit = UnitTestUtils.createUnit(1);

        unit.setUnitId(ScrUnitIds.TERRAN_MARINE);
        unit.setOwnerIndex(OWNER_WITHOUT_PLAYER);
        unit.setPositionX(TRUNCATED_POSITION);
        unit.setPositionY(-1);

        Assertions.assertSame(ScrUnits.TERRAN_MARINE, unit.getType());
        Assertions.assertEquals(OWNER_WITHOUT_PLAYER, unit.getOwnerIndex());
        Assertions.assertNull(unit.getOwner());
        Assertions.assertEquals((short) TRUNCATED_POSITION, unit.getPositionX());
        Assertions.assertEquals(-1, unit.getPositionY());

        unit.setUnitId(ScrUnits.COUNT);

        Assertions.assertNull(unit.getType());
    }

    @Test
    void owner_byte_names_the_player_of_the_pool() {
        UnitPool pool = new UnitPool(1);
        Player[] players = {new Player(0), new Player(1)};
        Unit unit = pool.getUnit(1);

        unit.setOwnerIndex(1);
        pool.setPlayers(players);

        Assertions.assertSame(players[1], unit.getOwner());

        unit.setOwnerIndex(0);

        Assertions.assertSame(players[0], unit.getOwner());
        Assertions.assertSame(unit, Unit.of(unit));
        Assertions.assertThrows(IllegalArgumentException.class, () -> Unit.of(new org.fogofwarlabs.sc4j.engine.entity.Unit(1)));
    }

    @Test
    void calculate_native_direction_vector_should_wrap_direction_as_a_byte() {
        Assertions.assertEquals(FlingyMotionUtils.calculateDirectionVector(252, 32 << 8),
                                FlingyMotionUtils.calculateDirectionVector(-4, 32 << 8),
                                "heading - 64 below zero wraps as a direction BYTE");
        Assertions.assertEquals(FlingyMotionUtils.calculateDirectionVector(144, 64), FlingyMotionUtils.calculateDirectionVector(-112, 64));
        Assertions.assertEquals(FlingyMotionUtils.calculateDirectionVector(1, 64), FlingyMotionUtils.calculateDirectionVector(257, 64));
    }

    @Test
    void resource_animation_shares_the_larva_side_word_without_replacing_its_upper_byte() {
        Unit unit = UnitTestUtils.createUnit(1);

        unit.setSecondLarvaSideCount(0xABCD);

        Assertions.assertEquals(0xCD, unit.getResourceAnimation());

        unit.setResourceAnimation(0x114);

        Assertions.assertEquals((short) 0xAB14, unit.getLarvaSideCount(Unit.LARVA_SIDE_UPPER));
        Assertions.assertEquals(0x14, unit.getResourceAnimation());

        UnitMemoryScenario.of(unit).writeMemoryByte(UPPER_LARVA_SIDE_HIGH_OFFSET, 0x56);

        Assertions.assertEquals(0x5614, unit.getLarvaSideCount(Unit.LARVA_SIDE_UPPER));

        UnitMemoryScenario.of(unit).clearMemoryBytes(RESOURCE_ANIMATION_OFFSET, 1);

        Assertions.assertEquals(0x5600, unit.getLarvaSideCount(Unit.LARVA_SIDE_UPPER));
        Assertions.assertEquals(0, unit.getResourceAnimation());
    }

    @Test
    void status_bytes_map_packed_writes_and_narrow_setter_values() {
        Unit unit = UnitTestUtils.createUnit(1);

        UnitMemoryScenario.of(unit).writeMemoryInteger(FIRST_STATUS_TIMERS_OFFSET, 0x84736251);
        UnitMemoryScenario.of(unit).writeMemoryInteger(SECOND_STATUS_TIMERS_OFFSET, 0xC8B7A695);

        Assertions.assertEquals(0x51, unit.getDefensiveMatrixTimer());
        Assertions.assertEquals(0x62, unit.getSpeedIncreaseByte());
        Assertions.assertEquals(0x73, unit.getSpeedDecreaseByte());
        Assertions.assertEquals(0x84, unit.getLockdownTimer());
        Assertions.assertEquals(0x95, unit.getIrradiateTimer());
        Assertions.assertEquals(0xA6, unit.getStasisTimer());
        Assertions.assertEquals(0xB7, unit.getPlagueTimer());
        Assertions.assertEquals(0xC8, unit.getStormTimer());

        unit.setLockdownTimer(0x1FF);
        UnitMemoryScenario.of(unit).clearMemoryBytes(SECOND_STATUS_TIMERS_OFFSET, Short.BYTES);

        Assertions.assertEquals(0xFF736251, UnitMemoryScenario.of(unit).readMemoryInteger(FIRST_STATUS_TIMERS_OFFSET));
        Assertions.assertEquals(0xC8B70000, UnitMemoryScenario.of(unit).readMemoryInteger(SECOND_STATUS_TIMERS_OFFSET));

        unit.setHealingState(0x180);

        Assertions.assertEquals(0x80, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(HEALING_STATE_OFFSET));

        unit.setHighlightedOrderCount(0x101);
        unit.setUserActionFlags(-1);

        Assertions.assertEquals(1, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(HIGHLIGHTED_ORDER_COUNT_OFFSET));
        Assertions.assertEquals(255, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(USER_ACTION_FLAGS_OFFSET));
    }

    @Test
    void acid_spore_count_and_timers_stay_independent_and_bounded() {
        Unit unit = UnitTestUtils.createUnit(1);

        unit.setAcidSporeCount(0x109);

        for (int index = 0; index < ACID_SPORE_TIMER_COUNT; index++) {
            unit.setAcidSporeTimer(index, 0x100 + index + 1);
        }

        UnitMemoryScenario.of(unit).clearMemoryBytes(SECOND_ACID_SPORE_TIMER_OFFSET, CLEARED_ACID_SPORE_TIMERS);

        Assertions.assertEquals(9, unit.getAcidSporeCount());
        Assertions.assertEquals(1, unit.getAcidSporeTimer(0));
        Assertions.assertEquals(0, unit.getAcidSporeTimer(1));
        Assertions.assertEquals(0, unit.getAcidSporeTimer(2));
        Assertions.assertEquals(0, unit.getAcidSporeTimer(3));
        Assertions.assertEquals(5, unit.getAcidSporeTimer(4));
        Assertions.assertEquals(9, unit.getAcidSporeTimer(ACID_SPORE_TIMER_COUNT - 1));
        Assertions.assertThrows(ArrayIndexOutOfBoundsException.class, () -> unit.getAcidSporeTimer(-1));
        Assertions.assertThrows(ArrayIndexOutOfBoundsException.class, () -> unit.setAcidSporeTimer(-1, 4));
        Assertions.assertThrows(ArrayIndexOutOfBoundsException.class, () -> unit.getAcidSporeTimer(ACID_SPORE_TIMER_COUNT));
        Assertions.assertThrows(ArrayIndexOutOfBoundsException.class, () -> unit.setAcidSporeTimer(ACID_SPORE_TIMER_COUNT, 4));
        Assertions.assertEquals(9, unit.getAcidSporeCount());

        UnitMemoryScenario.of(unit).writeMemoryByte(ACID_SPORE_COUNT_OFFSET, 4);

        Assertions.assertEquals(4, unit.getAcidSporeCount());
    }

    @Test
    void presentation_and_repulsion_setters_narrow_without_changing_packed_neighbors() {
        Unit unit = UnitTestUtils.createUnit(1);

        UnitMemoryScenario.of(unit).writeMemoryInteger(CARRYING_FLAGS_OFFSET, 0x88776655);

        Assertions.assertEquals(0x55, unit.getCarryingFlags());
        Assertions.assertEquals(0x66, unit.getWireframeRandomizer());

        unit.setWireframeRandomizer(0x123);

        Assertions.assertEquals(0x88772355, UnitMemoryScenario.of(unit).readMemoryInteger(CARRYING_FLAGS_OFFSET));

        unit.setDetectionFlags(0x80000000);

        Assertions.assertEquals(0x80000000, UnitMemoryScenario.of(unit).readMemoryInteger(DETECTION_FLAGS_OFFSET));

        UnitMemoryScenario.of(unit).writeMemoryInteger(REPULSION_FIELD_OFFSET, 0xFFEEDDCC);

        Assertions.assertEquals(0xCC, unit.getRepulsionFlags());
        Assertions.assertEquals(0xDD, unit.getRepulsionDirection());
        Assertions.assertEquals(0xEE, unit.getRepulsionCellX());
        Assertions.assertEquals(0xFF, unit.getRepulsionCellY());

        unit.setRepulsionCellX(0x101);
        unit.setRepulsionCellY(-2);

        Assertions.assertEquals(0xFE01DDCC, UnitMemoryScenario.of(unit).readMemoryInteger(REPULSION_FIELD_OFFSET));

        UnitMemoryScenario.of(unit).writeMemoryInteger(PREVIOUS_VISIBLE_HIT_POINTS_OFFSET, 0xABCD1234);
        unit.setPreviousVisibleHitPoints(0x1FFFF);

        Assertions.assertEquals(0xABCDFFFF, UnitMemoryScenario.of(unit).readMemoryInteger(PREVIOUS_VISIBLE_HIT_POINTS_OFFSET));
    }

    @Test
    void loaded_handles_keep_word_parts_and_reject_out_of_range_indices() {
        Unit unit = UnitTestUtils.createUnit(1);

        unit.setLoadedUnitHandle(0, 0x12345);
        unit.setLoadedUnitHandle(1, 0xABCD);
        unit.setLoadedUnitHandleExtensionBits(0x1A55A);
        UnitMemoryScenario.of(unit).writeMemoryWord(LOADED_HANDLE_BOUNDARY_OFFSET, 0xBEEF);

        Assertions.assertEquals(0xEF45, unit.getLoadedUnitHandle(0));
        Assertions.assertEquals(0xABBE, unit.getLoadedUnitHandle(1));

        UnitMemoryScenario.of(unit).writeMemoryByte(LOADED_HANDLE_EXTENSION_HIGH_OFFSET, 0x71);

        Assertions.assertEquals(0x715A, unit.getLoadedUnitHandleExtensionBits());

        UnitMemoryScenario.of(unit).clearMemoryBytes(LOADED_HANDLE_BOUNDARY_OFFSET, Short.BYTES);

        Assertions.assertEquals(0x0045, unit.getLoadedUnitHandle(0));
        Assertions.assertEquals(0xAB00, unit.getLoadedUnitHandle(1));
        Assertions.assertThrows(ArrayIndexOutOfBoundsException.class, () -> unit.getLoadedUnitHandle(-1));
        Assertions.assertThrows(ArrayIndexOutOfBoundsException.class, () -> unit.setLoadedUnitHandle(LOADED_HANDLE_COUNT, 1));

        UnitMemoryScenario.of(unit).writeMemoryInteger(RESOURCE_AREA_INDEX_OFFSET, 0xAABBCCDD);
        unit.setResourceAreaIndex(0x123);

        Assertions.assertEquals(0xAABBCC23, UnitMemoryScenario.of(unit).readMemoryInteger(RESOURCE_AREA_INDEX_OFFSET));
    }

    @Test
    void status_reset_clears_only_its_range_and_failed_initialization_changes_nothing() {
        Unit unit = UnitTestUtils.createUnit(1);

        for (int offset = 0; offset < UnitMemoryCheck.SIZE; offset++) {
            UnitMemoryScenario.of(unit).writeMemoryByte(offset, FILL_BYTE);
        }

        unit.setDefensiveMatrixHealth(0x1FFFF);

        byte[] expected = UnitMemoryScenario.of(unit).exportMemoryBytes();

        Arrays.fill(expected, LIFETIME_AND_DURATIONS_OFFSET, LIFETIME_AND_DURATIONS_END, (byte) 0);

        unit.resetLifetimeAndStatusDurations();

        Assertions.assertArrayEquals(expected, UnitMemoryScenario.of(unit).exportMemoryBytes());
        Assertions.assertEquals(0, unit.getDefensiveMatrixHealth());
        Assertions.assertEquals(FILL_BYTE, unit.getMaelstromTimer());
        Assertions.assertEquals(FILL_WORD, unit.getLoadedUnitHandle(LAST_LOADED_HANDLE_INDEX));

        UnitStorageConstructor.clearBeforeInnerInitialization(unit);

        byte[] beforeFailure = UnitMemoryScenario.of(unit).exportMemoryBytes();
        Game game = Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));
        UnitInitializer initializer = game.getUnitInitializer();

        Assertions.assertThrows(UnsupportedOperationException.class, () -> initializer.initialize(unit, 0xFFFF, 0, 0, 0, 1));
        Assertions.assertArrayEquals(beforeFailure, UnitMemoryScenario.of(unit).exportMemoryBytes());
        Assertions.assertEquals(FILL_WORD, unit.getLoadedUnitHandle(LAST_LOADED_HANDLE_INDEX));
        Assertions.assertEquals(0, unit.getLoadedUnitHandleExtensionBits());
    }

    @Test
    void reset_clears_role_and_status_fields_but_keeps_other_bytes_and_loaded_handles() {
        Unit unit = UnitTestUtils.createUnit(1);
        byte[] expected = new byte[UnitMemoryCheck.SIZE];

        for (int offset = 0; offset < expected.length; offset++) {
            expected[offset] = (byte) (offset * PATTERN_MULTIPLIER + PATTERN_ADDEND);

            UnitMemoryScenario.of(unit).writeMemoryByte(offset, expected[offset]);
        }

        int retainedHandle = unit.getLoadedUnitHandle(RETAINED_HANDLE_INDEX);

        unit.resetResourceAndLinkState();
        unit.resetStatusEffectsAndLoadedUnitExtensions();
        Arrays.fill(expected, RESOURCE_FIELDS_OFFSET, RESOURCE_FIELDS_END, (byte) 0);

        expected[RESOURCE_AREA_INDEX_OFFSET] = 0;

        Arrays.fill(expected, STATUS_EFFECTS_OFFSET, STATUS_EFFECTS_END, (byte) 0);
        Arrays.fill(expected, ACID_SPORE_COUNT_OFFSET, ACID_SPORE_TIMERS_END, (byte) 0);
        Arrays.fill(expected, LOADED_HANDLE_EXTENSION_OFFSET, LOADED_HANDLE_EXTENSION_END, (byte) 0);

        Assertions.assertArrayEquals(expected, UnitMemoryScenario.of(unit).exportCaptureBytes());
        Assertions.assertEquals(0, unit.getResources());

        for (int side = 0; side < LARVA_SIDE_COUNT; side++) {
            Assertions.assertEquals(0, unit.getLarvaSideCount(side));
        }

        Assertions.assertEquals(0, unit.getResourceAreaIndex());
        Assertions.assertEquals(0, unit.getLoadedUnitHandleExtensionBits());
        Assertions.assertEquals(retainedHandle, unit.getLoadedUnitHandle(RETAINED_HANDLE_INDEX));
    }

    @Test
    void current_button_set_keeps_its_word_width_under_overlapping_byte_writes() {
        Unit unit = UnitTestUtils.createUnit(1);

        UnitMemoryScenario.of(unit).writeMemoryByte(USER_ACTION_FLAGS_OFFSET, 0x91);
        UnitMemoryScenario.of(unit).writeMemoryByte(CLOAK_COUNTER_OFFSET, 0x92);
        unit.setCurrentButtonSet(0x12345678);

        Assertions.assertEquals(0x5678, unit.getCurrentButtonSet());
        Assertions.assertEquals(0x5678, UnitMemoryScenario.of(unit).readMemoryUnsignedWord(CURRENT_BUTTON_SET_OFFSET));

        UnitMemoryScenario.of(unit).writeMemoryInteger(USER_ACTION_FLAGS_OFFSET, 0xA1B2C3D4);

        Assertions.assertEquals(0xB2C3, unit.getCurrentButtonSet());
        Assertions.assertEquals(0xD4, unit.getUserActionFlags());
        Assertions.assertEquals(0xA1, unit.getCloakCounter());

        UnitMemoryScenario.of(unit).clearMemoryBytes(CURRENT_BUTTON_SET_HIGH_OFFSET, 1);

        Assertions.assertEquals(0xC3, unit.getCurrentButtonSet());
    }

    @Test
    void get_rank_increase_should_read_only_its_own_byte() {
        Unit unit = UnitTestUtils.createUnit(1);

        Assertions.assertEquals(0, unit.getRankIncrease(), "the initializer clears the byte");

        NativeUnitFields.writeByte(unit, RANK_INCREASE_OFFSET, 7);
        NativeUnitFields.writeByte(unit, RANK_INCREASE_OFFSET - 1, 0x55);
        NativeUnitFields.writeByte(unit, RANK_INCREASE_OFFSET + 1, 0x66);

        Assertions.assertEquals(7, unit.getRankIncrease());
        Assertions.assertEquals(7, NativeUnitFields.readByte(unit, RANK_INCREASE_OFFSET));
    }
}
