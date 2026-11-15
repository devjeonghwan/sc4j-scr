package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.flow;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationInstruction;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.jetbrains.annotations.NotNull;

/**
 * Runs a byte above the last iscript opcode: a one-byte instruction without operands and without effect, which a bounded
 * update runs like any other.
 */
public final class ScrSkipByte extends AnimationInstruction {
    /**
     * The one instance: the instruction holds no value.
     */
    public static final @NotNull ScrSkipByte INSTANCE = new ScrSkipByte();

    /**
     * Creates the {@link ScrSkipByte} instance.
     */
    private ScrSkipByte() {
    }


    /**
     * Runs this instruction for the animated object of the context.
     * <p>
     * Continues at the next byte.
     *
     * @param context the running animation of one object
     * @return how the program continues
     */
    @Override
    public @NotNull AnimationStep execute(@NotNull AnimationContext context) {
        return AnimationStep.CONTINUE;
    }
}
