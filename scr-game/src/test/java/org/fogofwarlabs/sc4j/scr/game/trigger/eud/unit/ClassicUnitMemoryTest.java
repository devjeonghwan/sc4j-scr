package org.fogofwarlabs.sc4j.scr.game.trigger.eud.unit;

import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerModifiers;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks bounded unit memory reads, writes, masks and encoded unsigned comparisons.
 */
final class ClassicUnitMemoryTest {
    private static final int UNIT_TABLE = 0x59CCA8;
    private static final int UNIT_ENTRY_SIZE = 336;
    private static final int UNIT_COUNT = 1700;
    private static final int DEATH_TABLE = 0x58A364;

    private static final int HIT_POINTS = 0x08;
    private static final int SHIELD_POINTS = 0x60;
    private static final int ENERGY = 0xA2;
    private static final int RESOURCES = 0xD0;
    private static final int STATUS = 0xDC;

    @Test
    void unit_memory_reads_and_writes_unit_fields_in_first_and_last_slots() {
        UnitPool pool = new UnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);
        ClassicUnitMemory memory = new ClassicUnitMemory(pool);

        for (int poolIndex : new int[]{1, 2, UNIT_COUNT}) {
            Unit unit = pool.getUnit(poolIndex);
            int address = UNIT_TABLE + (poolIndex - 1) * UNIT_ENTRY_SIZE;

            unit.setHitPoints(0x81234567);
            unit.setShieldPoints(0x12345678);
            unit.setEnergy(0xABCD);
            unit.setResources(0xFEDC);
            unit.setStatus(0x87654321);

            Assertions.assertEquals(0x81234567, memory.read(address + HIT_POINTS, Integer.BYTES));
            Assertions.assertEquals(0x12345678, memory.read(address + SHIELD_POINTS, Integer.BYTES));
            Assertions.assertEquals(0xABCD, memory.read(address + ENERGY, Short.BYTES));
            Assertions.assertEquals(0xFEDC, memory.read(address + RESOURCES, Short.BYTES));
            Assertions.assertEquals(0x87654321, memory.read(address + STATUS, Integer.BYTES));

            memory.write(address + HIT_POINTS, Integer.BYTES, -1);
            memory.write(address + SHIELD_POINTS, Integer.BYTES, Integer.MIN_VALUE);
            memory.write(address + ENERGY, Short.BYTES, 0x12345);
            memory.write(address + RESOURCES, Short.BYTES, -2);
            memory.write(address + STATUS, Integer.BYTES, 3);

            Assertions.assertEquals(-1, unit.getHitPoints());
            Assertions.assertEquals(Integer.MIN_VALUE, unit.getShieldPoints());
            Assertions.assertEquals(0x2345, unit.getEnergy());
            Assertions.assertEquals(65534, unit.getResources());
            Assertions.assertEquals(3, unit.getStatus());
            Assertions.assertFalse(pool.isAllocated(poolIndex));
        }
    }

    @Test
    void unit_memory_merges_partial_and_masked_writes_without_replacing_neighbors() {
        UnitPool pool = new UnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);
        ClassicUnitMemory memory = new ClassicUnitMemory(pool);
        Unit unit = pool.getUnit(1);

        unit.setHitPoints(0xA1B2C3D4);
        unit.setResources(0xABCD);

        memory.write(UNIT_TABLE + HIT_POINTS + 1, Short.BYTES, 0x12345);

        Assertions.assertEquals(0xA12345D4, unit.getHitPoints());
        Assertions.assertEquals(0x45, memory.read(UNIT_TABLE + HIT_POINTS + 1, Byte.BYTES));
        Assertions.assertEquals(0xA123, memory.read(UNIT_TABLE + HIT_POINTS + 2, Short.BYTES));

        memory.write(UNIT_TABLE + RESOURCES, Byte.BYTES, 0x1FE);

        Assertions.assertEquals(0xABFE, unit.getResources());

        unit.setStatus(0x12345678);
        memory.writeMaskedInteger(UNIT_TABLE + STATUS, 0xABCDEF01, 0x00FF00FF);

        Assertions.assertEquals(0x12CD5601, unit.getStatus());

        memory.writeMaskedInteger(UNIT_TABLE + STATUS, -1, 0);

        Assertions.assertEquals(0x12CD5601, unit.getStatus());
    }

    @Test
    void unit_memory_rejects_unsupported_intervals_before_changing_game_state() {
        UnitPool pool = new UnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);
        ClassicUnitMemory memory = new ClassicUnitMemory(pool);
        Unit unit = pool.getUnit(1);

        unit.setHitPoints(12345);
        unit.setEnergy(0x1234);

        for (int address : new int[]{0, -1, UNIT_TABLE - 1, UNIT_TABLE + UNIT_COUNT * UNIT_ENTRY_SIZE, UNIT_TABLE + 0x0B,
                                     UNIT_TABLE + 0xA0, UNIT_TABLE + 0x128}) {
            Assertions.assertThrows(UnsupportedOperationException.class, () -> memory.write(address, Integer.BYTES, -1));
        }

        Assertions.assertThrows(IllegalArgumentException.class, () -> memory.write(UNIT_TABLE + HIT_POINTS, 3, -1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ClassicUnitMemory(new UnitPool(2 * UNIT_COUNT)));
        Assertions.assertEquals(12345, unit.getHitPoints());
        Assertions.assertEquals(0x1234, unit.getEnergy());
    }

    @Test
    void deaths_conditions_and_set_to_act_on_persistent_pool_state() {
        UnitPool pool = new UnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);
        ClassicUnitMemory memory = new ClassicUnitMemory(pool);
        int playerOperand = (UNIT_TABLE + HIT_POINTS - DEATH_TABLE) / Integer.BYTES;

        Assertions.assertEquals(UNIT_TABLE + HIT_POINTS, ClassicUnitMemory.getDeathsAddress(playerOperand, 0));
        Assertions.assertEquals(UNIT_TABLE + HIT_POINTS, ClassicUnitMemory.getDeathsAddress(playerOperand - PlayerTable.PLAYER_SLOTS, 1));
        Assertions.assertEquals(UNIT_TABLE + HIT_POINTS, ClassicUnitMemory.getDeathsAddress(playerOperand - PlayerTable.PLAYER_SLOTS, 0x10001));
        Assertions.assertEquals(DEATH_TABLE - Integer.BYTES, ClassicUnitMemory.getDeathsAddress(-1, 0));

        memory.setDeaths(playerOperand, 0, TriggerModifiers.SET_TO, -1);

        Assertions.assertEquals(-1, pool.getUnit(1).getHitPoints());
        Assertions.assertTrue(memory.testDeaths(playerOperand, 0, TriggerModifiers.AT_LEAST, Integer.MIN_VALUE));
        Assertions.assertFalse(memory.testDeaths(playerOperand, 0, TriggerModifiers.AT_MOST, Integer.MIN_VALUE));
        Assertions.assertTrue(memory.testDeaths(playerOperand, 0, TriggerModifiers.EXACTLY, -1));

        pool.getUnit(1).setHitPoints(256);

        Assertions.assertTrue(memory.testDeaths(playerOperand, 0, TriggerModifiers.EXACTLY, 256));
        Assertions.assertThrows(IllegalArgumentException.class, () -> memory.setDeaths(playerOperand, 0, TriggerModifiers.ADD, 1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> memory.testDeaths(playerOperand, 0, 9, 256));
        Assertions.assertEquals(256, pool.getUnit(1).getHitPoints());

        Unit initializedUnit = pool.getUnit(pool.getFirstFreeIndex());

        pool.advanceGeneration(initializedUnit);
        pool.commitInitializedUnit(initializedUnit);

        Assertions.assertEquals(1, initializedUnit.getIndex());
        Assertions.assertEquals(256, memory.read(UNIT_TABLE + HIT_POINTS, Integer.BYTES));
    }
}
