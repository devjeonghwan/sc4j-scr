package org.fogofwarlabs.sc4j.scr.game.sprite.script;

import org.fogofwarlabs.sc4j.engine.animation.InstructionBudget;
import org.jetbrains.annotations.NotNull;

/**
 * Holds the state of one bounded invocation of the iscript interpreter: the instruction budget that the invocation and the
 * Init scripts of the images it creates share, and the nesting depth of those image creations. The interpreter reuses its
 * invocations ({@link #reset}).
 */
public final class IscriptExecution {
    private final @NotNull InstructionBudget budget;
    private int creationDepth;

    /**
     * Creates an {@link IscriptExecution} instance.
     *
     * @param instructionLimit the instruction budget of the invocation, at least {@code 0}
     */
    public IscriptExecution(int instructionLimit) {
        this.budget        = new InstructionBudget(instructionLimit);
        this.creationDepth = 0;
    }

    /**
     * @return the instruction budget shared by the invocation and its nested image Init scripts
     */
    public @NotNull InstructionBudget getBudget() {
        return this.budget;
    }

    /**
     * @return the number of image creations whose Init script is running
     */
    public int getCreationDepth() {
        return this.creationDepth;
    }


    /**
     * Prepares the invocation for a new run: a full budget and no running image creation.
     *
     * @param instructionLimit the instruction budget of the invocation, at least {@code 0}
     */
    void reset(int instructionLimit) {
        this.budget.reset(instructionLimit);
        this.creationDepth = 0;
    }

    /**
     * Counts an image creation whose Init script starts.
     */
    void enterCreation() {
        this.creationDepth++;
    }

    /**
     * Counts an image creation whose Init script ended or failed.
     */
    void leaveCreation() {
        this.creationDepth--;
    }
}
