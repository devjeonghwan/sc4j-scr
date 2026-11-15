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
 * Runs {@code setfldirect}: sets the heading of the unit to the operand in frame directions.
 */
public final class ScrSetDirection extends ScrInstruction {
    private final int direction;

    /**
     * Creates a {@link ScrSetDirection} instance.
     *
     * @param decoded the decoded {@code setfldirect} instruction
     */
    public ScrSetDirection(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.direction = decoded.getOperands()[0];
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

        if (unit != null) {
            unit.resetDirection(target.getGame(), FlingyMotionUtils.DIRECTION_THIRTY_SECOND_TURN * this.direction);
        }

        return AnimationStep.CONTINUE;
    }
}
