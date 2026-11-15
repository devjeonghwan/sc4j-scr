package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.unit;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code sigorder}: sets order signal bits of the unit, or signals the bullet being updated without a unit context.
 */
public final class ScrSignalOrder extends ScrInstruction {
    private final int signal;

    /**
     * Creates a {@link ScrSignalOrder} instance.
     *
     * @param decoded the decoded {@code sigorder} instruction
     */
    public ScrSignalOrder(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.signal = decoded.getOperands()[0];
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
            unit.setOrderSignal(unit.getOrderSignal() | this.signal);
        } else {
            target.getGame().getProjectilePhase().signal(this.signal);
        }

        return AnimationStep.CONTINUE;
    }
}
