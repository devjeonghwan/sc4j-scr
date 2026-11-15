package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.branch;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.engine.animation.BranchInstruction;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptModeOneInterpreter;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrModeOneStep;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code call}: stores the position after it as the one return position of the image, overwriting it, and jumps.
 */
public final class ScrCall extends ScrInstruction implements BranchInstruction, ScrModeOneStep {
    /**
     * The call stack entry that holds the one return address of an image.
     */
    private static final int RETURN_ENTRY = 0;

    private final int targetPosition;
    private final int returnPosition;

    /**
     * Creates a {@link ScrCall} instance.
     *
     * @param decoded        the decoded {@code call} instruction
     * @param targetPosition the program position of the branch target
     * @param returnPosition the program position of the offset after the instruction, as a WORD
     */
    public ScrCall(@NotNull IscriptInstruction decoded, int targetPosition, int returnPosition) {
        super(decoded);

        this.targetPosition = targetPosition;
        this.returnPosition = returnPosition;
    }

    /**
     * @return the position the instruction may continue at, {@code 0} up to the program length minus one
     */
    @Override
    public int getTargetPosition() {
        return this.targetPosition;
    }


    /**
     * Runs this instruction for the animated object of the context.
     * <p>
     * Writes call stack entry {@code 0} without changing the call depth: an image keeps one return address.
     *
     * @param context the running animation of one object
     * @return how the program continues
     */
    @Override
    public @NotNull AnimationStep execute(@NotNull AnimationContext context) {
        context.setCallEntry(RETURN_ENTRY, this.returnPosition);
        context.jump(this.targetPosition);

        return AnimationStep.CONTINUE;
    }

    /**
     * Runs this instruction in mode one on the scratch state of the interpreter, whose program counter is the offset of the
     * instruction.
     * <p>
     * Stores the offset after it as the scratch return address and jumps.
     *
     * @param interpreter  the mode-one interpreter
     * @param game         the game, whose random generator the instruction may draw
     * @param unit         the unit whose speed modifiers the instruction may apply
     * @param nextPosition the offset after the instruction
     */
    @Override
    public void stepModeOne(@NotNull IscriptModeOneInterpreter interpreter, @NotNull Game game, @NotNull Unit unit, int nextPosition) {
        interpreter.setReturnPosition(this.returnPosition);
        interpreter.setProgramPosition(this.targetPosition);
    }
}
