package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.unit;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrPreviewSupport;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code setflspeed}: sets the top speed of the unit.
 */
public final class ScrSetTopSpeed extends ScrInstruction {
    /**
     * The mask retaining an unsigned WORD.
     */
    private static final int WORD_MASK = 0xFFFF;

    private final int topSpeed;

    /**
     * Creates a {@link ScrSetTopSpeed} instance.
     *
     * @param decoded the decoded {@code setflspeed} instruction
     */
    public ScrSetTopSpeed(@NotNull IscriptInstruction decoded) {
        super(decoded, ScrPreviewSupport.WITH_UNIT);

        this.topSpeed = decoded.getOperands()[0];
    }


    /**
     * Runs this instruction for the animated object of the context.
     * <p>
     * Fails with an {@link IllegalStateException} without a unit context.
     *
     * @param context the running animation of one object
     * @return how the program continues
     * @throws UnsupportedOperationException if a bounded update meets the instruction and does not run it
     */
    @Override
    public @NotNull AnimationStep execute(@NotNull AnimationContext context) {
        ScrImageTarget target = getTarget(context);

        this.checkPreviewSupport(context, target);

        this.requireUnit(target).setTopSpeed(this.topSpeed & WORD_MASK);

        return AnimationStep.CONTINUE;
    }
}
