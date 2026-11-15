package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.unit;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.movement.FlingyMotionUtils;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code turn1cwise}: turns a unit without an order target one frame direction clockwise.
 */
public final class ScrTurnOnce extends ScrInstruction {
    /**
     * Creates a {@link ScrTurnOnce} instance.
     *
     * @param decoded the decoded {@code turn1cwise} instruction
     */
    public ScrTurnOnce(@NotNull IscriptInstruction decoded) {
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

        if (unit != null && unit.getOrderTargetUnit() == null) {
            unit.resetDirection(target.getGame(), unit.getHeading() + FlingyMotionUtils.DIRECTION_THIRTY_SECOND_TURN);
        }

        return AnimationStep.CONTINUE;
    }
}
