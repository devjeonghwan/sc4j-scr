package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.branch;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.engine.animation.BranchInstruction;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptModeOneInterpreter;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrModeOneStep;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code randcondjmp}: draws a random number, also without a unit context, and jumps when its low byte does not exceed
 * the chance.
 */
public final class ScrRandomJump extends ScrInstruction implements BranchInstruction, ScrModeOneStep {
    /**
     * The random source of {@code randcondjmp}.
     */
    private static final int RANDOM_SOURCE = 7;
    /**
     * The mask retaining the low byte of a random value.
     */
    private static final int BYTE_MASK = 0xFF;

    private final int chance;
    private final int targetPosition;

    /**
     * Creates a {@link ScrRandomJump} instance.
     *
     * @param decoded        the decoded {@code randcondjmp} instruction
     * @param targetPosition the program position of the branch target
     */
    public ScrRandomJump(@NotNull IscriptInstruction decoded, int targetPosition) {
        super(decoded);

        this.chance         = decoded.getOperands()[0];
        this.targetPosition = targetPosition;
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
     * Draws one value from the random source of {@code randcondjmp} and jumps to the target if its low byte is at most the chance.
     *
     * @param context the running animation of one object
     * @return how the program continues
     */
    @Override
    public @NotNull AnimationStep execute(@NotNull AnimationContext context) {
        ScrImageTarget target = getTarget(context);

        if ((target.getGame().getRandom().next(RANDOM_SOURCE) & BYTE_MASK) <= this.chance) {
            context.jump(this.targetPosition);
        }

        return AnimationStep.CONTINUE;
    }

    /**
     * Runs this instruction in mode one on the scratch state of the interpreter, whose program counter is the offset of the
     * instruction.
     * <p>
     * Draws one value from the random source of {@code randcondjmp}, as there is no mode test, and jumps if its low byte is
     * at most the chance.
     *
     * @param interpreter  the mode-one interpreter
     * @param game         the game, whose random generator the instruction may draw
     * @param unit         the unit whose speed modifiers the instruction may apply
     * @param nextPosition the offset after the instruction
     */
    @Override
    public void stepModeOne(@NotNull IscriptModeOneInterpreter interpreter, @NotNull Game game, @NotNull Unit unit, int nextPosition) {
        if ((game.getRandom().next(RANDOM_SOURCE) & BYTE_MASK) <= this.chance) {
            interpreter.setProgramPosition(this.targetPosition);
        } else {
            interpreter.setProgramPosition(nextPosition);
        }
    }
}
