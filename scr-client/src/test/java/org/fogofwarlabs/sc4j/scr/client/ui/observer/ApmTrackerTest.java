package org.fogofwarlabs.sc4j.scr.client.ui.observer;

import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Checks counted actions, per-frame decay, warm-up and replay restart.
 */
final class ApmTrackerTest {
    private static final int FASTEST_MILLISECONDS = 42;
    private static final int PLAYER_ID = 3;
    private static final int RIGHT_CLICK = 0x14;
    private static final int CHAT = 0x5C;
    private static final int SYNC = 0x37;
    private static final int SELECT = 0x09;
    private static final int SHIFT_SELECT = 0x0A;
    private static final int PAUSE = 0x10;
    private static final int TURN_RATE = 0x5F;
    private static final float TIME_CONSTANT = 45000f;
    private static final float MINIMUM_WARM_UP = 0.01f;
    private static final float WARM_UP_SCALE = 0.75f;
    /**
     * The length of the player slot table: one entry per BYTE player id.
     */
    private static final int PLAYER_ID_COUNT = 256;
    private static final int PLAYER_SLOT = 1;
    private static final int SECOND_COMMAND_FRAME = 2;
    private static final int UPDATED_FRAMES = 3;
    private static final int ONE_MINUTE_MILLISECONDS = 60000;
    private static final float ACTIONS_IN_ONE_MINUTE = 30f;
    private static final int RESTART_COMMAND_FRAME = 5;
    private static final int RESTART_FRAMES = 10;
    /**
     * A frame before the restart command: updating back to it starts the count again.
     */
    private static final int BEFORE_RESTART_FRAME = 3;

    @Test
    void is_counted_should_accept_only_commands_with_action_scope() {
        Assertions.assertTrue(ApmTracker.isCounted(RIGHT_CLICK));
        Assertions.assertTrue(ApmTracker.isCounted(SELECT));
        Assertions.assertTrue(ApmTracker.isCounted(SHIFT_SELECT), "Shift Select is counted like Select.");
        Assertions.assertFalse(ApmTracker.isCounted(CHAT));
        Assertions.assertFalse(ApmTracker.isCounted(SYNC));
        Assertions.assertFalse(ApmTracker.isCounted(PAUSE));
        Assertions.assertFalse(ApmTracker.isCounted(TURN_RATE), "Turn Rate has no action scope.");
    }

    @Test
    void update_should_decay_every_frame_after_frame_commands() {
        List<ReplayCommand> commands = new ArrayList<>();

        commands.add(new ReplayCommand(0, PLAYER_ID, RIGHT_CLICK, new byte[0]));
        commands.add(new ReplayCommand(0, PLAYER_ID, CHAT, new byte[0]));
        commands.add(new ReplayCommand(SECOND_COMMAND_FRAME, PLAYER_ID, RIGHT_CLICK, new byte[0]));

        int[] slots = new int[PLAYER_ID_COUNT];

        Arrays.fill(slots, -1);
        slots[PLAYER_ID] = PLAYER_SLOT;

        ApmTracker tracker = new ApmTracker(commands, slots);

        float decay = (float) Math.exp(-((float) FASTEST_MILLISECONDS / TIME_CONSTANT));
        float counter = (1f * decay * decay + 1f) * decay;
        int[] apm = tracker.update(UPDATED_FRAMES, FASTEST_MILLISECONDS);

        Assertions.assertEquals(ApmTracker.calculateActionsPerMinute(counter, (UPDATED_FRAMES + 1L) * FASTEST_MILLISECONDS), apm[PLAYER_SLOT]);
        Assertions.assertEquals(0, apm[0]);
    }

    @Test
    void calculate_actions_per_minute_should_apply_warm_up_floor_early_in_game() {
        Assertions.assertEquals((int) (1f / (MINIMUM_WARM_UP * WARM_UP_SCALE)), ApmTracker.calculateActionsPerMinute(1f, 0));

        float warmUp = 1f - (float) Math.exp(-((float) ONE_MINUTE_MILLISECONDS / TIME_CONSTANT));

        Assertions.assertEquals((int) (ACTIONS_IN_ONE_MINUTE / (warmUp * WARM_UP_SCALE)),
                                ApmTracker.calculateActionsPerMinute(ACTIONS_IN_ONE_MINUTE, ONE_MINUTE_MILLISECONDS));
    }

    @Test
    void update_should_restart_when_frame_counter_goes_back() {
        List<ReplayCommand> commands = List.of(new ReplayCommand(RESTART_COMMAND_FRAME, PLAYER_ID, RIGHT_CLICK, new byte[0]));
        int[] slots = new int[PLAYER_ID_COUNT];

        Arrays.fill(slots, -1);
        slots[PLAYER_ID] = 0;

        ApmTracker tracker = new ApmTracker(commands, slots);

        int counted = tracker.update(RESTART_FRAMES, FASTEST_MILLISECONDS)[0];

        Assertions.assertTrue(counted > 0);
        Assertions.assertEquals(0, tracker.update(BEFORE_RESTART_FRAME, FASTEST_MILLISECONDS)[0]);
        Assertions.assertEquals(counted, tracker.update(RESTART_FRAMES, FASTEST_MILLISECONDS)[0]);
    }
}
