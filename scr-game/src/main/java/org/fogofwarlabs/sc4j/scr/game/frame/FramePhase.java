package org.fogofwarlabs.sc4j.scr.game.frame;

import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

/**
 * One phase of a game frame. The game owns the ordered {@link FramePhaseList}; {@link GameFrame} runs every phase of it once per frame, in list
 * order, inside the frame's random scope.
 * <p>
 * A phase reads the frame's inputs (player types, caller controls, instruction budget) from the {@link GameFrame} and the game's systems from the
 * {@link Game} at the time it runs. It runs once per frame and must not allocate.
 */
public interface FramePhase {
    /**
     * Checks whether the game has what this phase needs before a frame starts. A frame whose phases are not all ready fails before any phase runs.
     *
     * @param game the game
     * @return {@code true} if the phase can run, {@code false} otherwise
     */
    default boolean isReady(@NotNull Game game) {
        return true;
    }


    /**
     * Runs this phase of the current frame.
     *
     * @param game  the game
     * @param frame the frame, with this frame's inputs
     */
    void update(@NotNull Game game, @NotNull GameFrame frame);
}
