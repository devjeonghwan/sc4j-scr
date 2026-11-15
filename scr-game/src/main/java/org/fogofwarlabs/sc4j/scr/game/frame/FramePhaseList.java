package org.fogofwarlabs.sc4j.scr.game.frame;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

/**
 * The ordered phases of a game frame. The game creates the list with its phase order; the order and the number of phases stay fixed, and a phase can
 * be replaced by another one at the same position.
 */
public final class FramePhaseList {
    private final @NotNull FramePhase @NotNull [] phases;

    /**
     * Creates a {@link FramePhaseList} instance with a fixed phase order.
     *
     * @param phases the phases in the order they run
     */
    public FramePhaseList(@NotNull FramePhase @NotNull ... phases) {
        this.phases = phases.clone();
    }

    /**
     * @return the number of phases
     */
    public int getCount() {
        return this.phases.length;
    }

    /**
     * Returns the phase at a position.
     *
     * @param index the position, {@code 0} for the first phase
     * @return the phase at the position
     */
    public @NotNull FramePhase get(int index) {
        return this.phases[index];
    }


    /**
     * Finds the position of a phase.
     *
     * @param phase the phase
     * @return the position of the phase, or {@code -1} if the list does not hold it
     */
    public int findIndex(@NotNull FramePhase phase) {
        for (int index = 0; index < this.phases.length; index++) {
            if (this.phases[index] == phase) {
                return index;
            }
        }

        return -1;
    }

    /**
     * Replaces a phase at its position.
     *
     * @param existing    the phase to replace
     * @param replacement the phase that runs at its position from the next frame on
     * @throws IllegalArgumentException if the list does not hold the existing phase
     */
    public void replace(@NotNull FramePhase existing, @NotNull FramePhase replacement) {
        int index = this.findIndex(existing);

        if (index < 0) {
            throw Exceptions.newIllegalArgumentException("The frame phase list does not hold the phase: {0}", existing);
        }

        this.phases[index] = replacement;
    }

    /**
     * Checks whether every phase is ready ({@link FramePhase#isReady}).
     *
     * @param game the game
     * @return {@code true} if every phase is ready, {@code false} otherwise
     */
    public boolean isReady(@NotNull Game game) {
        for (FramePhase phase : this.phases) {
            if (!phase.isReady(game)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Runs every phase once, in order.
     *
     * @param game  the game
     * @param frame the frame, with this frame's inputs
     */
    public void update(@NotNull Game game, @NotNull GameFrame frame) {
        FramePhase[] phases = this.phases;

        for (int index = 0; index < phases.length; index++) {
            phases[index].update(game, frame);
        }
    }
}
