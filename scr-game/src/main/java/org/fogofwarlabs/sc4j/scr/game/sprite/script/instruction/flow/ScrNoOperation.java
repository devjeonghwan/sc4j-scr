package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.flow;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Runs an opcode without effect: {@code __0c}, {@code __3e} and {@code __43}.
 */
public final class ScrNoOperation extends ScrInstruction {
    /**
     * Creates a {@link ScrNoOperation} instance.
     *
     * @param decoded the decoded opcode instruction
     */
    public ScrNoOperation(@NotNull IscriptInstruction decoded) {
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
        return AnimationStep.CONTINUE;
    }
}
