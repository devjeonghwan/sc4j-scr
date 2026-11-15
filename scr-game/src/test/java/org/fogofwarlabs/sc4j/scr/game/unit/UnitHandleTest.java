package org.fogofwarlabs.sc4j.scr.game.unit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests native handle arithmetic independently of unit allocation and memory lookup.
 */
final class UnitHandleTest {
    /**
     * The classic unit capacity, the largest capacity with the narrow handle layout.
     */
    private static final int CLASSIC_CAPACITY = 1700;
    private static final int EXTENDED_CAPACITY = 1701;
    private static final int RECORDED_HANDLE = 0x0E26;
    private static final int GENERATION_COUNT = 32;
    private static final int GENERATION_SAMPLES = 256;

    @Test
    void get_index_bit_count_should_widen_above_1700_with_a_signed_comparison() {
        Assertions.assertEquals(11, UnitHandle.getIndexBitCount(0));
        Assertions.assertEquals(11, UnitHandle.getIndexBitCount(CLASSIC_CAPACITY));
        Assertions.assertEquals(13, UnitHandle.getIndexBitCount(EXTENDED_CAPACITY));
        Assertions.assertEquals(13, UnitHandle.getIndexBitCount(Integer.MAX_VALUE));
        Assertions.assertEquals(11, UnitHandle.getIndexBitCount(Integer.MIN_VALUE));
        Assertions.assertEquals(11, UnitHandle.getIndexBitCount(-1));
    }

    @Test
    void recorded_handles_decode_and_encode_under_both_capacity_layouts() {
        Assertions.assertEquals(1574, UnitHandle.getPoolIndex(RECORDED_HANDLE, CLASSIC_CAPACITY));
        Assertions.assertEquals(1, UnitHandle.getGeneration(RECORDED_HANDLE, CLASSIC_CAPACITY));
        Assertions.assertEquals(3622, UnitHandle.getPoolIndex(RECORDED_HANDLE, EXTENDED_CAPACITY));
        Assertions.assertEquals(0, UnitHandle.getGeneration(RECORDED_HANDLE, EXTENDED_CAPACITY));
        Assertions.assertEquals(RECORDED_HANDLE, UnitHandle.encode(1574, 1, CLASSIC_CAPACITY));
        Assertions.assertEquals(0x3FFFF, UnitHandle.encode(8191, 31, 8191));
    }

    @Test
    void malformed_handles_decode_with_their_bits_and_an_arithmetic_shift() {
        Assertions.assertEquals(0, UnitHandle.getPoolIndex(0x800, CLASSIC_CAPACITY));
        Assertions.assertEquals(2047, UnitHandle.getPoolIndex(-1, CLASSIC_CAPACITY));
        Assertions.assertEquals(8191, UnitHandle.getPoolIndex(-1, EXTENDED_CAPACITY));
        Assertions.assertEquals(-1, UnitHandle.getGeneration(-1, CLASSIC_CAPACITY));
        Assertions.assertEquals(-1048576, UnitHandle.getGeneration(Integer.MIN_VALUE, CLASSIC_CAPACITY));
        Assertions.assertEquals(-262144, UnitHandle.getGeneration(Integer.MIN_VALUE, EXTENDED_CAPACITY));
        Assertions.assertEquals(256, UnitHandle.getGeneration(0x80001, CLASSIC_CAPACITY));
    }

    @Test
    void encode_should_keep_the_generation_byte_and_compare_the_index_unsigned() {
        Assertions.assertEquals(0x7F801, UnitHandle.encode(1, 255, CLASSIC_CAPACITY));
        Assertions.assertEquals(0x801, UnitHandle.encode(1, 257, CLASSIC_CAPACITY));
        Assertions.assertEquals(0, UnitHandle.encode(EXTENDED_CAPACITY, 1, CLASSIC_CAPACITY));
        Assertions.assertEquals(0, UnitHandle.encode(-1, 1, CLASSIC_CAPACITY));
        Assertions.assertEquals(0x801, UnitHandle.encode(1, 1, -1));
        Assertions.assertEquals(0x800, UnitHandle.encode(0, 1, CLASSIC_CAPACITY));
    }

    @Test
    void next_generation_should_wrap_every_thirty_two_generations() {
        for (int generation = 0; generation < GENERATION_SAMPLES; generation++) {
            Assertions.assertEquals((generation % GENERATION_COUNT + 1) % GENERATION_COUNT, UnitHandle.nextGeneration(generation));
        }

        Assertions.assertEquals(0, UnitHandle.nextGeneration(Integer.MAX_VALUE));
    }
}
