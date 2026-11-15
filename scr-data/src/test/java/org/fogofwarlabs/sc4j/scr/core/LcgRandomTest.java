package org.fogofwarlabs.sc4j.scr.core;

import org.fogofwarlabs.sc4j.engine.random.RandomState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link LcgRandom} against independently computed reference sequences.
 */
final class LcgRandomTest {
    /**
     * The multiplier of {@code state = state * 22695477 + 1}, as the 64-bit reference computation and the 32-bit
     * expectation use it.
     */
    private static final long REFERENCE_MULTIPLIER = 22695477L;
    private static final int MULTIPLIER = 22695477;
    /**
     * The mask that keeps the 32 state bits of the 64-bit reference computation.
     */
    private static final long STATE_MASK = 0xFFFFFFFFL;
    /**
     * The output: state bits 16-30, a value of 15 bits.
     */
    private static final int OUTPUT_SHIFT = 16;
    private static final int OUTPUT_MASK = 0x7FFF;
    private static final int OUTPUT_BITS = 15;
    /**
     * The first output and state of the seed {@code 1}.
     */
    private static final int FIRST_OUTPUT = 346;
    private static final int FIRST_STATE = 22695478;
    /**
     * The number of calls compared with the reference computation.
     */
    private static final int REFERENCE_CALL_COUNT = 1000;
    /**
     * The inclusive range of the range mapping reference test.
     */
    private static final int RANGE_FROM = 15;
    private static final int RANGE_TO = 100;
    /**
     * An enable flag other than {@code 1}, as an EUD write leaves it.
     */
    private static final int EUD_ENABLED_FLAG = 7;
    /**
     * The seed of the save and restore test.
     */
    private static final int SAVED_SEED = 12345;

    @Test
    void next_should_produce_reference_sequence() {
        LcgRandom random = new LcgRandom(1);

        // Independently computed with 64-bit arithmetic from state = state * 22695477 + 1.
        long state = 1;

        for (int callIndex = 0; callIndex < REFERENCE_CALL_COUNT; callIndex++) {
            state = (state * REFERENCE_MULTIPLIER + 1) & STATE_MASK;

            int expected = (int) ((state >>> OUTPUT_SHIFT) & OUTPUT_MASK);

            Assertions.assertEquals(expected, random.next(0), "The sequence diverges at call " + callIndex);
        }
    }

    @Test
    void next_should_produce_known_first_value_and_state() {
        LcgRandom random = new LcgRandom(1);

        // The state becomes 22695478 = 0x015A4E36 and the output 0x015A = 346.
        Assertions.assertEquals(FIRST_OUTPUT, random.next(0));
        Assertions.assertEquals(FIRST_STATE, random.getState());
    }

    @Test
    void next_should_map_range_like_independent_reference_computation() {
        LcgRandom random = new LcgRandom(12345);
        long state = 12345;

        for (int callIndex = 0; callIndex < REFERENCE_CALL_COUNT; callIndex++) {
            state = (state * REFERENCE_MULTIPLIER + 1) & STATE_MASK;

            int rawValue = (int) ((state >>> OUTPUT_SHIFT) & OUTPUT_MASK);
            int expected = RANGE_FROM + (int) ((rawValue * (long) (RANGE_TO - RANGE_FROM + 1)) >> OUTPUT_BITS);

            Assertions.assertEquals(expected, random.next(0, RANGE_FROM, RANGE_TO), "The range mapping diverges at call " + callIndex);
        }
    }

    @Test
    void next_should_stay_within_inclusive_range() {
        LcgRandom random = new LcgRandom(999);

        for (int callIndex = 0; callIndex < 10000; callIndex++) {
            int value = random.next(0, -127, 128);

            Assertions.assertTrue(value >= -127 && value <= 128, "The value escaped the range: " + value);
        }
    }

    @Test
    void next_should_map_range_with_unsigned_shift_after_multiplication_overflow() {
        LcgRandom random = new LcgRandom(-1);

        // Raw value 32421 times 131072 wraps to 0xFD4A0000 before the logical shift.
        Assertions.assertEquals(129684, random.next(0, 0, 131071));
    }

    @Test
    void next_should_preserve_overflow_of_inclusive_range_width() {
        LcgRandom random = new LcgRandom(1);

        Assertions.assertEquals(Integer.MIN_VALUE, random.next(0, Integer.MIN_VALUE, Integer.MAX_VALUE));
        Assertions.assertEquals(FIRST_STATE, random.getState());
        Assertions.assertEquals(1, random.getTotalRandomCounts());
    }

    @Test
    void next_should_count_calls_per_source() {
        LcgRandom random = new LcgRandom(1);

        random.next(8);
        random.next(8);
        random.next(12);

        Assertions.assertEquals(2, random.getCallCount(8));
        Assertions.assertEquals(1, random.getCallCount(12));
        Assertions.assertEquals(0, random.getCallCount(9));
        Assertions.assertEquals(3, random.getTotalRandomCounts());
    }

    @Test
    void set_enabled_should_disable_generation_without_advancing_state_or_counters() {
        LcgRandom random = new LcgRandom(1);

        random.next(8);

        int previousState = random.getState();
        int previousEnabled = random.setEnabled(0);

        Assertions.assertEquals(1, previousEnabled);
        Assertions.assertEquals(0, random.next(8));
        Assertions.assertEquals(0, random.next(-1));
        Assertions.assertEquals(previousState, random.getState());
        Assertions.assertEquals(1, random.getCallCount(8));
        Assertions.assertEquals(1, random.getTotalRandomCounts());
        Assertions.assertEquals(0, random.setEnabled(previousEnabled));
        Assertions.assertEquals((previousState * MULTIPLIER + 1) >>> OUTPUT_SHIFT & OUTPUT_MASK, random.next(8));
        Assertions.assertEquals(2, random.getTotalRandomCounts());
    }

    @Test
    void set_enabled_should_accept_any_nonzero_flag() {
        LcgRandom random = new LcgRandom(1);

        Assertions.assertEquals(1, random.setEnabled(-1));
        Assertions.assertEquals(-1, random.getEnabled());
        Assertions.assertEquals(FIRST_OUTPUT, random.next(0));
    }

    @Test
    void set_state_should_reset_all_counters_without_changing_enabled_flag() {
        LcgRandom random = new LcgRandom(999);

        for (int source = 0; source < LcgRandom.SOURCE_COUNT; source++) {
            random.next(source);
        }

        random.setEnabled(0);
        random.setState(1);

        Assertions.assertEquals(0, random.getEnabled());
        Assertions.assertEquals(1, random.getState());
        Assertions.assertEquals(0, random.getTotalRandomCounts());

        for (int source = 0; source < LcgRandom.SOURCE_COUNT; source++) {
            Assertions.assertEquals(0, random.getCallCount(source));
        }

        random.setEnabled(1);

        Assertions.assertEquals(FIRST_OUTPUT, random.next(0));
    }

    @Test
    void set_active_should_write_the_enable_flag_and_return_whether_it_was_set() {
        LcgRandom random = new LcgRandom(1);

        random.setEnabled(EUD_ENABLED_FLAG);

        Assertions.assertTrue(random.isActive());
        Assertions.assertTrue(random.setActive(false));
        Assertions.assertEquals(0, random.getEnabled());
        Assertions.assertEquals(RANGE_FROM, random.nextRange(0, RANGE_FROM, RANGE_TO));
        Assertions.assertEquals(0, random.getTotalRandomCounts());
        Assertions.assertFalse(random.setActive(true));
        Assertions.assertEquals(1, random.getEnabled());
        Assertions.assertEquals(RANGE_FROM + ((FIRST_OUTPUT * (RANGE_TO - RANGE_FROM + 1)) >>> OUTPUT_BITS),
                                random.nextRange(0, RANGE_FROM, RANGE_TO));
    }

    @Test
    void restore_state_should_restore_the_state_counters_and_enable_flag_of_save_state() {
        LcgRandom random = new LcgRandom(SAVED_SEED);
        RandomState saved;
        int[] expected = new int[REFERENCE_CALL_COUNT];

        for (int source = 0; source < LcgRandom.SOURCE_COUNT; source++) {
            random.next(source);
        }

        random.setEnabled(EUD_ENABLED_FLAG);
        saved = random.saveState();

        for (int callIndex = 0; callIndex < expected.length; callIndex++) {
            expected[callIndex] = random.next(callIndex % LcgRandom.SOURCE_COUNT);
        }

        random.setEnabled(0);
        random.restoreState(saved);

        Assertions.assertEquals(EUD_ENABLED_FLAG, random.getEnabled());
        Assertions.assertEquals(LcgRandom.SOURCE_COUNT, random.getTotalRandomCounts());
        Assertions.assertEquals(1, random.getCallCount(LcgRandom.SOURCE_COUNT - 1));

        for (int callIndex = 0; callIndex < expected.length; callIndex++) {
            Assertions.assertEquals(expected[callIndex], random.next(callIndex % LcgRandom.SOURCE_COUNT));
        }

        LcgRandom copy = new LcgRandom(0);

        copy.restoreState(saved);

        Assertions.assertEquals(saved, copy.saveState());
        Assertions.assertThrows(IllegalArgumentException.class, () -> random.restoreState(RandomState.of(1, 2, 3)));
    }
}
