package org.fogofwarlabs.sc4j.scr.game.creep;

import org.fogofwarlabs.sc4j.scr.game.frame.FramePhase;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

/**
 * The frame phase of Zerg creep: the game's {@link CreepRecession} recedes creep without a creep source.
 */
public final class CreepFramePhase implements FramePhase {
    /**
     * Creates a {@link CreepFramePhase} instance.
     */
    public CreepFramePhase() {
    }

    /**
     * Checks whether the game has what this phase needs before a frame starts. A frame whose phases are not all ready
     * fails before any phase runs.
     * <p>
     * This phase requires the creep recession installed by world initialization.
     *
     * @param game the game
     * @return {@code true} if the phase can run, {@code false} otherwise
     */
    @Override
    public boolean isReady(@NotNull Game game) {
        return game.getCreepRecession() != null;
    }


    /**
     * Runs this phase of the current frame.
     *
     * @param game  the game
     * @param frame the frame, with this frame's inputs
     * @throws UnsupportedOperationException if the creep recession is not initialized
     */
    @Override
    public void update(@NotNull Game game, @NotNull GameFrame frame) {
        game.requireCreepRecession().update(game);
    }
}
