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
 * Runs {@code turnccwise} and {@code turncwise}: turns the unit by the operand in frame directions.
 */
public final class ScrTurn extends ScrInstruction {
    private final int sign;
    private final int turns;

    /**
     * Creates a {@link ScrTurn} instance.
     *
     * @param decoded the decoded {@code turnccwise} or {@code turncwise} instruction
     * @param sign    {@code -1} for {@code turnccwise}, {@code 1} for {@code turncwise}
     */
    public ScrTurn(@NotNull IscriptInstruction decoded, int sign) {
        super(decoded);

        this.sign  = sign;
        this.turns = decoded.getOperands()[0];
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
            unit.resetDirection(target.getGame(), unit.getHeading() + this.sign * FlingyMotionUtils.DIRECTION_THIRTY_SECOND_TURN * this.turns);
        }

        return AnimationStep.CONTINUE;
    }
}
