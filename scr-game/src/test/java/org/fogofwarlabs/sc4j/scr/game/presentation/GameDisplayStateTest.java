package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks retained display ownership, snapshot isolation and rejected initialization.
 */
final class GameDisplayStateTest {
    /**
     * The preceding diagnostic hash of the compared state hashes.
     */
    private static final int PRECEDING_HASH = 1234;
    /**
     * A QWORD control value whose every byte differs.
     */
    private static final long QWORD_PATTERN = 0x123456789ABCDEF0L;
    /**
     * A control value with every bit set.
     */
    private static final long ALL_BITS = -1L;
    /**
     * The largest unsigned DWORD.
     */
    private static final long UNSIGNED_DWORD_MAXIMUM = 0xFFFFFFFFL;
    /**
     * The largest unsigned byte.
     */
    private static final long UNSIGNED_BYTE_MAXIMUM = 255;
    /**
     * A visibility counter written into the supplied array after the initialization.
     */
    private static final int CHANGED_COUNTER = 100;
    /**
     * A display refresh control count one below the six controls.
     */
    private static final int SHORT_REFRESH_CONTROL_COUNT = 5;
    /**
     * The number of replay display controls.
     */
    private static final int REPLAY_CONTROL_COUNT = 9;
    /**
     * A visibility counter count one below the ten counters.
     */
    private static final int SHORT_VISIBILITY_COUNTER_COUNT = 9;
    /**
     * The display refresh control index of the panel redraw flag.
     */
    private static final int PANEL_DIRTY_CONTROL = 1;
    /**
     * The display refresh controls after a refresh with panel identity {@code 0}: the three redraw flags set and the cached identities cleared.
     */
    private static final long @NotNull [] REFRESHED_CONTROLS = {0, 1, 1, 1, 0, 0};

    @Test
    void refresh_display_state_should_fail_without_mutation_when_uninitialized() {
        GameDisplayState state = new GameDisplayState();

        Assertions.assertNull(state.getDisplayRefreshState());
        Assertions.assertNull(state.getPanelSelectionState());
        Assertions.assertFalse(state.isPanelSelectionInitialized());

        int initial = state.computeStateHash(PRECEDING_HASH);

        Assertions.assertThrows(UnsupportedOperationException.class, state::refreshDisplayState);
        Assertions.assertEquals(initial, state.computeStateHash(PRECEDING_HASH));
        Assertions.assertNull(state.getDisplayRefreshState());
        Assertions.assertNull(state.getPanelSelectionState());
        Assertions.assertFalse(state.isPanelSelectionInitialized());
    }

    @Test
    void display_state_keeps_field_widths_and_copied_snapshots_across_dirty_refresh() {
        GameDisplayState state = new GameDisplayState();
        long[] refresh = {0, ALL_BITS, ALL_BITS, ALL_BITS, ALL_BITS, QWORD_PATTERN};
        int[] controls = {-4, -3, -2, -1, 0, 1, 2, 3, 4};
        int[] visibilityCounters = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

        state.initializeDisplayRefresh(refresh);
        state.initializeReplayDisplay(controls, visibilityCounters);

        int initialized = state.computeStateHash(PRECEDING_HASH);

        refresh[PANEL_DIRTY_CONTROL] = 0;
        controls[0]                  = 0;
        visibilityCounters[0]        = CHANGED_COUNTER;

        Assertions.assertEquals(initialized, state.computeStateHash(PRECEDING_HASH), "the state keeps copies of the supplied arrays");

        long[] snapshot = state.getDisplayRefreshState();

        Assertions.assertNotNull(snapshot);

        snapshot[PANEL_DIRTY_CONTROL] = 0;

        Assertions.assertArrayEquals(new long[]{0, UNSIGNED_DWORD_MAXIMUM, UNSIGNED_BYTE_MAXIMUM, UNSIGNED_BYTE_MAXIMUM, ALL_BITS, QWORD_PATTERN},
                                     state.getDisplayRefreshState());
        Assertions.assertThrows(IllegalArgumentException.class, () -> state.initializeDisplayRefresh(new long[SHORT_REFRESH_CONTROL_COUNT]));
        Assertions.assertThrows(IllegalArgumentException.class,
                                () -> state.initializeReplayDisplay(new int[REPLAY_CONTROL_COUNT], new int[SHORT_VISIBILITY_COUNTER_COUNT]));
        Assertions.assertEquals(initialized, state.computeStateHash(PRECEDING_HASH), "rejected initializations change nothing");

        state.refreshDisplayState();

        Assertions.assertArrayEquals(REFRESHED_CONTROLS, state.getDisplayRefreshState());
    }
}
