package org.fogofwarlabs.sc4j.scr.game.trigger;

/**
 * Names the comparison and modifier bytes of trigger conditions and actions.
 * <p>
 * Conditions compare with {@link #AT_LEAST}, {@link #AT_MOST} or {@link #EXACTLY}, Switch conditions with
 * {@link #SWITCH_IS_SET} or {@link #SWITCH_IS_CLEARED}; switch and state actions use
 * {@link #SET}, {@link #CLEAR}, {@link #TOGGLE} and {@link #RANDOMIZE}; numeric actions use {@link #SET_TO}, {@link #ADD}
 * and {@link #SUBTRACT}. The names follow StarEdit.
 */
public final class TriggerModifiers {
    /**
     * The value is at least the amount.
     */
    public static final int AT_LEAST = 0;

    /**
     * The value is at most the amount.
     */
    public static final int AT_MOST = 1;

    /**
     * The switch of a Switch condition is set.
     */
    public static final int SWITCH_IS_SET = 2;

    /**
     * The switch of a Switch condition is cleared.
     */
    public static final int SWITCH_IS_CLEARED = 3;

    /**
     * A switch or state is set.
     */
    public static final int SET = 4;

    /**
     * A switch or state is cleared.
     */
    public static final int CLEAR = 5;

    /**
     * A switch or state is toggled.
     */
    public static final int TOGGLE = 6;

    /**
     * A value is set to the amount.
     */
    public static final int SET_TO = 7;

    /**
     * The amount is added to a value.
     */
    public static final int ADD = 8;

    /**
     * The amount is subtracted from a value.
     */
    public static final int SUBTRACT = 9;

    /**
     * The value equals the amount.
     */
    public static final int EXACTLY = 10;

    /**
     * A switch is randomized.
     */
    public static final int RANDOMIZE = 11;

    /**
     * The number of comparison and modifier values; higher values are invalid.
     */
    public static final int COUNT = 12;


    /**
     * Tests whether a modifier is one of the numeric modifiers {@link #SET_TO}, {@link #ADD} and {@link #SUBTRACT}.
     *
     * @param modifier the modifier byte
     * @return {@code true} if the modifier is numeric, {@code false} otherwise
     */
    public static boolean isNumericModifier(int modifier) {
        return modifier >= SET_TO && modifier <= SUBTRACT;
    }


    private TriggerModifiers() {
    }
}
