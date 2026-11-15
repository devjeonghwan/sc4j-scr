package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.scr.game.state.RuntimeRandom;
import org.jetbrains.annotations.NotNull;

/**
 * Holds the game music rotation of one client process: the offset of the first game track from the race's first track.
 * <p>
 * Every non-campaign game start advances it by one, wrapping after the last offset. Before the first such start it has no offset, and that
 * start draws the starting offset from the game's C runtime generator; later starts draw nothing. The client keeps one instance for its whole
 * run; a headless run starts with a new one, as a new process does.
 */
public final class MusicRotation {
    /**
     * The number of rotation offsets.
     */
    public static final int ROTATION_COUNT = 4;
    /**
     * The rotation before the first non-campaign game start.
     */
    public static final int NO_ROTATION = -1;

    /**
     * The mask that reduces a runtime generator draw to the starting rotation offset.
     */
    private static final int FIRST_ROTATION_MASK = ROTATION_COUNT - 1;

    private int rotation;

    /**
     * Creates a {@link MusicRotation} instance before the first non-campaign game start.
     */
    public MusicRotation() {
        this.rotation = NO_ROTATION;
    }

    /**
     * @return the rotation offset from {@code 0} to {@code 3}, or {@link #NO_ROTATION} before the first non-campaign game start
     */
    public int getRotation() {
        return this.rotation;
    }

    /**
     * Advances the rotation at a non-campaign game start; the first start draws its starting offset.
     *
     * @param runtimeRandom the game's C runtime generator, drawn from only at the first start
     * @return the rotation offset from {@code 0} to {@code 3}
     */
    public int advance(@NotNull RuntimeRandom runtimeRandom) {
        int start = this.rotation == NO_ROTATION ? runtimeRandom.draw() & FIRST_ROTATION_MASK : this.rotation;
        int next = start + 1;

        this.rotation = next >= ROTATION_COUNT ? 0 : next;

        return this.rotation;
    }
}
