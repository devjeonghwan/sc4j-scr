package org.fogofwarlabs.sc4j.scr.game.frame;

import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Verifies the timing tables, the pristine replay timing, the rebuild that discards EUD duration writes, the turn-wait budget's schedule of
 * turn steps, the speed override of a game name, the overrides that throttle drawing and the lag limits.
 */
final class GameSpeedTest {
    private static final int CAPTURED_CASE_COUNT = 1024;
    private static final int CAPTURED_RECORD_INTEGER_COUNT = 14;
    private static final int BYTE_MASK = 0xFF;
    /**
     * The speed indices of Slower, Fast and Faster.
     */
    private static final int SLOWER_SPEED_INDEX = 1;
    private static final int FAST_SPEED_INDEX = 4;
    private static final int FASTER_SPEED_INDEX = 5;
    /**
     * The frame durations in milliseconds of Fast and Faster.
     */
    private static final int FAST_FRAME_DURATION = 56;
    private static final int FASTER_FRAME_DURATION = 48;
    /**
     * The first speed index outside the speed table and the first override index outside the override table.
     */
    private static final int OUTSIDE_SPEED_INDEX = 7;
    private static final int OUTSIDE_OVERRIDE_INDEX = 6;
    /**
     * The frame duration an EUD write stores in the speed table.
     */
    private static final int EUD_FRAME_DURATION = 10;
    /**
     * The multiplayer session BYTE of a network game.
     */
    private static final int MULTIPLAYER_SESSION = 1;
    /**
     * A network game's turns per second below the provider's {@code 24}.
     */
    private static final int LOW_TURN_RATE = 8;
    /**
     * The timeout of Fastest at {@link #LOW_TURN_RATE} turns per second: {@code 1000 / (42 * 8) * 1000 + 0.5} truncated.
     */
    private static final int LOW_TURN_RATE_TIMEOUT = 2976;
    /**
     * The game loops the schedule test runs.
     */
    private static final int SCHEDULED_LOOPS = 10;
    /**
     * The turn-wait budget one game loop takes, and the budget the game start stores.
     */
    private static final int FRAME_BUDGET = 1000;
    /**
     * The override selector of {@code #x3} and its frame duration in milliseconds.
     */
    private static final int THIRD_OVERRIDE_INDEX = 3;
    private static final int THIRD_OVERRIDE_DURATION = 21;
    /**
     * The timeout of the third override at {@code 24} turns per second: {@code 1000 / (21 * 24) * 1000 + 0.5} truncated.
     */
    private static final int THIRD_OVERRIDE_TIMEOUT = 1984;
    /**
     * Whether the override selectors {@code 0..5} throttle drawing.
     */
    private static final boolean @NotNull [] THROTTLED_OVERRIDES = {false, false, false, true, true, true};
    /**
     * The lag limits of the speed settings {@code 0..6}: the game loop's static table of three frame durations.
     */
    private static final int @NotNull [] LAG_LIMITS = {501, 333, 249, 201, 168, 144, 126};
    /**
     * The provider's default turns per second.
     */
    private static final int PROVIDER_TURNS_PER_SECOND = 24;
    /**
     * Game names and the override selector each one parses to.
     */
    private static final @NotNull Map<String, Integer> GAME_NAME_OVERRIDES = Map.ofEntries(
            Map.entry("#x1", 1),
            Map.entry("#x2", 2),
            Map.entry("#x3", 3),
            Map.entry("#x4", 4),
            Map.entry("#x5", 5),
            Map.entry("#X3", 3),
            Map.entry("Room #x4", 4),
            Map.entry("Room #x2 fast", 2),
            Map.entry("#x25", 2),
            Map.entry("#x2abc", 2),
            Map.entry("#x0", 0),
            Map.entry("#x6", 0),
            Map.entry("#x9", 0),
            Map.entry("#xa", 0),
            Map.entry("#x", 0),
            Map.entry("#", 0),
            Map.entry("#3", 0),
            Map.entry("# x3", 0),
            Map.entry("#y3", 0),
            Map.entry("#1 #x3", 0),
            Map.entry("##x3", 0),
            Map.entry("x3", 0),
            Map.entry("", 0)
    );
    /**
     * The budget after the scheduled loops: {@code 1000 - 10 * 1000 + 4 * 2976}.
     */
    private static final int BUDGET_AFTER_SCHEDULED_LOOPS = 2904;

    @Test
    void matches_native_frame_timing_cases() throws IOException {
        ByteBuffer cases = ByteBuffer.wrap(GameTestResources.readNativeCapture(GameTestResources.CAPTURE_FRAME_TIMING))
                                     .order(ByteOrder.LITTLE_ENDIAN);

        Assertions.assertEquals(CAPTURED_CASE_COUNT * CAPTURED_RECORD_INTEGER_COUNT * Integer.BYTES, cases.remaining());

        for (int caseIndex = 0; caseIndex < CAPTURED_CASE_COUNT; caseIndex++) {
            int speedIndex = cases.getInt();
            int factor = cases.getInt();
            int mode = cases.getInt();
            int overrideIndex = cases.getInt();

            GameSpeed timing = new GameSpeed(speedIndex, factor, mode, overrideIndex);

            Assertions.assertEquals(speedIndex, timing.getSpeedIndex());
            Assertions.assertEquals(factor, timing.getFactor());
            Assertions.assertEquals(mode & BYTE_MASK, timing.getMode());
            Assertions.assertEquals(overrideIndex, timing.getOverrideIndex());
            Assertions.assertEquals(cases.getInt(), timing.getCommandInterval(), "case " + caseIndex);

            for (int durationIndex = 0; durationIndex < GameSpeed.SPEED_COUNT; durationIndex++) {
                Assertions.assertEquals(cases.getInt(), timing.getDuration(durationIndex), "case " + caseIndex + " index " + durationIndex);
            }

            Assertions.assertEquals(cases.getInt(), timing.getFrameDuration(), "case " + caseIndex);
            Assertions.assertEquals(1, cases.getInt(), "native display callback count");
        }
    }

    @Test
    void pristine_replay_timing_uses_fallback_and_invalid_speeds_throw() {
        GameSpeed timing = GameSpeed.createPristineReplay();

        Assertions.assertEquals(FAST_SPEED_INDEX, timing.getSpeedIndex());
        Assertions.assertEquals(1, timing.getFactor());
        Assertions.assertEquals(0, timing.getMode());
        Assertions.assertEquals(0, timing.getOverrideIndex());
        Assertions.assertEquals(FAST_FRAME_DURATION, timing.getFrameDuration());

        Assertions.assertEquals(0, new GameSpeed(0, 0, 0, 0).getFrameDuration());
        Assertions.assertThrows(ArithmeticException.class, () -> new GameSpeed(SLOWER_SPEED_INDEX, 0, 0, 0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new GameSpeed(OUTSIDE_SPEED_INDEX, 1, 0, 0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new GameSpeed(FAST_SPEED_INDEX, 1, 0, OUTSIDE_OVERRIDE_INDEX));
    }

    @Test
    void eud_duration_writes_last_until_speed_is_reconfigured() {
        GameSpeed timing = GameSpeed.createPristineReplay();

        timing.setTableDuration(FAST_SPEED_INDEX, EUD_FRAME_DURATION);

        Assertions.assertEquals(EUD_FRAME_DURATION, timing.getFrameDuration());

        timing.configure(FASTER_SPEED_INDEX, 1, timing.getMode());

        Assertions.assertEquals(FASTER_FRAME_DURATION, timing.getFrameDuration());
        Assertions.assertEquals(FAST_FRAME_DURATION, timing.getTableDuration(FAST_SPEED_INDEX), "the rebuild discards the EUD write");
    }

    @Test
    void turn_budget_runs_a_turn_step_once_a_loop_leaves_it_at_most_zero() {
        GameSpeed timing = new GameSpeed(GameSpeed.FASTEST, 1, 0, 0);
        List<Integer> turnLoops = new ArrayList<>();

        timing.startSession(MULTIPLAYER_SESSION, LOW_TURN_RATE, 0);

        Assertions.assertEquals(LOW_TURN_RATE_TIMEOUT, timing.getTimeouts()[GameSpeed.FASTEST]);
        Assertions.assertEquals(FRAME_BUDGET, timing.getTurnBudget(), "The game start stores 1000.");

        for (int loop = 0; loop < SCHEDULED_LOOPS; loop++) {
            boolean due = timing.isTurnStepDue();

            Assertions.assertEquals(due, timing.takeFrameBudget(), "loop " + loop);

            if (due) {
                timing.addTurnTimeout();
                turnLoops.add(loop);
            }
        }

        Assertions.assertEquals(List.of(0, 3, 6, 9), turnLoops, "A turn step every 2976 / 1000 loops, the remainder carried.");
        Assertions.assertEquals(BUDGET_AFTER_SCHEDULED_LOOPS, timing.getTurnBudget());

        timing.startSession(0, 0, 0);
        timing.resetTurnBudget();

        for (int loop = 0; loop < SCHEDULED_LOOPS; loop++) {
            Assertions.assertTrue(timing.takeFrameBudget(), "Outside a multiplayer session every loop runs a turn step.");

            timing.addTurnTimeout();
        }

        Assertions.assertEquals(FRAME_BUDGET, timing.getTurnBudget());
    }

    @Test
    void parse_override_index_should_read_only_the_first_marker_with_its_letter_and_digit() {
        for (Map.Entry<String, Integer> entry : GAME_NAME_OVERRIDES.entrySet()) {
            Assertions.assertEquals(entry.getValue(), GameSpeed.parseOverrideIndex(entry.getKey()), entry.getKey());
        }
    }

    @Test
    void override_replaces_the_duration_and_the_timeouts_of_every_speed_setting_but_not_the_clock_duration() {
        for (int speedIndex = 0; speedIndex < GameSpeed.SPEED_COUNT; speedIndex++) {
            GameSpeed timing = new GameSpeed(speedIndex, 1, 0, THIRD_OVERRIDE_INDEX);

            timing.startSession(MULTIPLAYER_SESSION, 0, PROVIDER_TURNS_PER_SECOND);

            Assertions.assertEquals(THIRD_OVERRIDE_DURATION, timing.getFrameDuration(), "speed " + speedIndex);
            Assertions.assertEquals(THIRD_OVERRIDE_TIMEOUT, timing.getTimeouts()[speedIndex], "speed " + speedIndex);
        }

        Assertions.assertEquals(GameSpeed.FASTEST_DURATION, GameSpeed.getBaseDuration(GameSpeed.FASTEST));
        Assertions.assertEquals(THIRD_OVERRIDE_DURATION, GameSpeed.createPristineReplay(THIRD_OVERRIDE_INDEX).getDuration(FAST_SPEED_INDEX));
    }

    @Test
    void overrides_three_to_five_throttle_drawing_and_the_lag_limit_ignores_the_override() {
        for (int overrideIndex = 0; overrideIndex < THROTTLED_OVERRIDES.length; overrideIndex++) {
            Assertions.assertEquals(THROTTLED_OVERRIDES[overrideIndex], new GameSpeed(GameSpeed.FASTEST, 1, 0, overrideIndex).isDrawThrottled(),
                                    "override " + overrideIndex);
        }

        for (int speedIndex = 0; speedIndex < GameSpeed.SPEED_COUNT; speedIndex++) {
            Assertions.assertEquals(LAG_LIMITS[speedIndex], GameSpeed.getLagLimit(speedIndex), "speed " + speedIndex);
        }
    }
}
