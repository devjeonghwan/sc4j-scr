package org.fogofwarlabs.sc4j.scr.game.sprite.script;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.animation.AnimationInstruction;
import org.fogofwarlabs.sc4j.engine.animation.AnimationProgram;
import org.fogofwarlabs.sc4j.engine.animation.instruction.Jump;
import org.fogofwarlabs.sc4j.engine.animation.instruction.Wait;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrModeOneStep;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.load.ScrIscriptOffsets;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Runs the mode-one path of the iscript interpreter on a scratch script state, as the Walking speed sampler of unit initialization and the movement
 * prediction do. It walks the game's iscript program ({@link GameData#getIscriptProgram}); the scratch state keeps iscript offsets, converted
 * through the program's offset map ({@link GameData#getIscriptOffsets}).
 * <p>
 * In mode one every opcode only consumes its operands and goes on, including {@code end}, the damage and attack opcodes and the conditional jumps,
 * which fall through. The exceptions are {@code wait}, which ends the step, {@code goto}, {@code call} and {@code return}, {@code move}, which yields
 * its modified speed, and {@code randcondjmp}, which has no mode test: it draws its random number and jumps when the low byte does not exceed the
 * chance. An opcode above the last opcode is one byte.
 */
public final class IscriptModeOneInterpreter {
    /**
     * The mask retaining the unsigned wait byte and the low random byte.
     */
    private static final int BYTE_MASK = 0xFF;
    /**
     * The mask retaining an unsigned WORD iscript offset.
     */
    private static final int WORD_MASK = 0xFFFF;

    private int programCounter;
    private int returnAddress;
    private int wait;

    private int remainingInstructions;
    private int speed;
    private @Nullable ScrIscriptOffsets offsets;

    /**
     * Creates a {@link IscriptModeOneInterpreter} instance with a zeroed script state.
     */
    public IscriptModeOneInterpreter() {
        this.programCounter = 0;
        this.returnAddress  = 0;
        this.wait           = 0;

        this.remainingInstructions = 0;
        this.speed                 = 0;
        this.offsets               = null;
    }

    /**
     * @return the scratch program counter
     */
    public int getProgramCounter() {
        return this.programCounter;
    }

    /**
     * @return the scratch return address
     */
    public int getReturnAddress() {
        return this.returnAddress;
    }

    /**
     * @return the scratch wait byte
     */
    int getWait() {
        return this.wait;
    }


    /**
     * Sets the scratch program counter.
     *
     * @param programCounter the iscript offset
     */
    public void setProgramCounter(int programCounter) {
        this.programCounter = programCounter;
    }

    /**
     * Sets the scratch program counter to the offset of a program position, as an instruction of the running step continues.
     *
     * @param position the program position
     * @throws IllegalStateException if no step runs
     */
    public void setProgramPosition(int position) {
        this.programCounter = this.requireOffsets().getOffset(position);
    }

    /**
     * Sets the scratch return address to the offset of a program position, as {@code call} stores it.
     *
     * @param position the program position
     * @throws IllegalStateException if no step runs
     */
    public void setReturnPosition(int position) {
        this.returnAddress = this.requireOffsets().getOffset(position);
    }

    private @NotNull ScrIscriptOffsets requireOffsets() {
        ScrIscriptOffsets map = this.offsets;

        if (map == null) {
            throw Exceptions.newIllegalStateException("No mode-one step runs.");
        }

        return map;
    }

    /**
     * Sets the speed the running step returns, as {@code move} yields it.
     *
     * @param speed the modified movement speed
     */
    public void setSpeed(int speed) {
        this.speed = speed;
    }

    /**
     * Clears the scratch return address.
     */
    void clearReturnAddress() {
        this.returnAddress = 0;
    }

    /**
     * Clears the scratch wait byte.
     */
    void clearWait() {
        this.wait = 0;
    }


    /**
     * Starts a run with a copied script state and a fresh instruction budget.
     *
     * @param programCounter   the iscript offset
     * @param returnAddress    the return address
     * @param wait             the wait byte
     * @param instructionLimit the instruction budget shared by every following step
     * @throws IllegalArgumentException if the budget is not positive
     */
    public void start(int programCounter, int returnAddress, int wait, int instructionLimit) {
        if (instructionLimit <= 0) {
            throw Exceptions.newIllegalArgumentException("Native mode-one iscript requires a positive instruction budget.");
        }

        this.programCounter = programCounter;
        this.returnAddress  = returnAddress;
        this.wait           = wait;

        this.remainingInstructions = instructionLimit;
    }

    /**
     * Runs one frame of the script: counts a pending wait down, or executes up to the next {@code wait}.
     *
     * @param game  the simulation state, whose random generator {@code randcondjmp} draws
     * @param unit  the unit whose speed modifiers {@code move} applies
     * @param speed the speed returned when no {@code move} executes
     * @return the speed of the last {@code move}, or {@code speed}
     * @throws IOException           if the iscript cannot be read
     * @throws IllegalStateException if the instruction budget is exhausted
     * @throws NullPointerException  if the world was built without game data
     */
    public int step(@NotNull Game game, @NotNull Unit unit, int speed) throws IOException {
        if (this.wait != 0) {
            this.wait = (this.wait - 1) & BYTE_MASK;

            return speed;
        }

        GameData gameData = game.requireGameData();
        AnimationProgram program = gameData.getIscriptProgram();
        ScrIscriptOffsets map = gameData.getIscriptOffsets();

        this.speed   = speed;
        this.offsets = map;

        while (true) {
            if (--this.remainingInstructions < 0) {
                throw Exceptions.newIllegalStateException("Native mode-one iscript reached its instruction limit.");
            }

            int position = map.getPosition(this.programCounter);
            AnimationInstruction instruction = program.get(position);
            int nextPosition = program.getNextPosition(position);

            if (instruction instanceof Wait wait) {
                this.wait           = (wait.getTicks() - 1) & BYTE_MASK;
                this.programCounter = map.getOffset(nextPosition) & WORD_MASK;

                return this.speed;
            }

            // The engine goto jumps, an instruction with a mode-one meaning runs it, and every other one goes on.
            if (instruction instanceof Jump jump) {
                this.programCounter = map.getOffset(jump.getTargetPosition());
            } else if (instruction instanceof ScrModeOneStep modeOneStep) {
                modeOneStep.stepModeOne(this, game, unit, nextPosition);
            } else {
                this.programCounter = map.getOffset(nextPosition);
            }
        }
    }
}
