package org.fogofwarlabs.sc4j.scr.game.state;

/**
 * Advances the C runtime random generator ({@code srand}/{@code rand}), which is distinct from the simulation generator
 * {@link org.fogofwarlabs.sc4j.scr.core.LcgRandom}.
 * <p>
 * A draw advances the 32-bit state as {@code state = state * 214013 + 2531011} with 32-bit overflow and returns bits 16-30
 * of the new state. {@link RuntimeRandom} keeps the state, so a generator seeded with {@code srand(seed)} starts from
 * {@code seed}.
 */
public final class RuntimeRandomUtils {
    private static final int MULTIPLIER = 214013;
    private static final int INCREMENT = 2531011;
    private static final int OUTPUT_SHIFT = 16;
    private static final int OUTPUT_MASK = 0x7FFF;

    /**
     * Advances the state by one draw.
     *
     * @param state the current state
     * @return the state after the draw
     */
    public static int advance(int state) {
        return state * MULTIPLIER + INCREMENT;
    }

    /**
     * Reads the value a draw returns from the state the draw left.
     *
     * @param state the state after the draw
     * @return the drawn value, {@code 0..0x7FFF}
     */
    public static int getValue(int state) {
        return state >>> OUTPUT_SHIFT & OUTPUT_MASK;
    }

    private RuntimeRandomUtils() {
    }
}
