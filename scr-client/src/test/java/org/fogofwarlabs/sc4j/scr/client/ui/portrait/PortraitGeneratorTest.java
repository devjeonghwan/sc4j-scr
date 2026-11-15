package org.fogofwarlabs.sc4j.scr.client.ui.portrait;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the portrait generator's state steps and the ranges of its percentage and talking-clip draws.
 */
final class PortraitGeneratorTest {
    private static final int INITIAL_FIRST_STATE = 0x12345678;
    private static final int INITIAL_SECOND_STATE = 0xE9E9E9E9;
    private static final int INITIAL_THIRD_STATE = 0x10934134;
    private static final int INCREMENT = 0x71823402;
    private static final int STEP_COUNT = 50;
    private static final int DRAW_COUNT = 1000;
    private static final int MAXIMUM_PERCENT = 100;
    /**
     * The talking clips a draw chooses from.
     */
    private static final int TALK_CLIPS = 3;

    @Test
    void next_should_combine_three_state_generator_steps() {
        // Update the three generator states in order, then combine the first and second states.
        int firstState = INITIAL_FIRST_STATE;
        int secondState = INITIAL_SECOND_STATE;
        int thirdState = INITIAL_THIRD_STATE;
        PortraitGenerator generator = new PortraitGenerator();

        for (int step = 0; step < STEP_COUNT; step++) {
            firstState += secondState;
            secondState += INCREMENT;
            thirdState += firstState;
            firstState >>>= 1;
            secondState ^= thirdState;

            Assertions.assertEquals(firstState ^ secondState, generator.next());
        }
    }

    @Test
    void percent_and_talk_clip_draws_stay_in_range() {
        PortraitGenerator generator = new PortraitGenerator();

        for (int draw = 0; draw < DRAW_COUNT; draw++) {
            int percent = generator.nextPercent();
            int talk = generator.nextTalkClip();

            Assertions.assertTrue(percent >= 0 && percent <= MAXIMUM_PERCENT);
            Assertions.assertTrue(talk >= 0 && talk < TALK_CLIPS);
        }
    }
}
