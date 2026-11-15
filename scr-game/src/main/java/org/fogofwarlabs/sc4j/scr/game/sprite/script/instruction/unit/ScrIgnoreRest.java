package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.unit;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code ignorerest}: a unit without an order target goes to idle and the program goes on; otherwise the image pauses
 * and runs the opcode again.
 */
public final class ScrIgnoreRest extends ScrInstruction {
    /**
     * The ticks of the {@code ignorerest} pause: the wait timer becomes {@code 10}, so the opcode runs again eleven updates
     * later.
     */
    private static final int REST_WAIT_TICKS = 11;

    /**
     * Creates a {@link ScrIgnoreRest} instance.
     *
     * @param decoded the decoded {@code ignorerest} instruction
     */
    public ScrIgnoreRest(@NotNull IscriptInstruction decoded) {
        super(decoded);
    }


    /**
     * Runs this instruction for the animated object of the context.
     *
     * @param context the running animation of one object
     * @return how the program continues
     */
    @Override
    public @NotNull AnimationStep execute(@NotNull AnimationContext context) {
        ScrImageTarget target = getTarget(context);

        Unit unit = target.getUnit();

        // A unit without a target unit returns to idle and execution continues.
        if (unit != null && unit.getOrderTargetUnit() == null) {
            target.getExecutor().runToIdle(unit, getRemainingInstructions(context));

            return AnimationStep.CONTINUE;
        }

        context.setWait(REST_WAIT_TICKS);
        context.jump(context.getPosition());

        return AnimationStep.WAIT;
    }
}
