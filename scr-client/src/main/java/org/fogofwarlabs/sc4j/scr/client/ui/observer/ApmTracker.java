package org.fogofwarlabs.sc4j.scr.client.ui.observer;

import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * Counts represented replay commands by player slot and decays the action counters once per frame.
 * The counters use a 45-second time constant and a warm-up correction when converted to actions per minute.
 * Opcode classification counts a command even if its game handler later rejects or skips the action.
 */
public final class ApmTracker {
    /**
     * The time constant of the decay and of the warm-up factor.
     */
    private static final float TIME_CONSTANT = 45000f;
    /**
     * The scale of the warm-up factor.
     */
    private static final float WARM_UP_SCALE = 0.75f;
    /**
     * The smallest warm-up factor.
     */
    private static final float MINIMUM_WARM_UP = 0.01f;
    /**
     * The players of the panel.
     */
    private static final int PLAYER_SLOTS = PlayerTable.PLAYABLE_PLAYER_SLOTS;
    /**
     * The largest command opcode and player id (both BYTEs).
     */
    private static final int BYTE_MASK = 0xFF;
    /**
     * The opcodes whose dispatcher case opens the action scope (case table).
     */
    private static final int @NotNull [] COUNTED_OPCODES = {
            0x09, 0x0A, 0x0B, 0x0C, 0x13, 0x14, 0x15, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20, 0x21, 0x22, 0x23,
            0x25, 0x26, 0x27, 0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36,
            0x5A, 0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x6A
    };
    private static final boolean @NotNull [] COUNTED = new boolean[BYTE_MASK + 1];

    static {
        for (int opcode : COUNTED_OPCODES) {
            COUNTED[opcode] = true;
        }
    }

    /**
     * @param opcode a command opcode
     * @return {@code true} if the command counts as an action, {@code false} otherwise
     */
    public static boolean isCounted(int opcode) {
        return COUNTED[opcode & BYTE_MASK];
    }

    /**
     * Converts a decayed action counter to actions per minute with the minimum warm-up factor.
     *
     * @param counter      the decayed action counter
     * @param milliseconds the history clock plus one frame duration
     * @return the truncated actions per minute
     */
    public static int calculateActionsPerMinute(float counter, long milliseconds) {
        float warmUp = 1f - (float) Math.exp(-((float) milliseconds / TIME_CONSTANT));

        if (!(warmUp > MINIMUM_WARM_UP)) {
            warmUp = MINIMUM_WARM_UP;
        }

        return (int) (counter / (warmUp * WARM_UP_SCALE));
    }

    private final @NotNull List<ReplayCommand> commands;
    private final int @NotNull [] slotOfPlayerId;

    private final float @NotNull [] counters;
    private int nextCommand;
    private int framesDone;
    private long clock;

    /**
     * Creates an {@link ApmTracker} instance.
     *
     * @param commands       the replay commands in frame order
     * @param slotOfPlayerId the player slot of each command player id ({@code 256} entries, {@code -1} for none)
     */
    public ApmTracker(@NotNull List<ReplayCommand> commands, int @NotNull [] slotOfPlayerId) {
        this.commands       = commands;
        this.slotOfPlayerId = slotOfPlayerId.clone();

        this.counters    = new float[PLAYER_SLOTS];
        this.nextCommand = 0;
        this.framesDone  = 0;
        this.clock       = 0;
    }

    /**
     * @return the clock {@code t} of the history head: the game time in milliseconds of the frames counted so far
     */
    public long getClock() {
        return this.clock;
    }


    /**
     * Advances the counters to a frame counter and returns the APM of each slot.
     *
     * @param frameCounter      the game frame counter (game): the frames whose commands ran and whose decay followed
     * @param frameMilliseconds the frame duration {@code d}
     * @return the APM of slots {@code 0..7}
     */
    public int @NotNull [] update(int frameCounter, int frameMilliseconds) {
        if (frameCounter < this.framesDone) {
            Arrays.fill(this.counters, 0f);

            this.nextCommand = 0;
            this.framesDone  = 0;
            this.clock       = 0;
        }

        float decay = (float) Math.exp(-((float) frameMilliseconds / TIME_CONSTANT));

        while (this.framesDone < frameCounter) {
            int frame = this.framesDone;

            while (this.nextCommand < this.commands.size() && this.commands.get(this.nextCommand).getFrame() <= frame) {
                ReplayCommand command = this.commands.get(this.nextCommand++);
                int slot = this.slotOfPlayerId[command.getPlayerId() & BYTE_MASK];

                if (slot >= 0 && slot < PLAYER_SLOTS && isCounted(command.getTypeId())) {
                    this.counters[slot] += 1f;
                }
            }

            for (int slot = 0; slot < PLAYER_SLOTS; slot++) {
                this.counters[slot] *= decay;
            }

            this.clock += frameMilliseconds;
            this.framesDone++;
        }

        int[] actionsPerMinute = new int[PLAYER_SLOTS];

        for (int slot = 0; slot < PLAYER_SLOTS; slot++) {
            actionsPerMinute[slot] = calculateActionsPerMinute(this.counters[slot], this.clock + frameMilliseconds);
        }

        return actionsPerMinute;
    }
}
