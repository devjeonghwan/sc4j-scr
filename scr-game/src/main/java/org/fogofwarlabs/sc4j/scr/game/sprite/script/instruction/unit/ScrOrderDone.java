package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.unit;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrPreviewSupport;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code orderdone}: clears order signal bits of the unit.
 */
public final class ScrOrderDone extends ScrInstruction {
    private final int signal;

    /**
     * Creates a {@link ScrOrderDone} instance.
     *
     * @param decoded the decoded {@code orderdone} instruction
     */
    public ScrOrderDone(@NotNull IscriptInstruction decoded) {
        super(decoded, ScrPreviewSupport.WITH_UNIT);

        this.signal = decoded.getOperands()[0];
    }


    /**
     * Runs this instruction for the animated object of the context.
     *
     * @param context the running animation of one object
     * @return how the program continues
     * @throws UnsupportedOperationException if a bounded update meets the instruction and does not run it
     */
    @Override
    public @NotNull AnimationStep execute(@NotNull AnimationContext context) {
        ScrImageTarget target = getTarget(context);

        this.checkPreviewSupport(context, target);

        Unit unit = target.getUnit();

        if (unit != null) {
            unit.setOrderSignal(unit.getOrderSignal() & ~this.signal);
        }

        return AnimationStep.CONTINUE;
    }
}
