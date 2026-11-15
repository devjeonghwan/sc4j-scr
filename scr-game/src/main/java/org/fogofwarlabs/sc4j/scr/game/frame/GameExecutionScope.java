package org.fogofwarlabs.sc4j.scr.game.frame;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;

/**
 * Tracks construction and sequential frame execution without executable protection state.
 */
public final class GameExecutionScope {
    private boolean initializationActive;
    private int framePhase;
    private boolean commandDispatchActive;

    /**
     * Creates a {@link GameExecutionScope} instance with initial construction active, outside any frame phase and command dispatch.
     */
    public GameExecutionScope() {
        this.initializationActive  = true;
        this.framePhase            = -1;
        this.commandDispatchActive = false;
    }

    /**
     * @return {@code true} if initial construction is active, {@code false} otherwise
     */
    public boolean isInitializationActive() {
        return this.initializationActive;
    }

    /**
     * @return the active phase index, or {@code -1} outside a frame phase
     */
    public int getFramePhase() {
        return this.framePhase;
    }

    /**
     * @return {@code true} if recorded-command dispatch is active, {@code false} otherwise
     */
    public boolean isCommandDispatchActive() {
        return this.commandDispatchActive;
    }


    /**
     * Begins a sequential frame phase after construction has ended.
     *
     * @param phaseIndex the nonnegative phase invocation index
     * @throws IllegalStateException    if construction, command dispatch or another frame phase is active
     * @throws IllegalArgumentException if the index is negative
     */
    public void beginFramePhase(int phaseIndex) {
        if (this.initializationActive || this.framePhase >= 0 || this.commandDispatchActive) {
            throw Exceptions.newIllegalStateException("Game execution phases must not overlap.");
        }

        if (phaseIndex < 0) {
            throw Exceptions.newIllegalArgumentException("Game phase index must be nonnegative.");
        }

        this.framePhase = phaseIndex;
    }

    /**
     * Ends a frame phase, including one interrupted after partial state changes.
     */
    public void endFramePhase() {
        this.framePhase = -1;
    }


    /**
     * Begins recorded-command dispatch before the frame counter increment and frame execution, with the game random stream enabled by the caller.
     *
     * @throws IllegalStateException if a frame phase or another dispatch is active
     */
    public void beginCommandDispatch() {
        if (this.framePhase >= 0 || this.commandDispatchActive) {
            throw Exceptions.newIllegalStateException("Command dispatch must not overlap a frame phase or another dispatch.");
        }

        this.commandDispatchActive = true;
    }

    /**
     * Ends recorded-command dispatch, including one interrupted after partial state changes.
     */
    public void endCommandDispatch() {
        this.commandDispatchActive = false;
    }


    /**
     * Requires construction, recorded-command dispatch or frame execution to be active.
     *
     * @throws UnsupportedOperationException if no scope is active
     */
    public void requireActiveScope() {
        if (!this.initializationActive && this.framePhase < 0 && !this.commandDispatchActive) {
            throw Exceptions.newUnsupportedOperationException("Game execution requires an explicitly active phase.");
        }
    }

    /**
     * Ends initial construction; repeated calls leave it ended.
     */
    public void finishInitialization() {
        this.initializationActive = false;
    }
}
