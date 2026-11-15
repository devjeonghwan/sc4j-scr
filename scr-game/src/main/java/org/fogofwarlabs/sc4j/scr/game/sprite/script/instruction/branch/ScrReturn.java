package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.branch;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptModeOneInterpreter;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrModeOneStep;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code return}: continues at the return position of the image, which stays stored.
 */
public final class ScrReturn extends ScrInstruction implements ScrModeOneStep {
    /**
     * The call stack entry that holds the one return address of an image.
     */
    private static final int RETURN_ENTRY = 0;

    /**
     * Creates a {@link ScrReturn} instance.
     *
     * @param decoded the decoded {@code return} instruction
     */
    public ScrReturn(@NotNull IscriptInstruction decoded) {
        super(decoded);
    }


    /**
     * Runs this instruction for the animated object of the context.
     * <p>
     * Jumps to call stack entry {@code 0} without popping it.
     *
     * @param context the running animation of one object
     * @return how the program continues
     */
    @Override
    public @NotNull AnimationStep execute(@NotNull AnimationContext context) {
        context.jump(context.getCallEntry(RETURN_ENTRY));

        return AnimationStep.CONTINUE;
    }

    /**
     * Runs this instruction in mode one on the scratch state of the interpreter, whose program counter is the offset of the
     * instruction.
     * <p>
     * Continues at the scratch return address.
     *
     * @param interpreter  the mode-one interpreter
     * @param game         the game, whose random generator the instruction may draw
     * @param unit         the unit whose speed modifiers the instruction may apply
     * @param nextPosition the offset after the instruction
     */
    @Override
    public void stepModeOne(@NotNull IscriptModeOneInterpreter interpreter, @NotNull Game game, @NotNull Unit unit, int nextPosition) {
        interpreter.setProgramCounter(interpreter.getReturnAddress());
    }
}
