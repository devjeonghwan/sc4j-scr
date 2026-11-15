package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.production.ScrRequirementSystem;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Recovers the failure code that computer routines read after the production test ({@link ScrRequirementSystem#checkBuild})
 * rejected a type.
 */
public final class ComputerRequirements {
    private final @NotNull Game game;

    /**
     * Creates a {@link ComputerRequirements} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public ComputerRequirements(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Repeats the production test for a type the caller reported as rejected and returns its failure code.
     *
     * @param type     the requested type
     * @param owner    the requesting owner
     * @param producer the producing unit
     * @return the failure code the production test leaves for the computer routines
     * @throws IllegalStateException if the production test accepts the type
     */
    int getFailureCode(int type, int owner, @NotNull Unit producer) {
        int result = this.game.getRequirements().checkBuild(producer, type, owner);

        if (ScrRequirementSystem.isAccepted(result)) {
            throw Exceptions.newIllegalStateException("The computer production test accepts a type the caller reported as rejected: {0}", type);
        }

        return ScrRequirementSystem.getFailureCode(result);
    }
}
