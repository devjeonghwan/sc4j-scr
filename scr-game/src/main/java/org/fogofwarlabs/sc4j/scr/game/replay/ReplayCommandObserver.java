package org.fogofwarlabs.sc4j.scr.game.replay;

import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

/**
 * Observes recorded commands immediately before and after their execution for diagnostics.
 * <p>
 * Observers must not change game state or consume random numbers; they exist to report divergence evidence such as
 * commands the simulation rejects although the original game accepted them.
 */
public interface ReplayCommandObserver {
    /**
     * Called before the command executes.
     *
     * @param game    the simulation state
     * @param command the recorded command
     */
    void onBeforeCommand(@NotNull Game game, @NotNull ReplayCommand command);

    /**
     * Called after the command executed without an exception.
     *
     * @param game    the simulation state
     * @param command the recorded command
     */
    void onAfterCommand(@NotNull Game game, @NotNull ReplayCommand command);
}
