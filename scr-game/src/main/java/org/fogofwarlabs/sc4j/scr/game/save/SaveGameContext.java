package org.fogofwarlabs.sc4j.scr.game.save;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Gives the parts of a saved game the objects they write or restore: the game, the runtime that runs it and the
 * live session that issues its commands.
 */
public final class SaveGameContext {
    private final @NotNull ReplayRuntime runtime;
    private final @Nullable LiveGame liveGame;

    /**
     * Creates a {@link SaveGameContext} instance.
     *
     * @param runtime  the started runtime of the game
     * @param liveGame the live session, or {@code null} for a game without one (a replay restored in tests)
     * @throws IllegalArgumentException if the runtime has not started successfully
     */
    public SaveGameContext(@NotNull ReplayRuntime runtime, @Nullable LiveGame liveGame) {
        if (runtime.getSession() == null || runtime.getFailure() != null) {
            throw Exceptions.newIllegalArgumentException("A saved game needs a successfully started runtime.");
        }

        this.runtime  = runtime;
        this.liveGame = liveGame;
    }

    /**
     * @return the game
     */
    public @NotNull Game getGame() {
        return this.runtime.getGame();
    }

    /**
     * @return the runtime that runs the game
     */
    public @NotNull ReplayRuntime getRuntime() {
        return this.runtime;
    }

    /**
     * @return the live session, or {@code null} for a game without one
     */
    public @Nullable LiveGame getLiveGame() {
        return this.liveGame;
    }
}
