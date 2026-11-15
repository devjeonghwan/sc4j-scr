package org.fogofwarlabs.sc4j.scr.game.replay;

import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

/**
 * Handles a replay command at its recorded frame boundary.
 */
@FunctionalInterface
public interface ReplayCommandHandler {
    /**
     * Executes the command before the simulation advances the current frame.
     *
     * @param game    the simulation state
     * @param command the replay command
     */
    void execute(@NotNull Game game, @NotNull ReplayCommand command);
}
