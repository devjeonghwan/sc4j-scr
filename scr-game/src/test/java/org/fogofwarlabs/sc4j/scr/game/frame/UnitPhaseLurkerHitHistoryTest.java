package org.fogofwarlabs.sc4j.scr.game.frame;

import org.fogofwarlabs.sc4j.scr.game.frame.UnitPhase.Context;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.function.IntPredicate;

/**
 * Checks the rotation of the Lurker hit history and the window move before a missing history is rejected.
 */
final class UnitPhaseLurkerHitHistoryTest {
    private static final int HISTORY_WINDOW_COUNT = 32;
    private static final int HITS_PER_WINDOW = 16;

    /**
     * Asserts which windows retain their pairs: the pair of window {@code window} and hit {@code hit} is ({@code sources[window]},
     * {@code targets[hit]}).
     *
     * @param context  the unit phase context that holds the hit history
     * @param sources  the source unit of each window
     * @param targets  the target unit of each hit
     * @param retained the test of the windows whose pairs are retained
     */
    private static void assertRetainedWindows(@NotNull Context context,
                                              @NotNull Unit @NotNull [] sources, @NotNull Unit @NotNull [] targets,
                                              @NotNull IntPredicate retained) {
        for (int window = 0; window < HISTORY_WINDOW_COUNT; window++) {
            for (int hit = 0; hit < HITS_PER_WINDOW; hit++) {
                Assertions.assertEquals(retained.test(window), context.hasLurkerHit(sources[window], targets[hit]),
                                        "window " + window + ", hit " + hit);
            }
        }
    }


    @Test
    void advance_lurker_hit_history_should_clear_one_window_and_keep_other_entries() {
        Context context = new Context(UnitPhase.INITIAL_PRIMARY_REDISTRIBUTION_TIMER, UnitPhase.INITIAL_SECONDARY_REDISTRIBUTION_TIMER);
        Unit[] sources = new Unit[HISTORY_WINDOW_COUNT];
        Unit[] targets = new Unit[HITS_PER_WINDOW + 1];

        for (int window = 0; window < sources.length; window++) {
            sources[window] = UnitTestUtils.createUnit(1 + window);
        }

        for (int hit = 0; hit < targets.length; hit++) {
            targets[hit] = UnitTestUtils.createUnit(1 + HISTORY_WINDOW_COUNT + hit);
        }

        context.initializeLurkerHitHistory();

        for (int window = 0; window < HISTORY_WINDOW_COUNT; window++) {
            if (window > 0) {
                UnitPhase.advanceLurkerHitHistory(context);
            }

            Assertions.assertEquals(window, context.getLurkerHitWindowIndex());
            Assertions.assertEquals(0, context.getLurkerHitCount());

            for (int hit = 0; hit <= HITS_PER_WINDOW; hit++) {
                context.addLurkerHit(sources[window], targets[hit]);
            }

            Assertions.assertEquals(HITS_PER_WINDOW, context.getLurkerHitCount());
            Assertions.assertFalse(context.hasLurkerHit(sources[window], targets[HITS_PER_WINDOW]), "a full window ignores another pair");
        }

        assertRetainedWindows(context, sources, targets, window -> true);

        UnitPhase.advanceLurkerHitHistory(context);

        Assertions.assertEquals(0, context.getLurkerHitWindowIndex());
        Assertions.assertEquals(0, context.getLurkerHitCount());
        assertRetainedWindows(context, sources, targets, window -> window != 0);

        for (int window = 1; window < HISTORY_WINDOW_COUNT; window++) {
            UnitPhase.advanceLurkerHitHistory(context);
        }

        Assertions.assertEquals(HISTORY_WINDOW_COUNT - 1, context.getLurkerHitWindowIndex());
        assertRetainedWindows(context, sources, targets, window -> false);

        UnitPhase.advanceLurkerHitHistory(context);

        Assertions.assertEquals(0, context.getLurkerHitWindowIndex());
    }

    @Test
    void advance_lurker_hit_history_should_move_window_before_rejecting_missing_history() {
        Context context = new Context(UnitPhase.INITIAL_PRIMARY_REDISTRIBUTION_TIMER, UnitPhase.INITIAL_SECONDARY_REDISTRIBUTION_TIMER);

        Assertions.assertThrows(UnsupportedOperationException.class, () -> UnitPhase.advanceLurkerHitHistory(context));
        Assertions.assertEquals(1, context.getLurkerHitWindowIndex());

        context.initializeLurkerHitHistory();

        Assertions.assertEquals(0, context.getLurkerHitWindowIndex());
        Assertions.assertEquals(0, context.getLurkerHitCount());
    }
}
