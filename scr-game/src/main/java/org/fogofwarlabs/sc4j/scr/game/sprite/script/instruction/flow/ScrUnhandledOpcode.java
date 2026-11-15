package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.flow;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrPreviewSupport;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code __2d}, which the interpreter does not handle: running it fails. A bounded update does not run it.
 */
public final class ScrUnhandledOpcode extends ScrInstruction {
    /**
     * Creates a {@link ScrUnhandledOpcode} instance.
     *
     * @param decoded the decoded {@code __2d} instruction
     */
    public ScrUnhandledOpcode(@NotNull IscriptInstruction decoded) {
        super(decoded, ScrPreviewSupport.NEVER);
    }


    /**
     * Runs this instruction for the animated object of the context.
     * <p>
     * Fails with an {@link IllegalStateException}.
     *
     * @param context the running animation of one object
     * @return how the program continues
     * @throws UnsupportedOperationException if a bounded update meets the instruction and does not run it
     */
    @Override
    public @NotNull AnimationStep execute(@NotNull AnimationContext context) {
        ScrImageTarget target = getTarget(context);

        this.checkPreviewSupport(context, target);

        throw Exceptions.newIllegalStateException("Unhandled iscript opcode 0x{0} at offset {1}",
                                                  Integer.toHexString(this.getDecoded().getOpcode()),
                                                  this.getDecoded().getOffset());
    }
}
