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
 * Runs {@code turnrand}: draws a random number, also without a unit context, and turns the unit counterclockwise for one
 * of four outcomes and clockwise otherwise.
 */
public final class ScrTurnRandom extends ScrInstruction {
    /**
     * The random source of {@code turnrand}.
     */
    private static final int RANDOM_SOURCE = 6;
    /**
     * The number of {@code turnrand} outcomes; one of them turns counterclockwise.
     */
    private static final int RANDOM_CHOICES = 4;
    /**
     * The {@code turnrand} outcome selecting a counterclockwise turn.
     */
    private static final int COUNTERCLOCKWISE_CHOICE = 1;

    private final int turns;

    /**
     * Creates a {@link ScrTurnRandom} instance.
     *
     * @param decoded the decoded {@code turnrand} instruction
     */
    public ScrTurnRandom(@NotNull IscriptInstruction decoded) {
        super(decoded);

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

        if (target.getGame().getRandom().next(RANDOM_SOURCE) % RANDOM_CHOICES == COUNTERCLOCKWISE_CHOICE) {
            if (unit != null) {
                unit.resetDirection(target.getGame(), unit.getHeading() - FlingyMotionUtils.DIRECTION_THIRTY_SECOND_TURN * this.turns);
            }
        } else if (unit != null) {
            unit.resetDirection(target.getGame(), unit.getHeading() + FlingyMotionUtils.DIRECTION_THIRTY_SECOND_TURN * this.turns);
        }

        return AnimationStep.CONTINUE;
    }
}
