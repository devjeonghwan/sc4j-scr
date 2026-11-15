package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.computer.ComputerScriptContext;
import org.fogofwarlabs.sc4j.engine.player.Player;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the running script record of the aiscript interpreter ({@link ComputerScripts}) for the instruction it executes: the computer-player
 * phase, the record, its owner and program, the iscript budget of the update, and the opcode with the program counter of its BYTE. The
 * interpreter keeps one context and fills it when it starts a record ({@link #begin}) and before each opcode ({@link #setInstruction}), so
 * the dispatch allocates nothing.
 */
final class ScrComputerScriptContext implements ComputerScriptContext {
    private final @NotNull Game game;

    private @Nullable ComputerPlayerPhase phase;
    private int record;
    private int owner;
    private boolean expansion;
    private int instructionLimit;

    private int opcode;
    private int start;

    /**
     * Creates a {@link ScrComputerScriptContext} instance without a running record.
     *
     * @param game the SC:R game of the interpreter
     */
    ScrComputerScriptContext(@NotNull Game game) {
        this.game = game;

        this.phase            = null;
        this.record           = 0;
        this.owner            = 0;
        this.expansion        = false;
        this.instructionLimit = 0;

        this.opcode = 0;
        this.start  = 0;
    }

    /**
     * @return the game whose computer players run
     * @throws IllegalStateException if the SC:R game is not attached to its engine game
     */
    @Override
    public org.fogofwarlabs.sc4j.engine.game.@NotNull Game getGame() {
        return this.game.getEngineGame();
    }

    /**
     * @return the player the running script plays for: the record's owner
     * @throws IllegalStateException if the SC:R game is not attached to its engine game
     */
    @Override
    public @NotNull Player getPlayer() {
        return this.game.getEngineGame().getPlayer(this.owner);
    }

    /**
     * @return the computer-player phase of the running update
     * @throws IllegalStateException if no record runs
     */
    @NotNull ComputerPlayerPhase getPhase() {
        ComputerPlayerPhase current = this.phase;

        if (current == null) {
            throw Exceptions.newIllegalStateException("No computer script record runs.");
        }

        return current;
    }

    /**
     * @return the running script record
     */
    int getRecord() {
        return this.record;
    }

    /**
     * @return the record's owner
     */
    int getOwner() {
        return this.owner;
    }

    /**
     * @return {@code true} if the record runs the expansion program, {@code false} otherwise
     */
    boolean isExpansion() {
        return this.expansion;
    }

    /**
     * @return the iscript budget for issued orders
     */
    int getInstructionLimit() {
        return this.instructionLimit;
    }

    /**
     * @return the opcode being executed
     */
    int getOpcode() {
        return this.opcode;
    }

    /**
     * @return the program counter of the opcode BYTE, for a retry
     */
    int getStart() {
        return this.start;
    }


    /**
     * Starts the opcodes of a record.
     *
     * @param phase            the owning computer phase
     * @param record           the script record
     * @param owner            the record's owner
     * @param expansion        whether the record runs the expansion program
     * @param instructionLimit the iscript budget for issued orders
     */
    void begin(@NotNull ComputerPlayerPhase phase, int record, int owner, boolean expansion, int instructionLimit) {
        this.phase            = phase;
        this.record           = record;
        this.owner            = owner;
        this.expansion        = expansion;
        this.instructionLimit = instructionLimit;
    }

    /**
     * Sets the opcode the interpreter executes next.
     *
     * @param opcode the opcode
     * @param start  the program counter of the opcode BYTE
     */
    void setInstruction(int opcode, int start) {
        this.opcode = opcode;
        this.start  = start;
    }
}
