package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.scr.game.state.RuntimeRandom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that the music rotation draws its start only once per process and then rotates per game.
 */
final class MusicRotationTest {
    /**
     * A generator seed whose first draw is {@code 21238} ({@code & 3} = 2), so the first rotation is 3.
     */
    private static final int SEED = 0x1E278E7A;
    private static final int NO_ROTATION = -1;
    private static final int FIRST_ROTATION = 3;
    private static final int SECOND_ROTATION = 0;
    private static final int THIRD_ROTATION = 1;

    @Test
    void advance_should_draw_only_before_the_first_game_and_wrap_after_the_last_offset() {
        MusicRotation rotation = new MusicRotation();
        RuntimeRandom runtimeRandom = new RuntimeRandom(SEED);

        Assertions.assertEquals(NO_ROTATION, rotation.getRotation());
        Assertions.assertEquals(FIRST_ROTATION, rotation.advance(runtimeRandom));
        Assertions.assertEquals(1, runtimeRandom.getDrawCount());
        Assertions.assertEquals(SECOND_ROTATION, rotation.advance(runtimeRandom), "3 wraps to 0");
        Assertions.assertEquals(THIRD_ROTATION, rotation.advance(runtimeRandom));
        Assertions.assertEquals(1, runtimeRandom.getDrawCount(), "later games draw nothing");
    }
}
