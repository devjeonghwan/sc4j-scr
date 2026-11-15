package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.flow;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code waitrand}: pauses for a random number of ticks between the two operands.
 */
public final class ScrWaitRandom extends ScrInstruction {
    /**
     * The random source of {@code waitrand}.
     */
    private static final int RANDOM_SOURCE = 3;
    /**
     * The mask retaining the low byte of a random value.
     */
    private static final int BYTE_MASK = 0xFF;

    private final int minimum;
    private final int maximum;

    /**
     * Creates a {@link ScrWaitRandom} instance.
     *
     * @param decoded the decoded {@code waitrand} instruction
     */
    public ScrWaitRandom(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.minimum = decoded.getOperands()[0];
        this.maximum = decoded.getOperands()[1];
    }


    /**
     * Runs this instruction for the animated object of the context.
     * <p>
     * Draws one value from the random source of {@code waitrand}; the wait timer becomes the minimum plus the low byte of the value
     * modulo the range size, minus one, as an unsigned byte.
     *
     * @param context the running animation of one object
     * @return how the program continues
     */
    @Override
    public @NotNull AnimationStep execute(@NotNull AnimationContext context) {
        ScrImageTarget target = getTarget(context);

        int draw = target.getGame().getRandom().next(RANDOM_SOURCE) & BYTE_MASK;
        int wait = (this.minimum + draw % (this.maximum - this.minimum + 1) - 1) & BYTE_MASK;

        context.setWait(wait + 1);

        return AnimationStep.WAIT;
    }
}
