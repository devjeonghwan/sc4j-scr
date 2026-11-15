package org.fogofwarlabs.sc4j.scr.client.ui.portrait;

/**
 * Advances the private portrait random state used for clip choice and static noise.
 * A new instance uses fixed initial words; rejected percentage and talking-clip draws consume additional steps.
 */
public final class PortraitGenerator {
    /**
     * The number of talking clips of a portrait, numbered {@code 0} through {@code 2}.
     */
    static final int TALK_CLIP_COUNT = 3;

    private static final int INITIAL_FIRST_STATE = 0x12345678;
    private static final int INITIAL_SECOND_STATE = 0xE9E9E9E9;
    private static final int INITIAL_THIRD_STATE = 0x10934134;
    private static final int INCREMENT = 0x71823402;
    /**
     * The percent draw: the low bits of the next output, accepted below {@link #PERCENT_LIMIT}.
     */
    private static final int PERCENT_MASK = 0x7F;
    private static final int PERCENT_LIMIT = 101;
    /**
     * The talking clip draw: the low bits of the next output, accepted below {@link #TALK_CLIP_COUNT}.
     */
    private static final int TALK_MASK = 3;
    /**
     * The noise color draw: the low bits of the next output.
     */
    private static final int NIBBLE_MASK = 0xF;

    private int firstState;
    private int secondState;
    private int thirdState;

    /**
     * Creates a {@link PortraitGenerator} instance in the fixed initial state.
     */
    public PortraitGenerator() {
        this.firstState  = INITIAL_FIRST_STATE;
        this.secondState = INITIAL_SECOND_STATE;
        this.thirdState  = INITIAL_THIRD_STATE;
    }


    /**
     * Advances the state by one step.
     *
     * @return the next output
     */
    int next() {
        this.firstState += this.secondState;
        this.secondState += INCREMENT;
        this.thirdState += this.firstState;
        this.firstState >>>= 1;
        this.secondState ^= this.thirdState;

        return this.firstState ^ this.secondState;
    }

    /**
     * Reads the next percentage value.
     *
     * @return a value from {@code 0} through {@code 100} (rejection on the low {@code 7} bits)
     */
    public int nextPercent() {
        int value;

        do {
            value = this.next() & PERCENT_MASK;
        } while (value >= PERCENT_LIMIT);

        return value;
    }

    /**
     * Selects the next talking clip.
     *
     * @return a talking clip number from {@code 0} through {@code 2} (rejection on the low {@code 2} bits)
     */
    public int nextTalkClip() {
        int value;

        do {
            value = this.next() & TALK_MASK;
        } while (value >= TALK_CLIP_COUNT);

        return value;
    }

    /**
     * Reads the next four-bit value.
     *
     * @return the low {@code 4} bits of the next output
     */
    public int nextNibble() {
        return this.next() & NIBBLE_MASK;
    }
}
